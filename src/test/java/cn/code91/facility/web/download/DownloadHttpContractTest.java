package cn.code91.facility.web.download;

import cn.code91.facility.autoconfigure.FacilityIdempotencyAutoConfiguration;
import cn.code91.facility.autoconfigure.FacilityWebAutoConfiguration;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import cn.code91.facility.web.test.EmbeddedServletApplication;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.RandomAccessFile;
import java.io.IOException;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DownloadHttpContractTest {
    @TempDir Path directory;

    @ParameterizedTest @ValueSource(booleans={false,true}) @Timeout(30)
    void slowConsumerCancellationOrResetStopsTheFileProducerAndReleasesItsFile(boolean resetSocket) throws Exception {
        Path file = directory.resolve("large.bin");
        try (var data = new RandomAccessFile(file.toFile(), "rw")) { data.setLength(64L * 1024 * 1024); }
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            var endpoint = app.context().getBean(Endpoints.class);
            endpoint.file = file;
            if (resetSocket) {
                try (var socket = new Socket()) {
                    socket.setReceiveBufferSize(1024);
                    socket.setSoTimeout(5000);
                    socket.connect(new java.net.InetSocketAddress("127.0.0.1", app.uri("/").getPort()));
                    socket.getOutputStream().write("GET /file HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    assertThat(socket.getInputStream().readNBytes(1024)).hasSize(1024);
                    assertThat(endpoint.finished.getCount()).isEqualTo(1);
                    socket.setSoLinger(true, 0); // actual peer RST after a deliberately tiny receive window
                }
            } else {
                var response = client.send(HttpRequest.newBuilder(app.uri("/file")).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                try (var body = response.body()) {
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(body.readNBytes(1)).hasSize(1);
                    assertThat(endpoint.finished.getCount()).isEqualTo(1);
                    // Closing the body is JDK HttpClient's public consumer cancellation boundary.
                }
            }
            assertThat(endpoint.finished.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(endpoint.result.get().isErr()).isTrue();
            assertThat(endpoint.written.get()).isBetween(1L, 64L * 1024 * 1024 - 1);
            Files.delete(file); // still-running application cannot retain the download's file handle
        }
    }

    @Test @Timeout(20)
    void headAndEmptyFilePreserveHttpBodySemantics() throws Exception {
        Path file = directory.resolve("empty.bin");
        Files.createFile(file);
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            app.context().getBean(Endpoints.class).file = file;
            for (String method : new String[]{"HEAD", "GET"}) {
                var response = client.send(HttpRequest.newBuilder(app.uri("/file")).method(method, HttpRequest.BodyPublishers.noBody()).build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).isEmpty();
                assertThat(response.headers().firstValue("Content-Length")).contains("0");
            }
        }
    }

    private EmbeddedServletApplication application() {
        return EmbeddedServletApplication.start(directory.resolve("tomcat"), new Class<?>[]{WebConfiguration.class},
                "facility.web.access-log.enabled=false");
    }
    @Configuration(proxyBeanMethods=false) @EnableWebMvc
    @Import({FacilityIdempotencyAutoConfiguration.class, FacilityWebAutoConfiguration.class, Endpoints.class,
            org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration.class})
    static class WebConfiguration { @Bean DispatcherServlet dispatcherServlet() { return new DispatcherServlet(); } }
    @RestController static class Endpoints {
        volatile Path file;
        final CountDownLatch finished = new CountDownLatch(1);
        final AtomicReference<Result<Void, WrappedError>> result = new AtomicReference<>();
        final AtomicLong written = new AtomicLong();
        @GetMapping("/file") void file(HttpServletResponse response) throws Exception {
            var output = response.getOutputStream();
            var counted = new ServletOutputStream() {
                public boolean isReady() { return output.isReady(); }
                public void setWriteListener(WriteListener listener) { output.setWriteListener(listener); }
                public void write(int value) throws IOException { output.write(value); written.incrementAndGet(); }
                public void write(byte[] bytes, int offset, int length) throws IOException { output.write(bytes, offset, length); written.addAndGet(length); }
                public void flush() throws IOException { output.flush(); }
            };
            try { result.set(HttpFileResponses.download(new HttpServletResponseWrapper(response) {
                @Override public ServletOutputStream getOutputStream() { return counted; }
            }, file.toFile())); }
            finally { finished.countDown(); }
        }
    }
}
