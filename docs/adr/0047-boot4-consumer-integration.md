# ADR-0047: 以实际依赖图和 Servlet 契约验收目标平台

## Status

Accepted，2026-10-04。对应票 24、FR-01/02/09/10；执行状态见本票报告。

完成 ADR-0045/0046 的平台接合验证责任；补全 ADR-0028 的 Servlet 6.1 重载语义。ADR-0015 的两项可选依赖回退条件按原公开约定修正，不替代票 08 的后端 TTL/容量与失败政策。旧 Boot 3 构建证据保留为历史，不再作为当前平台支持结论。

## Context

根测试 classpath 同时有 Servlet、缓存实现、validation provider、Tika、POI，单靠 FilteredClassLoader 或根构建无法证明独立应用缺包时可启动。Boot 4 技术模块拆分和 Servlet 6.1 新重载使旧条件及包装器覆盖不再充分。票 22 允许的 Jackson 编译中间态已由票 23 消除，最终平台门必须保留全部质量约束。

## Decision

- `verification/platform-consumer` 的五个 Maven profile 解析实际生产依赖图；每个独立 JVM 只引用 fixture 自身 classes 和本次隔离仓库中的 jar。验收最小非 Web、缺 Jackson 技术模块、仅 Caffeine、仅 context-support、完整缓存对。类存在/缺席在进程内断言，依赖树、effective POM、运行 classpath 一同归档。安装 jar 必须逐字节等于本次根构建 jar。
- 最小图还验证用户 CacheManager、具名且 primary 的 MessageSource、JsonMapper、Jsons、普通 Executor，以及两个未限定 mapper 的确定歧义和显式 primary 选择。默认平台线程与宿主显式虚拟线程选择观察实际工作线程。缺 Jackson 模块且没有用户 mapper 时 JSON 自动装配让位，其余非 Web 能力仍可启动。
- 独立 Web 应用明确选择 `spring-boot-starter-webmvc`，核对 Jsons 与 MVC 的应用 mapper 身份及真实 HTTP 政策。普通、用户覆盖及能力关闭三种进程验证 ServletContext 注册次数、实际过滤链、413、旧幂等捕获与 ERROR 派发。06 的 request-context 边界调用 trace；trace 自身不再由 Boot 注册第二次。
- Servlet 6.1 的三个新 sendRedirect 重载与旧入口一致丢弃捕获；容器仍决定状态、Location、clearBuffer 和提交。旧重放记录不保存 Location，不能把任何 redirect 当作完整重放。Charset 重载同样在选择 writer 后冻结字符集；不改变未选择 writer 或容器已提交时的标准行为。
- 两项缓存可选依赖缺任一即进入原 ConcurrentMap 回退。当前回退不支持 TTL/容量，继续保留装配诊断；不能把这个平台装配验收宣称为新缓存保障已实现，票 08/33 负责收缩业务政策。
- 不引入 Jackson 2 兼容 starter、双主版本运行适配或第二默认静态 mapper。票 23 已删除 mutable mapper 回调、退出全局 Spring 发布；显式 `new Jsons(mapper)` 有独立真实消费者，保留。JsonUtil / ResponseUtil 等残留静态消费者交票 26，Java8 no-op builder 方法保留源迁移用途并明确其无运行期配置作用。本票不在已有消费者仍使用时继续删除入口。
- Windows/Linux 在同一最终来源上执行 `all --fresh` 和 `platform --fresh`。all 保持全库质量门及普通 jar 消费，platform 仍只是独立引擎/工具链正负控制；不得用后者替代前者。缺 OS 证据时票 24 保持 verification-pending，不能提前关闭 03/05 的目标平台验证项。

## Consequences

消费者矩阵的代价是若干小型 Maven 构建和 256 MiB、45/60 秒上限的独立 JVM。它提供可审阅的真实依赖边界；启动成功不代表尚未实施的 required Adapter、TTL、持久化幂等、认证或所有文件能力已满足。票 33 仍需在同一最终候选产物完成跨能力验收。

## References

- [Servlet 6.1 HTTP wrapper](https://jakarta.ee/specifications/servlet/6.1/apidocs/jakarta.servlet/jakarta/servlet/http/httpservletresponsewrapper)
- [Servlet 6.1 response wrapper](https://jakarta.ee/specifications/servlet/6.1/apidocs/jakarta.servlet/jakarta/servlet/servletresponsewrapper)
- [平台账本](../building/boot4-platform.md)、[消费者矩阵](../../verification/platform-consumer/README.md)、[票 24 报告](../verification/ticket-24-platform-integration.md)

---

本 ADR 遵循 [仓库模板](0000-adr-template.md)。
