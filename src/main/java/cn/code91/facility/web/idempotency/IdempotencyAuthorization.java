package cn.code91.facility.web.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.method.HandlerMethod;

/**
 * Host-owned current operation authorization and business-command normalization.
 * Runs before every claim/replay. It must not execute business side effects. Identity must
 * come from the host's trusted security context; fingerprints cover the complete normalized
 * command, including relevant resource identifiers, and exclude credentials/trace metadata.
 */
@FunctionalInterface
public interface IdempotencyAuthorization {
    Command authorize(HttpServletRequest request, HandlerMethod operation, byte[] boundedBody);

    /** Explicit tenant and actor; use an application-owned constant only when that dimension is inapplicable. */
    record Command(String tenant, String actor, String fingerprint) {
        public Command {
            require(tenant, 256); require(actor, 256); require(fingerprint, 128);
        }
        private static void require(String value, int limit) {
            if (value == null || value.isBlank() || value.length() > limit || value.chars().anyMatch(Character::isISOControl)
                    || value.codePoints().anyMatch(codePoint -> codePoint >= 0xD800 && codePoint <= 0xDFFF))
                throw new IllegalArgumentException("Invalid authorized command identity");
        }
        @Override public String toString() { return "AuthorizedCommand[redacted]"; }
    }
}
