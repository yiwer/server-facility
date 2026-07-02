package cn.code91.facility.id;

import cn.code91.facility.context.SpringContextHolder;
import cn.code91.facility.id.support.SnowIdGenerator;

import java.util.UUID;

/**
 * <b>ID生成工具类 - 重构版本</b>
 * <p>
 * 提供多种 ID 生成策略，包括雪花算法ID和UUID。
 * 雪花算法ID支持分布式环境，可通过Spring配置注入自定义的{@link SnowIdGenerator}。
 * </p>
 *
 * <h3>重构改进：</h3>
 * <ul>
 *     <li><b>修复DCL问题</b>：正确的双重检查锁定实现</li>
 *     <li><b>性能优化</b>：避免重复的Spring Bean查找</li>
 *     <li><b>逻辑清晰</b>：明确的初始化流程</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 生成雪花ID
 * Long id = IdUtil.snowId();
 *
 * // 生成UUID
 * UUID uuid = IdUtil.uuid();
 * String uuidStr = IdUtil.uuidStr();
 *
 * // 解析雪花ID
 * long timestamp = IdUtil.parseTimestamp(id);
 * String info = IdUtil.parseInfo(id);
 * }</pre>
 *
 * @author yvvb
 * @since 2.0.0
 * @apiNote 重构版本，修复了双重检查锁定问题
 */
public final class IdUtil {

    private IdUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 默认的雪花ID生成器（dataCenterId=0, workerId=0）
     */
    private static final SnowIdGenerator DEFAULT_GENERATOR = new SnowIdGenerator(0, 0);

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IdUtil.class);

    /** 已对 DEFAULT 回退发过一次 WARN 的标志（避免日志刷屏；resetGenerator 清零）。 */
    private static volatile boolean warnedDefaultFallback = false;

    /**
     * 缓存的ID生成器引用
     * <p>重构说明：初始值为 null，第一次调用时初始化</p>
     */
    private static volatile SnowIdGenerator cachedGenerator = null;

    /**
     * Spring查找是否已完成的标志
     * <p>重构说明：避免重复查找Spring Bean</p>
     */
    private static volatile boolean springLookupDone = false;

    /**
     * <b>获取ID生成器</b>
     * <p>
     * 重构说明：修复了双重检查锁定的逻辑问题。
     * 初始化流程：
     * 1. 首次调用时尝试从 Spring 容器获取
     * 2. 如果获取成功，缓存 Spring Bean
     * 3. 如果获取失败，缓存默认生成器
     * 4. 后续调用直接返回缓存的生成器
     * </p>
     *
     * @return ID生成器实例
     */
    private static SnowIdGenerator getIdGenerator() {
        // 快速路径：已初始化
        SnowIdGenerator cached = cachedGenerator;
        if (cached != null) {
            return cached;
        }

        // 慢速路径：初始化
        synchronized (IdUtil.class) {
            cached = cachedGenerator;
            if (cached != null) {
                return cached;
            }

            // 只在尚未确定时尝试从 Spring 获取
            if (!springLookupDone) {
                SnowIdGenerator springBean = SpringContextHolder
                        .getBean(SnowIdGenerator.class)
                        .orElseGet(() -> null);

                if (springBean != null) {
                    cachedGenerator = springBean;
                    springLookupDone = true;
                    return cachedGenerator;
                }

                // Spring 未就绪：暂用 DEFAULT，但【不缓存、不置 springLookupDone】，下次调用继续重试 lookup。
                if (!warnedDefaultFallback) {
                    log.warn("IdUtil: SnowIdGenerator Spring bean 未就绪，暂用 DEFAULT(dataCenterId=0/workerId=0)"
                            + " 并将持续重试 Spring lookup。分布式环境下若长期落 DEFAULT，多节点 workerId 全为 0"
                            + " 会导致雪花 ID 冲突。");
                    warnedDefaultFallback = true;
                }
                return DEFAULT_GENERATOR;
            }

            // 理论上不会到达这里，但为了安全起见
            return DEFAULT_GENERATOR;
        }
    }

    // ==================== ID 生成方法 ====================

    /**
     * <b>生成雪花算法ID</b>
     * <p>
     * 生成的ID具有以下特性：
     * </p>
     * <ul>
     *     <li>全局唯一</li>
     *     <li>趋势递增</li>
     *     <li>支持分布式环境</li>
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
        SnowIdGenerator generator = getIdGenerator();
        if (generator == DEFAULT_GENERATOR) {
            return "DEFAULT (dataCenterId=0, workerId=0)";
        }
        return "SPRING_BEAN";
    }

    /**
     * <b>检查是否使用了Spring配置的生成器</b>
     *
     * @return true 如果使用Spring Bean
     */
    public static boolean isUsingSpringGenerator() {
        return getIdGenerator() != DEFAULT_GENERATOR;
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
        springLookupDone = false;
        warnedDefaultFallback = false;
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
        springLookupDone = true;
    }
}
