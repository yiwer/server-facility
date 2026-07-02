package cn.code91.facility.log;

import org.slf4j.event.Level;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * <b>日志上下文</b>
 * <p>
 * 封装日志记录的完整上下文信息，包括：
 * </p>
 * <ul>
 *     <li>日志消息内容</li>
 *     <li>日志级别</li>
 *     <li>调用者类名</li>
 *     <li>异常信息（可选）</li>
 *     <li>线程名称</li>
 *     <li>时间戳</li>
 * </ul>
 *
 * @author yvvb
 * @since 2025/5/4
 * @see LogPostHandler
 */
public class LogContext {

    /**
     * 日志消息内容
     */
    private final String message;

    /**
     * 日志级别
     */
    private final Level level;

    /**
     * 调用者类名
     */
    private final String callerClassName;

    /**
     * 异常信息（可为null）
     */
    private final Throwable throwable;

    /**
     * 线程名称
     */
    private final String threadName;

    /**
     * 日志记录时间
     */
    private final LocalDateTime timestamp;

    private LogContext(Builder builder) {
        this.message = builder.message;
        this.level = builder.level;
        this.callerClassName = builder.callerClassName;
        this.throwable = builder.throwable;
        this.threadName = builder.threadName;
        this.timestamp = builder.timestamp;
    }

    /**
     * <b>创建Builder实例</b>
     *
     * @return {@link Builder} 构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    public String getMessage() {
        return message;
    }

    public Level getLevel() {
        return level;
    }

    public String getCallerClassName() {
        return callerClassName;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public String getThreadName() {
        return threadName;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }



    /**
     * <b>判断是否包含异常</b>
     *
     * @return 是否有异常信息
     */
    public boolean hasThrowable() {
        return throwable != null;
    }

    /**
     * <b>获取指定行数的堆栈信息</b>
     *
     * @param maxLines 最大堆栈行数
     * @return 格式化的堆栈信息
     */
    public String getStackTrace(int maxLines) {
        if (throwable == null) {
            return "";
        }
        String header = throwable.getClass().getName() + ": " + throwable.getMessage();
        String trace = Arrays.stream(throwable.getStackTrace())
                .limit(maxLines)
                .map(e -> "\tat " + e.toString())
                .collect(Collectors.joining("\n"));
        return header + "\n" + trace;
    }

    @Override
    public String toString() {
        return String.format("[%s] [%s] [%s] %s%s",
                timestamp, level, callerClassName, message,
                throwable != null ? " - Exception: " + throwable.getMessage() : "");
    }

    /**
     * <b>LogContext构建器</b>
     */
    public static class Builder {
        private String message;
        private Level level;
        private String callerClassName;
        private Throwable throwable;
        private String threadName;
        private LocalDateTime timestamp;

        private Builder() {
            this.timestamp = LocalDateTime.now();
            this.threadName = Thread.currentThread().getName();
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder level(Level level) {
            this.level = level;
            return this;
        }

        public Builder callerClassName(String callerClassName) {
            this.callerClassName = callerClassName;
            return this;
        }

        public Builder throwable(Throwable throwable) {
            this.throwable = throwable;
            return this;
        }

        public Builder threadName(String threadName) {
            this.threadName = threadName;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public LogContext build() {
            return new LogContext(this);
        }
    }
}
