package cn.code91.facility.web.exception;

import cn.code91.facility.error.ErrorTypeInterface;
import cn.code91.facility.error.WrappedError;

import java.io.Serial;
import java.util.function.Supplier;

/**
 * <b>业务异常</b>（HTTP 400 类）。错误类型 + 格式化参数承载于 {@link AbstractFacilityException}。
 *
 * @author yvvb
 * @since 2.0.0
 */
public class BusinessException extends AbstractFacilityException {

    @Serial
    private static final long serialVersionUID = 1L;

    private BusinessException(ErrorTypeInterface errorType, Throwable cause, Object[] args) {
        super(errorType, cause, args);
    }

    public static BusinessException fromWrappedError(WrappedError wrappedError) {
        return new BusinessException(wrappedError.getErrorType(), wrappedError.getException(), wrappedError.getArgs());
    }

    public static BusinessException of(ErrorTypeInterface errorType, Object... args) {
        return new BusinessException(errorType, null, args);
    }

    public static Supplier<BusinessException> supplierErr(ErrorTypeInterface errorType, Object... args) {
        return () -> new BusinessException(errorType, null, args);
    }

    public static BusinessException of(ErrorTypeInterface errorType, Exception cause, Object... args) {
        return new BusinessException(errorType, cause, args);
    }
}
