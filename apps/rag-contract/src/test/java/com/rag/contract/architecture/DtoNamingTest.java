package com.rag.contract.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Naming rules for the generated OpenAPI transfer beans. Every top-level
 * contract model carries a transfer suffix (DTO/Request/Response/Result) so
 * its simple name can never collide with a rag-* domain/entity class (see
 * architecture-guidelines). Nested types (e.g. generated enums) are exempt.
 */
class DtoNamingTest {

    private static final String MODEL_PACKAGE = "com.rag.contract.model..";

    private static final DescribedPredicate<JavaClass> TOP_LEVEL =
            new DescribedPredicate<>("top-level classes") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return !javaClass.getName().contains("$");
                }
            };

    private final ClassFileImporter importer = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests());

    @Test
    void transferBeansCarryAnExplicitSuffix() {
        classes()
                .that().resideInAPackage(MODEL_PACKAGE)
                .and(TOP_LEVEL)
                .should().haveSimpleNameEndingWith("DTO")
                .orShould().haveSimpleNameEndingWith("Request")
                .orShould().haveSimpleNameEndingWith("Response")
                .orShould().haveSimpleNameEndingWith("Result")
                .because("API beans need an explicit transfer suffix to avoid naming collisions")
                .check(importer.importPackages(MODEL_PACKAGE));
    }
}