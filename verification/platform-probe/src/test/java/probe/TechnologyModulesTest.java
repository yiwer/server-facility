package probe;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.error.ErrorPage;
import org.springframework.boot.web.error.ErrorPageRegistrar;
import org.springframework.boot.web.error.ErrorPageRegistry;
import org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletRegistrationBean;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TechnologyModulesTest {
    @Test
    void actualTechnologyTypesResolveFromTheirTargetModules() {
        // Module assignments were checked against official 4.1.1 JAR entries, not inferred from packages.
        var modules = Map.ofEntries(
                Map.entry(FilterRegistrationBean.class, "spring-boot"),
                Map.entry(ErrorPage.class, "spring-boot"),
                Map.entry(ErrorPageRegistrar.class, "spring-boot"),
                Map.entry(ErrorPageRegistry.class, "spring-boot"),
                Map.entry(MessageSourceAutoConfiguration.class, "spring-boot-autoconfigure"),
                Map.entry(TaskExecutionAutoConfiguration.class, "spring-boot-autoconfigure"),
                Map.entry(JacksonAutoConfiguration.class, "spring-boot-jackson"),
                Map.entry(JsonMapperBuilderCustomizer.class, "spring-boot-jackson"),
                Map.entry(TomcatServletWebServerAutoConfiguration.class, "spring-boot-tomcat"),
                Map.entry(TomcatServletWebServerFactory.class, "spring-boot-tomcat"),
                Map.entry(ServletWebServerApplicationContext.class, "spring-boot-web-server"),
                Map.entry(AnnotationConfigServletWebServerApplicationContext.class, "spring-boot-web-server"),
                Map.entry(DispatcherServletRegistrationBean.class, "spring-boot-webmvc"),
                Map.entry(ErrorMvcAutoConfiguration.class, "spring-boot-webmvc"));
        modules.forEach((type, module) -> {
            var actual = type.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
            assertTrue(actual.endsWith("/" + module + "-4.1.1.jar"), type.getName() + " loaded from " + actual);
        });
    }
}
