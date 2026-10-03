# 独立目标平台消费者

从仓库根目录运行 `java verification/Verify.java all --fresh`（需要 JDK 25，并设置真实 JDK 21 的 `VERIFY_WRONG_JAVA_HOME`）。`integration` 也执行此矩阵；两者都先完整构建并安装普通库 jar，不借用根 test classpath。逐片开发日志不替代最终同源全门。

| Maven graph/profile | JVM 场景 | 实际依赖边界 |
|---|---|---|
| minimal | default / disabled / override / jsons-override / ambiguous / primary / virtual | 没有 Servlet/MVC、validation provider、Tika/POI、Caffeine/context-support，也没有 JUnit/context runner |
| no-jackson-module | default | 通过 Maven exclusion 真正移除 spring-boot-jackson；Jackson core/databind 仍是库公开 API 依赖；没有隐式 mapper/Jsons bean，其余能力可用 |
| caffeine-only | default | 仅 Caffeine，没有 context-support，进入现有 ConcurrentMap 回退 |
| context-support-only | default | 仅 context-support，没有 Caffeine，同一回退 |
| cache-pair | default | 两者齐全，实际 CacheManager 使用 Caffeine |

override 使用合法具名/primary MessageSource，保留库消息贡献 bean；按类型获取 MessageSource 时不能假设只有一个贡献源。两个无 primary 的 JsonMapper 必须通过带候选名称的 NoUniqueBeanDefinitionException 拒绝启动；显式 primary 则确定选择。缓存回退的无 TTL/容量保障是已登记旧政策，平台矩阵不能把其改称新业务契约通过。

相邻 `json-consumer` 使用生产 Web starter，保留旧平台字面 JSON 金样和两个同时存活应用的 HTTP 比较；`PlatformWebConsumer` 另验证默认、用户过滤器/mapper 覆盖、能力关闭。它检查 ServletContext 实际注册和 HTTP 可观察结果，不只查看 BeanDefinition。用户 trace 场景显式启用入站信任；默认场景必须生成本应用 trace。

`PlatformUploadConsumer` 再用该生产 Web 图及显式 Tika profile 两次启动独立 JVM：真实 Spring MultipartFile 接口提供虚假大小/类型元数据，核对普通 jar 的实际字节预算、文本 MIME、服务端存储名、缺必需 detector 的明确拒绝以及 stage 清理。没有 Spring mock multipart；没有 Tika 时无类型保存前后均仍可用。每次最多保存 5 字节，成功成品由 fixture 删除。本片是票 13 的发布产物接合，不替代其大输入、并发或实际 HTTP 质量门。

每个 JVM 上限 256 MiB，非 Web 45 秒、Web 60 秒；HTTP 5 秒截止，Tomcat 最多 4 个工作线程，context/client 正常关闭。超时由 runner 终止本次子进程树并失败。报告保存输入、模型、依赖树、classpath、退出码、标记及普通 jar SHA；runner 逐字节核对安装产物与本次根构建产物。

没有用此矩阵宣称所有可选文件 API 或后续锁/缓存/限流失败政策通过；这些分别属于 07/08/09/13/16/32，并由 33 在最终产物接合。
