package cn.code91.facility.web.filter;

import cn.code91.facility.web.session.SessionUserHolder;
import cn.code91.facility.web.util.ClientIpPolicy;
import jakarta.servlet.*;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import org.springframework.mock.web.*;
import java.security.Principal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class RequestBoundaryScopeTest {
    @AfterEach void clear() { SessionUserHolder.clear(); MDC.clear(); }
    @Test void nestedErrorRestoresItsOuterScopeAndTopLevelAlwaysRemovesStaleIdentity() throws Exception {
        var request = new MockHttpServletRequest(); request.setUserPrincipal(() -> "alice");
        var response = new MockHttpServletResponse();
        var filter = new FacilityRequestContextFilter(new TraceIdFilter(new FacilityWebTraceProperties()), new ClientIpPolicy(List.of()));
        SessionUserHolder.setUser("stale-user"); MDC.put("traceId", "outer-observation"); MDC.put("host-key", "retained");
        assertThatThrownBy(() -> filter.doFilter(request, response, (rq, rs) -> {
            assertThat(SessionUserHolder.getUser(Principal.class).orElseThrow().getName()).isEqualTo("alice");
            SessionUserHolder.setUser("explicit-outer-user");
            request.setDispatcherType(DispatcherType.ERROR); request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/broken");
            filter.doFilter(request, response, (nestedRequest, nestedResponse) -> {
                assertThat(SessionUserHolder.getUser(Principal.class).orElseThrow().getName()).isEqualTo("alice");
                SessionUserHolder.setUser("nested-only"); MDC.put("traceId", "nested-only");
            });
            assertThat(SessionUserHolder.getUser(String.class)).contains("explicit-outer-user");
            assertThat(MDC.get("traceId")).isEqualTo("outer-observation");
            throw new ServletException("controlled failure");
        })).isInstanceOf(ServletException.class);
        assertThat(SessionUserHolder.getUser(Object.class)).isEmpty();
        assertThat(MDC.get("traceId")).isEqualTo("outer-observation"); assertThat(MDC.get("host-key")).isEqualTo("retained");
    }

    @Test void invalidConfigurationFailsBeforeServingAndMutablePropertiesCannotChangeAnInstalledTraceScope() throws Exception {
        var props = new FacilityWebTraceProperties();
        for (String name : new String[]{"", "a".repeat(129), "X:Trace", "X Trace"}) {
            props.setHeaderName(name); assertThatIllegalArgumentException().isThrownBy(() -> new TraceIdFilter(props));
        }
        props.setHeaderName("X-Trace-Id"); props.setMdcKey("bad key");
        assertThatIllegalArgumentException().isThrownBy(() -> new TraceIdFilter(props));
        props.setMdcKey("traceId"); var installed = new TraceIdFilter(props);
        props.setHeaderName("Invalid\r\n"); props.setMdcKey("changed");
        var request = new MockHttpServletRequest(); request.addHeader("X-Trace-Id", "frozen");
        installed.doFilter(request, new MockHttpServletResponse(), (rq, rs) -> assertThat(MDC.get("traceId")).isEqualTo("frozen"));
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test void failingHostOriginPolicyStillClearsEntryThreadAndErrorDispatchCanRecover() throws Exception {
        var request = new MockHttpServletRequest();
        var filter = new FacilityRequestContextFilter(null, new ClientIpPolicy(List.of()) {
            @Override public String resolve(jakarta.servlet.http.HttpServletRequest req) { throw new IllegalStateException("PRIVATE-POLICY"); }
        });
        SessionUserHolder.setUser("stale");
        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (rq, rs) -> fail("must not reach chain")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(SessionUserHolder.getUser(Object.class)).isEmpty();
        request.setDispatcherType(DispatcherType.ERROR); request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/failure");
        filter.doFilter(request, new MockHttpServletResponse(), (rq, rs) -> {
            assertThat(cn.code91.facility.web.util.RequestUtil.getClientIp((jakarta.servlet.http.HttpServletRequest) rq)).isEqualTo("unknown");
            assertThat(SessionUserHolder.getUser(Object.class)).isEmpty();
        });
    }
}
