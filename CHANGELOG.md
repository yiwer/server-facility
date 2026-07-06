# Changelog

面向消费方的破坏性变更与行为变更记录(含迁移指引)。格式取意 [Keep a Changelog](https://keepachangelog.com/);
当前尚无已发布版本,以下均为 0.1.0-SNAPSHOT 发布前的演进记录。内部决策全史见 [ADR 索引](docs/adr/INDEX.md)。

## [Unreleased] — 0.1.0-SNAPSHOT

### Breaking(API 变更,2026-07-06 一致性宪法批)

- **`ErrorTypeInterface.formatFallback(...)` 移出接口契约面(降为 private 实现细节)**。
  - 影响:调用或覆写过该 default 方法的代码编译失败。
  - 迁移:删除对它的调用/覆写即可——`format()` 内部自带 MessageFormat 失败兜底,无需外部参与。
- **`ErrorTypeInterface.getSeverity()` 与 `ErrorSeverity` 枚举删除**(全库零消费的投机扩展点)。
  - 影响:引用处编译失败。
  - 迁移:严重度语义不再由本库承载;如需分级,按 `getCode()`/`getModule()` 在消费方自行映射。
- **`NullSafe.allNotNull()` 对空参数组由返回 `false` 改为返回 `true`**(对齐「空集合上全称命题为真」惯例;
  `allNotNull(null)`(null 数组)仍返回 `false`)。
  - 影响:**编译不报错、行为静默反转**,依赖旧「空数组=false」的调用点请重点排查。
  - 迁移:若语义是「至少一个且全非 null」,显式加 `args.length > 0` 判断。

### Behavior changes(无 API 变更,语义修正)

- **LogUtil 级别门控改按调用方 logger 判定**(ADR-0022):`logging.level.<调用方包>` 的 per-package
  配置对 LogUtil 通道生效(旧实现按 LogUtil 自身/root 级别短路,业务包放开也无输出)。升级后同
  配置下日志量可能增多——这是修正而非回归。调用方解析同时切换 `StackWalker` 惰性遍历。
- **LogUtil 默认开启日志脱敏**(ADR-0020):写盘与 LogPostHandler 旁路均收到脱敏后消息(六类内置
  规则,身份证/银行卡带校验位抑误伤);`LogUtil.setMaskingEnabled(false)` 为总开关逃生舱;
  Throwable 的 message/stack trace 不脱敏(诚实局限)。
- **SnowId `throw-on-clock-backwards-exceed-threshold=false` 语义忠实化**(ADR-0023):任何幅度的
  时钟回拨都等待追上、绝不抛(旧实现回拨超约 1 秒仍抛);等待期间 ID 生成阻塞,风险见 properties javadoc。
- **锁/幂等的溢出防护改 fail-closed**(ADR-0016/0017 修订):锁数/记录数达 max 上限时**拒绝新建**
  (`tryLock` 返 false;幂等先清过期再拒)而非清空全表——在途持锁与未过期幂等记录永不因防护被打破。
  限流的 maxBuckets 保持 clear-all(fail-open):锁是正确性组件、限流是保护组件,不对称有意。
- **限流门面降级哨兵改 `-1`**:无 `RateLimiter` bean 时 `RateLimitResult.remaining()` 返 `-1` 表
  「未知/降级」(旧为 `Long.MAX_VALUE`);勿将该值直接透传到 `X-RateLimit-Remaining` 等响应头。
- **TraceIdFilter 校验入站 `X-Trace-Id`**:仅接受 `[0-9A-Za-z_-]{1,64}`,不合法按缺失处理(依
  `generate-if-absent` 重新生成或不注入)——防日志伪造/响应头注入。
- **构造器参数守卫兑现**(ADR-0013):`InMemoryDistributedLock`/`InMemoryIdempotencyStore`/
  `TokenBucketRateLimiter` 对非正参数(maxLocks/maxEntries/capacity/permitsPerSecond/maxBuckets ≤0)
  启动期抛 `IllegalArgumentException` 快速失败(旧实现静默接受并产生荒谬行为)。

### Removed(配置项)

- **`facility.web.access-log.log-headers` 删除**(从未被消费的死配置;Spring 宽松绑定下遗留配置行
  不会导致启动失败,建议清理);同期 `slow-threshold-millis` 真实生效(超阈值 WARN + slow 标记)。

### Added(0.1.0 主体能力,概要)

- 自 beacon-facility 迁移的基座簇:`Result<T,E>` 错误通道、error/i18n、雪花 ID、JSON 多命名空间、
  日志门面、date/number/copy/io/path/mime/pattern/hash、Web 栈(traceId/可重复读请求体/访问日志/
  全局异常/统一响应/安全上传下载/XSS)。
- §10 新组件八项:令牌桶限流(SPI)、缓存门面(Caffeine optional)、完整幂等(SPI)、分布式锁(SPI)、
  HTTP client(RestClient 委托)、crypto(AES-256-GCM/HMAC/PBKDF2,纯 JDK)、日志脱敏 masking、
  Excel/CSV(POI 双类探测降级 + RFC 4180 纯 JDK)。
- 质量门:1172 测试、5 条 ArchUnit 架构守护、JaCoCo gate 0.88/0.75、`dependency:analyze` failOnWarning。
