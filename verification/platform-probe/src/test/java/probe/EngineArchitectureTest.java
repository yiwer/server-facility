package probe;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "probe.model")
class EngineArchitectureTest {
    @ArchTest
    static final ArchRule java25_record_is_imported = classes()
            .that().haveSimpleName("ProbeProperties")
            .should().beRecords();

    @ArchTest
    static final ArchRule engine_negative_control = classes()
            .that().haveSimpleName("ProbeProperties")
            .should().haveSimpleName(Boolean.getBoolean("probe.fail.archunit")
                    ? "IntentionallyWrong" : "ProbeProperties");
}
