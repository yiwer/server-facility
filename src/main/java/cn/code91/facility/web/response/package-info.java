/**
 * <h2>cn.code91.facility.web.response</h2>
 *
 * <p><b>Purpose:</b> Uniform API response envelopes: {@code BaseResponse}
 * (code / message / data / description + {@code ok} / {@code err} factories and a
 * {@code fromResult} bridge from the {@code Result} error channel) and
 * {@code PageBaseResponse} (adds total / pageNum / pageSize for page replies).</p>
 *
 * <p><b>Entry classes:</b> {@code BaseResponse}, {@code PageBaseResponse}.</p>
 *
 * <p><b>Depends on:</b> {@code result} ({@code fromResult} bridge) and {@code error}
 * ({@code ErrorTypeInterface} / {@code WrappedError} drive the {@code err} factories).</p>
 *
 * <p><b>Depended on by:</b> {@code web.argument} ({@code PageQuery} pairs with
 * {@code PageBaseResponse}), {@code web.exception} (handlers build envelopes),
 * {@code web.util} ({@code ResponseUtil.writeJson}), and downstream controllers.</p>
 */
package cn.code91.facility.web.response;
