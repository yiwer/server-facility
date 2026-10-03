package cn.code91.facility.context;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.result.Result;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Compatibility service locator for the first successfully refreshed application.
 *
 * <p>New code should constructor-inject its required services. This process-wide facade
 * cannot route calls between applications. Only the holder instance that published a
 * context may withdraw it; rejected contexts remain independent and are not promoted
 * automatically. Parent/child lifecycle events are matched by context identity.</p>
 *
 * <p>Publication occurs on this context's {@link ContextRefreshedEvent}, and withdrawal
 * on {@link ContextClosedEvent} or bean destruction. Repeated destruction is harmless.
 * Lookups racing with close return an error; already returned services and in-flight
 * work remain governed by their own Spring lifecycle.</p>
 *
 * @deprecated Prefer constructor injection of the required bean; retained for migration.
 */
@Deprecated(since = "0.1.0", forRemoval = false)
public class SpringContextHolder implements ApplicationContextAware, DisposableBean, ApplicationListener<ApplicationContextEvent>, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SpringContextHolder.class);

    /**
     * 单个静态注册令牌，归发布它的 holder 实例所有。
     */
    private static final AtomicReference<SpringContextHolder> CONTEXT_REF = new AtomicReference<>();

    private volatile ApplicationContext ownedContext;
    private boolean destroyed;

    /**
     * <b>检查ApplicationContext是否未初始化</b>
     *
     * @return true-未初始化，false-已初始化
     */
    public static boolean isNotInitialized() {
        return getApplicationContext() == null;
    }

    /**
     * <b>检查ApplicationContext是否已初始化</b>
     *
     * @return true-已初始化，false-未初始化
     */
    public static boolean isInitialized() {
        return getApplicationContext() != null;
    }

    /**
     * <b>获取ApplicationContext实例</b>
     *
     * @return ApplicationContext 或 null
     */
    @Nullable
    public static ApplicationContext getApplicationContext() {
        SpringContextHolder owner = CONTEXT_REF.get();
        ApplicationContext context = owner == null ? null : owner.ownedContext;
        if (context instanceof AbstractApplicationContext application && application.isClosed()
                || context instanceof ConfigurableApplicationContext configurable && !configurable.isActive()) {
            return null;
        }
        return context;
    }

    /**
     * <b>根据类型获取Spring Bean实例</b>
     *
     * @param clazz {@link Class} Bean的类型
     * @param <T>   Bean类型泛型
     * @return {@link Result} 包含 Bean 实例或错误信息
     */
    public static <T> Result<T, WrappedError> getBean(Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz");
        ApplicationContext context = getApplicationContext();
        if (context == null) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED
            ));
        }

        try {
            T bean = context.getBean(clazz);
            return Result.ok(bean);
        } catch (BeansException | IllegalStateException e) {
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
    public static <T> Result<T, WrappedError> getBean(@Nullable String beanName, Class<T> requiredType) {
        Objects.requireNonNull(requiredType, "requiredType");
        ApplicationContext context = getApplicationContext();
        if (context == null) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED
            ));
        }

        if (beanName == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CONTEXT_GET_BEAN_ERROR));
        }
        try {
            T bean = context.getBean(beanName, requiredType);
            return Result.ok(bean);
        } catch (BeansException | IllegalStateException e) {
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
    public static Result<Object, WrappedError> getBean(@Nullable String beanName) {
        ApplicationContext context = getApplicationContext();
        if (context == null) {
            return Result.err(WrappedError.of(
                    FacilityErrorType.CONTEXT_INSTANCE_NOT_INITIALIZED
            ));
        }

        if (beanName == null) {
            return Result.err(WrappedError.of(FacilityErrorType.CONTEXT_GET_BEAN_ERROR));
        }
        try {
            Object bean = context.getBean(beanName);
            return Result.ok(bean);
        } catch (BeansException | IllegalStateException e) {
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
    public static boolean containsBean(@Nullable String beanName) {
        ApplicationContext context = getApplicationContext();
        try {
            return context != null && beanName != null && context.containsBean(beanName);
        } catch (IllegalStateException closed) {
            return false;
        }
    }

    /**
     * <b>获取指定类型的所有Bean名称</b>
     *
     * @param type Bean类型
     * @return Bean名称数组
     */
    public static String[] getBeanNamesForType(Class<?> type) {
        Objects.requireNonNull(type, "type");
        ApplicationContext context = getApplicationContext();
        if (context == null) {
            return new String[0];
        }
        try {
            return context.getBeanNamesForType(type);
        } catch (IllegalStateException closed) {
            return new String[0];
        }
    }

    // ==================== 生命周期回调 ====================

    /**
     * <b>Spring容器回调方法，注入ApplicationContext</b>
     * <p>仅绑定本实例；成功刷新事件到达后才发布。</p>
     *
     * @param applicationContext {@link ApplicationContext} Spring应用上下文
     * @throws BeansException Bean异常
     */
    @Override
    public synchronized void setApplicationContext(@Nullable ApplicationContext applicationContext) throws BeansException {
        if (applicationContext == null) {
            log.warn("Received null ApplicationContext, ignoring");
            return;
        }

        if (!destroyed && ownedContext == null) {
            ownedContext = applicationContext;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public boolean supportsAsyncExecution() {
        return false;
    }

    @Override
    public synchronized void onApplicationEvent(ApplicationContextEvent event) {
        if (!destroyed && event instanceof ContextRefreshedEvent && event.getApplicationContext() == ownedContext) {
            if (CONTEXT_REF.compareAndSet(null, this)) {
                log.info("SpringContextHolder registered ApplicationContext: {}", ownedContext.getId());
            } else if (CONTEXT_REF.get() != this) {
                log.warn("SpringContextHolder already has an owner; ignoring ApplicationContext: {}", ownedContext.getId());
            }
        } else if (event instanceof ContextClosedEvent && event.getApplicationContext() == ownedContext) {
            destroy();
        }
    }

    /**
     * <b>Spring容器销毁时的回调方法</b>
     * <p>重构说明：清理 ApplicationContext 引用</p>
     */
    @Override
    public synchronized void destroy() {
        destroyed = true;
        ApplicationContext context = ownedContext;
        if (context != null && CONTEXT_REF.compareAndSet(this, null)) {
            log.info("SpringContextHolder destroyed, cleared ApplicationContext: {}", context.getId());
        }
        if (context instanceof AbstractApplicationContext application) {
            application.removeApplicationListener(this);
        }
        ownedContext = null;
    }

    // ==================== 工具方法 ====================

    /**
     * <b>手动设置ApplicationContext</b>
     * <p>
     * 兼容入口：须在 refresh 返回后传入活跃且未开始关闭、使用 Spring 标准 singleton registry 的 AbstractApplicationContext。
     * 不替换现有 owner，自动随该 context 关闭撤销。新代码应使用构造器注入。
     * </p>
     *
     * @param context ApplicationContext 实例
     */
    public static synchronized void setApplicationContextManually(@Nullable ApplicationContext context) {
        if (context == null) {
            log.warn("Attempting to set null ApplicationContext manually");
            return;
        }

        if (!(context instanceof AbstractApplicationContext configurable)
                || !configurable.isActive() || configurable.isClosed()) {
            throw new IllegalArgumentException("Manual registration requires an active, non-closing AbstractApplicationContext");
        }
        if (CONTEXT_REF.get() != null) {
            return;
        }
        if (!(configurable.getBeanFactory() instanceof DefaultSingletonBeanRegistry registry)) {
            throw new IllegalArgumentException("Manual registration requires Spring's singleton lifecycle registry");
        }
        SpringContextHolder registration = new SpringContextHolder();
        synchronized (registration) {
            registration.setApplicationContext(context);
            configurable.addApplicationListener(registration);
            if (configurable.isActive() && !configurable.isClosed()) {
                registry.registerDisposableBean(SpringContextHolder.class.getName() + ".manual", registration);
                registration.onApplicationEvent(new ContextRefreshedEvent(context));
            } else {
                registration.destroy();
            }
        }
    }

    /**
     * <b>获取上下文信息（用于调试）</b>
     *
     * @return 上下文描述信息
     */
    public static String getContextInfo() {
        ApplicationContext context = getApplicationContext();
        if (context == null) {
            return "ApplicationContext not initialized";
        }

        try {
            return String.format("ApplicationContext{id='%s', displayName='%s', beanCount=%d}",
                    context.getId(), context.getDisplayName(), context.getBeanDefinitionCount());
        } catch (IllegalStateException closed) {
            return "ApplicationContext not initialized";
        }
    }
}
