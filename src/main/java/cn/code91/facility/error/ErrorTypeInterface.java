package cn.code91.facility.error;

import java.text.MessageFormat;
import java.util.Arrays;

/**
 * <b>错误类型接口</b>
 * <p>
 * 定义可选的错误描述接口；领域可拥有自己的实现，Result 的错误类型 E 不要求实现此接口。
 * 用于统一错误码和错误消息的定义，便于错误处理和国际化。
 * </p>
 *
 * <h3>设计原则：</h3>
 * <ul>
 *     <li><b>模块隔离</b>：通过 {@link #getModule()} 区分错误来源模块</li>
 *     <li><b>标识约定</b>：{@code module + code} 的唯一性由应用分配规则保证，本接口没有全局注册表</li>
 *     <li><b>纯数据契约</b>：本接口只承载 code / messageKey / defaultMessage，不做 i18n 解析；
 *         {@link #getMessageKey()} 是给边界（locale 包）解析用的数据（ADR-0010，C1 断环）</li>
 *     <li><b>参数化消息</b>：通过 {@link #format(Object...)} 渲染默认模板的动态参数</li>
 *     <li><b>安全降级</b>：格式化失败时提供降级方案</li>
 * </ul>
 *
 * <h3>错误码规范：</h3>
 * <pre>
 * 模块前缀（3位） + 错误序号（3位）
 * 例如：FACILITY 模块 = 500xxx
 *       USER 模块     = 100xxx
 *       ORDER 模块    = 200xxx
 * </pre>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * public enum UserErrorType implements ErrorTypeInterface {
 *     USER_NOT_FOUND(100001, "用户 {0} 不存在"),
 *     INVALID_PASSWORD(100002, "密码格式错误：{0}");
 *
 *     private final int code;
 *     private final String message;
 *
 *     UserErrorType(int code, String message) {
 *         this.code = code;
 *         this.message = message;
 *     }
 *
 *     @Override
 *     public String getModule() {
 *         return "USER";
 *     }
 *
 *     @Override
 *     public int getCode() {
 *         return code;
 *     }
 *
 *     @Override
 *     public String getDefaultMessage() {
 *         return message;
 *     }
 *
 *     @Override
 *     public String getMessageKey() {
 *         return "user." + name().toLowerCase();
 *     }
 * }
 * }</pre>
 *
 * @author yvvb
 * @see WrappedError
 * @since 2.0.0
 * @apiNote C1 断环版本：i18n 解析移交展示边界（locale 包），本接口不依赖 Spring（ADR-0010）
 */
public interface ErrorTypeInterface {

    // ==================== 核心属性 ====================

    /**
     * 获取模块标识
     * <p>用于区分错误来源模块，建议使用大写字母</p>
     *
     * @return 模块标识，如 "FACILITY", "USER", "ORDER"
     */
    default String getModule() {
        return "UNKNOWN";
    }

    /**
     * 获取错误码
     * <p>在模块内唯一，建议使用模块前缀+序号的方式</p>
     *
     * @return 错误码，用于唯一标识错误类型
     */
    int getCode();

    /**
     * 获取 i18n 消息键
     * <p>纯数据：由展示边界（locale 包的 MessageSource 解析）使用；本接口自身不做解析</p>
     *
     * @return 消息键，格式建议：{module}.{error_name}
     */
    String getMessageKey();

    /**
     * 获取默认错误消息（i18n 未命中或不可用时的兜底模板）
     * <p>支持 {@link MessageFormat} 占位符，如 "用户 {0} 不存在"</p>
     *
     * @return 默认错误消息模板
     */
    String getDefaultMessage();

    // ==================== 派生方法 ====================

    /**
     * 获取完整错误码
     * <p>格式：MODULE-CODE，如 FACILITY-5001</p>
     *
     * @return 完整错误码
     */
    default String getFullCode() {
        return getModule() + "-" + getCode();
    }

    /**
     * 渲染默认消息模板
     * <p>
     * 只使用 {@link #getDefaultMessage()}，不做 i18n 解析（C1 断环，ADR-0010）：
     * 无参时返回模板原文（不经 {@link MessageFormat}，模板中的单引号不会被吞）；
     * 有参时用 {@link MessageFormat#format} 渲染；模板为 null 时返回 {@link #getMessageKey()}。
     * 需要本地化消息的场景在边界调用 locale 包的解析入口。
     * </p>
     * <p>仅当 {@link #getDefaultMessage()} 含 MessageFormat 占位符时 args 才被注入;facility 内置
     * {@link FacilityErrorType} 模板均无占位符,此时 args 被忽略(供日志/调试上下文与消费方自定义类型使用,
     * 不进面向用户消息,见 ADR-0010)。</p>
     *
     * @param args 消息参数
     * @return 渲染后的消息
     */
    default String format(Object... args) {
        try {
            return formatDefault(args);
        } catch (IllegalArgumentException e) {
            return formatFallback(args, e);
        }
    }

    private String formatDefault(Object[] args) {
        String pattern = getDefaultMessage();
        if (pattern == null) {
            return getMessageKey();
        }
        if (args == null || args.length == 0) {
            return pattern;
        }
        return MessageFormat.format(pattern, args);
    }

    /**
     * 格式化失败时的降级处理
     * <p>
     * 当 MessageFormat 格式化失败时（如占位符数量不匹配），
     * 提供一个友好的降级消息而不是抛出异常。
     * </p>
     *
     * @param args 原始参数
     * @param e    格式化异常
     * @return 降级后的消息
     */
    private String formatFallback(Object[] args, IllegalArgumentException e) {
        StringBuilder sb = new StringBuilder();
        sb.append(getDefaultMessage());
        sb.append(" [格式化失败: ").append(e.getMessage());
        sb.append(", 参数: ").append(Arrays.toString(args));
        sb.append("]");
        return sb.toString();
    }

    /**
     * 验证错误码是否符合规范
     * <p>
     * 检查错误码是否在模块分配的范围内。
     * </p>
     *
     * @param minCode 最小错误码
     * @param maxCode 最大错误码
     * @return true 如果在范围内
     */
    default boolean validateCodeRange(int minCode, int maxCode) {
        int code = getCode();
        return code >= minCode && code <= maxCode;
    }

    /**
     * 获取详细的错误描述（用于调试）
     *
     * @return 包含完整错误码、消息键和默认消息的描述
     */
    default String getDetailedDescription() {
        return String.format(
                "ErrorType{fullCode='%s', messageKey='%s', defaultMessage='%s'}",
                getFullCode(),
                getMessageKey(),
                getDefaultMessage()
        );
    }
}
