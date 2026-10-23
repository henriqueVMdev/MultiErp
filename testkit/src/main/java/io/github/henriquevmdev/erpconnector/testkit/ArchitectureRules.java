package io.github.henriquevmdev.erpconnector.testkit;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Architecture rules every connector must pass. Import the connector's production classes and call
 * {@code rule.check(classes)} from a test.
 */
public final class ArchitectureRules {

    /** No global state: one connector instance per ERP account must never share data with another. */
    public static final ArchRule NO_MUTABLE_STATIC_FIELDS = fields()
            .that().areStatic()
            .should().beFinal()
            .because("connectors must not hold global state (AGENTS.md, hard rule 3)")
            // Having no static fields at all is the desired state, not an error.
            .allowEmptyShould(true);

    /** No dependency cycles between the direct sub-packages of {@code basePackage}. */
    public static ArchRule noPackageCycles(String basePackage) {
        return slices().matching(basePackage + ".(*)..")
                .should().beFreeOfCycles()
                .allowEmptyShould(true);
    }

    private ArchitectureRules() {
    }
}
