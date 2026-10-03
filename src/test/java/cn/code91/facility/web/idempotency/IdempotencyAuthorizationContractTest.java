package cn.code91.facility.web.idempotency;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IdempotencyAuthorizationContractTest {
    @Test void authorizedIdentityIsBoundedUnambiguousUnicodeAndRedactedInDiagnostics() {
        for (String invalid : new String[]{null, "", " ", "a\u0000b", "\ud800", "\udc00", "a\ud800b"}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyAuthorization.Command(invalid, "actor", "fp"));
            assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyAuthorization.Command("tenant", invalid, "fp"));
            assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyAuthorization.Command("tenant", "actor", invalid));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyAuthorization.Command("t".repeat(257), "a", "f"));
        assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyAuthorization.Command("t", "a".repeat(257), "f"));
        assertThatIllegalArgumentException().isThrownBy(() -> new IdempotencyAuthorization.Command("t", "a", "f".repeat(129)));
        var maximum = new IdempotencyAuthorization.Command("t".repeat(256), "a".repeat(256), "f".repeat(128));
        assertThat(maximum.actor()).hasSize(256);
        var unicode = new IdempotencyAuthorization.Command("租户-🚀", "PRIVATE-ACTOR", "PRIVATE-FINGERPRINT");
        assertThat(unicode.tenant()).isEqualTo("租户-🚀");
        assertThat(unicode.toString()).doesNotContain("PRIVATE", "租户");
    }
}
