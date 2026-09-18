package com.wl.cwa.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dependency-boundary gate. These rules encode the module layout required by the design docs and
 * fail the build as soon as a violation is introduced.
 */
@AnalyzeClasses(packages = "com.wl.cwa", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllersLiveInApiPackages = classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .or()
            .areAnnotatedWith(Controller.class)
            .should()
            .resideInAPackage("..api..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule servicesLiveInApplicationPackages = classes()
            .that()
            .areAnnotatedWith(Service.class)
            .should()
            .resideInAnyPackage("..application..", "com.wl.cwa.shared..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule configurationLivesInSharedConfig = classes()
            .that()
            .areAnnotatedWith(Configuration.class)
            .should()
            .resideInAnyPackage("com.wl.cwa.shared.config", "com.wl.cwa.auth.infrastructure.security", "com.wl.cwa")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule entitiesLiveInDomainPackages = classes()
            .that()
            .areAnnotatedWith(Entity.class)
            .should()
            .resideInAPackage("..domain..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule apiNeverTouchesEntitiesOrRepositories = noClasses()
            .that()
            .resideInAPackage("com.wl.cwa..api..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.wl.cwa..domain..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainStaysFreeOfWebAndRedis = noClasses()
            .that()
            .resideInAPackage("com.wl.cwa..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework.web..", "jakarta.servlet..", "org.springframework.data.redis..", "org.springframework.security.web..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule sharedNeverDependsOnBusinessDomains = noClasses()
            .that()
            .resideInAPackage("com.wl.cwa.shared..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.wl.cwa.auth..", "com.wl.cwa.user..", "com.wl.cwa.authorization..", "com.wl.cwa.audit..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule authorizationDomainIsLeaf = noClasses()
            .that()
            .resideInAPackage("com.wl.cwa.authorization..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.wl.cwa.auth..", "com.wl.cwa.user..", "com.wl.cwa.audit..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule auditDomainIsLeaf = noClasses()
            .that()
            .resideInAPackage("com.wl.cwa.audit..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.wl.cwa.auth..", "com.wl.cwa.user..", "com.wl.cwa.authorization..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule userDomainDoesNotDependOnAuth = noClasses()
            .that()
            .resideInAPackage("com.wl.cwa.user..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.wl.cwa.auth..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule transactionalMethodsOnlyInApplicationLayer = methods()
            .that()
            .areAnnotatedWith(Transactional.class)
            .should()
            .beDeclaredInClassesThat()
            .resideInAnyPackage("..application..", "com.wl.cwa.shared..")
            .orShould()
            .beDeclaredInClassesThat()
            .areAssignableTo(org.springframework.data.repository.Repository.class)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule noFieldInjection = fields().should().notBeAnnotatedWith(Autowired.class);

    @ArchTest
    static final ArchRule businessDomainsAreCycleFree = slices()
            .matching("com.wl.cwa.(*)..")
            .should()
            .beFreeOfCycles();
}
