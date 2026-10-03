# 票09：本地配额与真实HTTP接合验证

**2026-10-04 平台闭合**：`c2f0f6b`的Windows/Ubuntu完整门与独立平台门均成功，本票closed。见[同源CI证据](ticket-09-14-15-ci.md)。下文保留各次本地执行原值与历史状态。

状态：verification-pending（仅本票Linux CI待集成后补齐）。Windows同一候选的产品/消费者/资源检查全部通过，三项先决条件负控独立补跑PASS；首轮all原始FAIL保留，详见末节。12/29/33的专属组合重验单独登记，不反向阻塞09本票闭合。公共契约与迁移见[ADR-0032](../adr/0032-local-rate-limit-contract.md)。

## 范围与实现

起点06中央 `5faff896d04a1b15ed10310be81bed91a14121b7`，分支codex/ticket-09。核心检查点39bd54d、必需/Optional门面27a4953、HTTP缺设施/故障f275524；完整主体/ASYNC/入口次序实现000d5b0。候选 `50d492d9160476052560910db5d1c1664e92d4ad` 已合入最新integration `8cfaa6e`（含24正式双OS闭合）。本次src/pom/verification提交后启动完整runner，工作树当时干净。

容量、速率、成本和key在变更状态前校验，精确整数扣费与实际缺额等待，驻留key拒绝冲突政策。严格maxBuckets及最多16候选full-only回收替代整体clear；required与Optional入口区别；完整操作签名、可信IP/宿主Principal/Global范围；Servlet错误交04处理。固定MVC order先于默认幂等重放，正常ASYNC完成单次扣费。无分布式后端、JWT解析或新错误DSL。

## TDD轨迹

日志位于本工作树 `.verification-results/ticket-09`，不会被Maven clean删除。以下RED均来自公开操作、装配或真实Tomcat客户端，未以私有字段验证实现。

| 契约/最小反例 | RED日志 | GREEN或后续边界日志 |
|---|---|---|
| cost为0/-1/Integer.MIN_VALUE不能恢复额度 | red-01-cost | green-01-cost |
| 非法容量/rate/key、超容量cost不能占槽位 | red-02-input-policy | green-02-input-policy |
| 同驻留key改政策不能伪造RetryAfter | red-03-conflict | green-03-conflict |
| Long.MAX_VALUE扣1必须剩9223372036854775806 | red-04-long-precision | green-04-long-precision |
| 显式单调时间/分数余额实际等待167ms | red-05a-clock-seam（新入口编译红）/red-05b-fractional-retry（原334ms） | green-05-fractional-retry |
| key churn不能清空两名耗尽actor | red-06-key-churn | green-06-key-churn |
| 满槽位只回收已补满actor，其他债务保留 | red-07-safe-reclaim | green-07-safe-reclaim |
| 必需门面缺Adapter拒绝；显式Optional才放行 | red-09-required-adapter/red-10-optional-entry/red-11-failure-policy | green-09/10/11对应日志 |
| 真实HTTP重载方法独立（原第二个429） | red-12-http-operation | green-12-http-operation |
| 真实HTTP缺Adapter503（原200）、故障503（原500） | red-13-http-missing/red-14-http-outage | green-13/14对应日志 |
| 仅显式fail-open放行设施不可用 | red-15-explicit-fail-open | green-15-explicit-fail-open |
| Principal scope公共注解（新入口编译红） | red-16-principal-scope | green-16-principal-scope |
| 同请求Callable完成不重复扣费（原首请求429） | red-17-async | green-17b-async |
| 非空全blank操作别名不伪装成合法key | red-18-alias | green-18-alias |
| 同key重放也消耗入口额度（原第三次200） | red-19-replay-order | green-19-replay-order |

保留失败：green-17-async仍然429，检查发现第一次补丁只比较而未写入请求收据，green-17b补齐后通过。当前green-16日志是Wrapper成功运行；该轮最初误用已过时Maven3.9.16被Enforcer拒绝，没有跳过门或以该失败当通过。最早baseline普通jar探针来自协调目录rate-baseline-probe.log，独立复现负cost/key churn恢复额度；新断言使用明确预期值，不复制旧缺陷。

## Q01–Q10与适用边界

| 标准 | 验证入口及观察 | 证据/限制 |
|---|---|---|
| Q01 契约追踪 | RateLimitContractTest/RateLimitHttpContractTest/ADR0032对齐FR03/05、AC04/08/09 | 上表各修复有独立RED→GREEN；旧ADR原理由保留 |
| Q02 正常与边界 | capacity/cost/rate/key极值；511/512/513 UTF16、Unicode原样、NaN/Inf/Double.MIN；分数等待、倒退、signed wrap | RateLimitContractTest、RateLimitBoundaryTest；时区不进入算法，连续观察间隔<2^63ns |
| Q03 真实接合 | 真实Tomcat11 + JDK HttpClient；非MockMvc：重载/跨包同名、伪XFF/显式可信链、Principal分离及128/129边界、Global别名、缺/故障Adapter、安全429/503/legacy200、Callable、幂等重放 | RateLimitHttpContractTest共13项；04 fixture复用；普通jar资源consumer属于全门 |
| Q04 故障与并发 | 16线程CyclicBarrier/有界Future等待，同key正好17次允许；128新身份正好5槽；Adapter真实bean故障、null、IllegalArgumentException/Error保首因 | RateLimitBoundaryTest/RateLimiterUtilTest；无随机sleep，无远程后端/网络重试协议；拒绝无退款是明确政策 |
| Q05 有界资源 | 最长key512、Principal128、严格maxBuckets、最多16候选、单请求1收据；已满集合反复失败仍保持原余额 | 64槽轮转与时间源观察上界；独立普通jar进程1024槽、32768次churn+非法成本、16线程，-Xmx64m/2核/45s；非吞吐基准 |
| Q06 独立兼容样本 | 明确保留原固定key跨IP共享和04显式legacy envelope；Long.MAX_VALUE与部分余额用手算字面值；原实现baseline反例 | 无新增持久格式；SPI key编码变更有迁移说明，外部存储不得依赖旧表示 |
| Q07 可重放性质 | seed900032、512步、3actor独立四分之一令牌整数账本 | RateLimitBoundaryTest.quarterSecondReferenceLedgerMatchesAReplayableMixedWorkload；不用BigDecimal复算实现 |
| Q08 环境与诊断 | commit、JDK/OS/locale/时区、依赖树/effective POM、jar SHA、逐步骤日志 | 本机Windows证据如下；Linux本票CI pending，不冒充已过 |
| Q09 质量门 | 不降原88/88/75门；架构、依赖及验证负控保留 | 原8个桶测试保留，旧clear-all/缺Bean放行断言随批准契约迁移；1478/0/0/0，原覆盖率/架构/依赖门均通过 |
| Q10 审阅交付 | 代码、ADR0032、USAGE/CHANGELOG、普通jar消费者、此报告/正式票同分支 | 本票Linux未齐仍verification-pending；12/29/33后续组合单独登记 |

## J07后续组合登记

09已经证明入口限流先于现有幂等重放，容量2下同key前两次得到相同receipt，第三次429且业务效果为1。12应补处理中/异内容冲突及保存资格后的计费；29应在事务receipt模型中分开入口防滥用与成功业务配额；33复跑最终候选。这里只承诺本地额度，重启/管理clear重置与多实例不共享均是限制，不把后续票状态机标绿。

## 完整运行

候选 `50d492d9160476052560910db5d1c1664e92d4ad`；JDK25.0.4.1 Oracle、Maven Wrapper3.10.0、Windows11 amd64、Asia/Shanghai、zh_CN；Boot4.1.1/Spring7.0.9/Jackson3.1.5/Servlet6.1（依赖树与effective POM归档）。后续只提交本报告/票状态，不改变src/pom/verification。

1. `java verification/Verify.java all` → `.verification-results/20261004-025443-462-all/summary.txt`。主库、全部普通jar消费者、真实HTTP、实际缺类矩阵、5次重复启动关闭均成功；checksum和missing-JDK负控也正常拒绝。最后因启动shell未设置VERIFY_WRONG_JAVA_HOME而主动失败，**该summary仍为RESULT=FAIL，不能声称一次all全绿**。
2. 设置 `VERIFY_WRONG_JAVA_HOME=C:/Users/yiwer/AppData/Local/Temp/server-facility-research-tools/jdk21/jdk-21.0.12.1+1` 后，同一候选运行 `java verification/Verify.java prerequisites` → `.verification-results/20261004-030212-643-prerequisites/summary.txt`，**RESULT=PASS**。checksum/missing-JDK/wrong-JDK三项均观察到预期拒绝；未删原失败、未跳过或修改门，也未重复无源码变化的1478测试。这里声明两次运行的组合证据。

| 已验证内容 | 真实结果 |
|---|---|
| 主库测试 | **1478 tests / 0 failures / 0 errors / 0 skipped**；较集成1449净增29，限流相关53项 |
| JaCoCo原门 | INSTRUCTION 19395/20856 = **92.995%**；LINE 3935/4205 = **93.579%**；BRANCH 2030/2371 = **85.618%**；原88/88/75阈值不变 |
| 架构/依赖 | 原5项ArchUnit、dependency analyze failOnWarning通过 |
| 新普通jar消费者 | `RATE_LIMIT_CONSUMER_PASS slots=1024 churn=32768 workers=16 exact-long=true framework=absent`；64MiB、2 processors、45秒进程截止 |
| 继承消费者与平台矩阵 | configured/override/invalid、core、crypto、JSON constructed/injected+双应用、Web default/user/disabled、Tika有/无、实际依赖排除矩阵均exit=0 |
| 生命周期资源 | 5个独立JVM启动/使用/关闭均成功；每个256MiB/45秒 |
| 负控 | 正确观测checksum拒绝、无JDK拒绝、真实JDK21拒绝；独立prerequisites PASS |
| 普通jar SHA-256 | `daad8d310de94ac375e00b61df2e5b6d78254374c5744de51ed943767d905a69` |

保留本地所有TDD日志与两次runner完整证据，路径均在clean之外。本票Linux由root集成CI再验证；没有将其他票的平台CI冒充本票执行。
