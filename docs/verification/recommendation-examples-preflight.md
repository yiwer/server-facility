# 当前推荐入口与配置样例核验（preflight）

2026-10-04；来源父提交 `2cf2dd850f893e5435548f8ae94bca3a234876c7`。这是08交接后的文档/资源样例修正，不是33最终候选审查，也不改变任何票的验证状态。产品Java/POM/验证门不变；历史ADR与既有成功/失败证据保留。

README/DESIGN推荐应用拥有的Jsons、MessageSource、Executor和服务Adapter；旧静态门面标明兼容范围。README/USAGE/application.example.yaml同步了真实filter让位、默认关闭的trace/repeatable/cache、安全HTTP错误、限流/幂等关闭provider后的注解守卫、固定缓存名字/正预算、仅本地mutex及宿主HTTP builder优先。Excel optional行按已实施0039改为5.5.1完整格式图。没有新增默认能力或放宽承诺。

本轮未改票10拥有的README/USAGE/YAML ID段，也未改票20的日期/数值段；它们由各自来源合入。所有绑定值的对照以本报告明确的父提交为准，不能把此局部检查称作后续联合候选全门。

## 实际核验

Windows11 amd64，Oracle JDK25.0.4.1+1-LTS-5。使用08已经测试的普通jar：
`SHA256 ce9edfb7198d82eee5ab40007718881552dc72370d19ed033567dfefb9f6a608`。

- 用Spring Boot `YamlPropertySourceLoader`加载资源样例，以及从USAGE「装配开关全表」抽取的YAML。
- `Binder`配合`NoUnboundElementsBindHandler`逐一绑定12个`Facility*Properties`组；98个属性值与源码默认或明确标注的应用选择比对通过。USAGE的lease=30s/result-retention=5m是应用选择；资源样例保留默认兼容回退并将选择写为注释。
- 真实`ApplicationContextRunner`配置Boot Jackson和11个facility自动配置（非Web context）；默认样例启动、Jsons构造注入序列化、应用Executor的Async map/timeout/recover、默认仅LocalKeyedMutex和无CacheManager均通过。
- 另一个context按注释将缓存选为`enabled=true/cache-names=catalog`，标准CacheManager/Cache写读成功，未列名字不创建缓存。
- `git diff --check`通过，ID块与父提交逐字相同。未重复产品全量门或Servlet平台门，本轮只验证文档示例适用性。

本地证据保存在 `.verification-results/doc-example-preflight/`：`PublicExampleCheck.java`、`classpath.txt`、从USAGE抽取的`usage-example.yaml`、`compile.log`、`run.log`。classpath从已经通过的库Surefire报告读取，并以被测普通jar替换target/classes；命令：

```powershell
$docCp = Get-Content .verification-results/doc-example-preflight/classpath.txt -Raw
javac -cp $docCp -d .verification-results/doc-example-preflight/classes .verification-results/doc-example-preflight/PublicExampleCheck.java
java -cp ('.verification-results/doc-example-preflight/classes;' + $docCp) PublicExampleCheck src/main/resources/application.example.yaml .verification-results/doc-example-preflight/usage-example.yaml
```

终端结果：两份`BIND PASS`、默认context与显式缓存context各`PASS`、`checked property values=98`，exit0。首次一次性fixture漏注册Boot Jackson配置，缺Jsons而失败；`run-incomplete-fixture.log`原样保留。随后补齐fixture所需Boot装配后通过，未改变产品实现。

## 公开API预勘边界

协调目录`release-api-gap-preflight.md`及配套javap/JSON差分保存固定基线0ee9d54与当前父提交的公开声明比较：29包、基线129/当前162可见类，无整类消失，25个旧成员描述符未匹配。包含Jackson2→3与public自动配置工厂，不把后者从破坏面默默排除。**不能宣称整个旧基线二进制兼容**；31/33须在最终同一个候选上重提取、登记迁移和消费者证据。该协调预勘不替代最终标准/spec双审、依赖账本或跨平台CI。
