package cn.code91.facility.locale;

import cn.code91.facility.common.NullSafe;
import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.error.ErrorTypeInterface;
import lombok.experimental.UtilityClass;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;

import java.text.MessageFormat;
import java.util.Locale;

/**
 * <b>国际化工具类</b>
 * <p>
 * 提供基于Spring MessageSource的国际化消息翻译功能。
 * 支持带参数的消息翻译和自动获取当前线程的Locale。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 简单翻译
 * String msg = LocaleUtil.translateMessage("user.not_exist");
 *
 * // 带参数的翻译
 * String msg = LocaleUtil.translateMessageWithArgs("user.welcome", new Object[]{username});
 *
 * // 指定Locale的翻译
 * String msg = LocaleUtil.translateMessage("user.not_exist", Locale.ENGLISH);
 * }</pre>
 *
 * @deprecated Constructor-inject the application MessageSource and pass an explicit Locale.
 * Static lookup retains historical single-context behavior only.
 * @author yvvb
 * @since 2025/5/4
 */
@Deprecated(since = "0.1", forRemoval = false)
@UtilityClass
public class LocaleUtil {

    /**
     * <b>获取当前线程的Locale</b>
     * <p>
     * 从Spring的LocaleContextHolder中获取当前请求的Locale。
     * </p>
     *
     * @return {@link Locale} 当前线程的Locale
     */
    public static Locale getLocale() {
        return LocaleContextHolder.getLocale();
    }

    /**
     * <b>带参数的消息翻译（指定Locale）</b>
     *
     * @param messageKey  消息键
     * @param messageArgs 消息参数数组
     * @param locale      {@link Locale} 目标语言环境
     *
     * @return 翻译后的消息；无 MessageSource bean 时返回原始 messageKey。注意:MessageSource 在场但 messageKey 缺失将抛 NoSuchMessageException 穿透——需缺键兜底请用 {@link #translateMessageWithFallback}
     */
    public static String translateMessageWithArgs(String messageKey, Object[] messageArgs, Locale locale) {
        if (NullSafe.isBlank(messageKey)) {
            return "";
        }
        return SpringContextHolder.getBean(MessageSource.class).map(messageSource ->
                messageSource.getMessage(messageKey, messageArgs, locale)
        ).orElse(messageKey);
    }

    /**
     * <b>简单消息翻译（指定Locale）</b>
     *
     * @param messageKey 消息键
     * @param locale     {@link Locale} 目标语言环境
     *
     * @return 翻译后的消息；无 MessageSource bean 时返回原始 messageKey。注意:MessageSource 在场但 messageKey 缺失将抛 NoSuchMessageException 穿透——需缺键兜底请用 {@link #translateMessageWithFallback}
     */
    public static String translateMessage(String messageKey, Locale locale) {
        if (NullSafe.isBlank(messageKey)) {
            return "";
        }
        return SpringContextHolder.getBean(MessageSource.class).map(messageSource ->
                messageSource.getMessage(messageKey, null, locale)
        ).orElse(messageKey);
    }

    /**
     * <b>带参数的消息翻译（使用当前线程Locale）</b>
     *
     * @param messageKey  消息键
     * @param messageArgs 消息参数数组
     *
     * @return 翻译后的消息；无 MessageSource bean 时返回原始 messageKey。注意:MessageSource 在场但 messageKey 缺失将抛 NoSuchMessageException 穿透——需缺键兜底请用 {@link #translateMessageWithFallback}
     */
    public static String translateMessageWithArgs(String messageKey, Object[] messageArgs) {
        if (NullSafe.isBlank(messageKey)) {
            return "";
        }
        return SpringContextHolder.getBean(MessageSource.class).map(messageSource ->
                messageSource.getMessage(messageKey, messageArgs, getLocale())
        ).orElse(messageKey);
    }

    /**
     * <b>简单消息翻译（使用当前线程Locale）</b>
     *
     * @param messageKey 消息键
     *
     * @return 翻译后的消息；无 MessageSource bean 时返回原始 messageKey。注意:MessageSource 在场但 messageKey 缺失将抛 NoSuchMessageException 穿透——需缺键兜底请用 {@link #translateMessageWithFallback}
     */
    public static String translateMessage(String messageKey) {
        if (NullSafe.isBlank(messageKey)) {
            return "";
        }
        return SpringContextHolder.getBean(MessageSource.class).map(messageSource ->
                messageSource.getMessage(messageKey, null, getLocale())
        ).orElse(messageKey);
    }

    /**
     * <b>带 fallback 的消息翻译</b>
     * <p>
     * 解析顺序：(1) MessageSource 命中，(2) {@code fallbackPattern} 用 {@link MessageFormat}
     * 渲染参数，(3) {@code fallbackPattern} 为 null 时返回 {@code messageKey} 本身。
     * </p>
     *
     * @param messageKey      消息键（空白时直接走 fallback）
     * @param args            消息参数
     * @param fallbackPattern MessageSource 未命中时使用的兜底模板
     * @param locale          {@link Locale} 目标语言环境
     *
     * @return 翻译后的消息
     */
    public static String translateMessageWithFallback(String messageKey,
                                                      Object[] args,
                                                      String fallbackPattern,
                                                      Locale locale) {
        if (NullSafe.isBlank(messageKey)) {
            return renderFallback(fallbackPattern, args, locale);
        }
        String resolved = SpringContextHolder.getBean(MessageSource.class)
                .map(ms -> {
                    try {
                        return ms.getMessage(messageKey, args, locale);
                    } catch (NoSuchMessageException e) {
                        return null;
                    }
                })
                .orElse(null);
        if (resolved != null) {
            return resolved;
        }
        return fallbackPattern == null ? messageKey : renderFallback(fallbackPattern, args, locale);
    }

    // ==================== ErrorTypeInterface 边界本地化(C1,ADR-0010) ====================

    /**
     * <b>错误类型的边界本地化解析</b>
     * <p>C1 断环(ADR-0010)后 error 包不做 i18n;需要本地化消息的边界(如 P6 的全局异常处理器)
     * 经此入口解析:MessageSource 命中返回本地化文案,未命中回退 {@code errorType.getDefaultMessage()}
     * 模板渲染——语义即 {@link #translateMessageWithFallback} 的等价旧行为。</p>
     *
     * @param errorType 错误类型(不能为 null)
     * @param args      消息参数
     * @param locale    目标语言环境
     * @return 本地化消息,或默认模板渲染结果
     */
    public static String localize(ErrorTypeInterface errorType, Object[] args, Locale locale) {
        java.util.Objects.requireNonNull(errorType, "errorType cannot be null");
        return translateMessageWithFallback(errorType.getMessageKey(), args, errorType.getDefaultMessage(), locale);
    }

    /**
     * <b>错误类型的边界本地化解析(当前线程 Locale)</b>
     *
     * @param errorType 错误类型(不能为 null)
     * @param args      消息参数
     * @return 本地化消息,或默认模板渲染结果
     */
    public static String localize(ErrorTypeInterface errorType, Object... args) {
        return localize(errorType, args, getLocale());
    }

    private static String renderFallback(String pattern, Object[] args, Locale locale) {
        if (pattern == null) {
            return "";
        }
        if (args == null || args.length == 0) {
            return pattern;
        }
        return new MessageFormat(pattern, locale == null ? getLocale() : locale).format(args);
    }
}
