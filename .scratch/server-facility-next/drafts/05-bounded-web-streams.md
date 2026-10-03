# 05: 让普通下载与请求体处理保持流式和有界

**What to build:** 普通下载、SSE 和非目标请求不再经过全量响应捕获；需要重复读取的请求体及选定响应捕获都有明确预算和关闭责任。

**Blocked by:** None (can start immediately)

**Status:** draft — 待确认粒度与阻塞关系；未发布为 ready-for-agent。

**Traceability:** FR-03、FR-05；AC-05、AC-07、AC-10

## Acceptance criteria

- [ ] 捕获仅限显式目标，非目标无需完整生成即可输出；单次注册及 wrapper 顺序明确。
- [ ] Repeatable body 与响应捕获分别定义限额、未知长度、溢出和部分提交政策。
- [ ] 消费者取消/断开时停止无意义工作并清理自身资源；流所有权公开。
- [ ] 重复读取必须返回一致字节，明确 charset 与 reader/stream 组合规则；Servlet 非阻塞读取实现约定回调或确定拒绝，禁止 setReadListener 静默无动作。
- [ ] 完成本票适用的共同测试完成标准 Q01–Q10；每项契约关联测试及运行结果，不适用维度说明理由；涉及旧 ADR 时先登记替代决策。

## Required scenarios

- [ ] 正常/接合：实际客户端在服务端生成完成前读到下载/SSE 前缀，HEAD、空体和错误派发遵守协议。
- [ ] 边界：Content-Length 正确/缺失/失真、chunked、N−1/N/N+1、超大但受控输入与多字节边界。
- [ ] 故障：慢读慢写、客户端断开、写失败、取消、已提交后异常，资源清理且不二次写入。
- [ ] 资源：受限堆的独立进程里响应规模增加不会无界增长；不耗尽宿主模拟极端情况。
- [ ] 请求协议：两次读取正文相同；合法 application/problem+json、伪 application/json-unknown、声明字符集/畸形 charset、非阻塞回调的完成/错误和重复注册按约定验证。

## Scope boundary

旧幂等资格与保存政策由票 12 接入；本票不建立全站响应缓存。

本票引用 server-facility 下一代脚手架 PRD v0.2，以及同批任务的测试策略与接合矩阵。当前是可评审草稿，不表示实现、测试执行或用户批准已经完成。
