package cn.code91.facility.web.idempotency;

import cn.code91.facility.idempotency.*;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;

/** Qualified finite HTTP response replay; requires a current-operation host authorization Adapter. */
public class IdempotencyInterceptor implements HandlerInterceptor {
    private static final String EXECUTION = IdempotencyInterceptor.class.getName() + ".execution";
    private static final String REPLAY = IdempotencyInterceptor.class.getName() + ".replay";
    private final @Nullable IdempotencyStore store;
    private final @Nullable IdempotencyAuthorization authorization;
    private final Duration defaultLease;
    private final Duration defaultRetention;
    private final int responseLimit;
    private final org.springframework.core.ReactiveAdapterRegistry reactiveTypes = new org.springframework.core.ReactiveAdapterRegistry();
    private record Replay(int status, String type, String location, byte[] body) { }
    private static final class Execution {
        final IdempotencyStore store;
        final ClaimToken token;
        final Duration retention;
        boolean mvcCompleted;
        Execution(IdempotencyStore store, ClaimToken token, Duration retention) {
            this.store = store; this.token = token; this.retention = retention;
        }
    }

    /**
     * Compatibility constructor: annotated operations reject until a current-authorization Adapter is supplied.
     * @deprecated Use the explicit authorization constructor; this path never falls back to ownerless claims.
     */
    @Deprecated(since = "0.1.0", forRemoval = false)
    public IdempotencyInterceptor(IdempotencyStore store, long defaultTtlMillis) {
        this.store = Objects.requireNonNull(store, "store");
        this.authorization = null;
        this.defaultLease = duration(Duration.ofMillis(defaultTtlMillis));
        this.defaultRetention = defaultLease;
        this.responseLimit = 1024 * 1024;
    }

    public IdempotencyInterceptor(@Nullable IdempotencyStore store, @Nullable IdempotencyAuthorization authorization,
                                  FacilityIdempotencyProperties properties) {
        this.store = store;
        this.authorization = authorization;
        this.defaultLease = duration(properties.getLease() == null ? properties.getDefaultTtl() : properties.getLease());
        this.defaultRetention = duration(properties.getResultRetention() == null ? properties.getDefaultTtl() : properties.getResultRetention());
        this.responseLimit = properties.getMaxResponseBytes();
        if (responseLimit <= 0) throw new IllegalArgumentException("maxResponseBytes must be positive");
    }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod method)) return true;
        var annotation = method.getMethodAnnotation(Idempotent.class);
        if (annotation == null) return true;
        if (authorization == null || store == null) throw unavailable();
        if (request.isAsyncStarted() || !finiteReturnType(method)) throw unavailable();
        var capture = WebUtils.getNativeResponse(response, BoundedResponseCapture.class);
        if (capture == null || !capture.selectable()) throw unavailable();
        var keys = request.getHeaders(annotation.headerName());
        String key = keys.hasMoreElements() ? keys.nextElement() : null;
        if (key == null || key.isBlank() || key.length() > 256 || key.chars().anyMatch(Character::isISOControl)
                || keys.hasMoreElements()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (request.getContentType() != null) {
            org.springframework.http.MediaType type;
            try { type = org.springframework.http.MediaType.parseMediaType(request.getContentType()); }
            catch (IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
            if (type.getType().equalsIgnoreCase("multipart")
                    || org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(type))
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        var input = ReplayRequest.selectedInput(request);
        var command = Objects.requireNonNull(authorization.authorize(request, method, input.select()), "authorized command");
        Duration lease = annotation.ttlSeconds() == 0 ? defaultLease : duration(Duration.ofSeconds(annotation.ttlSeconds()));
        Duration retention = annotation.ttlSeconds() == 0 ? defaultRetention : lease;
        ClaimResult result;
        try { result = store.claim(new ClaimRequest(scope(request, method, command), key, command.fingerprint(), lease)); }
        catch (RuntimeException failure) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, null, failure); }
        if (result instanceof ClaimResult.Replay replay) {
            var receipt = readReceipt(replay.receipt());
            applyReceiptMetadata(response, receipt);
            if (!capture.deferReplay()) throw unavailable();
            request.setAttribute(REPLAY, receipt); return false;
        }
        if (result instanceof ClaimResult.Acquired acquired) {
            request.setAttribute(EXECUTION, new Execution(store, acquired.token(), retention));
            capture.start(); return true;
        }
        if (result instanceof ClaimResult.Processing processing) {
            var failure = new org.springframework.web.ErrorResponseException(HttpStatus.CONFLICT);
            failure.getHeaders().set("Retry-After", Long.toString(1 + (processing.retryAfterMillis() - 1) / 1000));
            throw failure;
        }
        if (result instanceof ClaimResult.Conflict)
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        throw unavailable();
    }

    @Override public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                          @Nullable Exception failure) {
        var execution = (Execution) request.getAttribute(EXECUTION);
        if (execution == null) return;
        execution.mvcCompleted = failure == null
                && request.getAttribute(org.springframework.web.servlet.DispatcherServlet.EXCEPTION_ATTRIBUTE) == null;
    }

    static void finishRequest(HttpServletRequest request, HttpServletResponse response, boolean successful) throws IOException {
        var capture = WebUtils.getNativeResponse(response, BoundedResponseCapture.class);
        var replay = (Replay) request.getAttribute(REPLAY);
        request.removeAttribute(REPLAY);
        if (replay != null) {
            if (successful && capture != null && !capture.isCommitted()) {
                if (capture.getStatus() != replay.status())
                    throw new ResponseStatusException(capture.getStatus() >= 400
                            ? org.springframework.http.HttpStatusCode.valueOf(capture.getStatus()) : HttpStatus.SERVICE_UNAVAILABLE);
                writeReceipt((HttpServletResponse) capture.getResponse(), replay);
            }
            return;
        }
        var execution = (Execution) request.getAttribute(EXECUTION);
        if (execution == null) return;
        request.removeAttribute(EXECUTION);
        try {
            if (!successful || !execution.mvcCompleted || capture == null || !eligibleStatus(response.getStatus())
                    || !eligibleMetadata(response)) {
                execution.store.release(execution.token); return;
            }
            var body = capture.body();
            if (body.isEmpty() || !consistentLength(response, body.get().length)) { execution.store.release(execution.token); return; }
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                output.writeInt(0x46485231); output.writeInt(response.getStatus());
                output.writeUTF(Objects.requireNonNullElse(response.getContentType(), ""));
                output.writeUTF(Objects.requireNonNullElse(response.getHeader("Location"), ""));
                output.writeInt(body.get().length); output.write(body.get());
            }
            if (execution.store.complete(execution.token, bytes.toByteArray(), execution.retention) != ClaimUpdate.APPLIED)
                execution.store.release(execution.token);
        } catch (IOException | RuntimeException | Error failure) {
            try { execution.store.release(execution.token); }
            catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static String scope(HttpServletRequest request, HandlerMethod method, IdempotencyAuthorization.Command command) {
        try {
            String identity = String.join("\u0000", command.tenant(), command.actor(),
                    org.springframework.util.ClassUtils.getUserClass(method.getBeanType()).getName(), method.getMethod().toGenericString(),
                    request.getMethod(), request.getRequestURI());
            return "http-v1:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static boolean eligibleStatus(int status) {
        return status >= 200 && status < 300 && status != 206
                || status == 400 || status == 404 || status == 409 || status == 410 || status == 422;
    }

    private static boolean eligibleMetadata(HttpServletResponse response) {
        String encoding = response.getHeader("Content-Encoding");
        return (encoding == null || encoding.equalsIgnoreCase("identity"))
                && response.getHeaders("Content-Encoding").size() <= 1
                && response.getHeader("Content-Range") == null && response.getTrailerFields() == null
                && response.getHeaders("Content-Type").size() <= 1 && response.getHeaders("Location").size() <= 1
                && headerValue(response.getContentType()) && headerValue(response.getHeader("Location"));
    }

    private static boolean headerValue(String value) {
        return value == null || value.length() <= 4096 && value.chars().allMatch(character -> character >= 32 && character < 127);
    }

    private static boolean consistentLength(HttpServletResponse response, int bodyLength) {
        String length = response.getHeader("Content-Length");
        if (length == null) return true;
        if (response.getHeaders("Content-Length").size() != 1) return false;
        try { return Long.parseLong(length) == bodyLength; }
        catch (NumberFormatException invalid) { return false; }
    }

    private boolean finiteReturnType(HandlerMethod method) {
        var type = org.springframework.core.ResolvableType.forMethodReturnType(method.getMethod());
        for (int depth = 0; depth < 4; depth++) {
            Class<?> raw = type.resolve(Object.class);
            if (org.springframework.http.HttpEntity.class.isAssignableFrom(raw)) {
                type = type.as(org.springframework.http.HttpEntity.class).getGeneric(0);
                continue;
            }
            return !java.util.concurrent.Callable.class.isAssignableFrom(raw)
                    && !java.util.concurrent.CompletionStage.class.isAssignableFrom(raw)
                    && !java.util.concurrent.Flow.Publisher.class.isAssignableFrom(raw)
                    && !org.springframework.web.context.request.async.DeferredResult.class.isAssignableFrom(raw)
                    && !org.springframework.web.context.request.async.WebAsyncTask.class.isAssignableFrom(raw)
                    && !org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class.isAssignableFrom(raw)
                    && !org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody.class.isAssignableFrom(raw)
                    && reactiveTypes.getAdapter(raw) == null;
        }
        return false;
    }

    private Replay readReceipt(byte[] bytes) {
        if (bytes.length > (long) responseLimit + 8208) throw unavailable();
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != 0x46485231) throw unavailable();
            int status = input.readInt(); String type = input.readUTF(); String location = input.readUTF();
            int length = input.readInt();
            if (!eligibleStatus(status) || !headerValue(type) || !headerValue(location)
                    || length < 0 || length > responseLimit || length != input.available()) throw unavailable();
            return new Replay(status, type, location, input.readNBytes(length));
        } catch (IOException invalid) { throw unavailable(); }
    }

    private static void writeReceipt(HttpServletResponse response, Replay replay) throws IOException {
        for (String header : new String[]{"Content-Length", "Content-Encoding", "Content-Disposition", "Content-Range",
                "ETag", "Last-Modified", "Accept-Ranges", "Trailer", "Transfer-Encoding", "Content-Type", "Location"})
            response.setHeader(header, null);
        applyReceiptMetadata(response, replay);
        response.getOutputStream().write(replay.body()); response.flushBuffer();
    }

    private static void applyReceiptMetadata(HttpServletResponse response, Replay replay) {
        response.setStatus(replay.status());
        if (!replay.type().isEmpty()) response.setContentType(replay.type());
        if (!replay.location().isEmpty()) response.setHeader("Location", replay.location());
    }

    private static ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE); }

    private static Duration duration(Duration value) {
        Objects.requireNonNull(value, "HTTP replay duration");
        long millis = value.toMillis();
        if (millis <= 0 || !value.equals(Duration.ofMillis(millis)))
            throw new IllegalArgumentException("HTTP replay durations must be positive whole milliseconds");
        return value;
    }
}
