package cn.code91.facility.web.interceptor;

import cn.code91.facility.web.session.SessionUserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SessionUserClearInterceptor - afterCompletion 清 ThreadLocal (RV2-08)")
class SessionUserClearInterceptorTest {

    @AfterEach void cleanup() { SessionUserHolder.clear(); }

    @Test @DisplayName("afterCompletion 清空 SessionUserHolder")
    void afterCompletionClears() {
        SessionUserHolder.setUser("alice");
        assertThat(SessionUserHolder.isLoggedIn()).isTrue();

        new SessionUserClearInterceptor().afterCompletion(
            new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

        assertThat(SessionUserHolder.isLoggedIn()).isFalse();
    }
}
