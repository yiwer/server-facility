package cn.code91.facility.web.util;

import cn.code91.facility.web.test.EmbeddedServletApplication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class CookieHtmlHttpTest {
    @TempDir Path directory;

    @Test
    void actualHttpRetainsDefaultAndHostCustomizedCookieScopesThroughDeletion() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(app.uri("/cookies")).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            var headers = response.headers().allValues("Set-Cookie");
            assertThat(headers).hasSize(4);
            assertThat(headers.get(0)).startsWith("theme=dark;")
                    .contains("Path=/", "Max-Age=3600", "Secure", "HttpOnly", "SameSite=Lax").doesNotContain("Domain=");
            assertThat(headers.get(1)).startsWith("preview=on;").contains("Path=/", "HttpOnly", "SameSite=Lax")
                    .doesNotContain("Secure", "Max-Age=", "Domain=");
            assertThat(headers.get(2)).startsWith("host-choice=on;")
                    .contains("Path=/portal", "Domain=example.com", "Max-Age=60", "Secure", "Partitioned", "SameSite=None")
                    .doesNotContain("HttpOnly");
            assertThat(headers.get(3)).startsWith("host-choice=;")
                    .contains("Path=/portal", "Domain=example.com", "Max-Age=0", "Secure", "Partitioned", "SameSite=None")
                    .doesNotContain("HttpOnly");
        }
    }

    @Test
    void servletExposedDuplicatesAreRejectedForOneOrMultipleCookieHeaders() throws Exception {
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            for (boolean multipleHeaders : new boolean[]{false, true}) {
                var request = HttpRequest.newBuilder(app.uri("/read?name=theme")).timeout(Duration.ofSeconds(5));
                if (multipleHeaders) request.header("Cookie", "theme=one").header("Cookie", "theme=two");
                else request.header("Cookie", "theme=one; theme=two");
                var response = client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.body()).isEqualTo("ambiguous");
            }
            var response = client.send(HttpRequest.newBuilder(app.uri("/read?name=Theme"))
                    .header("Cookie", "theme=one; theme=two; Theme=upper").timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("upper");
        }
    }

    @Test
    void applicationKeepsRawBusinessInputUnlessItExplicitlySelectsHtmlCleaning() throws Exception {
        String raw = "备注: 2 < 3 + <b>bold</b><script>PRIVATE()</script>";
        try (var app = application(); var client = HttpClient.newHttpClient()) {
            for (String mode : new String[]{"raw", "html"}) {
                var response = client.send(HttpRequest.newBuilder(app.uri("/content?mode=" + mode))
                        .header("Content-Type", "text/plain;charset=UTF-8").timeout(Duration.ofSeconds(5))
                        .POST(HttpRequest.BodyPublishers.ofString(raw)).build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).isEqualTo(mode.equals("raw") ? raw : "备注: 2 &lt; 3 + <b>bold</b>");
            }
        }
    }

    private EmbeddedServletApplication application() {
        return EmbeddedServletApplication.start(directory.resolve("tomcat"), new Class<?>[]{WebConfiguration.class});
    }
    @Configuration(proxyBeanMethods=false) @EnableWebMvc
    @Import({Endpoints.class, org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration.class})
    static class WebConfiguration {
        @Bean DispatcherServlet dispatcherServlet() { return new DispatcherServlet(); }
        @Bean ResponseCookie hostCookie() {
            return ResponseCookie.from("host-choice", "on").domain("example.com").path("/portal")
                    .secure(true).httpOnly(false).partitioned(true).sameSite("None").maxAge(60).build();
        }
    }
    @RestController static class Endpoints {
        private final ResponseCookie policy;
        Endpoints(ResponseCookie policy) { this.policy = policy; }
        @GetMapping("/cookies") void cookies(HttpServletResponse response) {
            CookieUtil.addCookie(response, "theme", "dark", 3600);
            CookieUtil.addCookie(response, "preview", "on", -1, false);
            CookieUtil.addCookie(response, policy);
            CookieUtil.removeCookie(response, policy);
        }
        @GetMapping("/read") ResponseEntity<String> read(HttpServletRequest request, @RequestParam String name) {
            try { return ResponseEntity.ok(CookieUtil.getCookie(request, name).orElse("absent")); }
            catch (IllegalArgumentException ambiguous) { return ResponseEntity.badRequest().body("ambiguous"); }
        }
        @PostMapping(value="/content", produces="text/plain;charset=UTF-8")
        String content(@RequestBody String value, @RequestParam String mode) {
            return mode.equals("html") ? XssUtil.clean(value) : value;
        }
    }
}
