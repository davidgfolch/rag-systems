package com.rag.agentic.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture rules for rag-agentic, enforced by ArchUnit. The module owns its
 * api/services/agents/tools/orchestration/domain layers; chunking/parsing/
 * embedding/vector-store strategy implementations live in the rag-common-*
 * modules and are consumed through ports.
 */
class ArchitectureTest {

    private static final String ROOT = "com.rag.agentic";

    private final ClassFileImporter importer = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests());

    @Test
    void layersDependInOneDirection() {
        Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("api").definedBy("com.rag.agentic.api..")
                .layer("services").definedBy("com.rag.agentic.services..")
                .layer("orchestration").definedBy("com.rag.agentic.orchestration..")
                .layer("agents").definedBy("com.rag.agentic.agents..")
                .layer("tools").definedBy("com.rag.agentic.tools..")
                .layer("domain").definedBy("com.rag.agentic.domain..")
                .whereLayer("api").mayOnlyAccessLayers("services", "orchestration", "domain")
                .whereLayer("services").mayOnlyAccessLayers("orchestration", "domain")
                .whereLayer("orchestration").mayOnlyAccessLayers("agents", "tools", "domain")
                .whereLayer("agents").mayOnlyAccessLayers("domain")
                .whereLayer("tools").mayOnlyAccessLayers("services", "domain")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void controllersDoNotDependOnConfigOrCommonRepositories() {
        noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..config..", "com.rag.common.core.repositories")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void everyServiceAgentToolOrchestratorAndHandlerHasLogger() {
        classes()
                .that().resideInAnyPackage(
                        "..services..", "..api..", "..agents..", "..tools..", "..orchestration..")
                .and().areNotInterfaces()
                .and().areNotNestedClasses()
                .and().areNotRecords()
                .should(new ArchCondition<>("declare an SLF4J logger field named 'log'") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        boolean hasLogger = item.getFields().stream()
                                .anyMatch(f -> "log".equals(f.getName())
                                        && f.getRawType().isEquivalentTo(Logger.class));
                        if (!hasLogger) {
                            events.add(SimpleConditionEvent.violated(item,
                                    item.getDescription() + " has no SLF4J logger field 'log'"));
                        }
                    }
                })
                .because("structured logging is mandatory for capability modules")
                .check(importer.importPackages(ROOT));
    }
}