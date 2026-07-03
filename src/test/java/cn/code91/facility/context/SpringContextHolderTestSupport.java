package cn.code91.facility.context;

/**
 * 测试专用桥:向其他包的测试暴露包私有的 {@link SpringContextHolder#clear()}(RP-12 下
 * 生产 API 不提供全局重置,测试清理只能经由本类)。
 */
public final class SpringContextHolderTestSupport {
    private SpringContextHolderTestSupport() {}

    public static void reset() {
        SpringContextHolder.clear();
    }
}
