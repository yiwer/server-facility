package cn.code91.facility.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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

    @ArchTest
    static final ArchRule error_package_depends_only_on_jdk =
            classes().that().resideInAPackage("cn.code91.facility.error..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage("java..", "cn.code91.facility.error..");
}
