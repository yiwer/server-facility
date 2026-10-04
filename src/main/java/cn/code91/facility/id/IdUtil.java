package cn.code91.facility.id;

import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.id.support.SnowIdGenerator;

import java.util.UUID;

/**
 * Legacy identifier facade. Prefer JDK UUID.randomUUID or an injected explicit-node SnowIdGenerator.
 * Numeric generation/epoch parsing require an explicitly assigned provider; no node is invented.
 * Manual set/reset remains process-scoped compatibility state. Application beans are resolved without
 * retaining a closed context's generator. See docs/building/identifier-policy.md.
 * @deprecated Use the JDK UUID API or constructor-injected SnowIdGenerator.
 */
@Deprecated(since = "0.1.0", forRemoval = false)
public final class IdUtil {

    private IdUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 显式手工设置的ID生成器引用（不缓存 Spring bean）
     * <p>仅 setGenerator 显式设置；resetGenerator 恢复 Spring 查找；缺席拒绝。</p>
     */
    private static volatile SnowIdGenerator cachedGenerator = null;

    /** Resolves the current application's generator without retaining a Spring bean after close. */
    private static SnowIdGenerator getIdGenerator() {
        SnowIdGenerator explicit = cachedGenerator;
        if (explicit != null) {
            return explicit;
        }
        SnowIdGenerator springBean = SpringContextHolder.getBean(SnowIdGenerator.class).orElse(null);
        if (springBean != null) {
            return springBean;
        }
        throw new IllegalStateException("An explicit SnowIdGenerator is required; use UUID.randomUUID by default");
    }

    // ==================== ID 生成方法 ====================

    /**
     * <b>生成雪花算法ID</b>
     * <p>
     * 生成的ID具有以下特性：
     * </p>
     * <ul>
     *     <li>唯一性依赖显式节点分配与跨重启高水位协议</li>
     *     <li>趋势递增</li>
     *     <li>不会自行分配或检测其他实例的节点</li>
     * </ul>
     *
     * @return 雪花算法生成的唯一ID
     */
    public static Long snowId() {
        return getIdGenerator().nextId();
    }

    /**
     * <b>生成UUID对象</b>
     *
     * @return {@link UUID} 随机生成的UUID
     */
    public static UUID uuid() {
        return UUID.randomUUID();
    }

    /**
     * <b>生成UUID字符串</b>
     * <p>
     * 格式：xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
     * </p>
     *
     * @return UUID字符串
     */
    public static String uuidStr() {
        return UUID.randomUUID().toString();
    }

    /**
     * <b>生成不含连字符的UUID字符串</b>
     * <p>
     * 格式：xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx (32位)
     * </p>
     *
     * @return 不含连字符的UUID字符串
     */
    public static String uuidSimpleStr() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * <b>生成大写的UUID字符串</b>
     *
     * @return 大写的UUID字符串
     */
    public static String uuidStrUpperCase() {
        return UUID.randomUUID().toString().toUpperCase();
    }

    /**
     * <b>生成不含连字符的大写UUID字符串</b>
     *
     * @return 不含连字符的大写UUID字符串
     */
    public static String uuidSimpleStrUpperCase() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    // ==================== ID 解析方法 ====================

    /**
     * <b>从雪花ID中解析时间戳</b>
     * <p>通过持有的 {@link SnowIdGenerator} 实例调用，确保自定义 epoch 场景下解析正确。
     * 详见 ADR-0008。</p>
     *
     * @param id 雪花ID
     * @return 生成该ID时的时间戳（毫秒）
     */
    public static long parseTimestamp(long id) {
        return getIdGenerator().parseTimestamp(id);
    }

    /**
     * <b>从雪花ID中解析工作节点ID</b>
     *
     * @param id 雪花ID
     * @return 工作节点ID
     */
    public static long parseWorkerId(long id) {
        return SnowIdGenerator.parseWorkerId(id);
    }

    /**
     * <b>从雪花ID中解析数据中心ID</b>
     *
     * @param id 雪花ID
     * @return 数据中心ID
     */
    public static long parseDataCenterId(long id) {
        return SnowIdGenerator.parseDataCenterId(id);
    }

    /**
     * <b>从雪花ID中解析序列号</b>
     *
     * @param id 雪花ID
     * @return 序列号
     */
    public static long parseSequence(long id) {
        return SnowIdGenerator.parseSequence(id);
    }

    /**
     * <b>获取雪花ID的详细信息（用于调试）</b>
     * <p>通过持有的 {@link SnowIdGenerator} 实例调用，详见 ADR-0008。</p>
     *
     * @param id 雪花ID
     * @return 包含各部分信息的字符串
     */
    public static String parseInfo(long id) {
        return getIdGenerator().parseInfo(id);
    }

    // ==================== 工具方法 ====================

    /**
     * <b>获取当前使用的生成器类型</b>
     * <p>用于调试和监控</p>
     *
     * @return 生成器类型描述
     */
    public static String getGeneratorType() {
        if (cachedGenerator != null) return "EXPLICIT";
        return (SpringContextHolder.getBean(SnowIdGenerator.class).orElse(null) != null) ? "SPRING_BEAN" : "MISSING";
    }

    /**
     * <b>检查是否使用了Spring配置的生成器</b>
     *
     * @return true 如果使用Spring Bean
     */
    public static boolean isUsingSpringGenerator() {
        return cachedGenerator == null && (SpringContextHolder.getBean(SnowIdGenerator.class).orElse(null) != null);
    }

    /**
     * <b>重置生成器缓存</b>
     * <p>
     * 主要用于测试场景，强制重新初始化生成器。
     * <b>警告</b>：生产环境不应调用此方法。
     * </p>
     */
    public static synchronized void resetGenerator() {
        cachedGenerator = null;
    }

    /**
     * <b>手动设置生成器</b>
     * <p>
     * 允许手动指定生成器实例，覆盖默认行为。
     * 主要用于测试或特殊场景。
     * </p>
     *
     * @param generator 自定义生成器
     * @throws IllegalArgumentException 如果 generator 为 null
     */
    public static synchronized void setGenerator(SnowIdGenerator generator) {
        if (generator == null) {
            throw new IllegalArgumentException("Generator cannot be null");
        }
        cachedGenerator = generator;
    }
}
