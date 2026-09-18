package {{ package }}.architecture;

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
@AnalyzeClasses(packages = "{{ package }}", importOptions = ImportOption.DoNotIncludeTests.class)
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
            .resideInAnyPackage("..application..", "{{ package }}.shared..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule configurationLivesInSharedConfig = classes()
            .that()
            .areAnnotatedWith(Configuration.class)
            .should()
            .resideInAnyPackage("{{ package }}.shared.config", "{{ package }}.auth.infrastructure.security", "{{ package }}")
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
            .resideInAPackage("{{ package }}..api..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("{{ package }}..domain..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainStaysFreeOfWebAndRedis = noClasses()
            .that()
            .resideInAPackage("{{ package }}..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework.web..", "jakarta.servlet..", "org.springframework.data.redis..", "org.springframework.security.web..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule sharedNeverDependsOnBusinessDomains = noClasses()
            .that()
            .resideInAPackage("{{ package }}.shared..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("{{ package }}.auth..", "{{ package }}.user..", "{{ package }}.authorization..", "{{ package }}.audit..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule authorizationDomainIsLeaf = noClasses()
            .that()
            .resideInAPackage("{{ package }}.authorization..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("{{ package }}.auth..", "{{ package }}.user..", "{{ package }}.audit..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule auditDomainIsLeaf = noClasses()
            .that()
            .resideInAPackage("{{ package }}.audit..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("{{ package }}.auth..", "{{ package }}.user..", "{{ package }}.authorization..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule userDomainDoesNotDependOnAuth = noClasses()
            .that()
            .resideInAPackage("{{ package }}.user..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("{{ package }}.auth..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule transactionalMethodsOnlyInApplicationLayer = methods()
            .that()
            .areAnnotatedWith(Transactional.class)
            .should()
            .beDeclaredInClassesThat()
            .resideInAnyPackage("..application..", "{{ package }}.shared..")
            .orShould()
            .beDeclaredInClassesThat()
            .areAssignableTo(org.springframework.data.repository.Repository.class)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule noFieldInjection = fields().should().notBeAnnotatedWith(Autowired.class);

    @ArchTest
    static final ArchRule businessDomainsAreCycleFree = slices()
            .matching("{{ package }}.(*)..")
            .should()
            .beFreeOfCycles();
}
