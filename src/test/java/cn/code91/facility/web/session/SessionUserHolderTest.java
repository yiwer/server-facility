package cn.code91.facility.web.session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SessionUserHolder - ThreadLocal 用户上下文")
class SessionUserHolderTest {

    @AfterEach
    void cleanup() {
        SessionUserHolder.clear();
    }

    @Test
    @DisplayName("初始态:无用户 → getUser empty,isLoggedIn false")
    void initial_noUser_emptyAndNotLoggedIn() {
        assertThat(SessionUserHolder.getUser(String.class)).isEmpty();
        assertThat(SessionUserHolder.isLoggedIn()).isFalse();
    }

    @Test
    @DisplayName("set/get 带类型往返,isLoggedIn true")
    void setAndGet_typed() {
        SessionUserHolder.setUser("alice");
        assertThat(SessionUserHolder.getUser(String.class)).contains("alice");
        assertThat(SessionUserHolder.isLoggedIn()).isTrue();
    }

    @Test
    @DisplayName("类型不匹配 → empty(但 isLoggedIn 仍 true)")
    void getUser_typeMismatch_empty() {
        SessionUserHolder.setUser("alice");
        assertThat(SessionUserHolder.getUser(Integer.class)).isEmpty();
        assertThat(SessionUserHolder.isLoggedIn()).isTrue();
    }

    @Test
    @DisplayName("clear 后 empty + isLoggedIn false")
    void clear_thenEmptyAndLoggedOut() {
        SessionUserHolder.setUser("alice");
        SessionUserHolder.clear();
        assertThat(SessionUserHolder.getUser(String.class)).isEmpty();
        assertThat(SessionUserHolder.isLoggedIn()).isFalse();
    }

    @Test
    @DisplayName("跨线程隔离:主线程已 set,新线程内 getUser empty")
    void threadIsolation_newThreadSeesEmpty() throws InterruptedException {
        SessionUserHolder.setUser("alice");

        AtomicReference<Object> otherThreadUser = new AtomicReference<>("sentinel");
        AtomicBoolean otherThreadLoggedIn = new AtomicBoolean(true);
        Thread t = new Thread(() -> {
            otherThreadUser.set(SessionUserHolder.getUser(String.class).orElse(null));
            otherThreadLoggedIn.set(SessionUserHolder.isLoggedIn());
        });
        t.start();
        t.join();

        assertThat(otherThreadUser.get()).isNull();
        assertThat(otherThreadLoggedIn.get()).isFalse();
        // 主线程不受影响
        assertThat(SessionUserHolder.getUser(String.class)).contains("alice");
    }
}
