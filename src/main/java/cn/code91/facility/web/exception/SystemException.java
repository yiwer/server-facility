package cn.code91.facility.web.exception;

import cn.code91.facility.error.ErrorTypeInterface;
import cn.code91.facility.error.WrappedError;

import java.io.Serial;
import java.util.function.Supplier;

/**
 * <b>系统异常</b>（HTTP 500 类）。错误类型 + 格式化参数承载于 {@link AbstractFacilityException}。
 *
 * @author yvvb
 * @since 2.0.0
 */
public class SystemException extends AbstractFacilityException {

    @Serial
    private static final long serialVersionUID = 1L;

    private SystemException(ErrorTypeInterface errorType, Throwable cause, Object[] args) {
        super(errorType, cause, args);
    }

    public static SystemException fromWrappedError(WrappedError wrappedError) {
        return new SystemException(wrappedError.getErrorType(), wrappedError.getException(), wrappedError.getArgs());
    }

    public static SystemException of(ErrorTypeInterface errorType, Object... args) {
        return new SystemException(errorType, null, args);
    }

    public static Supplier<SystemException> supplierErr(ErrorTypeInterface errorType, Object... args) {
        return () -> new SystemException(errorType, null, args);
    }

    public static SystemException of(ErrorTypeInterface errorType, Exception cause, Object... args) {
        return new SystemException(errorType, cause, args);
    }
}
