> **inherited-from**: beacon ADR-0008(原仓库 docs/adr/0008-rp-15-snowid-parsetimestamp-instance.md)。在 server-facility 中继续生效;包名按 cn.code91.facility.* 对照阅读。

# ADR-0008: RP-15 SnowIdGenerator.parseTimestamp 实例方法（修自定义 epoch 解析偏差）

## Status

Accepted (phase-4, 2026-05-21)

## Context

`SnowIdGenerator.parseTimestamp(long id)` 在 phase-4 前是 static 方法，**硬编码 epoch** `1_735_660_800_000L`（`beacon-support/beacon-facility/src/main/java/cn/hbads/beacon/facility/id/support/SnowIdGenerator.java:170-172`）。但 `SnowIdGenerator` 实例可通过配置 `stele.facility.id.startTimestamp` 自定义 epoch（构造器 `SnowIdGenerator(dataCenterId, workerId, startTimestamp)` 接收实例 `startTimestamp`）。

自定义场景下：
- 生成 ID 用实例 `startTimestamp`
- 解析时间戳用 static 硬编码 epoch
- **偏差 = 实例 startTimestamp - 1_735_660_800_000L**（若实例配 `1_700_000_000_000L`，偏差 ≈ 413 天）

影响范围：所有通过 `IdUtil.parseTimestamp` / `IdUtil.parseInfo` 解析 ID 的场景。`parseInfo` 内部也调 `parseTimestamp`，故同样受影响。

问题来源：
- phase-1 REVIEW.md §4.1（`docs/facility/REVIEW.md:651-652`）
- phase-1 worksheet §C/id/ F-id-3
- phase-1 REVIEW.md §6.B.3 `id/`（`docs/facility/REVIEW.md:1204, 1207`）
- REVIEW.md §7 RP-15（`docs/facility/REVIEW.md:1690-1693`）

REVIEW.md §C-§4.1-2 建议方向：把 `parseTimestamp` 改为实例方法。

## Decision

- `SnowIdGenerator.parseTimestamp(long id)` static → instance，使用 `this.startTimestamp`
- `SnowIdGenerator.parseInfo(long id)` 同样 static → instance（依赖 `parseTimestamp`，且其字符串含时间戳）
- `SnowIdGenerator.parseWorkerId` / `parseDataCenterId` / `parseSequence` **不动**（纯位运算，与 epoch 无关）
- `IdUtil.parseTimestamp` / `IdUtil.parseInfo` 静态门面调整为通过 `getIdGenerator()` 实例调用；门面对外签名保持 static（调用方无感）
- 新增 3-arg ctor `SnowIdGenerator(long dataCenterId, long workerId, long startTimestamp)` 供测试与非 Spring 场景直接构造自定义 epoch 实例

替代方案考虑：
- **B: static overload 接收 startTimestamp 参数**（`parseTimestamp(long id, long startTimestamp)`）— 不破坏现 API，但调用方仍需传 startTimestamp（绕回实例字段，绕一圈）。否决：不解决"调用方默认不知道实际 epoch"的根因。
- **C: 静态方法保留 + 加 deprecated + instance overload**— 引入新 deprecation lifecycle，与 phase-4 "facility 收口"性质冲突。否决。

测试覆盖（5 @Test 分 2 文件）：
- `SnowIdGeneratorTest$InstanceParseMethodTests`：
  - 默认 epoch regression：`parseTimestamp(generator.nextId()) ∈ [beforeGen, afterGen]`
  - 自定义 epoch bug-fix verify：同上断言但用 `new SnowIdGenerator(0, 0, 1_700_000_000_000L)`
  - 自定义 epoch `parseInfo` 反映正确时间戳
- `IdUtilTest$CustomEpochFacadeTests`：
  - 门面 `IdUtil.parseTimestamp` 通过 `setGenerator` 注入自定义 epoch generator 后正确解析
  - 门面 `IdUtil.parseInfo` 同上 + 字符串含正确 timestamp

## Consequences

- 真 bug 修复：自定义 epoch 场景下解析与生成 epoch 对齐，偏差 = 0。
- **API break**：`SnowIdGenerator.parseTimestamp` / `parseInfo` 从 static → instance；外部直接调静态方法的调用方会编译失败。
  - facility 内部仅 `IdUtil.parseTimestamp` / `IdUtil.parseInfo` 一处调用（已同步改为实例调用）
  - 外部破坏面 ≈ 0（beacon 当前无 external consumer；stele-agent 是 MCP 客户端，不直接 import facility 类）
- **性能注解**：`IdUtil.parseTimestamp` 原 static-to-static 无锁；改后第一次调用进入 `getIdGenerator()` 同步块（Spring 查找 + DCL）；后续走 volatile 快速路径。性能影响仅首次调用 < 1ms（Spring 查找 cost），稳态后零开销。
- 不引入新业务术语。
- `parseInfo` 现 instance，内部 `parseTimestamp(id)` 隐式 `this.parseTimestamp(id)`，`parseWorkerId` / `parseDataCenterId` / `parseSequence` 仍 static 调用（编译器允许实例方法体内调用本类 static 方法，无源码歧义）。

## References

1. *Snowflake — Twitter's Distributed ID Generation* (Twitter Engineering Blog, 2010). <https://blog.x.com/engineering/en_us/a/2010/announcing-snowflake>
2. *Hutool — IdUtil.parseTimestamp* (Hutool Docs, accessed 2026-05-21). 对标实现基于实例 epoch，验证本 ADR 决策方向。
3. beacon `docs/facility/REVIEW.md` §4.1 + §6.B.3 + §7 RP-15 + worksheet §C/id/ F-id-3 (phase-1)
4. *Effective Java (3rd ed.) §17 Minimize Mutability* (Bloch, 2018) — 实例字段 `startTimestamp` final + ctor 注入。

---

*本 ADR 遵循 Michael Nygard 模板。模板见 `docs/adr/0000-adr-template.md`。*
