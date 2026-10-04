package com.example.fixtures;

import com.example.api.ApiApplication;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.web.context.request.*;

/** Test-classpath host for the real application. No fault switch exists in production code. */
public final class CommandRecoveryHost {
    private static Properties settings;
    private static final AtomicBoolean USED = new AtomicBoolean();

    public static void main(String[] args) throws Exception {
        settings = new Properties();
        try (var input = Files.newInputStream(Path.of(URI.create(args[0])))) { settings.load(input); }
        URI facility = cn.code91.facility.result.Result.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        if (!Files.isRegularFile(Path.of(facility)) || !facility.getPath().endsWith(".jar"))
            throw new IllegalStateException("Recovery host must consume the ordinary facility jar");
        var options = new ArrayList<String>(List.of("--server.port=0", "--server.address=127.0.0.1",
                "--spring.main.banner-mode=off", "--logging.level.root=WARN", "--server.shutdown=immediate",
                "--server.tomcat.basedir=tomcat",
                "--spring.profiles.active=local", "--spring.datasource.username=postgres", "--spring.datasource.password=",
                "--spring.datasource.url=" + settings.getProperty("database"),
                "--spring.datasource.hikari.data-source-properties.ApplicationName=" + settings.getProperty("application"),
                "--spring.security.oauth2.resourceserver.jwt.issuer-uri=" + settings.getProperty("issuer"),
                "--spring.security.oauth2.resourceserver.jwt.audiences=secured-api",
                "--app.security.resource-uri=https://api.example.test"));
        if (Boolean.parseBoolean(settings.getProperty("longLocks")))
            options.add("--spring.datasource.hikari.data-source-properties.options=-c statement_timeout=8000 -c lock_timeout=5000");
        try (var context = (ServletWebServerApplicationContext) new SpringApplication(ApiApplication.class, FaultConfiguration.class)
                .run(options.toArray(String[]::new))) {
            try (var ready = control(); var output = new DataOutputStream(ready.getOutputStream())) {
                output.writeUTF("ready"); output.writeLong(ProcessHandle.current().pid());
                output.writeInt(context.getWebServer().getPort()); output.writeUTF(facility.toASCIIString()); output.flush();
            }
            // The owning test closes stdin or sends one byte to request ordinary context shutdown.
            System.in.read();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class FaultConfiguration {
        @Bean @ConfigurationProperties("spring.datasource.hikari")
        HookedDataSource dataSource(DataSourceProperties properties) {
            // One normally bound Hikari object is shared by Flyway, JdbcClient and the transaction manager.
            return properties.initializeDataSourceBuilder().type(HookedDataSource.class).build();
        }
        @Bean FilterRegistrationBean<Filter> interruptedResponse() {
            Filter filter = (request, response, chain) -> {
                var http = (HttpServletRequest) request;
                if (!designated(http, "response")) { chain.doFilter(request, response); return; }
                var wrapper = new HttpServletResponseWrapper((HttpServletResponse) response) {
                    private ServletOutputStream stream;
                    @Override public ServletOutputStream getOutputStream() throws IOException {
                        if (stream != null) return stream;
                        ServletOutputStream delegate = super.getOutputStream();
                        stream = new ServletOutputStream() {
                            private boolean first = true;
                            @Override public boolean isReady() { return delegate.isReady(); }
                            @Override public void setWriteListener(WriteListener listener) { delegate.setWriteListener(listener); }
                            @Override public void write(int value) throws IOException { write(new byte[]{(byte) value}, 0, 1); }
                            @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                                if (first && length != 0) {
                                    first = false;
                                    int prefix = Math.min(length, 16);
                                    delegate.write(bytes, offset, prefix); delegate.flush();
                                    // Signal only after actual bytes have been flushed to the HTTP connection.
                                    signal("response");
                                    delegate.write(bytes, offset + prefix, length - prefix);
                                } else delegate.write(bytes, offset, length);
                            }
                            @Override public void flush() throws IOException { delegate.flush(); }
                            @Override public void close() throws IOException { delegate.close(); }
                        };
                        return stream;
                    }
                };
                chain.doFilter(request, wrapper);
            };
            var registration = new FilterRegistrationBean<>(filter);
            registration.setOrder(Integer.MIN_VALUE + 100); return registration;
        }
    }

    public static final class HookedDataSource extends HikariDataSource {
        public HookedDataSource() {}
        @Override public Connection getConnection() throws SQLException {
            Connection delegate = super.getConnection();
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(CommandRecoveryHost.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        boolean commit = method.getName().equals("commit") && method.getParameterCount() == 0;
                        if (commit && designated("before")) signal("before");
                        Object result;
                        try { result = method.invoke(delegate, arguments); }
                        catch (InvocationTargetException wrapped) { throw wrapped.getCause(); }
                        if (commit && designated("after")) signal("after");
                        return result;
                    });
        }
    }
    private static boolean designated(String phase) {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request
                && designated(request.getRequest(), phase);
    }
    private static boolean designated(HttpServletRequest request, String phase) {
        return request.getMethod().equals("POST") && request.getRequestURI().matches("/api/workspaces/[0-9a-f-]{36}/notes")
                && settings.getProperty("secret").equals(request.getHeader("X-Test-Command-Gate"))
                && phase.equals(request.getHeader("X-Test-Command-Phase")) && !USED.get();
    }
    private static Socket control() throws IOException {
        var socket = new Socket();
        try { socket.connect(new InetSocketAddress("127.0.0.1", Integer.parseInt(settings.getProperty("controlPort"))), 2000);
            socket.setSoTimeout(15000); return socket;
        } catch (IOException failure) { socket.close(); throw failure; }
    }
    private static void signal(String phase) throws IOException {
        if (!USED.compareAndSet(false, true)) throw new IOException("The designated commit/response gate was reused");
        try (var socket = control(); var output = new DataOutputStream(socket.getOutputStream())) {
            output.writeUTF(phase); output.writeLong(ProcessHandle.current().pid()); output.flush();
            if (socket.getInputStream().read() != 1) throw new IOException("Owning test abandoned the designated gate");
        }
    }
}
