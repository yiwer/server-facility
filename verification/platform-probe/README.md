# 独立平台工具链探针

从仓库根执行 `java verification/Verify.java platform --fresh`。项目没有 facility 依赖、父 POM、reactor 或库源码，因而可以在 22 的 Jackson 编译迁移中间态运行。Wrapper、Maven 隔离仓库和输入/输出证据由同一个 JDK runner 管理。

正向必须发现 2 项 Jupiter 和 2 项 ArchUnit，共 4 项且无失败/错误/跳过。fixture 使用 Lombok builder 和 Boot configuration processor；测试读取实际生成的配置 metadata 和 classfile 69.0，ArchUnit 导入这个 Java 25 record。JaCoCo 报告必须包含该类已覆盖的指令；dependency analyze-only 也真实读取编译输出并保持 failOnWarning。探针自身的覆盖比例不是库覆盖率。

runner 分别用 `probe.fail.jupiter=true` 和 `probe.fail.archunit=true` 运行两次负向控制。每次必须发现同样 4 项、恰好指定引擎的一项故意失败，并留下独立 Surefire XML。若某个引擎没有被发现，缺失用例不能被另一个引擎的绿色掩盖。

最后解析真实根工程的 effective POM、依赖树和全部依赖。这些目标不编译根工程。`platform` 的 PASS **仅表示工具链子集通过**，主库完整测试、五条架构规则、主库配置 metadata、普通 jar、实际 Servlet/MVC/JSON 行为、缺类组合和两类独立消费者仍由 `all` 与票 23/24 验证。CI 保留 `all` 的失败，同时另执行本模式；本模式不把中间制品变成可发布版本。

期望版本来自官方 Boot 4.1.1 BOM 与 ArchUnit 1.5.1 发行物，见 [平台账本](../../docs/building/boot4-platform.md)。升级根平台时须同步独立探针；它有意不继承根 POM，防止用继承掩盖消费者缺失配置。
