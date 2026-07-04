package cn.code91.facility.error;

import lombok.Getter;

/**
 * <b>通用错误类型枚举</b>
 * <p>
 * 定义 facility 模块的通用错误码，错误码范围为 500xxx。
 * 包含容器初始化、JSON序列化、HTTP请求、日期解析等常见错误类型。
 * </p>
 *
 * <h3>错误码规范：</h3>
 * <pre>
 * FACILITY 模块: 500xxx
 * - 500000-500099: 容器相关
 * - 500100-500199: JSON序列化
 * - 500200-500299: 日期处理
 * - 500300-500399: HTTP请求
 * - 500500-500599: Web相关
 * - 500600-500699: 文件 / MIME / 哈希 / 上传 / 路径
 * </pre>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 作为 Result 错误通道
 * Result<String, WrappedError> result = Result.err(
 *     WrappedError.of(FacilityErrorType.JSON_SERIALIZE_ERROR)
 * );
 *
 * // 带参数渲染默认模板(i18n 解析在展示边界完成,见 ADR-0010)
 * String msg = FacilityErrorType.CONTEXT_GET_BEAN_ERROR.format("UserService");
 * }</pre>
 *
 * @author yvvb
 * @since 2025/4/17
 * @see ErrorTypeInterface
 * @see WrappedError
 */
@Getter
public enum FacilityErrorType implements ErrorTypeInterface {

    // ======================== 容器相关错误 (500000-500099) ========================

    /**
     * 容器实例未正确初始化
     */
    CONTEXT_INSTANCE_NOT_INITIALIZED(
            500000,
            "facility.context.not_initialized",
            "容器实例未正确初始化"
    ),

    /**
     * 容器获取Bean实例异常
     */
    CONTEXT_GET_BEAN_ERROR(
            500001,
            "facility.context.get_bean_error",
            "容器获取实例异常"
    ),

    // ==================== JSON序列化错误 (500100-500199) ====================

    /**
     * JsonNode序列化异常
     */
    JSON_NODE_TRANSFER_ERROR(
            500100,
            "facility.json.node_transfer_error",
            "JsonNode序列化异常"
    ),

    /**
     * 对象序列化为JSON异常
     */
    JSON_SERIALIZE_ERROR(
            500101,
            "facility.json.serialize_error",
            "对象序列化异常"
    ),

    /**
     * JSON反序列化为对象异常
     */
    JSON_DESERIALIZE_ERROR(
            500102,
            "facility.json.deserialize_error",
            "对象反序列化异常"
    ),

    // ==================== 日期处理错误 (500200-500299) ====================

    /**
     * 格式化日期时间异常
     */
    FORMAT_TEMPORAL_ERROR(
            500200,
            "facility.date.format_error",
            "格式化日期异常"
    ),

    /**
     * 解析日期字符串异常
     */
    PARSE_STR_TO_TEMPORAL_ERROR(
            500201,
            "facility.date.parse_error",
            "解析日期字符串异常"
    ),

    // ==================== HTTP请求错误 (500300-500399) ====================

    /**
     * 构建HTTP请求体异常
     */
    BUILD_HTTP_REQUEST_BODY_ERROR(
            500300,
            "facility.http.build_body_error",
            "构建HTTP请求体异常"
    ),

    /**
     * HTTP请求模型非法
     */
    HTTP_REQUEST_MODEL_INVALID(
            500301,
            "facility.http.model_invalid",
            "HTTP请求模型非法"
    ),

    /**
     * 发送/解析HTTP异常
     */
    HTTP_SEND_AND_PARSE_ERROR(
            500302,
            "facility.http.send_parse_error",
            "发送/解析HTTP异常"
    ),

    /**
     * 期望的响应类型不能为空
     */
    EXPECT_RESPONSE_TYPE_NULL(
            500303,
            "facility.http.response_type_null",
            "期望的响应类型不能为空"
    ),

    /**
     * HTTP 请求返回错误状态
     */
    HTTP_STATUS_ERROR(
            500304,
            "facility.http.status_error",
            "HTTP 请求返回错误状态"
    ),

    // ==================== Web相关错误 (500500-500599) ====================

    /**
     * 非Web请求上下文
     */
    WEB_NOT_IN_REQUEST_CONTEXT(
            500500,
            "facility.web.not_in_request_context",
            "非Web请求上下文"
    ),

    /**
     * Session属性不存在
     */
    WEB_SESSION_ATTRIBUTE_NOT_FOUND(
            500501,
            "facility.web.session_attribute_not_found",
            "Session属性不存在"
    ),

    /**
     * 响应写入异常
     */
    WEB_RESPONSE_WRITE_ERROR(
            500502,
            "facility.web.response_write_error",
            "响应写入异常"
    ),

    /**
     * Cookie操作异常
     */
    WEB_COOKIE_OPERATION_ERROR(
            500503,
            "facility.web.cookie_operation_error",
            "Cookie操作异常"
    ),

    /**
     * 分页参数非法
     */
    WEB_INVALID_PAGE_PARAMETER(
            500504,
            "facility.web.invalid_page_parameter",
            "分页参数非法"
    ),

    // ==================== 文件/MIME/哈希/上传 (500600-500699) ====================

    FILE_NOT_FOUND(
            500600,
            "facility.file.not_found",
            "文件不存在"
    ),
    FILE_READ_ERROR(
            500601,
            "facility.file.read_error",
            "文件读取异常"
    ),
    FILE_WRITE_ERROR(
            500602,
            "facility.file.write_error",
            "文件写入异常"
    ),
    FILE_NAME_INVALID(
            500603,
            "facility.file.name_invalid",
            "文件名非法"
    ),
    FILE_TYPE_NOT_SUPPORTED(
            500604,
            "facility.file.type_not_supported",
            "文件类型不支持"
    ),
    FILE_SIZE_EXCEEDED(
            500605,
            "facility.file.size_exceeded",
            "文件大小超限"
    ),
    FILE_TYPE_DETECT_ERROR(
            500606,
            "facility.file.type_detect_error",
            "文件类型检测失败"
    ),
    FILE_HASH_ERROR(
            500607,
            "facility.file.hash_error",
            "文件哈希计算异常"
    ),
    FILE_UPLOAD_EMPTY(
            500608,
            "facility.file.upload_empty",
            "上传文件为空"
    ),
    FILE_DELETE_ERROR(
            500609,
            "facility.file.delete_error",
            "文件删除异常"
    );

    // ======================== 模块标识 ========================

    /**
     * 模块标识符
     */
    private static final String MODULE = "FACILITY";

    // ======================== 实例属性 ========================

    /**
     * 错误码
     */
    private final int code;

    /**
     * i18n 消息键
     */
    private final String messageKey;

    /**
     * 默认错误消息（内置类型均为无参描述文案;MessageFormat 占位符仅供消费方自定义 ErrorType 使用）
     */
    private final String defaultMessage;

    // ======================== 构造函数 ========================

    /**
     * 构造函数
     *
     * @param code           错误码
     * @param messageKey     i18n 消息键
     * @param defaultMessage 默认错误消息
     */
    FacilityErrorType(int code, String messageKey, String defaultMessage) {
        this.code = code;
        this.messageKey = messageKey;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String getModule() {
        return MODULE;
    }
}