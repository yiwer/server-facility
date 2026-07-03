package cn.code91.facility.web.session;

import jakarta.servlet.http.HttpSession;
import lombok.experimental.UtilityClass;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * <b>Session操作工具类</b>
 * <p>
 * 提供对 {@link HttpSession} 的便捷操作，自动从 RequestContextHolder 获取当前Session。
 * 所有读取操作不会创建新的Session。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 读取属性
 * Optional<User> user = SessionUtil.getAttribute(SessionKeyConstants.CURRENT_USER, User.class);
 *
 * // 写入属性
 * SessionUtil.setAttribute(SessionKeyConstants.CURRENT_USER, currentUser);
 *
 * // 注销
 * SessionUtil.invalidate();
 * }</pre>
 *
 * @author yvvb
 * @since 2.0.0
 * @see SessionKeyConstants
 */
@UtilityClass
public class SessionUtil {

    // ==================== Session获取 ====================

    /**
     * 获取当前Session（不创建新Session）
     *
     * @return Session实例，不在Web上下文或无Session时返回空
     */
    public Optional<HttpSession> getSession() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes sra) {
            return Optional.ofNullable(sra.getRequest().getSession(false));
        }
        return Optional.empty();
    }

    /**
     * 获取当前Session（创建新Session）
     *
     * @return Session实例，不在Web上下文返回空,无session则创建session
     */
    public Optional<HttpSession> getOrBuildSession() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes sra) {
            return Optional.ofNullable(sra.getRequest().getSession(true));
        }
        return Optional.empty();
    }

    // ==================== 属性操作 ====================

    /**
     * 获取Session属性
     *
     * @param name Session属性名
     * @param type 目标类型
     * @param <T>  类型参数
     * @return 属性值
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getAttribute(String name, Class<T> type) {
        return getSession()
                .map(session -> session.getAttribute(name))
                .filter(type::isInstance)
                .map(value -> (T) value);
    }

    /**
     * 设置Session属性（会自动创建Session）
     *
     * @param name  属性名
     * @param value 属性值
     */
    public void setAttribute(String name, Object value) {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes sra) {
            sra.getRequest().getSession(true).setAttribute(name, value);
        }
    }

    /**
     * 移除Session属性
     *
     * @param name 属性名
     */
    public void removeAttribute(String name) {
        getSession().ifPresent(session -> session.removeAttribute(name));
    }

    // ==================== Session管理 ====================

    /**
     * 使当前Session失效
     */
    public void invalidate() {
        getSession().ifPresent(HttpSession::invalidate);
    }

    /**
     * 获取当前Session ID
     *
     * @return Session ID
     */
    public Optional<String> getSessionId() {
        return getSession().map(HttpSession::getId);
    }
}
