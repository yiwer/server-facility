package cn.code91.facility.web.exception;

import cn.code91.facility.error.ErrorTypeInterface;
import cn.code91.facility.error.WrappedError;

import java.io.Serial;

/**
 * <b>facility 异常基类</b>（phase-14，RV2-13）
 *
 * <p>{@link BusinessException} / {@link SystemException} 的共有载体：{@code errorType} + {@code args}
 * + 构造 + {@link FacilityException} 接口实现。子类仅保留各自的公有静态工厂
 * （{@code of}/{@code supplierErr}/{@code fromWrappedError}），消除两类间的字面复制。</p>
 *
 * @since phase-14
 */
public abstract class AbstractFacilityException extends RuntimeException implements FacilityException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorTypeInterface errorType;
    private final Object[] args;

    protected AbstractFacilityException(ErrorTypeInterface errorType, Throwable cause, Object[] args) {
        super(errorType.format(args), cause);
        this.errorType = errorType;
        this.args = args != null ? args.clone() : new Object[0];
    }

    @Override
    public int getCode() {
        return errorType.getCode();
    }

    @Override
    public ErrorTypeInterface getErrorType() {
        return errorType;
    }

    @Override
    public String getFormattedMessage() {
        return errorType.format(args);
    }

    @Override
    public Object[] getArgs() {
        return args.clone();
    }

    @Override
    public WrappedError toWrappedError() {
        Throwable cause = getCause();
        if (cause instanceof Exception ex) {
            return WrappedError.of(errorType, ex, args);
        }
        return WrappedError.ofWithArgs(errorType, args);
    }
}
