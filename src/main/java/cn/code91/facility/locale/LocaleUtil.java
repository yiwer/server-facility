package cn.code91.facility.locale;

import cn.code91.facility.common.NullSafe;
import cn.code91.facility.context.SpringContextHolder;
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
 * @author yvvb
 * @since 2025/5/4
 */
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
     * @return 翻译后的消息，如果翻译失败则返回原始key
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
     * @return 翻译后的消息，如果翻译失败则返回原始key
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
     * @return 翻译后的消息，如果翻译失败则返回原始key
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
     * @return 翻译后的消息，如果翻译失败则返回原始key
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
            return renderFallback(fallbackPattern, args);
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
        return fallbackPattern == null ? messageKey : renderFallback(fallbackPattern, args);
    }

    private static String renderFallback(String pattern, Object[] args) {
        if (pattern == null) {
            return "";
        }
        if (args == null || args.length == 0) {
            return pattern;
        }
        return MessageFormat.format(pattern, args);
    }
}
