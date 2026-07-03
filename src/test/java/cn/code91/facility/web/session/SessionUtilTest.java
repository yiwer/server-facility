package cn.code91.facility.web.session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SessionUtil - RequestContextHolder 支撑的 Session 操作")
class SessionUtilTest {

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static void bindNewRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @Test
    @DisplayName("getSession 不建新:初始无 session → empty")
    void getSession_doesNotCreate() {
        bindNewRequest();
        assertThat(SessionUtil.getSession()).isEmpty();
        // 读操作不产生副作用:再读依旧 empty
        assertThat(SessionUtil.getSession()).isEmpty();
    }

    @Test
    @DisplayName("setAttribute 自动建 session,getAttribute 取回")
    void setAttribute_autoCreatesSession() {
        bindNewRequest();
        SessionUtil.setAttribute("k", "v");
        assertThat(SessionUtil.getSession()).isPresent();
        assertThat(SessionUtil.getAttribute("k", String.class)).contains("v");
    }

    @Test
    @DisplayName("getAttribute 类型不匹配 → empty")
    void getAttribute_typeMismatch_empty() {
        bindNewRequest();
        SessionUtil.setAttribute("k", "v");
        assertThat(SessionUtil.getAttribute("k", Integer.class)).isEmpty();
    }

    @Test
    @DisplayName("removeAttribute 后属性 empty")
    void removeAttribute_removes() {
        bindNewRequest();
        SessionUtil.setAttribute("k", "v");
        SessionUtil.removeAttribute("k");
        assertThat(SessionUtil.getAttribute("k", String.class)).isEmpty();
    }

    @Test
    @DisplayName("invalidate 后 getSession → empty(失效 session 被重置)")
    void invalidate_sessionGone() {
        bindNewRequest();
        SessionUtil.setAttribute("k", "v");
        assertThat(SessionUtil.getSession()).isPresent();

        SessionUtil.invalidate();

        assertThat(SessionUtil.getSession()).isEmpty();
        assertThat(SessionUtil.getAttribute("k", String.class)).isEmpty();
    }

    @Test
    @DisplayName("未设 RequestAttributes:一切读操作 empty,写操作静默无害")
    void noRequestAttributes_allEmpty() {
        RequestContextHolder.resetRequestAttributes();
        assertThat(SessionUtil.getSession()).isEmpty();
        assertThat(SessionUtil.getOrBuildSession()).isEmpty();
        assertThat(SessionUtil.getAttribute("k", String.class)).isEmpty();
        assertThat(SessionUtil.getSessionId()).isEmpty();
        SessionUtil.setAttribute("k", "v"); // no-op,不抛异常
        SessionUtil.removeAttribute("k");
        SessionUtil.invalidate();
    }

    @Test
    @DisplayName("getOrBuildSession 创建 session")
    void getOrBuildSession_creates() {
        bindNewRequest();
        assertThat(SessionUtil.getSession()).isEmpty();
        assertThat(SessionUtil.getOrBuildSession()).isPresent();
        assertThat(SessionUtil.getSession()).isPresent();
    }

    @Test
    @DisplayName("getSessionId:无 session empty,建后返回 id")
    void getSessionId_presentAfterCreation() {
        bindNewRequest();
        assertThat(SessionUtil.getSessionId()).isEmpty();
        SessionUtil.setAttribute("k", "v");
        assertThat(SessionUtil.getSessionId()).isPresent();
        assertThat(SessionUtil.getSessionId().get()).isNotBlank();
    }
}
