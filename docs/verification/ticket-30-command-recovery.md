# Ticket30：不确定提交恢复与回执清理证据

2026-10-04。实现冻结 `cd79a19540d1c4b1ce6ed26af3e14205bf1d79c3`，基于 CI19 后集成 `3f25c38a680be498ead77bf29fb7d5caf30537f6`。本报告覆盖 FR04、AC05/06 与 J10。Windows 最终同源完整门已通过；Linux 与最终同源 CI 尚待集成，不能以本机结果代替。

## 变更与保证

V4 只新增应用私有 maintenance function 和仍含 receipt 的 partial expiry index。原子批处理只清 note ID/slug/title/body；完整 workspace/Actor/operation/key、指纹、截止时间和终身额度保留。匹配已清理身份永久410，不因时间比较回拨恢复空成功或重新执行。当前授权先于内容冲突，内容冲突先于表示是否过期。

函数绑定实际迁移 schema，采用完整 PK join、FOR UPDATE SKIP LOCKED、cutoff≤数据库当前时钟且 finite、batch1–1000。SECURITY INVOKER/VOLATILE/PARALLEL UNSAFE，PUBLIC EXECUTE 显式撤销。调用者分开的 SET statement_timeout5s、SET lock_timeout500ms 先完成，再单独 autocommit SELECT；操作员角色显式 table/schema/function 权限，文档没有声称权限沙箱或自动后台任务。V1–V3 保持不可变，启动现在要求完整 V1–V4。

应用进程夹具在两个独立 JVM 加载真实 Boot/Security/Flyway/Notes/JDBC 与普通 facility jar，仅测试 classpath 的配置用一次性关联请求 gate 停在真实 Connection.commit 的前/后。另一个 gate 在真实 HTTP201 正文已 flush16字节后中断，不能把它混同为提交后无响应。实际 packaged jar 消费门单独保留。

## 可追溯测试

| 维度 | 公共/批准边界与断言 |
|---|---|
| Q01、FR04、AC05/06、J10 | NoteCommandRecoveryTest + NoteReceiptRecoveryTest；真实外部签名 HTTP、公开 Notes 与批准的受控 PG 观察；ADR0053 延续0052。 |
| Q02 输入与时间 | NoteReceiptMaintenanceTest：null/±infinity/future cutoff；batch null/0/-1/1/999/1000/1001/int min/max；1001个真实成功命令按1000+1+0清理；固定截止前/等于/后1微秒。NoteReceiptSchemaTest：合法 ops$body$schema 与临时表 shadow。 |
| Q03 实际接合 | NoteReceiptRecoveryTest：两PID、实际 unique claim wait、cleanup提交410、等待期间撤权403；deadline移至未来仍410。NoteReceiptIdentityTest：同key跨workspace/Actor/operation、另一个key；cleanup1只清目标，其余原结果可重放，身份与额度不变。 |
| Q04 故障并发 | before/after各3次精确kill+重启；after/response两类RST；双PID同键同/异内容与异键并发；默认500ms processing；迟到原请求不能覆盖更新/复活删除；3轮仅自有数据库拒绝/恢复；SKIP LOCKED行锁、并发cleanup rollback、关系锁55P03与实际5s语句57014。 |
| Q05 资源 | 子JVM128MiB/2CPU、pool4、HTTP10s、startup45s、正常stop15s+强制5s、PG观察5s且单查询3s、每child证据128files/16MiB；精确kill后pending future有限结束且独立PG会话归零；每方法DB正常DROP无FORCE；identity清理不释放quota。 |
| Q06 兼容 | 冻结CI19的V1–V3 SQL checkpoint；独立Python length-prefix/SHA256金样与字面SQL历史receipt，真实升级到V4后HTTP恢复原ID/Location/Unicode正文，再清理410。不是已有生产版本承诺。 |
| Q07 状态性质 | 固定seed30005364，6轮打乱8动作共48步：replay/conflict/expire/clear/revoke/restore/delete/clock-back；每步独立模型检查效果0/1、identity1、charge1。无宿主时钟修改或无界fuzz。 |
| Q08 环境诊断 | JDK25.0.4.1、Windows11 10.0 amd64、Asia/Shanghai、Maven3.10.0、native PostgreSQL18.6；clean冻结SHA、原始失败账本、每子PID事件/预算/退出、PG原生日志与有界统计保留。测试issuer与secret是人工fixture，无真实凭证。 |
| Q09 质量 | 原指令/行88%、分支75%及架构/依赖门保留；8个新测试类被Verify强制发现；新的schema-v3与fault host不能进入生产jar。进程独立coverage不合并冒充父coverage。 |
| Q10 交付 | 产品、ADR/协议/README、历史样本、fault测试、Verify归档同票；本机最终all、双OS CI来源分别记录。未验证不勾选closed。 |

## RED/GREEN 与失败账本

本树 `.verification-results/ticket-30/` 保存：01缺函数42883真实RED→02GREEN；03非法null未拒绝RED→04GREEN；05截止/批边界GREEN资格；06合法schema中的dollar-tag令原动态DDL42601真实RED→07通过5项，修复为先format body再%L字符串引用；08缺V4仍ready真实RED→09新场景已过但旧future历史数量断言失败，整个09为FAIL；10 fixture编译错误；11历史数量改错上下文导致4失败，整个11为FAIL；12精确修复正常history4/future5后14项PASS；13并发3项PASS（statement timeout实际5016ms）；14固定seed1PASS；15实际V4/双PIDcleanup接合1PASS；16完整PK保护1PASS。并发与process新增场景验证已有协议行为，没有伪造产品RED。

独立子树 `.verification-results/ticket-30-process/10-unicode-final.log` 是原865074f/V3基线8项通过，作为fixture资格保留；最终V4组合以本报告最终all为准。其01/06是编译失败，09在最终临时文件归属修复前的8项通过不替代10。工作树外PG预算预研是设计实证，不算业务验收。

## 最终同源执行

`verification/Verify.java all --fresh` 于2026-10-04 11:53:13–12:11:19（Asia/Shanghai，约18分钟）在 clean `cd79a19540d1c4b1ce6ed26af3e14205bf1d79c3` 完成，137个命令，外层 exit0，`summary.txt` 明确 `RESULT=PASS`。本报告与票的后续提交只更改文档，不改变该冻结的产品、测试、runner、依赖或工作流。

原始结果：`E:/GenCode/server-facility-worktrees/ticket-30/.verification-results/20261004-115313-477-all/summary.txt`；外层 `ticket-30/17-all-frozen.log`；独立派生机器账本 `ticket-30-observations.json` 同目录。runner保存输入文件SHA、effective POM、依赖树、生产jar、测试XML、coverage、PG日志、process事件与清理记录。解释性账本从原始XML/事件/产物生成，不代替原始门结果。

- 普通库1774项，0 failures/errors/skips；五项架构检查仍通过。JaCoCo指令26896/28899（93.07%）、行5204/5508（94.48%）、分支2966/3480（85.23%）。
- 独立Unicode/空格目录模板130项，0 failures/errors/skips，较CI19的108项增加22项。JaCoCo指令2423/2479（97.74%）、行328/337（97.33%）、分支226/261（86.59%）。8个新增场景类强制发现。模板build总5分20秒，其中进程恢复8项93.03秒；子进程单独coverage不混入父覆盖率。
- ordinary-jar消费者、平台配置矩阵、partner应用质量/真实HTTP/5生命周期与200尾部失败、独立模板复制/拒绝覆盖、真实数据库工具diagnostics/lifecycle/cleanup、实际Boot打包jar的platform/virtual重启HTTP均通过。反例门检查清理首因保留（预期2项=1failure+1error、0skip）、缺失coverage、错误distribution checksum、missing JDK和真实JDK21均按规定拒绝。没有把预期负控标作正向测试通过数量。
- 最终19个child实例、19次确认退出与独立PG会话归零，6次精确kill；实际两种SHOW预算为2s/500ms及显式8s/5s；清理交错观测2次unique-claim等待。启动最长5568ms、kill/close清理最长378ms，稳态会话采样最多2/pool4，无idle-in-transaction。每child保留证据最多5文件/82366字节，低于128files/16MiB。
- 71个自有方法scope数据库DROP全成功，无FORCE，最长1160ms；最终PG shutdown checkpoint仅2个sync files、0.032秒，原fast-stop预算未变。语句取消最终实测5002ms；回滚后无半清理。数据总量测试1001成功命令按1000+1+0清理，仍保留全部身份和额度。

| 最终实际产物 | SHA-256 |
|---|---|
| 普通 facility jar，472788 bytes | `0ad7ca80df4b8a8ddf2f5def024d6ccf4dc9ca21a68b6850c70e03ac90456b8b` |
| partner-aggregation 应用 jar，21989 bytes | `2b501f28ffec3916e1649ec7ab0eea1303d2394cefea043c008003bfa80c73e6` |
| secured-api executable jar，33254058 bytes | `19a2c9bcad968a2bf86a3f2a26fcb5be48c526b88166d929988b1adc90eb8fab` |

模板打包内 `BOOT-INF/lib/server-facility-0.1.0-SNAPSHOT.jar` 的SHA与普通jar完全相同，runner另做完整字节比较。这里记录同一Windows来源的实际产物，未据此宣称跨OS可复现构建；最终33负责同一候选的发布身份与重现核验。

Linux通过已有CI执行：本票未改workflow，也不增加外部服务/secret先决条件。现有PG准备入口与全门会执行新增tests及归档；预计模板阶段较29增加独立child恢复成本。本地完整门不是未来CI通过保证，ticket30仍待双OS正式来源通过后关闭。


复现（仓库根目录、同一冻结源码；runner 自建 fresh Maven repository 和 Wrapper cache）：

```powershell
$env:JAVA_HOME='C:/Program Files/Java/jdk-25.0.4.1'
$env:PG_BIN='C:/Users/yiwer/AppData/Local/Temp/server-facility-research-tools/postgres-18.6.0-windows/bin'
$env:VERIFY_WRONG_JAVA_HOME='C:/Users/yiwer/AppData/Local/Temp/server-facility-research-tools/jdk21/jdk-21.0.12.1+1'
$env:REDGATE_DISABLE_TELEMETRY='true'
& "$env:JAVA_HOME/bin/java.exe" verification/Verify.java all --fresh
```

本地解释性预研/短审：`E:/GenCode/server-facility-worktrees/coordination/ticket-30-maintenance-preflight-review.md`、`ticket-30-receipt-short-review.md`、`ticket-30-process-handoff.md`。短审未发现产品阻断，提出的完整PK保护回归已纳入最终源码。此局部审查不替代全部33票最终标准/规格双轴审查。
