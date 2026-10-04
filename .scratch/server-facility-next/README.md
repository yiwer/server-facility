# server-facility 下一代本地 tickets

发布日期：2026-10-03。用户已确认 33 张任务的粒度、依赖及本地发布方式。

- [正式任务清单与依赖](../../docs/superpowers/plans/2026-10-03-server-facility-next-tickets.md)
- [测试策略、Q01–Q10 与 J01–J17 接合矩阵](../../docs/superpowers/plans/2026-10-03-server-facility-next-test-strategy.md)
- [PRD v0.2](../../docs/superpowers/specs/2026-10-03-server-facility-next-prd.md)

issues 是唯一正式队列，一票一文件，编号01–33；drafts保留批准前的评审快照。当前33/33正式票均closed，验收和证据见各票及[最终报告](../../docs/verification/ticket-33-release-evidence.md)。被测实现为2d14f6a，CI25同候选双平台与双轴终审已完成，后续关闭提交仅更新文档。

历史发布时，所有正式票初始状态为ready-for-agent，验收框尚未勾选。

任务领取遵循先完成Blocked by。2026-10-03初始发布时无阻塞的任务为 01、02、03、04、05、06、07、08、10、11、13、14、15、16、17、18、19、20、32，共 19 张。后续以每张票的状态和阻塞关系为准。

每张票的功能、场景、接合与极端情况测试随实现交付；33 只负责最终组合与同一候选产物的证据闭合。22/23 的宽迁移只能进入约定集成线，24 验证后才能合入主线。

2026-10-03的发布仅创建本地任务，当时不表示功能或测试已实现。当前实现/验证闭合见上述最终记录；软件制品发布和部署始终不在本批任务范围。
