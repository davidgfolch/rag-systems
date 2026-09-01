package com.rag.memory.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.Architectures;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private static final String ROOT = "com.rag.memory";

    private final ClassFileImporter importer = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests());

    @Test
    void layersRespectDependencyFlow() {
        Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("domain").definedBy("..domain..")
                .layer("services").definedBy("..services..")
                .layer("repositories").definedBy("..repositories..")
                .layer("api").definedBy("..api..")
                .whereLayer("domain").mayNotAccessAnyLayer()
                .whereLayer("repositories").mayOnlyAccessLayers("domain")
                .whereLayer("services").mayOnlyAccessLayers("domain", "repositories")
                .whereLayer("api").mayOnlyAccessLayers("services")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void controllersDoNotAccessRepositories() {
        noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..repositories..")
                .check(importer.importPackages(ROOT));
    }

    @Test
    void jpaEntitiesAreSuffixedWithEntity() {
        classes()
                .that().areAnnotatedWith(Entity.class)
                .should().haveSimpleNameEndingWith("Entity")
                .because("bean classes carry a suffix so they never collide with same-named contract DTOs")
                .check(importer.importPackages(ROOT));
    }
}