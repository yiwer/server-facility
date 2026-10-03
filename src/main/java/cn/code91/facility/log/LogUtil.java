package cn.code91.facility.log;

import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.masking.MaskUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>日志输出工具类 - 重构版本</b>
 * <p>
 * 提供统一的日志输出接口，支持TRACE/DEBUG/INFO/WARN/ERROR等日志级别。
 * </p>
 *
 * <h3>重构改进：</h3>
 * <ul>
 *     <li><b>可靠的栈分析</b>：不依赖硬编码的栈深度</li>
 *     <li><b>per-package 级别生效</b>:级别门控基于调用方 logger,业务包的
 *         {@code logging.level.*} 配置对 LogUtil 通道生效;调用方经 {@link StackWalker}
 *         惰性解析(ADR-0022)</li>
 *     <li><b>线程安全</b>：改进的缓存策略</li>
 *     <li><b>实例缓存</b>：ConcurrentHashMap 缓存 Logger(键为 logger 名称,集合有界,不随用户输入增长);如需手动清理见 {@link #clearLoggerCache()}</li>
 *     <li><b>写前脱敏</b>:消息经 {@link cn.code91.facility.masking.MaskUtil} 默认脱敏,
 *         见 {@link #setMaskingEnabled(boolean)} 与 ADR-0020</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * LogUtil.info("用户{}登录成功", username);
 * LogUtil.error(exception, "处理订单{}失败", orderId);
 * }</pre>
 *
 * @author yvvb
 * @since 2.0.0
 * @apiNote 级别门控基于调用方 logger(per-package 配置生效);调用方经 StackWalker 惰性解析(ADR-0022)
 */
public final class LogUtil {

    private LogUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Logger 实例缓存（ConcurrentHashMap 强引用;logger 名称集有界,不构成泄漏）
     */
    private static final Map<String, Logger> LOGGER_CACHE = new ConcurrentHashMap<>();

    /**
     * 内部兜底 Logger:仅用于 post handler 失败时的错误日志。
     * 不参与级别门控——门控完全基于调用方 logger(per-package 级别生效,ADR-0022)。
     */
    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(LogUtil.class);

    /**
     * 惰性栈遍历器(JDK 保证线程安全,可静态共享);RETAIN_CLASS_REFERENCE 使帧携带
     * Class 引用,以引用比较精确跳过 LogUtil 自身帧(ADR-0022)。
     */
    private static final StackWalker STACK_WALKER =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /**
     * 写前脱敏总开关(ADR-0020):默认开启——消息最终化后、写盘与 post handler 之前
     * 经 {@link MaskUtil#mask} 脱敏。仅覆盖消息体;Throwable 的 message/stack trace
     * 不脱敏(重写异常对象不可行,诚实局限)。
     */
    private static volatile boolean maskingEnabled = true;

    // ==================== 日志输出方法 ====================

    /**
     * <b>输出TRACE级别日志</b>
     *
     * @param msgTemp 日志消息模板，支持{}占位符
     * @param args    占位符参数
     */
    public static void trace(String msgTemp, Object... args) {
        final String callerClassName = getCallerClassName();
        final Logger logger = getLogger(callerClassName);

        if (logger.isTraceEnabled()) {
            final String msg = maskIfEnabled(formatMessage(msgTemp, args));
            logger.trace(msg);
            invokePostHandler(msg, Level.TRACE, callerClassName, null);
        }
    }

    /**
     * <b>输出DEBUG级别日志</b>
     *
     * @param msgTemp 日志消息模板，支持{}占位符
     * @param args    占位符参数
     */
    public static void debug(String msgTemp, Object... args) {
        final String callerClassName = getCallerClassName();
        final Logger logger = getLogger(callerClassName);

        if (logger.isDebugEnabled()) {
            final String msg = maskIfEnabled(formatMessage(msgTemp, args));
            logger.debug(msg);
            invokePostHandler(msg, Level.DEBUG, callerClassName, null);
        }
    }

    /**
     * <b>输出INFO级别日志</b>
     *
     * @param msgTemp 日志消息模板，支持{}占位符
     * @param args    占位符参数
     */
    public static void info(String msgTemp, Object... args) {
        final String callerClassName = getCallerClassName();
        final Logger logger = getLogger(callerClassName);

        if (logger.isInfoEnabled()) {
            final String msg = maskIfEnabled(formatMessage(msgTemp, args));
            logger.info(msg);
            invokePostHandler(msg, Level.INFO, callerClassName, null);
        }
    }

    /**
     * <b>输出WARN级别日志</b>
     *
     * @param msgTemp 日志消息模板，支持{}占位符
     * @param args    占位符参数
     */
    public static void warn(String msgTemp, Object... args) {
        final String callerClassName = getCallerClassName();
        final Logger logger = getLogger(callerClassName);

        if (logger.isWarnEnabled()) {
            final String msg = maskIfEnabled(formatMessage(msgTemp, args));
            logger.warn(msg);
            invokePostHandler(msg, Level.WARN, callerClassName, null);
        }
    }

    /**
     * <b>WARN 级日志（含异常 stack trace）</b>
     * <p>SLF4J 风格签名：msg 在前，Throwable 在后。与 SLF4J {@code Logger.warn(String, Throwable)} 对齐。</p>
     *
     * <p>详见 docs/adr/0005-rp-08-slf4j-throwable-position.md</p>
     *
     * @param msg 日志消息
     * @param t   异常（可 null）
     * @since phase-3
     */
    public static void warn(String msg, Throwable t) {
        final String callerClassName = getCallerClassName();
        final Logger logger = getLogger(callerClassName);

        if (logger.isWarnEnabled()) {
            final String masked = maskIfEnabled(msg);
            logger.warn(masked, t);
            invokePostHandler(masked, Level.WARN, callerClassName, t);
        }
    }

    /**
     * <b>WARN 级日志（含 SLF4J 占位符参数 + 异常）</b>
     * <p>{@code msgPattern} 含 SLF4J {@code {}} 占位符，由 {@code args} 填充。Throwable 显式置于
     * msgPattern 后、args 前，避免调用方误传 Throwable 到 args 末位。</p>
     *
     * @param msgPattern SLF4J 占位符格式串
     * @param t          异常（可 null）
     * @param args       占位符参数
     * @since phase-3
     */
    public static void warn(String msgPattern, Throwable t, Object... args) {
        final String callerClassName = getCallerClassName();
        final Logger logger = getLogger(callerClassName);

        if (logger.isWarnEnabled()) {
            final String msg = maskIfEnabled(formatMessage(msgPattern, args));
            logger.warn(msg, t);
            invokePostHandler(msg, Level.WARN, callerClassName, t);
        }
    }

    /**
     * <b>输出ERROR级别日志</b>
     *
     * @param msgTemp 日志消息模板，支持{}占位符
     * @param args    占位符参数
     */
    public static void error(String msgTemp, Object... args) {
        final String callerClassName = getCallerClassName();
        final Logger logger = getLogger(callerClassName);

        if (logger.isErrorEnabled()) {
            final String msg = maskIfEnabled(formatMessage(msgTemp, args));
            logger.error(msg);
            invokePostHandler(msg, Level.ERROR, callerClassName, null);
        }
    }

    /**
     * <b>ERROR 级日志（含异常 stack trace）</b>
     * <p>详见 docs/adr/0005-rp-08-slf4j-throwable-position.md</p>
     *
     * @param msg 日志消息
     * @param t   异常（可 null）
     * @since phase-3
     */
    public static void error(String msg, Throwable t) {
        final String callerClassName = getCallerClassName();
        final Logger logger = getLogger(callerClassName);

        if (logger.isErrorEnabled()) {
            final String masked = maskIfEnabled(msg);
            logger.error(masked, t);
            invokePostHandler(masked, Level.ERROR, callerClassName, t);
        }
    }

    /**
     * <b>ERROR 级日志（含 SLF4J 占位符参数 + 异常）</b>
     *
     * @param msgPattern SLF4J 占位符格式串
     * @param t          异常（可 null）
     * @param args       占位符参数
     * @since phase-3
     */
    public static void error(String msgPattern, Throwable t, Object... args) {
        final String callerClassName = getCallerClassName();
        final Logger logger = getLogger(callerClassName);

        if (logger.isErrorEnabled()) {
            final String msg = maskIfEnabled(formatMessage(msgPattern, args));
            logger.error(msg, t);
            invokePostHandler(msg, Level.ERROR, callerClassName, t);
        }
    }

    // ==================== 内部方法 ====================

    /**
     * <b>获取调用者的类名</b>
     * <p>
     * 基于 {@link StackWalker} 惰性遍历:按 Class 引用跳过 LogUtil 自身帧,返回第一个
     * 外部调用方类名。相比 {@code Thread.currentThread().getStackTrace()} 的全栈快照,
     * 惰性遍历只实体化前几帧;反射帧默认隐藏,反射调用方也能正确解析(ADR-0022)。
     * </p>
     *
     * @return 调用者的完整类名
     */
    private static String getCallerClassName() {
        return STACK_WALKER.walk(frames -> frames
                .filter(frame -> frame.getDeclaringClass() != LogUtil.class)
                .findFirst()
                .map(StackWalker.StackFrame::getClassName)
                // 防御回退:理论不可达(公共方法帧之外必有调用方)
                .orElse(LogUtil.class.getName()));
    }

    /**
     * <b>格式化消息</b>
     * <p>委托 SLF4J {@link org.slf4j.helpers.MessageFormatter#arrayFormat},与 SLF4J
     * {@code Logger} 的占位符语义完全一致:支持 {@code \\{}} 转义、数组参数深度格式化、
     * null 模板安全(RV2-17 翻案,ADR-0012)。</p>
     */
    private static String formatMessage(String template, Object... args) {
        if (args == null || args.length == 0) {
            return template;
        }
        // 三参变体禁用"尾参 Throwable 自动剥离":所有参数(含 Throwable,经 toString)按占位符填充,
        // 与旧手写实现一致;Throwable 的 stack trace 输出走显式重载位(ADR-0005)。
        return org.slf4j.helpers.MessageFormatter.arrayFormat(template, args, null).getMessage();
    }

    /**
     * <b>写前脱敏</b>:开关开启时委托 {@link MaskUtil#mask};null 透传。
     */
    private static String maskIfEnabled(String msg) {
        return maskingEnabled ? MaskUtil.mask(msg) : msg;
    }

    /**
     * <b>获取或创建Logger实例</b>
     *
     * @param className 类名
     * @return {@link Logger} 日志器实例
     */
    private static Logger getLogger(String className) {
        return LOGGER_CACHE.computeIfAbsent(className, LoggerFactory::getLogger);
    }

    /**
     * <b>调用日志后处理器</b>
     */
    private static void invokePostHandler(String message, Level level, String callerClassName, Throwable throwable) {
        SpringContextHolder.getBean(LogPostHandlerComposite.class)
                .ifOk(handler -> doInvokePostHandler(handler, message, level, callerClassName, throwable));
    }

    /**
     * <b>执行后处理器调用</b>
     */
    private static void doInvokePostHandler(LogPostHandlerComposite handler, String message,
                                            Level level, String callerClassName, Throwable throwable) {
        try {
            LogContext context = LogContext.builder()
                    .message(message)
                    .level(level)
                    .callerClassName(callerClassName)
                    .throwable(throwable)
                    .build();
            handler.handle(context);
        } catch (Exception e) {
            // 后处理器失败不应影响日志输出
            DEFAULT_LOGGER.error("Failed to invoke log post handler", e);
        }
    }

    /**
     * <b>设置写前脱敏开关</b>
     * <p>默认 {@code true}(安全默认)。仅在排障且环境可控时才应关闭;测试中修改后必须复位。</p>
     * <p>注意:脱敏仅覆盖消息体;Throwable 的 message/stack trace 不脱敏(见类文档与 ADR-0020)。</p>
     *
     * @param enabled 是否启用脱敏
     */
    public static void setMaskingEnabled(boolean enabled) {
        maskingEnabled = enabled;
    }

    /**
     * <b>查询写前脱敏开关状态</b>
     *
     * @return 当前是否启用脱敏
     */
    public static boolean isMaskingEnabled() {
        return maskingEnabled;
    }

    /**
     * Compatibility no-op: handlers are resolved from the current application on each call.
     * @deprecated Handler cleanup is managed by the owning application context.
     */
    @Deprecated(since = "0.1.0", forRemoval = false)
    public static void clearHandlerCache() {
        // Compatibility no-op: Spring handlers are no longer cached across application lifecycles.
    }

    /**
     * <b>清除Logger缓存</b>
     * <p>主要用于测试场景</p>
     */
    public static void clearLoggerCache() {
        LOGGER_CACHE.clear();
    }
}
