package io.github.henriquevmdev.erpconnector.core;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

// ponytail: duplicates the static-field rule of testkit's ArchitectureRules; core cannot depend on testkit (cycle).
class ArchitectureTest {

    private static final String CORE = "io.github.henriquevmdev.erpconnector.core";

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(CORE);

    @Test
    void coreDependsOnNoFrameworkNorConnector() {
        noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.fasterxml..",
                        "org.springframework..",
                        "io.github.henriquevmdev.erpconnector.connector..")
                .because("core stays framework-free (AGENTS.md, hard rule 2)")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void noMutableStaticFields() {
        fields().that().areStatic()
                // Compiler-generated fields (e.g. the Eclipse compiler's $SWITCH_TABLE$ caches) are not our state.
                .and().doNotHaveModifier(JavaModifier.SYNTHETIC)
                .should().beFinal()
                .because("no global state (AGENTS.md, hard rule 3)")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void noPackageCycles() {
        slices().matching(CORE + ".(*)..")
                .should().beFreeOfCycles()
                .allowEmptyShould(true)
                .check(classes);
    }
}
