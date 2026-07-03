package cn.code91.facility.web.exception;

import org.springframework.core.env.Environment;

/**
 * <b>全局异常处理器 — 默认实现</b>
 * <p>
 * 直接继承 {@link AbstractGlobalExceptionHandler} 的所有默认行为，不做任何定制。
 * </p>
 * <p>
 * 由 {@link cn.code91.facility.autoconfigure.FacilityWebAutoConfiguration} 通过
 * {@code @ConditionalOnMissingBean(AbstractGlobalExceptionHandler.class)} 自动注册。
 * 若应用提供了自己的 {@link AbstractGlobalExceptionHandler} 子类，则本类不会被注册。
 * </p>
 *
 * @author yvvb
 * @see AbstractGlobalExceptionHandler
 * @since 2.0.0
 */
public class DefaultGlobalExceptionHandler extends AbstractGlobalExceptionHandler {

    public DefaultGlobalExceptionHandler(FacilityWebExceptionProperties props, Environment environment) {
        super(props, environment);
    }
}
