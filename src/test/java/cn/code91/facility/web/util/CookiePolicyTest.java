package cn.code91.facility.web.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.ResponseCookie;
import java.time.Duration;
import java.util.List;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CookiePolicyTest {
    @Test
    void domainAndExtremeDurationPoliciesAreExplicitBeforeResponseMutation() {
        for (String domain : new String[]{"example.com.", "-bad.example", "foo..example", "中文.example", "example.com:443", "example.com\r\nX: bad"}) {
            var response = new MockHttpServletResponse();
            assertThatThrownBy(() -> CookieUtil.addCookie(response,
                    ResponseCookie.from("c", "v").path("/").domain(domain).build()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(response.getHeaderNames()).isEmpty();
        }
        for (long seconds : new long[]{Long.MIN_VALUE, Long.MAX_VALUE}) {
            var response = new MockHttpServletResponse();
            var policy = ResponseCookie.from("c", "v").path("/").maxAge(Duration.ofSeconds(seconds)).build();
            assertThatThrownBy(() -> CookieUtil.addCookie(response, policy)).isInstanceOf(IllegalArgumentException.class);
            assertThat(response.getHeaderNames()).isEmpty();
        }
        var response = new MockHttpServletResponse();
        var policy = ResponseCookie.from("__Secure-choice", "v").path("/portal").domain(".example.com")
                .secure(true).sameSite("Strict").build();
        CookieUtil.addCookie(response, policy);
        assertThat(response.getHeader("Set-Cookie")).contains("Domain=.example.com", "Path=/portal", "Secure", "SameSite=Strict");
    }

    @Test
    void validBoundaryFlagsLifetimesAndAsciiValuesArePreservedWithoutAutomaticEncoding() {
        for (long age : new long[]{-1, 0, 1, 34560000}) {
            var response = new MockHttpServletResponse();
            var policy = ResponseCookie.from("__hOsT-choice", "a+b%20").path("/").secure(true)
                    .sameSite("lax").maxAge(age).build();
            CookieUtil.addCookie(response, policy);
            assertThat(response.getHeader("Set-Cookie")).startsWith("__hOsT-choice=a+b%20;")
                    .contains("Secure", "Path=/", "SameSite=lax").doesNotContain("HttpOnly", "Domain=");
        }
        for (String value : new String[]{null, ""}) {
            var response = new MockHttpServletResponse();
            CookieUtil.addCookie(response, "empty", value, -1);
            assertThat(response.getHeader("Set-Cookie")).startsWith("empty=;");
        }
    }

    @Test
    void invalidWireCharactersAreRejectedInsteadOfEncodedOrTruncated() {
        for (String value : new String[]{"中文", "a b", "a;b", "a,b", "a\r\nb", "\u0000", "\u007f", "a\\b"}) {
            var response = new MockHttpServletResponse();
            assertThatThrownBy(() -> CookieUtil.addCookie(response, "choice", value, -1))
                    .isInstanceOf(IllegalArgumentException.class).hasNoCause();
            assertThat(response.getHeaderNames()).isEmpty();
        }
        for (String path : new String[]{null, "", "relative", "/a;b", "/中文", "/a\r\nb"}) {
            var response = new MockHttpServletResponse();
            assertThatThrownBy(() -> CookieUtil.addCookie(response, "choice", "v", -1, path, true, true))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(response.getHeaderNames()).isEmpty();
        }
        assertThatThrownBy(() -> CookieUtil.addCookie(new MockHttpServletResponse(), "", "v", -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CookieUtil.getCookie(new MockHttpServletRequest(), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CookieUtil.getCookie(null, "c")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CookieUtil.getAllCookies(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CookieUtil.addCookie(new MockHttpServletResponse(), (ResponseCookie) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void legacyInvalidInputsDoNotCopyPrivateContentIntoDiagnostics() {
        var response = new MockHttpServletResponse();
        assertThatThrownBy(() -> CookieUtil.addCookie(response, "PRIVATE-SECRET;", "value", -1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("PRIVATE-SECRET").hasNoCause();
        assertThat(response.getHeaderNames()).isEmpty();
    }

    @Test
    void legacyDeletionUsesTheSameHttpsPolicyAsLegacyCreation() {
        var response = new MockHttpServletResponse();
        CookieUtil.removeCookie(response, "theme", "/portal");
        assertThat(response.getHeader("Set-Cookie")).startsWith("theme=;")
                .contains("Path=/portal", "Max-Age=0", "Secure", "HttpOnly", "SameSite=Lax")
                .doesNotContain("Domain=");
    }

    @Test
    void duplicateTargetCookiesAreRejectedWithoutChangingCaseSensitiveNames() {
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("theme", "first"), new Cookie("theme", "second"), new Cookie("Theme", "upper"));
        assertThatThrownBy(() -> CookieUtil.getCookie(request, "theme"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("first");
        assertThatThrownBy(() -> CookieUtil.getAllCookies(request)).isInstanceOf(IllegalArgumentException.class);
        assertThat(CookieUtil.getCookie(request, "Theme")).contains("upper");
        assertThat(CookieUtil.getCookie(request, "absent")).isEmpty();
        request.setCookies(new Cookie("nullValue", null), new Cookie("empty", ""));
        assertThat(CookieUtil.getCookie(request, "nullValue")).isEmpty();
        assertThat(CookieUtil.getCookie(request, "empty")).contains("");
        assertThat(CookieUtil.getAllCookies(request)).containsEntry("nullValue", null).containsEntry("empty", "");
    }

    @Test
    void completeHeaderBudgetAllows4096AsciiBytesAndRejectsTheNextByte() {
        for (int valueSize : new int[]{4085, 4086}) {
            var response = new MockHttpServletResponse();
            CookieUtil.addCookie(response, ResponseCookie.from("c", "x".repeat(valueSize)).path("/").build());
            assertThat(response.getHeader("Set-Cookie")).hasSize(valueSize + 10);
        }
        for (int valueSize : new int[]{4087, 65536}) {
            var response = new MockHttpServletResponse();
            var tooLarge = ResponseCookie.from("c", "x".repeat(valueSize)).path("/").build();
            assertThatThrownBy(() -> CookieUtil.addCookie(response, tooLarge))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(response.getHeaderNames()).isEmpty();
        }
    }

    @Test
    void browserIncompatibleSecurityScopeOrLifetimeIsRejectedBeforeSending() {
        var invalid = List.of(
                ResponseCookie.from("c", "v").path("/").sameSite("None").build(),
                ResponseCookie.from("c", "v").path("/").partitioned(true).build(),
                ResponseCookie.from("__sEcUrE-c", "v").path("/").build(),
                ResponseCookie.from("__hOsT-c", "v").path("/").secure(true).domain("example.com").build(),
                ResponseCookie.from("__Host-c", "v").path("/portal").secure(true).build(),
                ResponseCookie.from("c", "v").path("relative").build(),
                ResponseCookie.from("c", "v").build(),
                ResponseCookie.from("c", "v").path("/").maxAge(Duration.ofMillis(1500)).build(),
                ResponseCookie.from("c", "v").path("/").maxAge(Duration.ofMillis(-1500)).build(),
                ResponseCookie.from("c", "v").path("/").maxAge(Duration.ofDays(400).plusSeconds(1)).build());
        for (var cookie : invalid) {
            var response = new MockHttpServletResponse();
            response.addHeader("Set-Cookie", "existing=keep");
            assertThatThrownBy(() -> CookieUtil.addCookie(response, cookie))
                    .as("invalid policy for %s", cookie.getName()).isInstanceOf(IllegalArgumentException.class);
            assertThat(response.getHeaders("Set-Cookie")).containsExactly("existing=keep");
        }
        assertThatThrownBy(() -> CookieUtil.addCookie(new MockHttpServletResponse(), "c", "v", -2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidSameSiteCannotInjectAHeaderOrMutateTheResponse() {
        var response = new MockHttpServletResponse();
        var injected = ResponseCookie.from("theme", "dark").path("/")
                .sameSite("Lax; PRIVATE=secret\r\nX-Injected: true").build();
        assertThatThrownBy(() -> CookieUtil.addCookie(response, injected))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("secret");
        assertThat(response.getHeaderNames()).isEmpty();
    }

    @Test
    void deletingAnExplicitCookiePreservesItsCompleteScopeAndOtherHeaders() {
        var response = new MockHttpServletResponse();
        var policy = ResponseCookie.from("theme", "dark").domain("example.com").path("/portal")
                .secure(true).httpOnly(true).sameSite("Strict").maxAge(600).build();
        CookieUtil.addCookie(response, policy);
        CookieUtil.removeCookie(response, policy);
        assertThat(response.getHeaders("Set-Cookie")).hasSize(2);
        assertThat(response.getHeaders("Set-Cookie").get(0)).contains("theme=dark", "Max-Age=600");
        assertThat(response.getHeaders("Set-Cookie").get(1)).startsWith("theme=;")
                .contains("Domain=example.com", "Path=/portal", "Max-Age=0", "Secure", "HttpOnly", "SameSite=Strict");
        assertThat(policy.getValue()).isEqualTo("dark");
    }

    @Test
    void defaultHttpsCookieHasExplicitSameSiteAndHostScope() {
        var response = new MockHttpServletResponse();
        CookieUtil.addCookie(response, "theme", "dark", 3600);
        assertThat(response.getHeader("Set-Cookie"))
                .contains("theme=dark", "Path=/", "Max-Age=3600", "Secure", "HttpOnly", "SameSite=Lax")
                .doesNotContain("Domain=");
    }
}
