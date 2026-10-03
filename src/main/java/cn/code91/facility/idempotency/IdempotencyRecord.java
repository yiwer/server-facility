package cn.code91.facility.idempotency;

/**
 * <b>幂等记录</b>
 * <p>
 * 纯数据载体（无行为依赖），字段均为基本类型/String/byte[]，可直接序列化落地 Redis 等外部存储，
 * 供替换 {@link IdempotencyStore} 实现时复用同一记录形状。
 * </p>
 *
 * @param state           记录状态：{@link State#PROCESSING} 处理中 / {@link State#DONE} 已完成
 * @param statusCode      首次处理完成的 HTTP 状态码；{@link State#PROCESSING} 阶段恒为 0（占位无意义）
 * @param contentType     首次处理完成响应体的 Content-Type；{@link State#PROCESSING} 阶段恒为 {@code null}
 * @param body            首次处理完成的响应体原始字节；{@link State#PROCESSING} 阶段恒为 {@code null}
 * @param expiresAtMillis 记录过期时间点（{@link System#currentTimeMillis()} 纪元毫秒）
 * @author yvvb
 * @since 1.0.0
 */
public record IdempotencyRecord(State state, int statusCode, String contentType, byte[] body, long expiresAtMillis) {
    public IdempotencyRecord {
        java.util.Objects.requireNonNull(state, "state");
        if (state == State.PROCESSING) {
            if (statusCode != 0 || contentType != null || body != null)
                throw new IllegalArgumentException("PROCESSING has no response");
        } else {
            if (statusCode < 100 || statusCode > 599) throw new IllegalArgumentException("Invalid HTTP status");
            if (contentType != null) ClaimInputs.text(contentType, "contentType", 256);
            body = java.util.Objects.requireNonNull(body, "body").clone();
        }
    }

    @Override public byte[] body() { return body == null ? null : body.clone(); }

    int bodyLength() { return body == null ? 0 : body.length; }

    /**
     * 记录状态
     */
    public enum State {
        /** 占位中：请求已被首次调用方接手处理，尚未完成 */
        PROCESSING,
        /** 已完成：{@link #statusCode}/{@link #contentType}/{@link #body} 为首次处理的真实响应 */
        DONE
    }

    /**
     * 构造一条"占位中"记录（{@link IdempotencyStore#tryBegin(String, long)} 成功时写入）
     *
     * @param expiresAtMillis 过期时间点
     * @return {@link State#PROCESSING} 记录，statusCode/contentType/body 均为空值
     */
    public static IdempotencyRecord processing(long expiresAtMillis) {
        return new IdempotencyRecord(State.PROCESSING, 0, null, null, expiresAtMillis);
    }

    /**
     * 构造一条"已完成"记录（处理完成后写入，供重复请求直接返回）
     *
     * @param statusCode      HTTP 状态码
     * @param contentType     响应 Content-Type
     * @param body            响应体原始字节
     * @param expiresAtMillis 过期时间点
     * @return {@link State#DONE} 记录
     */
    public static IdempotencyRecord done(int statusCode, String contentType, byte[] body, long expiresAtMillis) {
        return new IdempotencyRecord(State.DONE, statusCode, contentType, body, expiresAtMillis);
    }

    /**
     * 判断记录相对于给定时刻是否已过期
     *
     * @param nowMillis 当前时刻（纪元毫秒）
     * @return {@code true} 表示已过期（{@code nowMillis > expiresAtMillis}）
     */
    public boolean isExpired(long nowMillis) {
        return nowMillis > expiresAtMillis;
    }
}
