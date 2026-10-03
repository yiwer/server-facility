package cn.code91.facility.web.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ClientIpPolicyTest {
    @Test void onlyExplicitTrustedProxiesCanForwardAndTraversalStopsAtFirstUntrustedHop() {
        var policy = new ClientIpPolicy(List.of("10.20.0.0/16", "2001:db8:abcd::/48"));
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.20.1.2");
        request.addHeader("X-Forwarded-For", "192.0.2.99, 198.51.100.5, 10.20.2.3");
        assertThat(policy.resolve(request)).isEqualTo("198.51.100.5");
        request.setRemoteAddr("203.0.113.9");
        assertThat(policy.resolve(request)).isEqualTo("203.0.113.9");
        request.setRemoteAddr("2001:db8:abcd::1");
        request.removeHeader("X-Forwarded-For");
        request.addHeader("X-Forwarded-For", "2001:db8:1234::9");
        assertThat(policy.resolve(request)).isEqualTo("2001:db8:1234:0:0:0:0:9");
    }

    @Test void forwardingIsSingleBoundedLiteralOnlyAndNeverReinterpretsContainerForwarding() {
        var policy = new ClientIpPolicy(List.of("127.0.0.1/32"));
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        for (String invalid : List.of("", " ", "unknown", "localhost", "1.2.3", "01.2.3.4",
                "[2001:db8::1]", "fe80::1%eth0", "192.0.2.1:443", "1.2.3.4\r\nforged", "é💣", "garbage, 192.0.2.2",
                "1.1.1.1,".repeat(32) + "1.1.1.1", " ".repeat(2042) + "1.1.1.1")) {
            request.removeHeader("X-Forwarded-For"); request.addHeader("X-Forwarded-For", invalid);
            assertThat(policy.resolve(request)).as("invalid %s", invalid).isEqualTo("127.0.0.1");
        }
        request.removeHeader("X-Forwarded-For");
        request.addHeader("X-Forwarded-For", "192.0.2.1"); request.addHeader("X-Forwarded-For", "192.0.2.2");
        assertThat(policy.resolve(request)).isEqualTo("127.0.0.1");
        request.removeHeader("X-Forwarded-For"); request.addHeader("X-Forwarded-For", "192.0.2.1");
        request.setAttribute("org.apache.tomcat.request.forwarded", true);
        assertThat(policy.resolve(request)).isEqualTo("127.0.0.1");
    }

    @Test void literalAndCidrBudgetsHaveDeterministicBoundaries() {
        var policy = new ClientIpPolicy(List.of("127.0.0.1", "2001:db8:abcd::/48"));
        var request = new MockHttpServletRequest(); request.setRemoteAddr("127.0.0.1");
        for (int hops : new int[]{31, 32, 33}) {
            request.removeHeader("X-Forwarded-For");
            request.addHeader("X-Forwarded-For", "198.51.100.7" + ",127.0.0.1".repeat(hops - 1));
            assertThat(policy.resolve(request)).isEqualTo(hops <= 32 ? "198.51.100.7" : "127.0.0.1");
        }
        for (int length : new int[]{2047, 2048, 2049}) {
            request.removeHeader("X-Forwarded-For"); request.addHeader("X-Forwarded-For", " ".repeat(length - 11) + "192.0.2.123");
            // Literal above is 11 bytes, padding is part of the received header budget.
            assertThat(policy.resolve(request)).isEqualTo(length <= 2048 ? "192.0.2.123" : "127.0.0.1");
        }
        request.removeHeader("X-Forwarded-For"); request.addHeader("X-Forwarded-For", "::ffff:192.0.2.9");
        assertThat(policy.resolve(request)).isEqualTo("192.0.2.9");
        for (String cidr : List.of("localhost/8", "10.0.0.1/-1", "10.0.0.1/33", "::/129", "10.0.0.1/8/9", "::ffff:192.0.2.1/120", "10.0.0.1/+8"))
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() -> new ClientIpPolicy(List.of(cidr)));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(() -> new ClientIpPolicy(java.util.Collections.nCopies(129, "127.0.0.1")));
        assertThat(new ClientIpPolicy(java.util.Collections.nCopies(128, "127.0.0.1")).resolve(request)).isEqualTo("192.0.2.9");
        assertThat(policy.resolve(null)).isEqualTo("unknown");
        request.setRemoteAddr("localhost"); assertThat(policy.resolve(request)).isEqualTo("unknown");
    }

    @Test void generatedUntrustedHeadersCannotMoveTheOriginAndConfigurationIsSnapshotted() {
        var configured = new java.util.ArrayList<>(List.of("10.20.0.0/16"));
        var policy = new ClientIpPolicy(configured); configured.clear();
        var random = new java.util.Random(0x06b0d1L);
        for (int i = 0; i < 256; i++) {
            var request = new MockHttpServletRequest(); request.setRemoteAddr("203.0.113.7");
            request.addHeader("X-Forwarded-For", random.nextInt(256) + "." + random.nextInt(256) + ".1.1, 💣\u0301\u202e");
            assertThat(policy.resolve(request)).isEqualTo("203.0.113.7");
        }
        var request = new MockHttpServletRequest(); request.setRemoteAddr("10.20.255.254");
        request.addHeader("X-Forwarded-For", "192.0.2.1"); assertThat(policy.resolve(request)).isEqualTo("192.0.2.1");
        request.setRemoteAddr("10.21.0.0"); assertThat(policy.resolve(request)).isEqualTo("10.21.0.0");
    }
}
