package cn.code91.facility.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * 架构守护(spec §4.4 / §7 第 5 条):
 * 规则随 phase 演进追加——P1 落地包无环 + error 纯度(C1);
 * C2/C3 的守护规则随 P4/P3 迁移任务补充。
 */
@AnalyzeClasses(packages = "cn.code91.facility", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule packages_are_cycle_free =
            slices().matching("cn.code91.facility.(*)..")
                    .should().beFreeOfCycles();

    /**
     * "纯 JDK"指运行期依赖(ADR-0010)。lombok.. 仅放行编译期注解:
     * addLombokGeneratedAnnotation=true 会在字节码标注 @lombok.Generated 供 JaCoCo 排除,
     * 不构成运行期依赖。
     */
    @ArchTest
    static final ArchRule error_package_depends_only_on_jdk =
            classes().that().resideInAPackage("cn.code91.facility.error..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("java..", "cn.code91.facility.error..", "lombok..");

    /**
     * ADR-0011:setLevel 已删除,主源码不得再依赖 logback 实现类
     * (logback-classic 仅存在于 test classpath,供 ListAppender 断言)。
     */
    @ArchTest
    static final ArchRule main_code_does_not_depend_on_logback =
            noClasses().that().resideInAPackage("cn.code91.facility..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("ch.qos.logback..");

    /**
     * C3(spec §4.4):装配层单向向下——业务包不得反向依赖 autoconfigure
     * (properties 各归其组件包后,该规则锁定归位成果)。
     */
    @ArchTest
    static final ArchRule autoconfigure_is_not_depended_on_by_main_packages =
            noClasses().that().resideOutsideOfPackage("cn.code91.facility.autoconfigure..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("cn.code91.facility.autoconfigure..");

    /**
     * ADR-0021:ExcelUtil 门面必须可在 POI 缺失的 classpath 上安全加载——
     * POI 类型只允许出现在包私有 ExcelSupport(探测通过才委托)。
     */
    @ArchTest
    static final ArchRule excel_facade_does_not_depend_on_poi =
            noClasses().that().haveFullyQualifiedName("cn.code91.facility.excel.ExcelUtil")
                    .should().dependOnClassesThat().resideInAnyPackage("org.apache.poi..");
}
