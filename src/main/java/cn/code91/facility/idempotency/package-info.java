/**
 * <h2>cn.code91.facility.idempotency</h2>
 *
 * <p><b>Purpose:</b> General-purpose idempotency storage — the {@code IdempotencyStore} SPI (a
 * replaceable seam), its data carrier {@code IdempotencyRecord} (an immutable record with a
 * {@code PROCESSING}/{@code DONE} state, plus {@code processing}/{@code done} static factories and
 * an {@code isExpired} predicate), and the default single-node implementation
 * {@code InMemoryIdempotencyStore} (one {@code ConcurrentHashMap} entry per key; {@code tryBegin}
 * uses {@code ConcurrentHashMap#compute} plus a reference-equality check against the freshly built
 * placeholder record to detect, without any external lock, which single caller actually won the
 * race to begin processing a key — the same unbounded-key clear-and-warn protection used by
 * {@code TokenBucketRateLimiter} and {@code InMemoryDistributedLock} guards the entry set, see
 * ADR-0014).</p>
 *
 * <p><b>Entry classes:</b> {@code IdempotencyStore}, {@code IdempotencyRecord},
 * {@code InMemoryIdempotencyStore}.</p>
 *
 * <p><b>Zero web dependency:</b> this package carries no servlet-stack import — {@code IdempotencyRecord}
 * is a plain data record ({@code state}/{@code statusCode}/{@code contentType}/{@code body}/
 * {@code expiresAtMillis}, all serializable primitives/{@code String}/{@code byte[]}) that can be
 * persisted to an external store (e.g. Redis) as-is when a real distributed {@code IdempotencyStore}
 * replaces the default. HTTP-facing pieces (the {@code @Idempotent} annotation, the interceptor
 * that reads/writes records around a handler invocation to return the first response for a
 * repeated key, the response-capturing filter) live in {@code cn.code91.facility.web.idempotency}
 * instead, precisely to keep this package free of that dependency — the same
 * generic/web-integration split already used for {@code ratelimit}/{@code web.ratelimit}, adopted
 * here from the start rather than fixed after the fact (see that package's {@code package-info}
 * for the cycle this split avoids).</p>
 *
 * <p><b>Depends on:</b> {@code log} ({@code InMemoryIdempotencyStore} logs a WARN when its
 * unbounded-key protection clears the record set).</p>
 *
 * <p><b>Depended on by:</b> {@code autoconfigure} ({@code FacilityIdempotencyAutoConfiguration}
 * wires the default {@code InMemoryIdempotencyStore} from {@code FacilityIdempotencyProperties}
 * with no web condition), {@code web.idempotency} ({@code IdempotencyInterceptor} consumes the
 * {@code IdempotencyStore}/{@code IdempotencyRecord} SPI types to implement full idempotency —
 * same key returns the first response, verbatim — around {@code @Idempotent}-annotated handler
 * methods), downstream application code ({@code IdempotencyStore} and {@code IdempotencyRecord}
 * work without the autoconfigure or web layers too, as long as some {@code IdempotencyStore} bean
 * or plain instance is wired by the caller).</p>
 */
package cn.code91.facility.idempotency;
