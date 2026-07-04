package cn.code91.facility.web.idempotency;

import cn.code91.facility.idempotency.InMemoryIdempotencyStore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 幂等端到端(standalone MockMvc:{@link IdempotencyFilter} + {@link IdempotencyInterceptor} +
 * {@link InMemoryIdempotencyStore} 完整装配链路)。
 * <p>
 * 验证完整幂等语义真实生效——而不仅仅是 preHandle/afterCompletion 各自的单元行为:同一
 * {@code Idempotency-Key} 连续两次请求,第二次必须原样返回第一次的响应,且 controller
 * 方法体绝不重复执行。{@code MockMvcBuilders.standaloneSetup(...).addFilters(...)} 注册的
 * 过滤器由 {@code MockFilterChain} 包在最外层、{@code addInterceptors(...)} 注册的拦截器
 * 由内层 {@code TestDispatcherServlet} 承载,这与生产 Servlet 容器"Filter 包 Servlet"的
 * 调用顺序一致,因此本测试是对生产装配行为的真实模拟。
 * </p>
 */
@DisplayName("幂等端到端(standalone MockMvc:Filter+拦截器+InMemoryIdempotencyStore,完整幂等——同 key 返首次响应,controller 只执行一次)")
class IdempotencyEndToEndTest {

    /** 测试夹具:@Idempotent 标注的支付接口,execCount 记录方法体真实执行次数。 */
    @RestController
    static class PayController {

        final AtomicInteger execCount = new AtomicInteger(0);

        @Idempotent
        @PostMapping("/pay")
        public PayResult pay() {
            return new PayResult(execCount.incrementAndGet());
        }
    }

    record PayResult(int id) {
    }

    private MockMvc mockMvcFor(PayController controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new IdempotencyInterceptor(new InMemoryIdempotencyStore(1000), 60_000))
                .addFilters(new IdempotencyFilter())
                .build();
    }

    @Test
    @DisplayName("同 Idempotency-Key 连发两次 POST:第一次执行 controller,第二次返回完全相同响应体,controller 只执行一次")
    void sameKey_secondRequestReturnsCachedResponse_controllerRunsOnce() throws Exception {
        PayController controller = new PayController();
        MockMvc mockMvc = mockMvcFor(controller);

        mockMvc.perform(post("/pay").header("Idempotency-Key", "order-1"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":1}"));

        mockMvc.perform(post("/pay").header("Idempotency-Key", "order-1"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":1}"));

        assertThat(controller.execCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("缺失 Idempotency-Key 请求头:400,controller 不执行")
    void missingKey_returns400() throws Exception {
        PayController controller = new PayController();
        MockMvc mockMvc = mockMvcFor(controller);

        mockMvc.perform(post("/pay"))
                .andExpect(status().isBadRequest());

        assertThat(controller.execCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("不同 Idempotency-Key 各自独立执行 controller")
    void differentKeys_eachExecutes() throws Exception {
        PayController controller = new PayController();
        MockMvc mockMvc = mockMvcFor(controller);

        mockMvc.perform(post("/pay").header("Idempotency-Key", "order-a"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":1}"));

        mockMvc.perform(post("/pay").header("Idempotency-Key", "order-b"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":2}"));

        assertThat(controller.execCount.get()).isEqualTo(2);
    }
}
