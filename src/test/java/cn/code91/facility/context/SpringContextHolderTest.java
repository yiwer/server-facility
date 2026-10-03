package cn.code91.facility.context;

import cn.code91.facility.error.FacilityErrorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SpringContextHolder - Spring 上下文静态门面")
class SpringContextHolderTest {

    private final SpringContextHolderTestSupport contexts = new SpringContextHolderTestSupport();

    @AfterEach
    void closeContexts() {
        contexts.close();
    }

    private StaticApplicationContext contextWithBean() {
        StaticApplicationContext context = new StaticApplicationContext();
        context.registerSingleton("sampleBean", StringBuilder.class);
        return contexts.refresh(context);
    }

    @Nested
    @DisplayName("未初始化状态")
    class NotInitialized {

        @Test
        void isNotInitialized_beforeInjection_true() {
            assertThat(SpringContextHolder.isNotInitialized()).isTrue();
            assertThat(SpringContextHolder.isInitialized()).isFalse();
            assertThat(SpringContextHolder.getApplicationContext()).isNull();
        }

        @Test
        void getBeanByType_notInitialized_returnsNotInitializedErr() {
            var result = SpringContextHolder.getBean(StringBuilder.class);
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr().isErrorType(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED)).isTrue();
        }

        @Test
        void getBeanByNameAndType_notInitialized_returnsNotInitializedErr() {
            var result = SpringContextHolder.getBean("sampleBean", StringBuilder.class);
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr().isErrorType(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED)).isTrue();
        }

        @Test
        void getBeanByName_notInitialized_returnsNotInitializedErr() {
            var result = SpringContextHolder.getBean("sampleBean");
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr().isErrorType(FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED)).isTrue();
        }

        @Test
        void containsBean_notInitialized_false() {
            assertThat(SpringContextHolder.containsBean("sampleBean")).isFalse();
        }

        @Test
        void getBeanNamesForType_notInitialized_emptyArray() {
            assertThat(SpringContextHolder.getBeanNamesForType(StringBuilder.class)).isEmpty();
        }

        @Test
        void getContextInfo_notInitialized_saysSo() {
            assertThat(SpringContextHolder.getContextInfo()).contains("not initialized");
        }
    }

    @Nested
    @DisplayName("已初始化状态")
    class Initialized {

        @Test
        void getBeanByType_present_returnsOk() {
            contextWithBean();
            var result = SpringContextHolder.getBean(StringBuilder.class);
            assertThat(result.isOk()).isTrue();
            assertThat(result.get()).isInstanceOf(StringBuilder.class);
        }

        @Test
        void getBeanByNameAndType_present_returnsOk() {
            contextWithBean();
            var result = SpringContextHolder.getBean("sampleBean", StringBuilder.class);
            assertThat(result.isOk()).isTrue();
        }

        @Test
        void getBeanByType_missing_returnsGetBeanErrWithTypeArg() {
            contextWithBean();
            var result = SpringContextHolder.getBean(java.util.concurrent.ExecutorService.class);
            assertThat(result.isErr()).isTrue();
            assertThat(result.getErr().isErrorType(FacilityErrorType.CONTEXT_GET_BEAN_ERROR)).isTrue();
            assertThat(result.getErr().getArg(0, String.class))
                    .isEqualTo("java.util.concurrent.ExecutorService");
        }

        @Test
        void containsBean_andBeanNamesForType_reflectRegistry() {
            contextWithBean();
            assertThat(SpringContextHolder.containsBean("sampleBean")).isTrue();
            assertThat(SpringContextHolder.containsBean("absent")).isFalse();
            assertThat(SpringContextHolder.getBeanNamesForType(StringBuilder.class))
                    .containsExactly("sampleBean");
        }

        @Test
        void getContextInfo_initialized_containsBeanCount() {
            contextWithBean();
            assertThat(SpringContextHolder.getContextInfo()).contains("beanCount");
        }
    }

    @Nested
    @DisplayName("生命周期与 CAS")
    class Lifecycle {

        @Test
        void setApplicationContext_duplicateInjection_keepsFirst() {
            StaticApplicationContext first = contextWithBean();
            StaticApplicationContext second = new StaticApplicationContext();
            contexts.refresh(second);

            SpringContextHolder holder = first.getBean(SpringContextHolder.class);
            holder.setApplicationContext(first);
            holder.setApplicationContext(second);

            assertThat(SpringContextHolder.getApplicationContext()).isSameAs(first);
        }

        @Test
        void destroy_clearsContext() {
            var context = contextWithBean();
            SpringContextHolder holder = context.getBean(SpringContextHolder.class);
            assertThat(SpringContextHolder.isInitialized()).isTrue();

            holder.destroy();
            assertThat(SpringContextHolder.isNotInitialized()).isTrue();
        }
    }
}
