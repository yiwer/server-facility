# Ticket33 contract, API and dependency ledger

**Status: qualified and closed against implementation `2d14f6a79a05e4e693a7d0e4abb83c4890653e45`.** Source review started from integration `568b16753e9c8756f2aeba1a803eaa0bf0faf221`. The [final release evidence](ticket-33-release-evidence.md#final-repaired-candidate-qualification) and [safe summary](ticket33/final-evidence-summary.json) bind actual all/platform, CI25 same-run four-role comparison, current-runtime historical upgrade and final independent review. Checked-in inventories remain historical `mode=preparation`/`candidateBinding=null`; final candidate inventories were re-extracted in `20261004-221057-625-all/release-inventory` and durably retained under `history/ticket-33-review-fixes/` with their exact source/runtime and digests.

## Provenance and compatibility scope

The [API inventory](ticket33/package-api-inventory.json) and [dependency inventory](ticket33/dependency-inventory.json) were freshly extracted, without Maven or a rebuild, from ticket31’s actual all-run inputs at `b2cbb0f368d7187c87fb90b0bd11741c9d71e0a7`. Ordinary runtime `cn.code91:server-facility:0.2.0-SNAPSHOT` is SHA256 `18e2c02dcf783eecbaadf12e8cce7ddc16bef6df64832feea55e935b3bc69ae5`; this is an explicitly historical preparation input. The script verified that run’s exact revision log, jar bytes and embedded POM against that source. The earlier comparison baseline remains `0ee9d547022371ad31f885605e999de17ec22777`, jar SHA256 `5ef94b3fc60274afd223070491b6d33dca39273df57d4923fd702eaf6ec6ca1f`, using the retained ticket31 baseline extraction. No old jar has been relabeled as ticket33.

Actual extraction: **29 packages, 129→162 reachable public/protected types, 1045→1305 declared members, 33 added types, no whole public type removed, 25 removed/replaced old descriptors across10 types, and14 same-descriptor declaration changes.** Nested accessibility follows actual classfile/enclosing visibility. The JSON contains every declaration, class parent/interface, source path and referenced-file digest. Stable runtime source-tree/POM object identities and extractor digest are recorded separately from the future candidate source/run identity.

| Compatibility layer | Deliberate disposition and evidence limit |
|---|---|
| Source | Boot4/Jackson3 is the `0.2.0-SNAPSHOT` line. Recompile, migrate Jackson imports and builder customization, and use application-owned beans. A retained JVM descriptor does not resolve generic types or overload ambiguity. |
| Declared binary ABI |25 old descriptors changed:8 public auto-configuration factories and17 Jackson members. Public `@Bean` factories count; direct callers/subclasses need migration. No unneeded bridge is introduced. Named legacy lock/copy/claim binaries establish their tested subset only. |
| Generics and parents | Four `Jsons`/`JsonUtil` parseTree/valueToTree methods keep erased Result descriptors but now contain tools.jackson JsonNode; old call-site casts can fail. TypeRef and serializer/deserializer parent types migrate. The other same-descriptor changes include synchronization/throws declarations; these are not all binary linkage removals. |
| Behavior and resource policy | Required-capability absence, cache names/TTL, ownership, finite quotas/retention, parsing and cleanup may tighten despite retained signatures. Per-package migration is explicit below; a method inventory is not behavioral certification. |
| HTTP/wire | Real status/ProblemDetail, challenges, trace/no-store and finite streaming/replay are separately exercised. Legacy JSON/CSV/ID wire fixtures remain named subsets, not universal old-client compatibility. |
| Persisted data | ID/cipher protocol literals and readers remain explicit. Notes V3 identity-byte preflight and V4 receipt cleanup belong to the application; receipt expiry never releases command identity. Arbitrary old databases are not upgraded by the historical five-file sample. |
| Template source | Exact lineage, owned-file preimages and preserved custom source/config/tests matter separately from runtime version. Current-candidate historical upgrade must rerun its declared overlay and actual packaged generations. |

See [runtime migration details and the25-member table](../building/runtime-migration-ledger.md), [Jackson migration](../building/jackson3-migration.md), [template upgrade recipe](../../verification/template-upgrade/README.md) and [command policy](../../templates/secured-api/COMMANDS.md). Extraction does not certify inherited/default resolution, annotation defaults/meta-annotations, inlined constants, serialVersionUID, arbitrary old downstream binaries or all thread/time semantics. Private registries/source-copy consumers remain unknown; no sunset date or maintenance promise is invented.

## All29 package decisions

The machine inventory maps all162 visible types to committed source, policy documents, tests and consumers. The following is the recommended surface; compatibility helpers remain inventoried rather than promoted as new defaults. Counts below belong to the explicit preparation artifact.

| Package | Old→prepared visible types | Recommended path and migration |
|---|---:|---|
| `async` | 8→8 | 显式应用 Executor + Async；采用真实工作的整体截止时间和协作取消。 传入宿主 Executor；将异步工作放到真实执行线程，不把 timeout 当作副作用撤销；宿主关闭自己拥有的 Executor。J05 双应用不同政策且 B 在途时关闭 A 的组合回归仍归33。 |
| `autoconfigure` | 11→11 | 通过 Spring Boot 自动配置及构造注入使用服务；宿主提供 bean 时依契约让位。 盘点直接调用/覆写工厂的消费者；迁到正式 bean/SPI 图。Jackson 工厂参数、Async 返回类型、Web advice 参数、移入 package-private ServletConfiguration 的工厂都要重编译/迁移；不为假设消费者新增桥。 |
| `cache` | 2→2 | 构造注入宿主标准 CacheManager/Cache，显式选择本地 Caffeine、固定名称、TTL 和容量。 声明依赖 Caffeine + spring-context-support；配置有限名称；注入 CacheManager 而非具体 CaffeineCacheManager。关闭前停止调用并等待 loader；借出的 Cache 不享有跨关闭可用保证。 |
| `common` | 2→2 | 保留有限集合组合和 null 数据策略；必需的回调不可空。 提供有效回调；调用者限制集合规模，饱和容量不等于可实际分配。输出复制仅保护容器结构。 |
| `context` | 1→1 | 新应用构造注入所需服务，避免进程级动态服务定位。 将静态查找改为应用持有对象；跨 parent/child、刷新失败和 sibling 关闭依归属规则，不能把最后启动应用视为全局政策权威。 |
| `copy` | 6→6 | 具名业务 DTO + 显式构造/映射；保留有明确策略的集合复制。 按 OrderDispatch 示例迁移真实流程；对 final/有状态比较器/循环和旧无界行为的依赖作显式决策，不以字段反射作为新业务协议。 |
| `crypto` | 1→1 | 新政策使用成熟 JCA/外部密钥管理；CryptoUtil 明确承担既有协议读取和有限兼容操作。 先保持历史 reader 和独立金样；新写入政策由应用版本化并独立管理密钥。有限测试不证明密码安全性，旧 PBKDF/编码参数不能无版本暗改。 |
| `csv` | 1→5 | 显式 CsvDialect + CsvLimits；机器交换与表格展示分别选择 writer。 按实际行/字段/字符/字节规模配置正预算；理解机器 writer 保真与 spreadsheet writer 公式防护差异；保留流借用和文件拥有者的清理规则。 |
| `date` | 1→1 | 固定 java.time formatter + 应用 Clock/ZoneId/Locale；ExportRequests 展示严格输入政策。 不要将 legacy SMART 解析当作日历合法性检查。对新业务选择严格模式、明确 DST gap/overlap、Clock 和时间窗；旧诊断异常不能直接公开。 |
| `error` | 3→3 | 领域错误独立建模；展示边界负责本地化和允许字段。 使用领域 enum/record 作为 Result 错误；仅在明确适配点转 WrappedError。不要直接返回 getFullMessage/toString/cause。 |
| `excel` | 1→6 | ExcelReadOptions/ExcelLimits 显式选择格式、公式、locale 与资源预算。 指定合理字节/行/列/单元格/膨胀预算和公式策略，理解借用流与自有临时文件清理；不能从缺一类安全推出任意残缺 POI 图可用。 |
| `hash` | 1→1 | 保留 JDK 摘要辅助；上传完整性绑定实际暂存字节。 新完整性选择 SHA-256；按 SafeUpload 使用同一暂存对象作类型/摘要/发布，不用文件名或客户端声明替代实际内容。 |
| `http` | 2→4 | 宿主 RestClient.Builder + 类型化外部 Adapter；边界拥有期限、大小与错误政策。 构造注入客户端，显式限定远端 URL/响应字节，映射可公开的失败；不要假定默认 builder 代表应用配置或 Async timeout 已取消下游副作用。 |
| `id` | 4→4 | 新业务直接 JDK UUID；既有数字协议显式注入 SnowIdGenerator。 显式分配节点0–3、epoch 和总预算；旧 false 无限等待改为有界等待。节点复用需旧owner停用且时间严格超过已发高水位；旧数字 JSON wire 类型不暗改。 |
| `idempotency` | 5→15 | qualified claim/token 协议明确表达资格；持久业务使用模板同库事务命令。 占位/重复判断/完成/释放整条路径切换，禁止混用；自定义 Adapter 必须实现原子 owner 校验。receipt 完成与副作用不原子、租约不能停止旧工作；29/30持久保证不应借此库协议宣称。 |
| `io` | 2→4 | 显式正 Limits + 完整成功才发布归档/目录结果；明确借用/拥有的资源。 为 entries/depth/bytes 配置预算，处理失败和临时清理；路径结果不等价恶意并发文件系统上的通用沙箱，Windows 与 Linux 权限/占用语义分别验证。 |
| `json` | 9→9 | 应用 Jsons/JsonsRegistry 和 Boot JsonMapper，所有政策在 builder 构建期选择。 重新编译消费者并迁 tools.jackson core/databind，annotations 仍 com.fasterxml；customize 改 customizeBuilder。旧 binary 即使解析到 Result 返回值也可能在旧 JsonNode 强转失败；不能只检查方法描述符。 |
| `locale` | 2→2 | 宿主 MessageSource + 显式 Locale；错误展示和领域值分离。 构造注入 MessageSource，明确缺key/fallback行为；Async 请求传播服从真实作用域恢复，不依靠线程复用残留。 |
| `lock` | 5→6 | 本地范围使用共享 LocalKeyedMutex；跨实例业务正确性由真实数据库/外部方案提供。 按真实保护范围换类型或实现外部 Adapter。Duration 是等待预算，不是持有租约；Future 取消不释放仍执行的锁，关闭不夺取已准入owner。 |
| `log` | 5→5 | 标准 SLF4J + 应用允许字段；Micrometer/宿主日志和消息政策。 新路径避免记录原始 payload/header/cause；上下文按应用生命周期清理。旧诊断入口留给受控场景，不把包装日志继续推广为统一平台。 |
| `masking` | 1→1 | 保留明确字段的字符串脱敏；日志/HTTP首先选择允许输出的字段。 在应用 schema 已知时逐字段调用；未知对象/正文不应先完整记录再期待万能脱敏。 |
| `mime` | 1→1 | 有界真实内容探测，SafeUpload 的必要类型政策缺detector时拒绝。 需要 Result 时选对应入口；保留 BufferedInputStream 包装继续读，接受mark被替换/reset失败语义。探测结果不是内容安全审查。 |
| `number` | 3→3 | 新预算用有界 ASCII 解析/BigDecimal 显式舍入；单位应用政策具名。 预算调用者继续检查正值；旧负半舍入与显式 HALF_UP 可能不同，需要领域金样。display 方法仍信任调用者控制 BigDecimal 尺度与规模。 |
| `path` | 1→1 | Filenames 保留字符串清洗；真实上传使用受控目录、暂存和原子发布流程。 按文档先完成所需解码，再调用sanitize；不要在其后再URL解码。设备名/链接/硬链接/占用等由真正文件边界处理。 |
| `pattern` | 2→2 | 开发者可信固定模式/有限白名单；业务语义验证交给具名解析器。 不向外部开放任意正则，不把缓存/线程Future取消当作正则CPU预算。legacy DATE仅形状检查，新日历校验用严格java.time。 |
| `ratelimit` | 5→6 | 明确本地令牌桶及可信主体/操作维度；业务命令计费与入口滥用额度分开。 选择 required 或明确 Optional 降级；不可把provider关闭等同注解绕过。PRINCIPAL 使用宿主身份，无有效身份不能回落IP；默认IP只信可信proxy配置。 |
| `result` | 5→5 | Result组合预期失败，Optional表达缺席；程序异常直接传播。 不把所有函数包成Result；理解toOptional/Stream丢失错误和成功null信息；所有权为浅引用，无暗中深复制。 |
| `structure` | 3→3 | Tuple/Triple用于局部组合；公开业务协议优先具名record。 对公开DTO显式命名；需要hash key/并发保证时选择不可变元素；nullable Entry和非空immutable Entry区别保留。 |
| `web` | 36→44 | 真实HTTP状态/安全ProblemDetail、可信请求来源、同步有限重放、真实流式/文件所有权；业务鉴权由宿主负责。 按下列 web 子表逐项迁移；BaseResponse兼容不等于强制HTTP200。required provider关闭不绕过保护；普通/流式响应不能被幂等捕获器全量缓存。 |

## Dependency, processor and plugin facts

All direct dependencies of the four deliverable projects are enumerated with declared/effective scope, optionality, actual version, version origin and rationale in the machine inventory. Full resolved graphs and effective models remain bound by exact path/SHA; direct declarations alone do not certify the transitive graph. No version was changed or dependency fetched for this ledger.

| Project role | GAV | Direct dependencies | Processors | Effective build plugins |
|---|---|---:|---:|---:|
| runtime | `cn.code91:server-facility:0.2.0-SNAPSHOT` | 33 | 2 | 12 |
| partner | `cn.code91.examples:partner-aggregation:1.0-SNAPSHOT` | 5 | 0 | 12 |
| secured-api | `com.example:secured-api:1.0.0-SNAPSHOT` | 12 | 0 | 13 |
| assembly-workflow | `com.example:secured-api:1.0.0-SNAPSHOT` | 14 | 0 | 13 |

The runtime has33 direct dependencies,2 explicitly admitted annotation processors and12 effective build plugins:10 source-declared plus Maven3.10.0 Super POM deploy/site defaults. Default plugin presence is not evidence of its execution; publishing/site generation is not performed. The four models each report inherited `project.build.outputTimestamp=1980-02-01T00:00:00Z`. The partner is an ordinary application jar; only the two Boot executables embed `BOOT-INF/lib` runtime bytes. Distinct roles can share a GAV without sharing bytes.

| Runtime direct dependency | Prepared version | Scope / optional | Version source and reason |
|---|---|---|---|
| `org.projectlombok:lombok` | 1.18.46 | compile / optional | imported Spring Boot dependency management; Compile-time generated boilerplate; optional runtime dependency, explicit annotation processor. |
| `jakarta.annotation:jakarta.annotation-api` | 3.0.0 | compile | imported Spring Boot dependency management; Public lifecycle/nullability annotation types. |
| `org.slf4j:slf4j-api` | 2.0.18 | compile | imported Spring Boot dependency management; Standard logging facade; host chooses its backend. |
| `org.springframework:spring-context` | 7.0.9 | compile | imported Spring Boot dependency management; Application context, events, standard cache/executor boundaries. |
| `org.springframework:spring-beans` | 7.0.9 | compile | imported Spring Boot dependency management; Bean contracts and explicit application configuration. |
| `org.springframework:spring-core` | 7.0.9 | compile | imported Spring Boot dependency management; Spring foundational resource/type and bounded utility integration. |
| `org.springframework.boot:spring-boot` | 4.1.1 | compile | imported Spring Boot dependency management; Boot properties and application integration contracts. |
| `org.springframework.boot:spring-boot-autoconfigure` | 4.1.1 | compile | imported Spring Boot dependency management; Conditional auto-configuration and host override semantics. |
| `org.springframework.boot:spring-boot-jackson` | 4.1.1 | compile | imported Spring Boot dependency management; Boot4 technical Jackson module; auto-configuration no longer belongs to core. |
| `jakarta.validation:jakarta.validation-api` | 3.1.1 | compile | imported Spring Boot dependency management; Declared validation annotations and HTTP validation translation. |
| `tools.jackson.core:jackson-core` | 3.1.5 | compile | imported Spring Boot dependency management; Jackson3 streaming/parser contracts; intentional public package migration. |
| `com.fasterxml.jackson.core:jackson-annotations` | 2.21 | compile | imported Spring Boot dependency management; Jackson annotations retain the com.fasterxml package in Jackson3. |
| `tools.jackson.core:jackson-databind` | 3.1.5 | compile | imported Spring Boot dependency management; Jackson3 application-owned mapping; JDK8/time/parameter support built in. |
| `org.apache.tika:tika-core` | 4.1.0 | compile / optional | project property ${tika.version}; Optional bounded MIME detection; explicit non-BOM pin, required capability fails absent. |
| `jakarta.servlet:jakarta.servlet-api` | 6.1.0 | compile / optional | imported Spring Boot dependency management; Optional real Servlet contract; container selected by consuming application. |
| `org.springframework:spring-web` | 7.0.9 | compile / optional | imported Spring Boot dependency management; Optional standard HTTP and MVC-adjacent contracts. |
| `org.springframework:spring-webmvc` | 7.0.9 | compile / optional | imported Spring Boot dependency management; Optional MVC interception/advice and bounded HTTP adapters. |
| `org.jsoup:jsoup` | 1.23.2 | compile / optional | project property ${jsoup.version}; Optional mature HTML parser/sanitizer; explicit non-BOM version. |
| `com.github.ben-manes.caffeine:caffeine` | 3.2.4 | compile / optional | imported Spring Boot dependency management; Optional finite local cache backend; pair with spring-context-support. |
| `org.springframework:spring-context-support` | 7.0.9 | compile / optional | imported Spring Boot dependency management; Optional CaffeineCacheManager integration; absent pair cannot silently fall back. |
| `org.apache.poi:poi` | 5.5.1 | compile / optional | project property ${poi.version}; Optional XLS/core format engine; exact version aligned with OOXML. |
| `org.apache.poi:poi-ooxml` | 5.5.1 | compile / optional | project property ${poi.version}; Optional XLSX/SXSSF engine and format graph; paired pin with POI core. |
| `org.apache.commons:commons-compress` | 1.28.0 | compile / optional | project property ${commons-compress.version}; Optional ZIP/Zip64 format preflight aligned with POI use, explicit pin. |
| `org.apache.commons:commons-csv` | 1.14.1 | compile | project property ${commons-csv.version}; Required mature CSV grammar engine for the always-available CSV facade; explicit pin. |
| `org.apache.tomcat.embed:tomcat-embed-core` | 11.0.24 | test | imported Spring Boot dependency management; Test-only real Servlet/ERROR/streaming integration; no runtime container choice. |
| `org.springframework.boot:spring-boot-starter-test` | 4.1.1 | test | imported Spring Boot dependency management; Test aggregation: JUnit/AssertJ/Mockito/Spring test dependencies. |
| `org.springframework.boot:spring-boot-web-server` | 4.1.1 | test | imported Spring Boot dependency management; Test-only Boot4 moved web server abstractions for actual container fixtures. |
| `org.springframework.boot:spring-boot-tomcat` | 4.1.1 | test | imported Spring Boot dependency management; Test-only Boot4 Tomcat integration for actual container fixtures. |
| `org.springframework.boot:spring-boot-webmvc` | 4.1.1 | test | imported Spring Boot dependency management; Test-only Boot4 MVC integration for actual container fixtures. |
| `org.junit.jupiter:junit-jupiter-params` | 6.0.3 | test | imported Spring Boot dependency management; Parameterized boundary/platform contracts discovered by JUnit. |
| `ch.qos.logback:logback-classic` | 1.5.38 | test | imported Spring Boot dependency management; Test-only actual logging/MDC behavior; application remains backend owner. |
| `org.hibernate.validator:hibernate-validator` | 9.1.3.Final | test | imported Spring Boot dependency management; Test-only real validation and missing-provider combinations. |
| `com.tngtech.archunit:archunit-junit6` | 1.5.1 | test | project property ${archunit.version}; JUnit6 architecture engine; explicit platform-compatible version and discovery guard. |

| Processor | Prepared version | Version source / purpose |
|---|---|---|
| `org.projectlombok:lombok` | 1.18.46 | effective dependency management; processor path has no version; Explicit JDK25 processor admission; Lombok generation |
| `org.springframework.boot:spring-boot-configuration-processor` | 4.1.1 | explicit processor path version; Explicit JDK25 processor admission; Boot configuration metadata |

| Effective runtime build plugin | Prepared version | Origin and actual role |
|---|---|---|
| `org.apache.maven.plugins:maven-enforcer-plugin` | 3.6.3 | explicit source POM/property; JDK25/Maven3.10.0 and required coverage input gates. |
| `org.apache.maven.plugins:maven-clean-plugin` | 3.4.1 | explicit source POM/property; Reproducible removal of project-owned build output. |
| `org.apache.maven.plugins:maven-resources-plugin` | 3.3.1 | explicit source POM/property; Deterministic resource copying and declared encoding. |
| `org.apache.maven.plugins:maven-install-plugin` | 3.1.4 | explicit source POM/property; Ordinary jar install into the verification-owned isolated repository; no publish. |
| `org.apache.maven.plugins:maven-help-plugin` | 3.5.1 | explicit source POM/property; Actual effective-model evidence, including inherited versions. |
| `org.apache.maven.plugins:maven-compiler-plugin` | 3.16.0 | explicit source POM/property; Release25, parameter names and explicit annotation processor paths. |
| `org.apache.maven.plugins:maven-surefire-plugin` | 3.6.0 | explicit source POM/property; Actual JUnit/ArchUnit discovery and positive/negative test reports. |
| `org.jacoco:jacoco-maven-plugin` | 0.8.15 | explicit source POM/property; Executed coverage evidence and unchanged instruction/line88%, branch75% gates. |
| `org.apache.maven.plugins:maven-jar-plugin` | 3.5.1 | explicit source POM/property; Ordinary artifact packaging with inherited effective output timestamp. |
| `org.apache.maven.plugins:maven-dependency-plugin` | 3.11.0 | explicit source POM/property; Dependency graph/classpath evidence and fail-on-warning declared usage guard. |
| `org.apache.maven.plugins:maven-deploy-plugin` | 3.2.0 | Maven3.10.0 Super POM default; Maven3.10.0 Super POM default; inventoried but deploy is not run or authorized. |
| `org.apache.maven.plugins:maven-site-plugin` | 3.22.0 | Maven3.10.0 Super POM default; Maven3.10.0 Super POM default; inventoried, not an all/platform execution. |

Boot application projects additionally use the Boot4.1.1 repackage plugin. Application-only dependencies include JWT security, JDBC/Flyway/PostgreSQL, standard tracing/context propagation and application-owned outbound HTTP; each individual version and reason is in its project section of the JSON. Two explicit compile-time processor paths are not two extra runtime dependencies. CI Actions are a separate tool inventory, not Maven plugin counts.

Root’s official-source recheck on2026-10-04 verified the following stable release tags, dereferenced commits and action.yml/README inputs. That recheck supplies pin provenance; the pinned workflow subsequently ran successfully at source2d14 in CI25, whose exact run/attempt is recorded in final release evidence.

| CI action | Official stable release | Immutable commit |
|---|---|---|
| `actions/checkout` | [v7.0.1](https://github.com/actions/checkout/releases/tag/v7.0.1) | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| `actions/setup-java` | [v6.0.1](https://github.com/actions/setup-java/releases/tag/v6.0.1) | `de7274f081f381c8f8158605e0321c36c376e2e6` |
| `actions/upload-artifact` | [v7.0.1](https://github.com/actions/upload-artifact/releases/tag/v7.0.1) | `043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` |
| `actions/download-artifact` | [v8.0.1](https://github.com/actions/download-artifact/releases/tag/v8.0.1) | `3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c` |


## Requirement and acceptance trace

Every row below maps to the same qualified2d14 candidate: local all `20261004-221057-625-all`, independent platform `20261004-224431-670-platform`, and CI25 run37203623059/attempt1 on Windows/Linux with four-role comparison. Actual all discovery is1790/15/132/153, historical78/80 and all three packaged stages; all positive failures/errors/skips are0. Full results, artifact/coverage/environment values, inventory digests and durable evidence are in the linked final report/summary. Test names remain navigation, not substituted execution counts. FR11/AC16 and the immutable benchmark retain their stated scope.

| FR | Acceptance coverage | Entry and ownership |
|---|---|---|
| FR-01 platform/dependencies | AC-01,02,13 | Wrapper3.10.0/JDK25; Verify library, consumerBuild, platformProbe, archiveQuality; this model/API extraction and four-role identity. |
| FR-02 assembly/dependency absence | AC-02,09 | Verify platformConsumers and ordinary jar missing/paired dependency graphs; required lock/quota/upload/cache/Excel consumers. |
| FR-03 HTTP/identity/error | AC-04,05 | Real Servlet error, request-boundary and streaming suites; template JWT/trust/lifecycle and command HTTP tests. |
| FR-04 business command identity | AC-06 | Notes Module plus NoteCommand*/NoteReceipt* real PostgreSQL suites, signed HTTP, exact commit fault hosts and two PIDs. |
| FR-05 runtime lifecycle/guarantees | AC-07,08 | Async/context/local mutex/cache/quota/ID contracts; J05 executor join; J17 action ownership; finite same-JVM campaigns. |
| FR-06 file/table contracts | AC-10 | Upload/ZIP/CSV/Excel real boundaries, independent format oracles and resource processes; workflow import join. |
| FR-07 independent application | AC-03 | Instantiate + TemplateLineage + clean standalone secured-api/workflow builds, executable-jar HTTP/restart/database consumer. |
| FR-08 smaller interfaces/locality | AC-11,15 |29-package map; five architecture rules; named DTO mapping, Notes/bench application modules, PartnerContractTest standard builder. |
| FR-09 compatibility/upgrade | AC-12 | Split0.1/0.2 runtime line, independent template marker, legacy literal/binary fixtures, current-candidate HistoricalUpgrade.py. |
| FR-10 quality/agent experience | AC-13,14 | Original coverage/architecture/dependency/discovery gates and preserved immutable ticket31 five-task benchmark. |
| FR-11 conditional extraction | AC-16 | Conditional future expansion requiring two real consumers; outside the approved first-release gate, not a missing completion item. |

| AC | Concrete final evidence required |
|---|---|
| AC-01 | JDK25/release25 class major69/minor0, exact wrapper/toolchain, actual all/platform success and mismatch prerequisites. |
| AC-02 | Ordinary runtime jar installed/consumed through isolated artifact classpaths; four role-specific identities and correct optional dependency graphs. |
| AC-03 | Copied applications build, authenticate, migrate, perform persistence and run executable jars independently; no source reactor/classpath escape. |
| AC-04 | AuthenticationHttpTest, TrustPolicyHttpTest, HttpErrorContractTest, safe validation/conflict/infrastructure status and required headers. |
| AC-05 | StreamingHttpContractTest/RepeatableHttpContractTest/authorized replay consumer plus original command status/Location and actual response-prefix RST recovery. |
| AC-06 | Qualified claim ownership plus NoteCommandAtomicity/Concurrency/Recovery and NoteReceiptMaintenance/Concurrency/Recovery; current permission and permanent identity. |
| AC-07 | SpringContextOwnershipTest, AsyncBoundaryTest/AsyncContextTest, RequestBoundaryHttpTest, template FailureLifecycleHttpTest and J05 actual executor isolation. |
| AC-08 | LockConsumer/CacheConsumer/RateLimitConsumer/IdConsumer with real capacity/ownership/clock/resource boundaries. |
| AC-09 | Real minimal/absent engine and host-override matrices; bounded filter registration and no protected effect when required implementation is absent. |
| AC-10 | Upload input/type/path/byte/temporary/publication tests; ZIP independent reader; CPython CSV and xlwt/XlsxWriter/openpyxl format evidence; workflow import cleanup. |
| AC-11 | Named app-owned Module changes and migration map; architecture tests; no new generic recovery framework, service locator default or unbounded global response cache. |
| AC-12 | Exact changed ABI/generic/parent ledger; retained named wire/data samples; guarded historical source/config/custom-test preservation using current runtime. |
| AC-13 | Positive reports discovered with0 failures/errors/skips, instruction/line≥88%, branch≥75%, five architecture tests, unchanged exact dependency ignores; expected negatives separate. |
| AC-14 | Immutable ticket31 before/after five-task inputs, outputs, correction/context costs and verified products. Later hardening and diagnosis remain outside common benchmark cost. |
| AC-15 | PartnerContractTest/PartnerConsumer and workflow outbound tests: real customizer/JSON/trace/header/deadline separation, one-send UNKNOWN mutation, explicit safe GET retry under remaining total budget. |

## High-risk joins J01–J17

Root test paths below are under `src/test/java/cn/code91/facility`; template paths are under `templates/secured-api/src/test/java/com/example/api`. Verify method names refer to `verification/Verify.java`. These are layered real joins, not a demand for one giant test containing every component.

| Join | Existing entry / actual observable contract | Final candidate disposition |
|---|---|---|
| J01 | consumerBuild + `verification/consumer` CodeSource, installedClasspath byte equality, major69, imports/metadata; platformProbe positive/negative engines. | PASS: local all/platform and CI25 bothOS/four-role comparison. |
| J02 | platformConsumers actual dependency matrices; cache/lock/rate/upload/Excel consumers reject missing required capability and admit explicit overrides without extra filter chains. | PASS: all real graph runs; no mock-only substitute. |
| J03 | web/exception/HttpErrorContractTest, DiagnosticPrivacyContractTest; template AuthenticationHttpTest and FailureLifecycleHttpTest; real Filter/MVC/ERROR/serialization/multipart paths. | PASS: local all and CI25 bothOS; committed response cannot be replaced by a second error body. |
| J04 | web/filter/RequestBoundaryHttpTest and MdcFailureHttpContractTest; actual worker reuse, timeout/RST cleanup; template ExecutorOwnershipTest, FailureLifecycleHttpTest, StandardObservationHttpTest. | PASS: local all and CI25 bothOS. Preserve evidence layering: template-only tracing does not establish every underlying reused-worker socket assertion. |
| J05 | JsonConsumer simultaneous mapper policies; HostMessageSourceContractTest; CacheConsumer; StandardObservationHttpTest sampler isolation; new FacilityAsyncAutoConfigurationTest executor method. | New focused executor execution12/0/0/0 exists, first runnable GREEN; final all/CI25 also PASS at2d14. |
| J06 | HttpReplayConsumer authorization before replay; NoteCommandsHttpTest scope isolation; NoteCommandConcurrencyTest revocation during actual claim wait; NoteReceiptAuthorizationTest. | PASS: local all and CI25 bothOS; local HTTP receipt and DB atomic command are distinct guarantees. |
| J07 | ReplayQuotaHttpTest / RateLimitHttpContractTest; template PrincipalQuotaHttpTest and NoteQuotaHttpTest actual JWT+optional ingress provider+successful command charge. | PASS: local all and CI25 bothOS; attempts and successful business identities have separate budgets. |
| J08 | StreamingHttpContractTest/StreamingResourceProcess, RepeatableHttpContractTest, ReplayBudget/Receipt/Disconnect tests; actual prefix/SSE before completion, finite capture/declared unsupported async wrapping. | PASS: local all and CI25 bothOS; ordinary64/256MiB stream stays outside capture, and committed transport loss is not rollback proof. |
| J09 | NoteCommandAtomicityTest/ConcurrencyTest/ProtocolTest/MigrationTest, real PG unique constraints and receipt/effect/command_count rollback together. | PASS: local all and CI25 bothOS; command fingerprints use an independent literal golden and finite keyed identity. |
| J10 | NoteCommandRecoveryTest separate JVM hosts, exact real JDBC before/after commit signals; NoteReceiptRecoveryTest/ConcurrencyTest/AuthorizationTest maintenance/replay joins. | PASS: local all and CI25 bothOS; actual process PIDs, session cleanup, default/effective lock budgets, receipt clearing and permanent identity recorded. |
| J11 | HostBuilderHttpContractTest, examples/partner-aggregation PartnerContractTest and PartnerConsumer; workflow WorkflowOutboundHttpTest. | PASS: local all and CI25 bothOS; host JSON/customizer/trace, per-remote policy, total deadline and no implicit mutation retries. |
| J12 | assembly-workflow ImportController via SafeUpload→strict CsvUtil→business checks→finally cleanup; BenchmarkImportHttpTest, WorkflowImportInterruptionTest, WorkflowTypeAwareUploadTest. | PASS: current workflow153-test and packaged gates; original benchmark acceptance is a separate historical run. |
| J13 | IoConsumer with independent JDK ZipFile; CsvIndependentGoldenTest; ExcelIndependentFormatTest and partial/absent engine consumers; temporary ownership/resource tests. | PASS: local all and CI25 bothOS; format readability alone does not prove cleanup. |
| J14 | Current named legacy JSON/CSV/ID/cipher and ABI consumers; TemplateLineage + HistoricalUpgrade.py declared historical overlay/preimage/custom behavior/packaged restart. | PASS: current-runtime historical78/80 and three packaged stages. Pinned old-runtime experiment is retained, not relabeled. |
| J15 | Dedicated same-JVM finite lock/cache/claim/quota/Async/context/HTTP/DB/file failure campaigns in next table, plus process sessions and retained owner checks. | PASS: all at frozen2d14 on bothOS. Five fresh startup JVMs are supplementary, not proof of retained-owner steadiness. |
| J16 | Windows NOSHARE_DELETE/junction/device-name and Linux permission/symlink branches, Unicode/chunked/encoding input; three valueConsumer locale/timezone processes. | PASS: genuine CI25 Windows+Linux gates; missing prerequisite cannot be reported as skipped success. |
| J17 | async/AsyncMutexContractTest platform/virtual × timeout/cancel(true)/cancel(false): actual action remains owner after observer ends; second same-key effect denied until true action exit. | PASS: all six combinations in same-source all; future cancellation is not lock release. |

The new J05 method is `FacilityAsyncAutoConfigurationTest#closingOneApplicationDoesNotAffectAnotherApplicationExecutor(ExecutorPolicy, boolean)`. Four cases pair BOOT_VIRTUAL with FALLBACK or BOOT_PLATFORM and reverse first-close order. Both contexts coexist; public Async calls interleave; the survivor's actual barrier task has entered before the other closes, stays uninterrupted/incomplete until released, then completes and accepts a new call. Both executors reject after close and actual owned workers terminate. It replaces one sequential case with four (net+3); remaining eight class tests are unchanged. Focused source/evidence is `.verification-results/j05/01-focused-first-execution.{json,log}` and the class XML in `first-execution-reports`. This focused run is not substituted for final all/CI.

## Q01–Q10 completion mapping

| Q | How the final release checks it | Current ledger status |
|---|---|---|
| Q01 | FR/AC/J tables, package/type/source map and original failure records; superseding ADR0056 owned by primary. | PASS: mappings, actual29-package API/dependency inventory, and final source-bound reports. |
| Q02 | Existing normal/null/N−1/N/N+1/invalid/overflow/Unicode/encoding/time policies in named suites; exact defaults stay explicit. | PASS: re-run through final all; no newly invented exhaustive domain claim. |
| Q03 | Ordinary jars, actual Servlet/Security/JDBC/PG/two JVMs, real absence graphs and controlled external HTTP; independent API/wire samples. | PASS: actual ordinary/Boot artifacts, effective models and runtime bindings retained. |
| Q04 | Deterministic barriers/events and actual IO/refusal/commit/process faults; failures/cancel/interruption assert state and cleanup. | PASS: final execution preserves each original deadline and root cause. |
| Q05 | Registered heap/bytes/keys/rows/concurrency/pool/queue/temp/cancel budgets, retained-owner campaigns and termination observations. | PASS: declared finite campaigns executed in final all; underlying logs/metrics retained, without extrapolating duration or scale. |
| Q06 | Historical binary/subset APIs, ID/cipher literals, CPython CSV and independent Excel/ZIP readers; signed JWT producer distinct from verifier. | PASS: current candidate executions preserve source/oracle provenance. |
| Q07 | Fixed seeds and finite iterations below; input minimization/regression if a failure occurs. | PASS: registered seeds/finite campaigns; no new fuzz framework or statistical long-stability assertion. |
| Q08 | Exact source, role-specific GAV/SHA/size, OS/JDK/PG/locale/timezone/run/attempt, positive suite ownership and sanitized diagnostics. | PASS: final source/role/environment/run/attempt binding; inaccessible raw CI archives are not claimed read. |
| Q09 | Original88/88/75 coverage, architecture/discovery/dependency gates; actual selected suite counts; expected-negative controls separately recognized. | PASS: actual positive reports and separate expected-negative controls; no stale XML totals. |
| Q10 | Code/docs/migration/consumer ledger and final candidate outcomes together; retained limitations and failure records. | PASS: all required gates, identity comparison, current-runtime history and both independent final reviews complete; ticket33 closed. |

## Bounded campaigns and seeds

These are registered source entrypoints/scales, checked against the current runner and relevant test sources. The primary final report supplies actual execution metadata and maxima. No promise of indefinite stability or every possible schedule follows from finite campaigns.

| Entry | Declared scale, budget or oracle |
|---|---|
| CoreConsumer | seed180041,512 properties; ordinary jar with framework absent;64MiB/45s. |
| ValuePolicyConsumer + ExportInputConsumer | seed200043,512 properties,10000 rotations;64MiB/45s plus JDK-only32MiB app in UTC/en-US, Shanghai/zh-CN and New_York/fr-FR; strict/DST/clock policies. |
| LegacyCopyConsumer + OrderDispatch | seed190042,512 cases,2000 bounded copies/rejections and200 callback Errors;64MiB/45s; named DTO business consumer128MiB/45s. |
| CryptoConsumer | Independent historical cipher/KDF samples,1MiB max bytes,64 rounds/four workers; not a security audit of key management. |
| IoConsumer | seed140037,64 independently read ZIP cases;32/128MiB input,64MiB heap,200 failures,60s. |
| LockConsumer |250000-key churn,10000 invalid inputs,16 platform/virtual workers;64MiB/two CPUs/45s. |
| RateLimitConsumer |1024 slots,512-character keys,32768 churn/invalid-cost operations,16 workers;64MiB/two CPUs/45s; depleted identity cannot reset via churn. |
| IdConsumer | seed100025,2048 properties,eight workers,10000 successful/rejected/interrupted cycles;64MiB/two CPUs/45s; fixed/rollback clock is bounded. |
| HtmlConsumer |16 fixed samples,seed320025,512 URI variants,10000 nesting and10000 successful/rejected operations acrossfive cycles;64MiB/two CPUs/45s. |
| ClaimConsumer | seed110034,2048 rounds,256 slots,32768 churn,16 workers,128 still-reachable closed stores;64MiB/two CPUs/45s. |
| CacheConsumer | seed80031,2048 operations,8192 unknown names,4096 churn,eight workers;256 reachable closed managers previously holding32768 entries and1MiB payload each;64MiB/two CPUs/120s. |
| HttpReplayConsumer |32 bindings,512 churn,two app lifecycles;128MiB/two CPUs/120s; tenant/actor/route and revoke/restore checks. |
| PartnerConsumer |five real same-JVM application lifecycles,200 bounded-tail failures,205 wire observations;128MiB/90s; clients terminate. |
| Async/context tests | Async seed20261003/512 order cases/128 barrier races; context seed20261003/120 operations,at mostfour live contexts. J17 six ownership combinations. |
| StreamingResourceProcess |64/256MiB ordinary downloads under96MiB heap/32MiB direct memory; retained growth≤16MiB; prefix arrives before producer completes. |
| Upload/CSV/Excel | Upload body preservation seed0x13b0d1/48 cases; independent CPython CSV seed0x15c5/164 records; CSV200 failures/growth≤8MiB; Excel seed160039 and400000-row/200-bad-input resource process. |
| Notes command/receipt | seed29005264/64 Unicode/delimiter commands;10000 lifetime identities/workspace;24h receipt; key1–128 ASCII and actor field≤65536UTF8 bytes; cleanup batch1–1000 with explicit finite operator statement/lock budgets. |
| Process recovery | Before/after commit eachthree kill/restart cycles; actual16-byte response prefix before RST; owned sessions returnzero; child128MiB/two CPUs,HTTP10s,graceful exit15s+force5s,per-child diagnostic128files/16MiB; effective default/alternate DB budgets checked separately. |
| Workflow import |4096-byte staged upload/CSV budget,33 rows including header,two fields,80UTF16 units; business≤32 items/name≤40 code points/quantity1–1000; success/refusal/cancel/error cleanup. Exact endpoint admission and outbound policies stay in its source/tests. |
| Generic resource mode |five fresh application JVM lifecycle runs,256MiB/45s each. Supplementary to the retained-object same-JVM campaigns above. |

## Historical boundaries and final binding

Ticket31's immutable five-task corpus and common costs remain historical evidence. Its manifest binds1318 files/306889855 bytes outside worktrees. Post-benchmark hardening, diagnostic loops and changed current-source tests are excluded from those common costs. The earlier historical upgrade is the declared four-file fixture overlay on both sides,78 before/80 after inherited+custom tests, five-file guarded patch and three actual packaged generations; it proves that bounded sample only. Ticket33 repeated that scenario using its actual2d14 ordinary runtime; the original pinned run is retained separately.

Ticket31 local18's10s observation timeout and local19's Hikari migration borrow failure remain distinct unexplained failures. Later all20/platform21 and CI22 passes are documented evidence, not claimed repairs of those failures. The Unicode source-launcher path repair is a separately observed/fixed failure. See [ticket31 report](ticket-31-template-upgrade.md) and [CI22](ticket-31-ci22.md); do not aggregate stale copied XMLs or move diagnostic work into benchmark costs.

Final binding is deliberately outside these tracked preparation snapshots. After a clean source freeze and actual all gate, run the read-only extractor against that exact all directory and its copied ordinary runtime artifact:

```text
python -X utf8 verification/release-evidence/Inventory.py
  --repository . --source <exact-frozen-40-character-SHA>
  --jar <all-evidence>/artifacts/server-facility-0.2.0-SNAPSHOT.jar
  --jar-sha256 <measured-runtime-SHA256>
  --all-evidence <all-evidence>
  --output <all-evidence>/release-inventory
  --javap <JDK25>/bin/javap[.exe] --mode candidate
```

Arguments are shown on separate lines for reading; supply one command or shell-appropriate continuations. The script runs no Maven/network/install and mutates no input. It refuses wrong artifact digest, source/revision mismatch, dirty or different candidate HEAD, embedded-POM/source mismatch, unresolved/missing models/graphs and an unexplained dependency/processor/plugin frontier change. Candidate output records source, runtime digest, extractor hash and stable production/POM identity; it is still extraction evidence, not a PASS announcement. The final report records actual all/platform outcomes, four-role manifests, same run/attempt/OS checks and current-runtime historical identity. Do not edit this document after every run merely to make its own SHA equal its final HEAD; final read-only run metadata establishes the validated branch tip.
