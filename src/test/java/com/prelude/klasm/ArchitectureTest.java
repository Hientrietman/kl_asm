package com.prelude.klasm;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;

@AnalyzeClasses(packages = "com.prelude.klasm", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "com.prelude.klasm";
    private static final List<String> MODULES =
            List.of("appointment", "availability", "dealership", "customer", "catalog");

    @ArchTest
    static final ArchRule noFieldInjection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule noSystemClockCalls = noClasses()
            .should()
            .callMethod(Instant.class, "now")
            .orShould()
            .callMethod(LocalDate.class, "now")
            .orShould()
            .callMethod(LocalDateTime.class, "now")
            .orShould()
            .callMethod(OffsetDateTime.class, "now")
            .orShould()
            .callMethod(ZonedDateTime.class, "now")
            .because("business code must use the injected Clock (AGENTS.md section 4)");

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..repository..");

    @ArchTest
    static void modulesDoNotUseOtherModulesRepositories(JavaClasses classes) {
        for (String module : MODULES) {
            String modulePackage = BASE + "." + module;
            noClasses()
                    .that()
                    .resideOutsideOfPackage(modulePackage + "..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(modulePackage + ".repository..")
                    .because("modules talk only through public service interfaces (ADR-002)")
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }
}
