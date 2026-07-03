package cn.code91.facility.web.session;

/**
 * <b>Session键名常量</b>
 * <p>
 * 定义系统中使用的Session属性键名，统一管理避免硬编码。
 * 所有键名以 {@code session:} 为前缀，后跟语义化名称。
 * </p>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * SessionUtil.setAttribute(SessionKeyConstants.CURRENT_USER, user);
 * Optional<User> user = SessionUtil.getAttribute(SessionKeyConstants.CURRENT_USER, User.class);
 * }</pre>
 *
 * @author yvvb
 * @since 2.0.0
 * @see SessionUtil
 */
public final class SessionKeyConstants {

    private SessionKeyConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    /**
     * 当前登录用户
     */
    public static final String CURRENT_USER = "session:current_user";

    /**
     * 用户权限集合
     */
    public static final String USER_PERMISSIONS = "session:user_permissions";

    /**
     * 租户ID
     */
    public static final String TENANT_ID = "session:tenant_id";

    /**
     * 登录时间
     */
    public static final String LOGIN_TIME = "session:login_time";

    /**
     * 验证码
     */
    public static final String CAPTCHA_CODE = "session:captcha_code";
}
