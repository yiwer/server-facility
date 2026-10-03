# ADR-0018: HTTP client 选型 RestClient + Result 化门面 + 超时装配(seam)

- **状态**:Accepted(2026-07-04)；2026-10-04 私有builder默认推荐、静态业务入口与宽泛错误政策由[ADR0048](0048-application-owned-outbound-http.md)部分替代，保留同步RestClient选型及弃用兼容签名。
- **源起**:幂等+分布式锁+HTTP client 实现计划(docs/superpowers/plans/2026-07-04-idempotency-lock-http.md),
  簇 D(Task D1 `HttpClients` 门面 + `FacilityErrorType.HTTP_STATUS_ERROR`、Task D2 `FacilityHttpProperties` +
  `FacilityHttpAutoConfiguration`)

## 背景

server-facility 此前无 HTTP client 组件。计划 §簇D 为服务端组件库新增"调用外部 HTTP 接口"的门面能力,
与锁(ADR-0016)、限流(ADR-0014)、缓存(ADR-0015)三簇面对相似的取舍结构:Spring 生态同步/异步 HTTP
client 有三个候选——`RestTemplate`、`RestClient`、`WebClient`——外加"门面该返回值还是抛异常""超时怎么
配、装配层怎么给消费方留 seam"两个设计问题。本 ADR 同时补记 D1(`HttpClients` 门面、错误码映射,已提交)
与 D2(properties、自动装配、超时、seam)两个任务的决策依据,与 ADR-0016 覆盖 C1+C2 整簇的写法一致。

## 决策

### 1. 客户端选型:`RestClient`(非 `RestTemplate`,非 `WebClient`)

- **`RestTemplate`**:类本身未标注 `@Deprecated`(探针:javap 反编译本项目实际解析的
  `spring-web-6.2.15.jar` 中 `RestTemplate.class`,未见 `Deprecated` 字节码属性),但 Spring 官方 Javadoc
  明确其处于"维护模式"——仅修复缺陷/安全问题,不再新增特性,并在类注释中直接建议新代码优先选用
  `RestClient`(同步)或 `WebClient`(响应式)。选择一个官方口径"不再演进"的 API 作为新组件库的默认门面,
  不符合面向未来维护的定位,否决。
- **`WebClient`**:响应式 API,需要 `spring-webflux`(进而 `reactor-core`)依赖——探针:本项目 `pom.xml`
  当前仅有 `spring-web`/`spring-webmvc`(均 optional),无 `spring-webflux`/`reactor-core`。`HttpClients`
  门面的契约是同步阻塞语义(`get`/`post`/`put`/`delete` 直接返回 `Result<T, WrappedError>`,调用方期望
  立即拿到结果或错误,不涉及 `Mono`/`Flux` 订阅),若选 `WebClient` 则每次调用都要 `.block()` 才能落回
  同步语义——多引入一整套响应式技术栈只为立即阻塞等待,收益为负,否决。
- **`RestClient`**(选定):Spring Framework 6.1 引入,`spring-web` 内置(本项目已有的 optional 依赖,
  `HttpClients`/D1 已直接使用),提供同步阻塞的现代 fluent API
  (`get()/post()/put()/delete().uri(...).retrieve().body(Class)`),语义与门面 `Result<T, WrappedError>`
  的同步返回契约天然匹配,且是 Spring 官方明确推荐的同步 client 前进方向。

### 2. 门面返回 `Result`,而非抛异常(D1 已落地,本 ADR 补记决策依据)

`HttpClients` 每个方法返回 `Result<T, WrappedError>`,统一在内部 `execute` helper 做异常映射(而非让
调用方逐个 `try/catch` `RestClientException`):

- `RestClientResponseException`(4xx/5xx 响应状态)→ `FacilityErrorType.HTTP_STATUS_ERROR`,
  `WrappedError` 的第一个参数为 HTTP 状态码(`e.getStatusCode().value()`),调用方可按状态码分支处理;
- 其他异常(连接超时、DNS 失败、连接被拒绝、响应体反序列化异常等网络/传输层故障)→
  `FacilityErrorType.HTTP_SEND_AND_PARSE_ERROR`,第一个参数为请求 URL,便于排障定位。

与 `error`/`result` 簇既有范式一致(`Result<T, WrappedError>` sealed 类型、`FacilityErrorType` 分段错误码)
——门面不假设调用方会记得处理 checked/unchecked 异常,而是把"网络请求可能失败"显式编码进返回类型,
调用方用 `result.ifOk(...)`/`isErr()` 处理,遗漏错误分支在代码审查甚至编译期更容易被发现。

### 3. 超时经 `FacilityHttpProperties` + `SimpleClientHttpRequestFactory`

`FacilityHttpProperties`(`facility.http` 前缀,ADR-0013 不用 `@Validated`):`connectTimeout`
(默认 5 秒)、`readTimeout`(默认 10 秒),均为 `java.time.Duration`。`FacilityHttpAutoConfiguration`
的 `facilityRestClient` 用两者构造 `SimpleClientHttpRequestFactory`:

```java
SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(props.getConnectTimeout());
factory.setReadTimeout(props.getReadTimeout());
return RestClient.builder().requestFactory(factory).build();
```

选 `SimpleClientHttpRequestFactory` 而非 Boot 的 factory-builder 抽象,是探针实证后的固化结论:

- Boot 3.5.10 里同时存在**两套**同名/近名 API——旧包 `org.springframework.boot.web.client`
  下的 `ClientHttpRequestFactorySettings`/`ClientHttpRequestFactories`(探针:javap 反编译确认该
  record 类整体标注 `@Deprecated`)已被新包 `org.springframework.boot.http.client` 下的
  `ClientHttpRequestFactoryBuilder` + 同名新 `ClientHttpRequestFactorySettings` 取代,连带一族按
  classpath 自动探测的 `SimpleClientHttpRequestFactoryBuilder`/`HttpComponentsClientHttpRequestFactoryBuilder`/
  `JdkClientHttpRequestFactoryBuilder`/`JettyClientHttpRequestFactoryBuilder`/
  `ReactorClientHttpRequestFactoryBuilder`(`ClientHttpRequestFactoryBuilder.detect()`);
- 这套新抽象本身仍在演进中(新旧两代同名类在同一 Boot 版本内共存即是信号),库若跟随会背上"每个 Boot
  版本重新确认该用哪个包"的心智负担,与"新组件长期稳定"的目标冲突;
- `spring-web` 的 `SimpleClientHttpRequestFactory` 是最基础、无额外抽象层的实现,直接基于 JDK
  `HttpURLConnection`,`setConnectTimeout(Duration)`/`setReadTimeout(Duration)` 两个重载已探针确认存在
  (javap 反编译 `spring-web-6.2.15.jar`——`mvn dependency:tree` 确认为本项目实际解析版本),行为可预期,
  不需要跟踪 Boot 内部 builder 抽象的版本迁移。

### 4. `RestClient` bean 是 seam:`@ConditionalOnMissingBean(RestClient.class)`

`FacilityHttpAutoConfiguration` 仅注册一个 `RestClient` bean(`@ConditionalOnMissingBean(RestClient.class)`),
`@ConditionalOnClass(name = "org.springframework.web.client.RestClient")` 用字符串形式声明——与
`FacilityCacheAutoConfiguration` 既定写法一致,`spring-web` 是 optional 依赖,ASM 字节码读取的条件求值
不会触发缺失类的加载。消费方有两级替换粒度:

- **仅替换底层 `ClientHttpRequestFactory`**:声明自己的 `RestClient` bean 时,自行选用 Apache
  HttpComponents5(`HttpComponentsClientHttpRequestFactory`,连接池/长连接复用等生产级能力)或其他
  `ClientHttpRequestFactory` 实现,`RestClient.builder().requestFactory(...).build()`——OkHttp 用户需
  注意:探针确认 `spring-web` 6.2.15 的 `OkHttp3ClientHttpRequestFactory` 类本身已整体标注
  `@Deprecated`,新代码若要接入 OkHttp,应直接基于 OkHttp 自身 API 实现 `ClientHttpRequestFactory`
  适配,而非依赖 Spring 提供的该适配类;
- **整体替换 `RestClient` bean**:消费方声明任意来源的 `RestClient` bean(如经 Boot 新版
  `ClientHttpRequestFactoryBuilder.detect()` 自行装配),`facilityRestClient` 让位,`HttpClients`
  门面调用点(`restClient()` 委托 `SpringContextHolder.getBean(RestClient.class)`)不需要任何改动
  ——这正是既定 seam 范式(与 ADR-0016 的 `DistributedLock`、ADR-0015 的 `CacheManager` 一致)。

无 `RestClient` bean 时(消费方未引入本自动装配、或显式排除),`HttpClients.restClient()` 降级为
`RestClient.create()` 默认实例(D1 已实现),不应用本 ADR 的超时配置——与"基础设施缺席不阻断业务"既定
降级哲学一致,但消费方需知悉:该降级路径下请求没有超时保护(`RestClient.create()` 底层默认工厂无超时
设置),生产环境应确保 `FacilityHttpAutoConfiguration` 生效,或自行提供带超时的 `RestClient` bean。

## 备选(否决)

- **默认选 `RestTemplate`**:官方"维护模式"口径明确不再新增能力,新组件库不应绑定一个只做缺陷修复的旧
  API,否决(决策 1);
- **默认选 `WebClient`**:为同步阻塞门面引入整套响应式技术栈(`spring-webflux` + `reactor-core`),而
  `HttpClients` 契约本就是同步返回 `Result`,收益为负,否决(决策 1);
- **门面抛异常而非返回 `Result`**:与 `error`/`result` 簇既定范式(`Result<T, WrappedError>` 显式编码
  失败路径)不一致,且会让调用方遗漏 `try/catch` 时故障静默传播为未处理异常,否决(决策 2,D1 已落地);
- **超时用 Boot `ClientHttpRequestFactoryBuilder`/新 `ClientHttpRequestFactorySettings` 抽象构建**:探针
  证实该抽象本身仍处于新旧两代并存的演进期(旧包 `boot.web.client` 整体 `@Deprecated`,新包
  `boot.http.client` 接替),额外引入一层需要跟随 Boot 版本迁移追踪的抽象,相比直接使用 `spring-web`
  基础类 `SimpleClientHttpRequestFactory` 没有必要的收益,否决(决策 3);
- **超时写死常量,不做成 properties**:与 ADR-0013 既定"properties 暴露可调参数,构造器/降级路径兜底"
  范式不一致,不同消费方的网络环境(内网调用 vs 公网调用)对超时诉求差异很大,否决。

## 后果

- 消费方零额外依赖即可用(`RestClient` + `SimpleClientHttpRequestFactory` 均在已有 optional 的
  `spring-web` 内);需要连接池、长连接复用等生产级能力时,替换 `ClientHttpRequestFactory` 或整个
  `RestClient` bean 均不影响 `HttpClients` 调用点。
- 降级路径(无 `RestClient` bean → `RestClient.create()`)不带超时保护,是本 ADR 重点披露的风险点——
  与 ADR-0016 决策 5"多实例部署必须确保 bean 在场"的风险披露方式一致,后续文档收尾阶段(USAGE)需要
  再次强调。
- `packages_are_cycle_free` ArchUnit 规则对 `http`/`autoconfigure` 两包保持绿,未引入新的包间边。
- **Carry-forward**:README/USAGE/DESIGN 文档三件套的 HTTP 特性矩阵、装配开关表(`facility.http.*`)、
  ADR 索引更新留给计划收尾阶段统一处理(与 ADR-0015/0016 的 Carry-forward 处理方式一致),本任务(D2)
  未涉及。
