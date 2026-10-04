package cn.code91.facility.web.session;

import jakarta.annotation.Nullable;
import java.util.Optional;

/**
 * <b>当前用户上下文持有器</b>
 * <p>
 * 基于 {@link ThreadLocal} 管理兼容用户数据；存在值不证明认证。新代码使用宿主 Security 上下文。
 * 自动装配的请求边界适配 Servlet Principal，并在实际 Servlet/Callable 执行线程退出时清理。外部生产者自行管理其作用域。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 在拦截器中设置
 * SessionUserHolder.setUser(currentUser);
 *
 * // 在业务代码中获取
 * Optional<User> user = SessionUserHolder.getUser(User.class);
 *
 * // 在 finally 中清理
 * SessionUserHolder.clear();
 * }</pre>
 *
 * @author yvvb
 * @since 2.0.0
 */
public final class SessionUserHolder {

    private SessionUserHolder() {
        throw new UnsupportedOperationException("Holder class cannot be instantiated");
    }

    private static final ThreadLocal<Object> USER_HOLDER = new ThreadLocal<>();

    // ==================== 操作方法 ====================

    /**
     * 设置当前线程的用户
     *
     * @param user 用户对象
     * @param <T>  用户类型
     */
    public static <T> void setUser(@Nullable T user) {
        if (user == null) USER_HOLDER.remove(); else USER_HOLDER.set(user);
    }

    /**
     * 获取当前线程的用户
     *
     * @param type 用户类型
     * @param <T>  类型参数
     * @return 用户对象
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> getUser(Class<T> type) {
        Object user = USER_HOLDER.get();
        if (user != null && type.isInstance(user)) {
            return Optional.of((T) user);
        }
        return Optional.empty();
    }

    /**
     * 清理当前线程的用户信息
     * <p>
     * 必须在请求结束时调用，避免内存泄漏。
     * </p>
     */
    public static void clear() {
        USER_HOLDER.remove();
    }

    /**
     * 兼容名称：仅判断当前线程是否已设置值，不证明登录或授权。
     *
     * @return true 如果已设置用户
     */
    @Deprecated(since = "0.1.0", forRemoval = false)
    public static boolean isLoggedIn() {
        return USER_HOLDER.get() != null;
    }
}
