# 23: 迁移 Jackson 3 并让 HTTP 与应用 mapper 政策一致

**What to build:** 目标平台应用通过不可变 builder 和应用作用域 mapper 处理 JSON，HTTP 输出与注入入口一致，两个应用的政策互不污染。

**Blocked by:** 22 迁移 Boot 4、Spring 技术模块与测试工具链

**Status:** in-progress

**Traceability:** FR-01、FR-08、FR-09；AC-02、AC-11、AC-12

**Integration rule:** 宽迁移批次；与票 22 共用集成线，由票 24 收敛验证。

## Acceptance criteria

- [ ] 迁移 Jackson 公共类型、异常和自定义序列化器，删除合并进 databind 的冗余模块，保留 annotations 的实际坐标。
- [ ] 运行时不修改共享 mapper，新默认不经进程级 registry；用户 builder/customizer 为唯一明确配置来源。
- [ ] 预期解析/I/O 失败进入约定错误通道，程序错误不被宽泛 catch 吞掉；敏感 payload 不作为错误详情。
- [ ] InputStream 序列化的显式入口、上限和所有权明确；排序模式不冒称规范化签名算法。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [ ] 金样/兼容：票 21 所有样本在新平台验证，标明允许差异与消费者迁移方式。
- [ ] 接合：真实 MVC 往返与注入 mapper 一致；两上下文交错调用、关闭其中一个、重建后不串配置。
- [ ] 边界/故障：坏/截断 JSON、泛型不符、尾随输入、流失败、预算超限和用户定制错误。
- [ ] 完成：清除票 22 登记的 Jackson 编译缺口，失败清单归零或转为票 24 的明确装配待验项。

## Scope boundary

不混装两套主版本试图保持所有二进制签名，不引入新 JSON DSL。

## 票 21 交接（2026-10-03，ADR-0044）

票 21 的新应用 Jsons 注入、customizeBuilder、独立 JSON/HTTP 金样和 InputStream 显式预算是 expand 接缝，旧无参/≤0 无上限仅为当前平台保留兼容。23 的新推荐字段入口必须正数预算或显式不注册能力，不能继续默认无界。解码数组在分配前必须受预算限制，JSON 字符串形成前的文档/字段输入预算也需由应用明确配置；21 当前 Base64 长度闸门将临时数组限制为 N+2 固定舍入开销，实际返回严格≤N。Jsons payload 日志/错误参数泄露及 catch 边界仍由 23 关闭。详见 docs/building/platform-migration-inventory.md 与 verification/json-consumer/README.md；所有样本是字面来源，不以写后读替代。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。用户已于 2026-10-03 确认任务拆分及依赖，本票已发布为本地任务；实际开始前须满足 Blocked by，实现与测试验收仍待完成。
