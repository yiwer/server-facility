package cn.code91.facility.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.MessageSource;
import java.util.Locale;
import static org.assertj.core.api.Assertions.*;

class HostMessageSourceContractTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FacilityCoreAutoConfiguration.class,
                    FacilityLocaleAutoConfiguration.class, MessageSourceAutoConfiguration.class));

    @Test void bootBasenamesOwnPriorityAndLocaleFallback() {
        runner.withPropertyValues("spring.messages.basename=ticket26-host,i18n/facility-messages",
                "spring.messages.fallback-to-system-locale=false").run(context -> {
            assertThat(context).hasNotFailed();
            MessageSource source = context.getBean("messageSource", MessageSource.class);
            assertThat(source.getMessage("facility.web.error.system", null, Locale.FRENCH))
                    .isEqualTo("Service indisponible");
            assertThat(source.getMessage("facility.web.error.system", null, Locale.JAPANESE))
                    .isEqualTo("Host service unavailable");
            assertThat(source.getMessage("facility.file.not_found", null, Locale.FRENCH))
                    .isEqualTo("File does not exist");
            assertThat(source.getMessage("welcome", new Object[]{"Ada"}, Locale.FRENCH)).isEqualTo("Bonjour Ada");
            assertThat(source.getMessage("unknown", null, "Safe fallback", Locale.FRENCH)).isEqualTo("Safe fallback");
        });
    }
    @Test void hostSourceIsUnambiguousForConstructorInjection() {
        runner.withPropertyValues("spring.messages.basename=ticket26-host")
                .withUserConfiguration(HostConsumer.class).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(Greeter.class).source().getMessage("welcome", new Object[]{"Ada"}, Locale.FRENCH))
                            .isEqualTo("Bonjour Ada");
                });
    }
    @Test void twoApplicationsAndChildUseOnlyTheirOwnMessagePolicy() {
        runner.withBean("messageSource", MessageSource.class, () -> source("first"))
                .run(first -> runner.withBean("messageSource", MessageSource.class, () -> source("second"))
                        .run(second -> {
                            assertThat(first.getMessage("owner", null, Locale.ENGLISH)).isEqualTo("first");
                            assertThat(second.getMessage("owner", null, Locale.FRENCH)).isEqualTo("second");
                            runner.withParent(first.getSourceApplicationContext()).run(child -> {
                                assertThat(child.getMessage("owner", null, Locale.ENGLISH)).isEqualTo("first");
                                assertThat(child.getBeanFactory().containsLocalBean("messageSource")).isTrue();
                            });
                            first.close();
                            assertThat(second.getMessage("owner", null, Locale.ENGLISH)).isEqualTo("second");
                        }));
    }

    @Test void unrelatedModuleSourcesAreNotImplicitDelegates() {
        var invocations = new java.util.concurrent.atomic.AtomicInteger();
        var unrelated = new org.springframework.context.support.AbstractMessageSource() {
            @Override protected java.text.MessageFormat resolveCode(String code, Locale locale) {
                invocations.incrementAndGet(); throw new IllegalStateException("a module with an invalid parent graph");
            }
        };
        runner.withBean("unrelatedModule", MessageSource.class, () -> unrelated).run(context -> {
            assertThat(context.getMessage("facility.file.not_found", null, Locale.ENGLISH)).isEqualTo("File does not exist");
            assertThat(invocations).hasValue(0);
        });
    }

    @Test void hostFormatsUnicodeAndPreservesStandardFormatFailureSemantics() {
        runner.withPropertyValues("spring.messages.basename=ticket26-host", "spring.messages.fallback-to-system-locale=false")
                .run(context -> {
                    String text = "人🌏\r\n".repeat(10000);
                    assertThat(context.getMessage("welcome", new Object[]{text}, Locale.FRENCH)).isEqualTo("Bonjour " + text);
                    assertThatThrownBy(() -> context.getMessage("amount", new Object[]{"not a number"}, Locale.ENGLISH))
                            .isInstanceOf(IllegalArgumentException.class);
                    assertThatThrownBy(() -> context.getMessage("absent", null, Locale.ENGLISH))
                            .isInstanceOf(org.springframework.context.NoSuchMessageException.class);
                });
    }
    private static MessageSource source(String owner) {
        var source = new org.springframework.context.support.StaticMessageSource();
        source.addMessage("owner", Locale.ENGLISH, owner); source.addMessage("owner", Locale.FRENCH, owner);
        return source;
    }

    record Greeter(MessageSource source) {}
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class HostConsumer {
        @org.springframework.context.annotation.Bean Greeter greeter(MessageSource source) { return new Greeter(source); }
    }

}
