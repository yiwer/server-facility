package cn.code91.facility.web.test;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext;

import java.net.URI;
import java.nio.file.Path;

/** Minimal actual Servlet server for consumer HTTP contracts; every caller owns its fixtures. */
public record EmbeddedServletApplication(AnnotationConfigServletWebServerApplicationContext context)
        implements AutoCloseable {
    public static EmbeddedServletApplication start(Path directory, Class<?>[] configurations, String... properties) {
        var context = new AnnotationConfigServletWebServerApplicationContext();
        TestPropertyValues.of(properties).applyTo(context);
        context.registerBean(TomcatServletWebServerFactory.class, () -> {
            var factory = new TomcatServletWebServerFactory(0);
            factory.setBaseDirectory(directory.toFile());
            factory.addConnectorCustomizers(EmbeddedServletApplication::bindLoopback);
            return factory;
        });
        context.register(configurations);
        try {
            context.refresh();
            return new EmbeddedServletApplication(context);
        } catch (RuntimeException | Error failure) {
            context.close();
            throw failure;
        }
    }

    private static void bindLoopback(Connector connector) { connector.setProperty("address", "127.0.0.1"); }

    public URI uri(String path) {
        return URI.create("http://127.0.0.1:" + context.getWebServer().getPort() + path);
    }

    @Override public void close() { context.close(); }
}
