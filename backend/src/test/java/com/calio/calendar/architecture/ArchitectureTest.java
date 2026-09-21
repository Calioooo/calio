package com.calio.calendar.architecture;

import static com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_TESTS;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

class ArchitectureTest {

  private static final String ROOT_PACKAGE = "com.calio.calendar";
  private static final String CONTROLLER_PACKAGE = "..controller..";
  private static final String DOMAIN_PACKAGE = "..domain..";
  private static final String REPOSITORY_PACKAGE = "..repository..";
  private static final String USE_CASE_PACKAGE = "..usecase..";
  private static final Set<String> FIELD_INJECTION_ANNOTATIONS =
      Set.of(
          "org.springframework.beans.factory.annotation.Autowired",
          "jakarta.inject.Inject",
          "javax.inject.Inject",
          "jakarta.annotation.Resource",
          "javax.annotation.Resource");

  private static JavaClasses productionClasses;

  @BeforeAll
  static void importProductionClasses() {
    productionClasses =
        new ClassFileImporter().withImportOption(DO_NOT_INCLUDE_TESTS).importPackages(ROOT_PACKAGE);
  }

  @AfterAll
  static void releaseImportedClasses() {
    productionClasses = null;
  }

  @Test
  @DisplayName("Controller는 Repository에 직접 의존하지 않는다")
  void controllersDoNotDependOnRepositories() {
    noClasses()
        .that()
        .resideInAPackage(CONTROLLER_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(REPOSITORY_PACKAGE)
        .check(productionClasses);
  }

  @Test
  @DisplayName("Domain은 Controller와 Application Service에 의존하지 않는다")
  void domainDoesNotDependOnUpperLayers() {
    noClasses()
        .that()
        .resideInAPackage(DOMAIN_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(CONTROLLER_PACKAGE)
        .check(productionClasses);

    noClasses()
        .that()
        .resideInAPackage(DOMAIN_PACKAGE)
        .should()
        .dependOnClassesThat()
        .areAnnotatedWith(Service.class)
        .check(productionClasses);
  }

  @Test
  @DisplayName("Production 코드에서는 필드 주입을 사용하지 않는다")
  void productionCodeDoesNotUseFieldInjection() {
    fields().should(notUseFieldInjection()).check(productionClasses);
  }

  @Test
  @DisplayName("Domain은 외부 Client, DTO와 기술 구현에 직접 의존하지 않는다")
  void domainDoesNotDependOnExternalImplementations() {
    noClasses()
        .that()
        .resideInAPackage(DOMAIN_PACKAGE)
        .and()
        .doNotHaveFullyQualifiedName(RecurrenceSchedule.class.getName())
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.calio.calendar..client..", "com.calio.calendar.external..", "net.fortuna.ical4j..")
        .check(productionClasses);
  }

  @Test
  @DisplayName("Controller의 API 계약에 Entity를 직접 사용하지 않는다")
  void controllersDoNotExposeEntities() {
    noClasses()
        .that()
        .areAnnotatedWith(RestController.class)
        .should()
        .dependOnClassesThat()
        .areAnnotatedWith(Entity.class)
        .check(productionClasses);
  }

  @Test
  @DisplayName("기술 역할이 명확한 타입은 정해진 패키지에 둔다")
  void technicalTypesResideInExpectedPackages() {
    classes()
        .that()
        .areAnnotatedWith(RestController.class)
        .should()
        .resideInAPackage(CONTROLLER_PACKAGE)
        .check(productionClasses);

    classes()
        .that()
        .areAnnotatedWith(Entity.class)
        .should()
        .resideInAPackage(DOMAIN_PACKAGE)
        .check(productionClasses);

    classes()
        .that()
        .areAssignableTo(Repository.class)
        .should()
        .resideInAPackage(REPOSITORY_PACKAGE)
        .check(productionClasses);
  }

  @Test
  @DisplayName("UseCase는 Application 유스케이스 패키지에 위치하고 Spring Service로 등록한다")
  void useCasesResideInUseCasePackages() {
    classes()
        .that()
        .haveSimpleNameEndingWith("UseCase")
        .should()
        .resideInAPackage(USE_CASE_PACKAGE)
        .andShould()
        .beAnnotatedWith(Service.class)
        .allowEmptyShould(true)
        .check(productionClasses);
  }

  @Test
  @DisplayName("UseCase는 다른 UseCase를 직접 호출하지 않는다")
  void useCasesDoNotDependOnOtherUseCases() {
    noClasses()
        .that()
        .haveSimpleNameEndingWith("UseCase")
        .should()
        .dependOnClassesThat()
        .haveSimpleNameEndingWith("UseCase")
        .allowEmptyShould(true)
        .check(productionClasses);
  }

  @Test
  @DisplayName("Domain은 Repository에 직접 의존하지 않는다")
  void domainDoesNotDependOnRepositories() {
    noClasses()
        .that()
        .resideInAPackage(DOMAIN_PACKAGE)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(REPOSITORY_PACKAGE)
        .check(productionClasses);
  }

  @Test
  @DisplayName("Controller와 Domain에는 트랜잭션 경계를 두지 않는다")
  void controllersAndDomainDoNotOwnTransactionBoundaries() {
    noClasses()
        .that()
        .resideInAnyPackage(CONTROLLER_PACKAGE, DOMAIN_PACKAGE)
        .should()
        .beAnnotatedWith(Transactional.class)
        .check(productionClasses);

    noMethods()
        .that()
        .areDeclaredInClassesThat()
        .resideInAnyPackage(CONTROLLER_PACKAGE, DOMAIN_PACKAGE)
        .should()
        .beAnnotatedWith(Transactional.class)
        .check(productionClasses);
  }

  @Test
  @DisplayName("Entity는 공개 범용 setter를 제공하지 않는다")
  void entitiesDoNotExposePublicSetters() {
    methods()
        .that()
        .areDeclaredInClassesThat()
        .areAnnotatedWith(Entity.class)
        .and()
        .arePublic()
        .should(notBeASetter())
        .check(productionClasses);
  }

  private static ArchCondition<JavaField> notUseFieldInjection() {
    return new ArchCondition<>("not use field injection") {
      @Override
      public void check(JavaField field, ConditionEvents events) {
        field.getAnnotations().stream()
            .map(annotation -> annotation.getRawType().getName())
            .filter(FIELD_INJECTION_ANNOTATIONS::contains)
            .findFirst()
            .ifPresent(
                annotation ->
                    events.add(
                        SimpleConditionEvent.violated(
                            field,
                            field.getFullName()
                                + " uses field injection via @"
                                + simpleName(annotation))));
      }
    };
  }

  private static ArchCondition<JavaMethod> notBeASetter() {
    return new ArchCondition<>("not be a public setter") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        if (method.getName().matches("set[A-Z].*")) {
          events.add(
              SimpleConditionEvent.violated(method, method.getFullName() + " is a public setter"));
        }
      }
    };
  }

  private static String simpleName(String fullyQualifiedName) {
    return fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.') + 1);
  }
}
