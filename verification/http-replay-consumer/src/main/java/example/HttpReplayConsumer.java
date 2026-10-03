package example;

import cn.code91.facility.web.idempotency.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Installed ordinary jar, actual Security wrappers and method authorization; no library test classpath. */
public final class HttpReplayConsumer {
    @SpringBootConfiguration @EnableAutoConfiguration @EnableMethodSecurity
    static class Application {
        @Bean Endpoint endpoint(Permission permission) { return new Endpoint(permission); }
        @Bean Permission permission() { return new Permission(); }
        @Bean InMemoryUserDetailsManager users() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("alice-a").password("{noop}fixture-password").roles("WRITE").build(),
                    User.withUsername("bob-a").password("{noop}fixture-password").roles("WRITE").build(),
                    User.withUsername("alice-b").password("{noop}fixture-password").roles("WRITE").build(),
                    User.withUsername("admin").password("{noop}fixture-password").roles("ADMIN").build());
        }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable()).requestCache(cache -> cache.disable())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                    .httpBasic(org.springframework.security.config.Customizer.withDefaults()).build();
        }
        @Bean IdempotencyAuthorization authorization(Permission permission) {
            return (request, operation, body) -> {
                if (!(request.getUserPrincipal() instanceof Authentication principal) || !permission.allowed(principal))
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                var identity = switch (principal.getName()) {
                    case "alice-a" -> new String[]{"tenant-a", "alice"};
                    case "bob-a" -> new String[]{"tenant-a", "bob"};
                    case "alice-b" -> new String[]{"tenant-b", "alice"};
                    default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                };
                try {
                    // This binary command contract treats every byte as significant.
                    return new IdempotencyAuthorization.Command(identity[0], identity[1],
                            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)));
                } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
            };
        }
    }

    public static class Permission {
        private final AtomicBoolean enabled = new AtomicBoolean(true);
        public boolean allowed(Authentication authentication) {
            return enabled.get() && authentication != null && authentication.isAuthenticated()
                    && authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_WRITE"));
        }
    }

    @RestController public static class Endpoint {
        private final Permission permission;
        private final AtomicInteger effects = new AtomicInteger();
        Endpoint(Permission permission) { this.permission = permission; }
        @Idempotent @PreAuthorize("@permission.allowed(authentication)") @PostMapping("/command")
        public String command(@RequestBody byte[] body, HttpServletRequest request) {
            return request.getUserPrincipal().getName() + ":" + effects.incrementAndGet() + ":" + new String(body, StandardCharsets.UTF_8);
        }
        @Idempotent @PreAuthorize("@permission.allowed(authentication)") @PostMapping("/other-command")
        public String other(@RequestBody byte[] body) { return "other:" + effects.incrementAndGet(); }
        @PreAuthorize("hasRole('ADMIN')") @PostMapping("/permission/{allowed}")
        public String permission(@PathVariable("allowed") boolean allowed) { permission.enabled.set(allowed); return "updated"; }
        @PreAuthorize("hasRole('ADMIN')") @GetMapping("/effects") public String effects() { return Integer.toString(effects.get()); }
    }

    public static void main(String[] args) throws Exception {
        require(HttpReplayConsumer.class.getClassLoader().getResource("cn/code91/facility/web/idempotency/IdempotencyInterceptor.class")
                .getProtocol().equals("jar"), "facility must be loaded from installed ordinary jar");
        for (String absent : List.of("org.junit.jupiter.api.Test", "cn.code91.facility.web.test.EmbeddedServletApplication")) {
            try { Class.forName(absent); throw new AssertionError("test graph leaked"); }
            catch (ClassNotFoundException expected) { }
        }
        for (int cycle = 0; cycle < 2; cycle++) {
            var application = new SpringApplication(Application.class);
            application.setRegisterShutdownHook(false);
            try (var context = application.run("--server.address=127.0.0.1", "--server.port=0", "--spring.main.banner-mode=off",
                    "--logging.level.root=WARN", "--server.tomcat.threads.max=4", "--server.tomcat.threads.min-spare=1",
                    "--server.shutdown=immediate", "--facility.idempotency.max-entries=32"); var client = HttpClient.newHttpClient()) {
                URI origin = URI.create("http://127.0.0.1:" + ((ServletWebServerApplicationContext) context).getWebServer().getPort());
                expect(send(client, origin, "alice-a", "/command", "purchase", "same"), 200, "alice-a:1:purchase");
                expect(send(client, origin, "alice-a", "/command", "purchase", "same"), 200, "alice-a:1:purchase");
                require(send(client, origin, "alice-a", "/command", "changed", "same").statusCode() == 409, "changed binary content must conflict");
                expect(send(client, origin, "bob-a", "/command", "purchase", "same"), 200, "bob-a:2:purchase");
                expect(send(client, origin, "alice-b", "/command", "purchase", "same"), 200, "alice-b:3:purchase");
                expect(send(client, origin, "alice-a", "/other-command", "purchase", "same"), 200, "other:4");
                expect(send(client, origin, "admin", "/permission/false", "", "control"), 200, "updated");
                var denied = send(client, origin, "alice-a", "/command", "purchase", "same");
                require(denied.statusCode() == 403 && !denied.body().contains("alice-a:1"), "current method/resource permission must run before replay");
                expect(send(client, origin, "admin", "/permission/true", "", "control"), 200, "updated");
                expect(send(client, origin, "alice-a", "/command", "purchase", "same"), 200, "alice-a:1:purchase");
                expect(send(client, origin, "admin", "/effects", null, null), 200, "4");
                for (int index = 0; index < 256; index++) {
                    var churn = send(client, origin, "alice-a", "/command", "bounded", "churn-" + index);
                    require(churn.statusCode() == (index < 28 ? 200 : 503), "strict capacity changed execution permission");
                }
                expect(send(client, origin, "alice-a", "/command", "purchase", "same"), 200, "alice-a:1:purchase");
                expect(send(client, origin, "admin", "/effects", null, null), 200, "32");
            }
        }
        System.out.println("HTTP_REPLAY_SECURITY_PASS identity=tenant/actor/route current-permission=revoked/restored capacity=32 churn=512 cycles=2");
    }

    static HttpResponse<String> send(HttpClient client, URI origin, String user, String path, String body, String key) throws Exception {
        var request = HttpRequest.newBuilder(origin.resolve(path)).timeout(Duration.ofSeconds(5))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((user + ":fixture-password").getBytes(StandardCharsets.UTF_8)))
                .header("X-User-Id", "forged-owner").header("X-Tenant", "forged-tenant");
        if (key != null) request.header("Idempotency-Key", key);
        if (body != null) request.header("Content-Type", "application/octet-stream").POST(HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    static void expect(HttpResponse<String> response, int status, String body) {
        require(response.statusCode() == status && response.body().equals(body), "unexpected HTTP result: " + response.statusCode());
    }
    static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
