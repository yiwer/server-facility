package cn.code91.facility.error;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * <b>错误包装类</b>
 * <p>
 * 封装错误类型、异常和参数的不可变容器。
 * 设计为线程安全的不可变对象。
 * </p>
 *
 * <h3>设计原则：</h3>
 * <ul>
 *     <li><b>不可变性</b>：所有字段 final，防御性复制</li>
 *     <li><b>线程安全</b>：不可变对象天然线程安全</li>
 *     <li><b>类型安全</b>：强制要求 ErrorTypeInterface</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 基本用法
 * WrappedError error = WrappedError.of(
 *     FacilityErrorType.VALIDATION_ERROR,
 *     new IllegalArgumentException("Invalid input")
 * );
 *
 * // 带参数
 * WrappedError error = WrappedError.of(
 *     UserErrorType.USER_NOT_FOUND,
 *     new NotFoundException(),
 *     userId
 * );
 *
 * // 访问参数
 * Long userId = error.getArg(0, Long.class);
 * }</pre>
 *
 * @author yvvb
 * @since 2.0.0
 * @apiNote 重构版本，修复了不可变性和线程安全问题
 */
public final class WrappedError implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * 错误类型（必需）
     */
    private final ErrorTypeInterface errorType;

    /**
     * 异常对象（可选）
     */
    private final Exception exception;

    /**
     * 错误参数（不可变）
     */
    private final Object[] args;

    // ==================== 构造函数 ====================

    /**
     * 私有构造函数，确保通过工厂方法创建
     */
    private WrappedError(ErrorTypeInterface errorType, Exception exception, Object[] args) {
        this.errorType = Objects.requireNonNull(errorType, "errorType cannot be null");
        this.exception = exception;
        // 防御性复制，确保不可变性
        this.args = args != null && args.length > 0 ? args.clone() : new Object[0];
    }

    // ==================== 工厂方法 ====================

    /**
     * 创建包含错误类型、异常和参数的错误
     * <p>args 语义见 {@link ErrorTypeInterface#format(Object...)}:内置 FacilityErrorType 模板无占位符时 args 不进格式化消息。</p>
     *
     * @param errorType 错误类型（不能为 null）
     * @param exception 异常对象（可以为 null）
     * @param args      错误参数（可变参数）
     * @return WrappedError 实例
     * @throws NullPointerException 如果 errorType 为 null
     */
    public static WrappedError of(ErrorTypeInterface errorType, Exception exception, Object... args) {
        return new WrappedError(errorType, exception, args);
    }

    /**
     * 创建包含错误类型和异常的错误
     *
     * @param errorType 错误类型（不能为 null）
     * @param exception 异常对象（可以为 null）
     * @return WrappedError 实例
     */
    public static WrappedError of(ErrorTypeInterface errorType, Exception exception) {
        return new WrappedError(errorType, exception, null);
    }

    /**
     * 创建仅包含错误类型的错误
     *
     * @param errorType 错误类型（不能为 null）
     * @return WrappedError 实例
     */
    public static WrappedError of(ErrorTypeInterface errorType) {
        return new WrappedError(errorType, null, null);
    }

    /**
     * 创建包含错误类型和参数的错误
     * <p>args 语义见 {@link ErrorTypeInterface#format(Object...)}:内置 FacilityErrorType 模板无占位符时 args 不进格式化消息。</p>
     *
     * @param errorType 错误类型（不能为 null）
     * @param args      错误参数（可变参数）
     * @return WrappedError 实例
     */
    public static WrappedError ofWithArgs(ErrorTypeInterface errorType, Object... args) {
        return new WrappedError(errorType, null, args);
    }

    // ==================== Getters ====================

    /**
     * 获取错误类型
     *
     * @return 错误类型（永不为 null）
     */
    public ErrorTypeInterface getErrorType() {
        return errorType;
    }

    /**
     * 获取异常对象
     *
     * @return 异常对象（可能为 null）
     */
    public Exception getException() {
        return exception;
    }

    /**
     * 获取参数数组的副本（防御性复制）
     *
     * @return 参数数组副本
     */
    public Object[] getArgs() {
        return args.clone();
    }

    /**
     * 获取参数的不可变列表视图
     *
     * @return 不可变的参数列表
     */
    public List<Object> getArgsList() {
        return Collections.unmodifiableList(Arrays.asList(args));
    }

    /**
     * 获取参数数量
     *
     * @return 参数数量
     */
    public int getArgCount() {
        return args.length;
    }

    /**
     * 获取指定索引的参数
     *
     * @param index 参数索引（从 0 开始）
     * @return 参数值
     * @throws IndexOutOfBoundsException 如果索引超出范围
     */
    public Object getArg(int index) {
        if (index < 0 || index >= args.length) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + args.length);
        }
        return args[index];
    }

    /**
     * 获取指定索引的参数，并转换为指定类型
     *
     * @param index 参数索引（从 0 开始）
     * @param type  目标类型
     * @param <T>   类型参数
     * @return 转换后的参数值
     * @throws IndexOutOfBoundsException 如果索引超出范围
     * @throws ClassCastException        如果类型转换失败
     */
    @SuppressWarnings("unchecked")
    public <T> T getArg(int index, Class<T> type) {
        Object arg = getArg(index);
        if (arg == null) {
            return null;
        }
        if (!type.isInstance(arg)) {
            throw new ClassCastException(String.format(
                    "Cannot cast arg[%d] from %s to %s",
                    index,
                    arg.getClass().getName(),
                    type.getName()
            ));
        }
        return (T) arg;
    }

    // ==================== 查询方法 ====================

    /**
     * 是否有异常
     *
     * @return true 如果包含异常
     */
    public boolean hasException() {
        return exception != null;
    }

    /**
     * 是否有参数
     *
     * @return true 如果包含参数
     */
    public boolean hasArgs() {
        return args.length > 0;
    }

    /**
     * 检查是否为指定的错误类型
     *
     * @param type 错误类型
     * @return true 如果匹配
     */
    public boolean isErrorType(ErrorTypeInterface type) {
        return errorType.equals(type);
    }

    /**
     * 检查错误码是否匹配
     *
     * @param code 错误码
     * @return true 如果匹配
     */
    public boolean isErrorCode(int code) {
        return errorType.getCode() == code;
    }

    // ==================== 消息格式化 ====================

    /**
     * 获取格式化的错误消息
     * <p>
     * 使用错误类型的默认消息模板和参数进行格式化。
     * </p>
     *
     * @return 格式化后的错误消息
     */
    public String getFormattedMessage() {
        return errorType.format(args);
    }

    /**
     * 获取完整的错误信息（包含异常消息）
     *
     * @return 完整错误信息
     */
    public String getFullMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(errorType.getFullCode()).append("] ");
        sb.append(getFormattedMessage());

        if (exception != null) {
            sb.append(" - ").append(exception.getClass().getSimpleName());
            if (exception.getMessage() != null) {
                sb.append(": ").append(exception.getMessage());
            }
        }

        return sb.toString();
    }

    // ==================== Object 方法 ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WrappedError that = (WrappedError) o;
        return errorType.equals(that.errorType) &&
                Objects.equals(exception, that.exception) &&
                Arrays.equals(args, that.args);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(errorType, exception);
        result = 31 * result + Arrays.hashCode(args);
        return result;
    }

    @Override
    public String toString() {
        return "WrappedError{" +
                "errorType=" + errorType.getFullCode() +
                ", message='" + getFormattedMessage() + '\'' +
                (exception != null ? ", exception=" + exception.getClass().getSimpleName() : "") +
                (args.length > 0 ? ", args=" + Arrays.toString(args) : "") +
                '}';
    }
}
