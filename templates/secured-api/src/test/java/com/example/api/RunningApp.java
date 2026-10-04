package com.example.api;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.core.ResolvableType;

final class RunningApp implements AutoCloseable {
    private static final Object JVM_LOGGING_CONFIGURATION = new Object();
    final ServletWebServerApplicationContext context;
    final HttpClient client;
    final String base;
    RunningApp(TestIssuer issuer, String... extra) {
        this(issuer, new Class<?>[0], extra);
    }
    RunningApp(TestIssuer issuer, Class<?>[] sources, String... extra) {
        this(issuer, sources, application -> {}, extra);
    }
    RunningApp(TestIssuer issuer, java.util.function.Consumer<SpringApplication> configure, String... extra) {
        this(issuer, new Class<?>[0], configure, extra);
    }
    private RunningApp(TestIssuer issuer, Class<?>[] sources, java.util.function.Consumer<SpringApplication> configure, String... extra) {
        var args = new ArrayList<>(List.of("--server.port=0", "--server.address=127.0.0.1", "--spring.main.banner-mode=off",
                "--logging.level.root=WARN", "--server.shutdown=immediate", "--spring.profiles.active=local",
                "--spring.datasource.url=" + Postgres.sharedUrl(), "--spring.datasource.username=postgres", "--spring.datasource.password=",
                "--spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuer.issuer(),
                "--spring.security.oauth2.resourceserver.jwt.audiences=secured-api",
                "--app.security.resource-uri=https://api.example.test"));
        for (String option : extra) {
            String name = option.substring(0, option.indexOf('=') + 1);
            args.removeIf(existing -> existing.startsWith(name)); args.add(option);
        }
        var types = new ArrayList<Class<?>>(); types.add(ApiApplication.class); types.addAll(Arrays.asList(sources));
        var application = new SpringApplication(types.toArray(Class<?>[]::new)); configure.accept(application);
        // These test applications share one SLF4J/Logback context. Coordinate only its Boot
        // configuration events; context refresh, Flyway callbacks and HTTP remain concurrent.
        application.setListeners(application.getListeners().stream().map(listener ->
                listener instanceof LoggingApplicationListener logging
                        ? new SharedLoggingListener(logging) : listener).toList());
        context = (ServletWebServerApplicationContext) application.run(args.toArray(String[]::new));
        client = HttpClient.newHttpClient();
        base = "http://127.0.0.1:" + context.getWebServer().getPort();
    }
    HttpResponse<String> get(String path, String token, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (headers.length != 0) request.headers(headers);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    @Override public void close() { client.close(); context.close(); }

    private record SharedLoggingListener(LoggingApplicationListener delegate) implements GenericApplicationListener {
        @Override public int getOrder() { return delegate.getOrder(); }
        @Override public boolean supportsEventType(ResolvableType type) { return delegate.supportsEventType(type); }
        @Override public boolean supportsSourceType(Class<?> type) { return delegate.supportsSourceType(type); }
        @Override public void onApplicationEvent(ApplicationEvent event) {
            synchronized (JVM_LOGGING_CONFIGURATION) { delegate.onApplicationEvent(event); }
        }
    }
}
