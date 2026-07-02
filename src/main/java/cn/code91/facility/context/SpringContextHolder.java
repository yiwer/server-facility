package cn.code91.facility.context;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <b>Spring上下文持有器 - 重构版本</b>
 * <p>
 * 提供静态方式访问Spring容器中的Bean实例，便于在非 Spring 管理的类中获取 Spring Bean。
 * 实现 {@link ApplicationContextAware} 接口，在 Spring 容器初始化时自动注入 ApplicationContext。
 * </p>
 *
 * <h3>重构改进：</h3>
 * <ul>
 *     <li><b>线程安全</b>：使用 AtomicReference 保证并发安全</li>
 *     <li><b>生命周期管理</b>：实现 DisposableBean 清理资源</li>
 *     <li><b>防重复注入</b>：检测并警告重复注入</li>
 *     <li><b>详细日志</b>：记录初始化和销毁过程</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 根据类型获取Bean
 * Result<UserService, WrappedError> result = SpringContextHolder.getBean(UserService.class);
 * result.ifOk(service -> service.doSomething());
 *
 * // 根据名称和类型获取Bean
 * SpringContextHolder.getBean("myService", MyService.class)
 *     .ifOk(service -> service.process());
 * }</pre>
 *
 * @author yvvb
 * @see Result
 * @see WrappedError
 * @since 2.0.0
 * @apiNote 重构版本，修复了并发和生命周期问题
 */
public class SpringContextHolder implements ApplicationContextAware, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SpringContextHolder.class);

    /**
     * Spring应用上下文（使用 AtomicReference 保证线程安全）
     * <p>重构说明：从 static 字段改为 AtomicReference</p>
     */
    private static final AtomicReference<ApplicationContext> CONTEXT_REF = new AtomicReference<>();

    /**
     * <b>检查ApplicationContext是否未初始化</b>
     *
     * @return true-未初始化，false-已初始化
     */
    public static boolean isNotInitialized() {
        return CONTEXT_REF.get() == null;
    }

    /**
     * <b>检查ApplicationContext是否已初始化</b>
     *
     * @return true-已初始化，false-未初始化
     */
    public static boolean isInitialized() {
        return CONTEXT_REF.get() != null;
    }

    /**
     * <b>获取ApplicationContext实例</b>
     *
     * @return ApplicationContext 或 null
     */
    @Nullable
    public static ApplicationContext getApplicationContext() {
        return CONTEXT_REF.get();
    }

    /**
     * <b>根据类型获取Spring Bean实例</b>
     *
     * @param clazz {@link Class} Bean的类型
     * @param <T>   Bean类型泛型
     * @return {@link Result} 包含 Bean 实例或错误信息
     */
    public static <T> Result<T, WrappedError> getBean(Class<T> clazz) {
        ApplicationContext context = CONTEXT_REF.get();
        if (context == null) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED
            ));
        }

        try {
            T bean = context.getBean(clazz);
            return Result.ok(bean);
        } catch (BeansException e) {
            log.debug("Failed to get bean of type: {}", clazz.getName(), e);
            return Result.err(WrappedError.of(
                    FacilityErrorType.CONTEXT_GET_BEAN_ERROR,
                    e,
                    clazz.getName()
            ));
        }
    }

    /**
     * <b>根据名称和类型获取Spring Bean实例</b>
     *
     * @param beanName     Bean的名称
     * @param requiredType {@link Class} Bean的类型
     * @param <T>          Bean类型泛型
     * @return {@link Result} 包含 Bean 实例或错误信息
     */
    public static <T> Result<T, WrappedError> getBean(String beanName, Class<T> requiredType) {
        ApplicationContext context = CONTEXT_REF.get();
        if (context == null) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED
            ));
        }

        try {
            T bean = context.getBean(beanName, requiredType);
            return Result.ok(bean);
        } catch (BeansException e) {
            log.debug("Failed to get bean '{}' of type: {}", beanName, requiredType.getName(), e);
            return Result.err(WrappedError.of(
                    FacilityErrorType.CONTEXT_GET_BEAN_ERROR,
                    e,
                    beanName,
                    requiredType.getName()
            ));
        }
    }

    /**
     * <b>根据名称获取Bean（不指定类型）</b>
     *
     * @param beanName Bean的名称
     * @return {@link Result} 包含 Bean 实例或错误信息
     */
    public static Result<Object, WrappedError> getBean(String beanName) {
        ApplicationContext context = CONTEXT_REF.get();
        if (context == null) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED
            ));
        }

        try {
            Object bean = context.getBean(beanName);
            return Result.ok(bean);
        } catch (BeansException e) {
            log.debug("Failed to get bean: {}", beanName, e);
            return Result.err(WrappedError.of(
                    FacilityErrorType.CONTEXT_GET_BEAN_ERROR,
                    e,
                    beanName
            ));
        }
    }

    /**
     * <b>检查是否包含指定名称的Bean</b>
     *
     * @param beanName Bean名称
     * @return true 如果包含
     */
    public static boolean containsBean(String beanName) {
        ApplicationContext context = CONTEXT_REF.get();
        return context != null && context.containsBean(beanName);
    }

    /**
     * <b>获取指定类型的所有Bean名称</b>
     *
     * @param type Bean类型
     * @return Bean名称数组
     */
    public static String[] getBeanNamesForType(Class<?> type) {
        ApplicationContext context = CONTEXT_REF.get();
        if (context == null) {
            return new String[0];
        }
        return context.getBeanNamesForType(type);
    }

    // ==================== 生命周期回调 ====================

    /**
     * <b>Spring容器回调方法，注入ApplicationContext</b>
     * <p>重构说明：使用 CAS 操作保证线程安全，检测重复注入</p>
     *
     * @param applicationContext {@link ApplicationContext} Spring应用上下文
     * @throws BeansException Bean异常
     */
    @Override
    public void setApplicationContext(@Nullable ApplicationContext applicationContext) throws BeansException {
        if (applicationContext == null) {
            log.warn("Received null ApplicationContext, ignoring");
            return;
        }

        // 使用 CAS 确保只设置一次
        if (!CONTEXT_REF.compareAndSet(null, applicationContext)) {
            ApplicationContext existing = CONTEXT_REF.get();
            if (existing != applicationContext) {
                log.warn("ApplicationContext already set. " +
                                "Existing: {}, New: {}. Ignoring duplicate injection.",
                        existing.getId(),
                        applicationContext.getId());
            }
        } else {
            log.info("SpringContextHolder initialized with ApplicationContext: {}",
                    applicationContext.getId());
        }
    }

    /**
     * <b>Spring容器销毁时的回调方法</b>
     * <p>重构说明：清理 ApplicationContext 引用</p>
     */
    @Override
    public void destroy() {
        ApplicationContext context = CONTEXT_REF.getAndSet(null);
        if (context != null) {
            log.info("SpringContextHolder destroyed, cleared ApplicationContext: {}", context.getId());
        }
    }

    // ==================== 工具方法 ====================

    /**
     * <b>手动设置ApplicationContext</b>
     * <p>
     * 主要用于测试场景。
     * <b>警告</b>：生产环境不应手动调用此方法。
     * </p>
     *
     * @param context ApplicationContext 实例
     */
    public static void setApplicationContextManually(ApplicationContext context) {
        if (context == null) {
            log.warn("Attempting to set null ApplicationContext manually");
            return;
        }

        ApplicationContext old = CONTEXT_REF.getAndSet(context);
        if (old != null && old != context) {
            log.warn("Replaced existing ApplicationContext {} with {}",
                    old.getId(), context.getId());
        } else {
            log.info("Manually set ApplicationContext: {}", context.getId());
        }
    }

    /**
     * <b>清除ApplicationContext</b>
     * <p>
     * 仅供测试与框架内部使用（package-private）；生产代码不应调用以免破坏静态访问语义（RP-12）。
     * </p>
     */
    static void clear() {
        ApplicationContext old = CONTEXT_REF.getAndSet(null);
        if (old != null) {
            log.info("Manually cleared ApplicationContext: {}", old.getId());
        }
    }

    /**
     * <b>获取上下文信息（用于调试）</b>
     *
     * @return 上下文描述信息
     */
    public static String getContextInfo() {
        ApplicationContext context = CONTEXT_REF.get();
        if (context == null) {
            return "ApplicationContext not initialized";
        }

        return String.format("ApplicationContext{id='%s', displayName='%s', beanCount=%d}",
                context.getId(),
                context.getDisplayName(),
                context.getBeanDefinitionCount());
    }
}
