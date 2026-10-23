package io.github.henriquevmdev.erpconnector.connector.fake;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.github.henriquevmdev.erpconnector.testkit.ArchitectureRules;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final String PACKAGE = "io.github.henriquevmdev.erpconnector.connector.fake";

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(PACKAGE);

    @Test
    void noMutableStaticFields() {
        ArchitectureRules.NO_MUTABLE_STATIC_FIELDS.check(classes);
    }

    @Test
    void noPackageCycles() {
        ArchitectureRules.noPackageCycles(PACKAGE).check(classes);
    }
}
