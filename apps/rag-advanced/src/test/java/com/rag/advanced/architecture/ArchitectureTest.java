package com.rag.advanced.architecture;

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
 * Architecture rules for rag-advanced, enforced by ArchUnit. The module owns its
 * api/services/retrieval layers; chunking/parsing/embedding/vector-store strategy
 * implementations live in the rag-common-* modules.
 */
class ArchitectureTest {

    private static final String ROOT = "com.rag.advanced";

    private final ClassFileImporter importer = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests());

    @Test
    void layersDependInOneDirection() {
        Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("api").definedBy("..api..")
                .layer("services").definedBy("..services..")
                .layer("retrieval").definedBy("..retrieval..")
                .whereLayer("api").mayOnlyAccessLayers("services")
                .whereLayer("services").mayOnlyAccessLayers("retrieval")
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
    void everyServiceControllerHandlerAndClientHasLogger() {
        classes()
                .that().resideInAnyPackage("..services..", "..api..")
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