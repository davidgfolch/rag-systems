package com.rag.common.generation.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/**
 * Architecture rules for rag-common-generation: chat orchestration and model
 * adapters may depend on the core kernel, the contract and Spring AI chat/model
 * abstractions, but never on the ingestion or retrieval capabilities.
 */
class ArchitectureTest {

    private static final String ROOT = "com.rag.common.generation";

    private final ClassFileImporter importer = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests());

    @Test
    void generationDoesNotDependOnIngestionOrRetrieval() {
        noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.rag.common.ingestion..", "com.rag.common.retrieval..")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void generationAdaptersAndServicesKeepStructuredLogging() {
        classes()
                .that().resideInAnyPackage("..generation..", "..adapter..")
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
                .check(importer.importPackages(ROOT));
    }
}
