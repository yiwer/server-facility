package cn.code91.facility.web.upload;

import cn.code91.facility.autoconfigure.FacilityWebAutoConfiguration;
import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.web.test.EmbeddedServletApplication;
import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.Environment;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UploadHttpContractTest {
    @TempDir Path directory;

    @Test @Timeout(30)
    void realMultipartAtTheApplicationBudgetPreservesBytesAndUsesSharedHttpErrors() throws Exception {
        Path root = Files.createDirectory(directory.resolve("uploads"));
        Path parts = Files.createDirectory(directory.resolve("parts"));
        try (var app = EmbeddedServletApplication.start(directory.resolve("server"), new Class<?>[]{WebConfiguration.class},
                "upload.root=" + root, "upload.parts=" + parts, "facility.web.access-log.enabled=false");
             var client = HttpClient.newHttpClient()) {
            for (boolean chunked : new boolean[]{false, true}) {
                for (String payload : new String[]{"", "éé", "ééx", "ééxy"}) {
                    byte[] wire = multipart(payload.getBytes(StandardCharsets.UTF_8));
                    var publisher = chunked ? HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(wire))
                            : HttpRequest.BodyPublishers.ofByteArray(wire);
                    var response = client.send(HttpRequest.newBuilder(app.uri("/upload"))
                            .timeout(Duration.ofSeconds(5)).header("Content-Type", "multipart/form-data; boundary=upload-boundary")
                            .POST(publisher).build(), HttpResponse.BodyHandlers.ofString());
                    int bytes = payload.getBytes(StandardCharsets.UTF_8).length;
                    assertThat(response.statusCode()).as("chunked=%s bytes=%s", chunked, bytes)
                            .isEqualTo(bytes == 0 ? 400 : bytes > 5 ? 413 : 200);
                    if (bytes > 0 && bytes <= 5) {
                        assertThat(response.body()).matches("[0-9a-f-]{36}\\.upload:[0-9a-f]+");
                        assertThat(response.body()).endsWith(":" + HexFormat.of().formatHex(payload.getBytes(StandardCharsets.UTF_8)));
                    } else {
                        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
                        assertThat(response.body()).doesNotContain(root.toString(), "Exception");
                    }
                }
            }
        }
        try (var files = Files.list(root)) { assertThat(files).isEmpty(); }
        try (var files = Files.list(parts)) { assertThat(files).isEmpty(); }
    }

    private static byte[] multipart(byte[] body) throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        bytes.write(("--upload-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"fake.pdf\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        bytes.write(body); bytes.write("\r\n--upload-boundary--\r\n".getBytes(StandardCharsets.US_ASCII));
        return bytes.toByteArray();
    }

    @Configuration(proxyBeanMethods = false) @EnableWebMvc
    @Import({FacilityWebAutoConfiguration.class, Endpoint.class})
    static class WebConfiguration {
        @Bean DispatcherServlet dispatcherServlet() { return new DispatcherServlet(); }
        @Bean DispatcherServletRegistrationBean dispatcherRegistration(DispatcherServlet servlet, Environment environment) {
            var registration = new DispatcherServletRegistrationBean(servlet, "/");
            registration.setMultipartConfig(new MultipartConfigElement(environment.getRequiredProperty("upload.parts"), 1024, 2048, 0));
            return registration;
        }
        @Bean MultipartResolver multipartResolver() { return new StandardServletMultipartResolver(); }
    }

    @RestController static class Endpoint {
        private final Path root;
        Endpoint(Environment environment) { this.root = Path.of(environment.getRequiredProperty("upload.root")); }
        @PostMapping("/upload") String upload(@RequestPart MultipartFile file) throws Exception {
            var result = SafeUpload.saveFile(file, root, 5, Set.of("text/plain"));
            if (result.isErr()) throw new ErrorResponseException(result.getErr().isErrorType(FacilityErrorType.FILE_SIZE_EXCEEDED)
                    ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST);
            Path path = result.get();
            try { return path.getFileName() + ":" + HexFormat.of().formatHex(Files.readAllBytes(path)); }
            finally { Files.delete(path); }
        }
    }
}
