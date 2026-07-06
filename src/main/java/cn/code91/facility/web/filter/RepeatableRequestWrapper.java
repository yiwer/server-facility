package cn.code91.facility.web.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * <b>可重复读取请求体的RequestWrapper</b>
 * <p>
 * 缓存请求体字节数组，使 {@link #getInputStream()} 和 {@link #getReader()} 可重复调用。
 * 适用于需要多次读取请求体的场景（如日志记录、签名验证）。
 * Body size is capped at {@code maxBodyBytes} — a {@link PayloadTooLargeException} is thrown
 * if the limit is exceeded.
 * </p>
 *
 * @author yvvb
 * @since 2.0.0
 * @see RepeatableRequestFilter
 */
public class RepeatableRequestWrapper extends HttpServletRequestWrapper {

    /**
     * 缓存的请求体字节
     */
    private final byte[] body;

    // ==================== 构造函数 ====================

    /**
     * 构造函数，读取并缓存请求体（无大小限制）
     *
     * @param request 原始请求
     * @throws IOException 读取失败时抛出
     */
    public RepeatableRequestWrapper(HttpServletRequest request) throws IOException {
        this(request, Long.MAX_VALUE);
    }

    /**
     * 构造函数，读取并缓存请求体，超出 {@code maxBodyBytes} 时抛出 {@link PayloadTooLargeException}
     *
     * @param request      原始请求
     * @param maxBodyBytes 允许的最大请求体字节数
     * @throws IOException              读取失败时抛出
     * @throws PayloadTooLargeException 请求体超出限制时抛出
     */
    public RepeatableRequestWrapper(HttpServletRequest request, long maxBodyBytes) throws IOException {
        super(request);
        try (var in = new LimitedSizeInputStream(request.getInputStream(), maxBodyBytes);
             var out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            this.body = out.toByteArray();
        }
    }

    // ==================== 重写方法 ====================

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream bais = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return bais.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // 不支持异步读取
            }

            @Override
            public int read() {
                return bais.read();
            }

            @Override
            public int available() {
                return bais.available();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    // ==================== 扩展方法 ====================

    /**
     * 获取缓存的请求体内容
     *
     * @return 请求体字符串
     */
    public String getBodyString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * 获取缓存的请求体字节
     *
     * @return 请求体字节数组副本
     */
    public byte[] getBodyBytes() {
        return body.clone();
    }

    // ==================== 内部工具类 ====================

    /**
     * InputStream that counts bytes and throws {@link PayloadTooLargeException} when limit exceeded.
     */
    private static class LimitedSizeInputStream extends FilterInputStream {
        private final long max;
        private long count = 0;

        LimitedSizeInputStream(InputStream in, long max) {
            super(in);
            this.max = max;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (max > 0 && b != -1 && ++count > max) {
                throw new PayloadTooLargeException(count, max);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (max > 0 && n > 0) {
                count += n;
                if (count > max) {
                    throw new PayloadTooLargeException(count, max);
                }
            }
            return n;
        }
    }
}
