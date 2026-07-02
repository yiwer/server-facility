# ADR-0013: 配置属性校验策略——构造器兜底,不用 @Validated

- **状态**:Accepted(2026-07-02)
- **源起**:P3 终审消费方仿真——默认配置下启动即崩(NoProviderFoundException)

## 背景

`FacilityIdProperties` 曾带 `@Validated`+`@Min/@Max`。Boot 3.5 的
ConfigurationPropertiesJsr303Validator 只探测 **API** 类(本库 compile 依赖
jakarta.validation-api 使其恒真),随即无守卫地构建 LocalValidatorFactoryBean;
而 spring-boot-starter-web 自 2.3 起不含 validation provider——纯默认消费方
classpath 无 provider,`facility.id` 零配置也在启动期抛
`jakarta.validation.NoProviderFoundException`(终审以 FilteredClassLoader 实验证实)。
源项目以 hibernate-validator runtime scope 掩盖该问题;spec §6 降 test 的理由
("仅测试需要实现")被实验证伪——但恢复 runtime 会给每个消费方强塞三个 jar。

## 决策

1. properties 类**不用 `@Validated`**;`@Min/@Max` 保留为可执行文档;
2. 范围守卫由组件构造器兜底(`SnowIdGenerator` 越界即
   `IllegalArgumentException("workerId out of range [0, 3]")`,任何 classpath 下快速失败);
3. hibernate-validator 维持 **test** scope;
4. 回归守卫:`FilteredClassLoader("org.hibernate.validator")` 用例钉住"无 provider 可启动";
5. **P5/P6 迁移后续 properties 类一体适用本决策**(web 簇 properties 同样处理)。

## 备选(否决)

- validation-api 降 optional:springdoc 等常见依赖会重新引入 API 而无实现,崩溃回归;
- hibernate-validator 回 runtime:违背 classpath 收敛目标,为两个 int 范围检查代价过高。
