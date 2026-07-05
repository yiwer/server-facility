# server-facility

> Spring Boot server 端基础设施脚手架 —— 一个 **deep module**(窄接口、宽实现):
> 用少量易用的静态门面与自动装配 bean,封装 server 开发反复要写的 Result 错误建模、
> 雪花/UUID 生成、JSON、i18n 聚合、Web 过滤链与异常处理等能力。

- **坐标**:`cn.code91:server-facility:0.1.0-SNAPSHOT`
- **要求**:Java 21+、Spring Boot 3.5.x(依赖版本经 `spring-boot-dependencies` BOM 收敛)
- **测试**:1098 项(含 4 条 ArchUnit 架构守护);行覆盖率 ≥88%,JaCoCo check gate ≥0.88

## 引入

```xml
<dependency>
    <groupId>cn.code91</groupId>
    <artifactId>server-facility</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

放到 classpath 即通过 Spring Boot 自动装配生效(11 个 `@AutoConfiguration`)。核心 bean(`messageSource`、异步执行器、全局异常处理器等)`@ConditionalOnMissingBean` 兜底,你声明的同类 bean 优先;Web 过滤器/拦截器则由 `facility.web.*` 开关控制(见[装配开关](#装配开关),非靠竞争 bean 覆盖)。Web / XSS / MIME 探测等能力依赖 optional 依赖,按需自行引入(见 [USAGE](docs/USAGE.md#optional-依赖矩阵))。

## 5 分钟上手

```java
// 1) Result<T,E>:显式错误通道,取代 try/catch 与 null
Result<User, WrappedError> r = userService.findById(id);
String name = r.map(User::getName).orElse("unknown");

// 2) ID 生成:雪花 ID 与 UUID
Long id = IdUtil.snowId();
String uuid = IdUtil.uuidSimpleStr();

// 3) JSON:序列化返回 Result,不抛异常
Result<String, WrappedError> json = JsonUtil.serialize(user);

// 4) 日志:SLF4J 风格静态门面(Throwable 显式置于 msg 后、占位符参数前)
LogUtil.info("user {} logged in", userId);
LogUtil.error("load failed: {}", ex, resourceId);   // ex 在 msg 与参数之间,不被当占位符实参吞掉

// 5) 异步:惰性 pipeline,结果落到 Result
Result<String, Throwable> out = Async.supply(() -> httpGet(url))
        .map(Response::body)
        .recover(e -> "fallback")
        .await();
```

## 特性矩阵

| 簇 | 入口 | 能力 |
|---|---|---|
| `result` | `Result<T,E>`(sealed) | 显式错误通道:`ok`/`err`/`empty`/`map`/`flatMap`/`orElse`/`fromOptional` |
| `error` | `WrappedError` / `FacilityErrorType` / `ErrorTypeInterface` | 错误码 + i18n 消息键 + 可扩展错误类型;error 包纯 JDK(C1 断环) |
| `structure` | `Tuple` / `Triple` | 轻量二/三元值容器 |
| `common` | `NullSafe` / `Collects` | 空安全与集合便捷 |
| `context` | `SpringContextHolder` | 静态持有 ApplicationContext(AtomicReference + CAS 单次发布) |
| `id` | `IdUtil` | 雪花 ID(可配 worker/dataCenter)+ UUID 多形态 |
| `json` | `JsonUtil` | 多命名空间(DEFAULT/GENERIC/CANONICAL/PRETTY)Jackson;序列化返回 Result |
| `log` | `LogUtil` | SLF4J 门面;Throwable 末位对齐(ADR-0005),主源零 logback 依赖(ADR-0011) |
| `date` | `DateUtil` | 日期格式化/解析(返回 Result)、区间规范化 |
| `number` | `Numbers` / `NumberFormat` / `NumberUnits` | 数值解析、大小格式化、单位换算 |
| `hash` | `Hashing` | 文件/字节哈希 |
| `io` | `PathIo` / `Zipping` | 路径读写、压缩 |
| `path` | `Filenames` | 文件名清洗、路径穿越防御、危险扩展名拦截 |
| `mime` | `MimeTyping` | 基于魔数的 MIME 探测(optional:tika-core) |
| `pattern` | `Patterns` | 常用正则校验 |
| `copy` | `CopyUtil` | Bean 属性拷贝 |
| `locale` | `LocaleUtil` | i18n 消息翻译 + 聚合 MessageSource |
| `async` | `Async<T>` | 惰性异步计算,结果落 `Result`;虚拟线程默认执行器 |
| `web.*` | filter / interceptor / exception / session / response / argument / util / download / upload | Servlet 栈:traceId、可重复读请求体、访问日志、全局异常、统一响应、安全上传下载、XSS(optional:jsoup) |
| `ratelimit` | `RateLimiterUtil` / `RateLimiter`(SPI) | 令牌桶限流:纯 JDK 默认实现 + SPI 可替换(Redis);编程门面 + 无 bean 降级放行 |
| `web.ratelimit` | `@RateLimit` | 方法级声明式限流(拦截器);超限 429 + `Retry-After` |
| `cache` | `CacheUtil` | 缓存门面委托 Spring `CacheManager`;`@Cacheable` 自然可用;Caffeine optional 支持 TTL/maxSize |
| `lock` | `LockUtil` / `DistributedLock`(SPI) | 分布式锁:高阶 `executeWithLock` 自动获取释放 + `tryLock`/`unlock`;默认单机 ReentrantLock,SPI 可替换 Redisson |
| `http` | `HttpClients` | HTTP client 门面:委托 RestClient,`get`/`post`/`put`/`delete`→`Result`;超时可配 |
| `idempotency` | `IdempotencyStore`(SPI) | 幂等存储:PROCESSING/DONE 状态机 + TTL,默认内存,SPI 可替换 Redis |
| `web.idempotency` | `@Idempotent` | 完整幂等:同 key 返首次响应,拦截器 + Filter 捕获响应,PROCESSING→409 |
| `crypto` | `CryptoUtil` | AES-256-GCM 对称加解密 + HMAC + 密钥派生/管理 + Base64/Hex(静态门面,纯 JDK,无需配置) |
| `masking` | `MaskUtil` | 日志脱敏(默认开启):秘密/JWT/身份证/银行卡/邮箱/手机号六规则,校验位(mod11-2/Luhn)抑误伤;`LogUtil` 写前集成,`setMaskingEnabled(false)` 可关(静态门面,纯 JDK,无需配置) |

## 装配开关

所有开关前缀 `facility.*`,均可在 `application.yml` 调整;`enabled=false` 关闭对应组件。

| 前缀 | 作用 |
|---|---|
| `facility.id` | 雪花 ID:`worker-id` / `data-center-id` / `clock-backwards-threshold-millis` |
| `facility.web.trace` | TraceId 过滤器:`header-name` / `mdc-key` / `generate-if-absent` |
| `facility.web.repeatable-request` | 可重复读请求体:`max-body-bytes` / `include-content-types` / `exclude-paths` |
| `facility.web.access-log` | 访问日志拦截器:`log-headers` / `slow-threshold-millis` |
| `facility.web.cors` | CORS:`allowed-origins`(默认空 = 不开)/ `allowed-methods` / `allow-credentials` |
| `facility.web.exception` | 全局异常:`include-trace-profiles` / `use-problem-detail`(RFC 7807) |
| `facility.ratelimit` | 限流:`default-capacity` / `default-permits-per-second` / `max-buckets` |
| `facility.cache` | 缓存:`default-ttl` / `maximum-size`(仅 Caffeine 后端生效) |
| `facility.lock` | 分布式锁:`max-locks`(锁集合无界防护上限) |
| `facility.http` | HTTP client:`connect-timeout` / `read-timeout` |
| `facility.idempotency` | 幂等:`default-ttl` / `max-entries` |

## 文档

- **[USAGE](docs/USAGE.md)** —— 各门面用法、装配开关全表、消费方须知(i18n 抢注模型、JsonUtil 单例语义、optional 依赖矩阵)
- **[DESIGN](docs/DESIGN.md)** —— deep module 哲学、包簇依赖地图、三组断环 C1/C2/C3、装配范式
- **[ADR 索引](docs/adr/INDEX.md)** —— 20 条架构决策记录
- **[设计规格](docs/superpowers/specs/2026-07-02-server-facility-migration-design.md)** —— 迁移工程 spec
- **[域术语](CONTEXT.md)**
