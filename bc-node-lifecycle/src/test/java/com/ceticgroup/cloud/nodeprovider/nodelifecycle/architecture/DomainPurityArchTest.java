package com.ceticgroup.cloud.nodeprovider.nodelifecycle.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class DomainPurityArchTest {

    private static final String DOMAIN_PACKAGE =
            "com.ceticgroup.cloud.nodeprovider.nodelifecycle.domain..";

    private static final JavaClasses PRODUCTION_CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.ceticgroup.cloud.nodeprovider.nodelifecycle");

    @Test
    void domain_must_not_depend_on_spring() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage(DOMAIN_PACKAGE)
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("org.springframework..");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void domain_must_not_depend_on_jakarta() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage(DOMAIN_PACKAGE)
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("jakarta..");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void domain_must_not_depend_on_jackson() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage(DOMAIN_PACKAGE)
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("com.fasterxml.jackson..");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void domain_must_not_depend_on_dockerjava() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage(DOMAIN_PACKAGE)
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("com.github.dockerjava..");

        rule.check(PRODUCTION_CLASSES);
    }
}
