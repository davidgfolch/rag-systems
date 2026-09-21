package com.rag.common.retrieval.architecture;

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
 * Architecture rules for rag-common-retrieval: vector-store adapters may depend
 * on the core kernel and Spring AI vector-store abstractions, but never on the
 * ingestion or generation capabilities.
 */
class ArchitectureTest {

    private static final String ROOT = "com.rag.common.retrieval";

    private final ClassFileImporter importer = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests());

    @Test
    void retrievalDoesNotDependOnIngestionOrGeneration() {
        noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.rag.common.ingestion..", "com.rag.common.generation..")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void storeAdaptersKeepStructuredLogging() {
        classes()
                .that().resideInAPackage("..store..")
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
