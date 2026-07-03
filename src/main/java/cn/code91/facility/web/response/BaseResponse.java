package cn.code91.facility.web.response;

import cn.code91.facility.error.ErrorTypeInterface;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * <b>统一API响应封装</b>
 * <p>
 * 统一的接口返回结构，包含状态码、消息和数据。
 * 提供从 {@link Result} 到 R 的桥接方法。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 成功
 * R<User> r = R.ok(user);
 *
 * // 失败
 * R<Void> r = R.err(500, "系统异常");
 *
 * // 从 Result 转换
 * Result<User, WrappedError> result = userService.findById(id);
 * R<User> r = R.fromResult(result);
 * }</pre>
 *
 * @param <T> 数据类型
 *
 * @author yvvb
 * @see PageBaseResponse
 * @see Result
 * @since 2.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 成功状态码
     */
    private static final int SUCCESS_CODE = 200;

    /**
     * 成功默认消息
     */
    private static final String SUCCESS_MESSAGE = "success";

    // ==================== 字段 ====================

    /**
     * 状态码
     */
    private int code;

    /**
     * 消息
     */
    private String message;

    /**
     * 数据
     */
    private T data;

    private String description;

    // ==================== 成功工厂方法 ====================

    /**
     * 成功（无数据）
     *
     * @param <T> 数据类型
     *
     * @return 成功响应
     */
    public static <T> BaseResponse<T> ok() {
        return new BaseResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, null, "");
    }

    /**
     * 成功（带数据）
     *
     * @param data 数据
     * @param <T>  数据类型
     *
     * @return 成功响应
     */
    public static <T> BaseResponse<T> ok(T data) {
        return new BaseResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data, "");
    }

    /**
     * 成功（带数据和消息）
     *
     * @param data    数据
     * @param message 消息
     * @param <T>     数据类型
     *
     * @return 成功响应
     */
    public static <T> BaseResponse<T> ok(T data, String message) {
        return new BaseResponse<>(SUCCESS_CODE, message, data, "");
    }

    // ==================== 失败工厂方法 ====================

    /**
     * 失败（指定错误码和消息）
     *
     * @param code    错误码
     * @param message 错误消息
     * @param <T>     数据类型
     *
     * @return 失败响应
     */
    public static <T> BaseResponse<T> err(int code, String message) {
        return new BaseResponse<>(code, message, null, "");
    }

    /**
     * 失败（从错误类型构建）
     *
     * @param errorType 错误类型
     * @param args      消息参数
     * @param <T>       数据类型
     *
     * @return 失败响应
     */
    public static <T> BaseResponse<T> err(ErrorTypeInterface errorType, Object... args) {
        return new BaseResponse<>(errorType.getCode(), errorType.format(args), null, errorType.getDetailedDescription());
    }

    // ==================== 桥接方法 ====================

    /**
     * 将 {@link Result} 转换为 {@link BaseResponse}
     * <p>
     * 成功时返回 {@code R.ok(data)}，失败时返回 {@code R.err(code, message)}。
     * </p>
     *
     * @param result Result 实例
     * @param <T>    数据类型
     *
     * @return R 响应
     */
    public static <T> BaseResponse<T> fromResult(Result<T, WrappedError> result) {
        if (result.isOk()) {
            return BaseResponse.ok(result.get());
        }
        WrappedError error = result.getErr();
        return BaseResponse.err(error.getErrorType().getCode(), error.getFormattedMessage());
    }

    // ==================== 查询方法 ====================

    /**
     * 是否成功
     *
     * @return true 如果状态码为200
     */
    public boolean isSuccess() {
        return code == SUCCESS_CODE;
    }
}
