package cn.code91.facility.web.exception;

import cn.code91.facility.error.ErrorTypeInterface;
import cn.code91.facility.error.WrappedError;

/**
 * <b>facility 自定义异常统一接口</b>
 * <p>
 * {@link BusinessException} 与 {@link SystemException} 均 implement 此 interface，
 * 使 {@link AbstractGlobalExceptionHandler} 能统一处理（无需为两类异常重复维护
 * handler 路径）。
 * </p>
 *
 * <p>详见 docs/adr/0004-rp-07-facility-exception-interface.md</p>
 *
 * @since phase-3
 */
public interface FacilityException {

    /**
     * 获取错误类型枚举。
     *
     * @return 错误类型 {@link ErrorTypeInterface} 实现
     */
    ErrorTypeInterface getErrorType();

    /**
     * 获取消息格式化参数。
     *
     * @return 参数数组（非 null，可空数组）
     */
    Object[] getArgs();

    /**
     * 获取错误码（短码，{@code errorType.getCode()} 的便捷别名）。
     *
     * @return 错误码整数
     */
    int getCode();

    /**
     * 获取已格式化的消息（{@code errorType.format(args)} 的便捷别名）。
     *
     * @return 格式化后的错误消息
     */
    String getFormattedMessage();

    /**
     * 转换为 {@link WrappedError}（含 errorType + args + cause 链路信息）。
     *
     * @return 包装后的不可变错误对象
     */
    WrappedError toWrappedError();
}
