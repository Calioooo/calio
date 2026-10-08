# 백엔드 아키텍처 원칙

## 목적

이 문서는 backend 구현의 구조와 유스케이스 경계를 정한다.

비즈니스 규칙의 소유자, Aggregate, Entity, Value Object 및 Domain Service의 도입 기준은 `docs/development/backend/domain-responsibility.md`에서 정의한다. 일반적인 코드 작성 방식은 `docs/development/backend/conventions.md`를 따른다.

## 패키지 구성

- 코드는 기능 영역을 기준으로 묶되, 영역의 목록과 경계를 고정하지 않는다.
- 도메인 모델과 도메인 의미를 표현하는 닫힌 enum은 해당 기능 영역의 `domain`에 둔다.
- `common`에는 여러 영역에서 같은 의미로 사용하는 공통 책임만 둔다.

## 구조적 역할


| 구성 요소                   | 역할                                                                                                |
| ----------------------- | ------------------------------------------------------------------------------------------------- |
| Controller              | HTTP 요청·응답 경계를 다루고 유스케이스를 호출한다.                                                                   |
| UseCase (Application Service) | 유스케이스의 조회·저장 순서, 협력 대상 및 트랜잭션을 조율한다.                                                        |
| Repository              | JPA를 통한 조회와 저장소 접근을 담당한다.                                                                         |
| Domain Model            | 비즈니스 상태와 행동을 표현한다. 구체적인 책임 기준은 `domain-responsibility.md`를 따른다.                                   |
| Domain Service (필요한 경우) | 특정 Entity나 Value Object가 자연스럽게 소유하지 않는 도메인 정책을 표현한다. 구체적인 도입 기준은 `domain-responsibility.md`를 따른다. |


계층 간 패키지 의존성처럼 기계적으로 판단할 수 있는 규칙은 정적 검증 대상으로 두고, 이 문서는 책임 배치와 경계의 의미적 판단에 집중한다.

## 유스케이스 기반 Application 구조

- 기능 영역은 기존처럼 `event`, `recurrence`, `notification` 등의 도메인 단위로 구분한다.
- Application 계층의 구현 단위는 포괄적인 도메인 Service나 기술적인 Command/Query 구분이 아니라 유스케이스로 한다.
- 하나의 UseCase는 하나의 명확한 외부 목적과 트랜잭션 경계를 표현한다.
- Controller, Scheduler, Event Listener 등의 외부 진입점은 목적에 해당하는 UseCase를 직접 호출한다.
- UseCase는 필요한 Repository를 직접 사용하며, Repository 호출만 위임하는 CommandService나 QueryService를 두지 않는다.
- UseCase는 다른 UseCase를 호출하지 않는다. 여러 유스케이스에 필요한 규칙이나 로직은 책임에 따라 Domain Model, Domain Service 또는 별도의 협력 객체로 분리한다.
- Entity, Value Object와 Domain Service는 특정 UseCase에 종속시키거나 UseCase마다 중복해서 정의하지 않는다.
- 기존 구조는 기능 영역 단위로 점진적으로 전환한다.

기능 영역은 다음 구조를 기본으로 한다.

```text
<feature>/
├── controller/
├── usecase/
├── domain/
├── repository/
└── infrastructure/
```

유스케이스의 수와 복잡도가 커진 경우에만 `usecase` 아래를 개별 유스케이스 패키지로 나눈다.

## 조회와 변경의 기본 흐름

Application Service는 해당 기능의 Repository를 직접 사용한다.

- 조회 유스케이스는 `@Transactional(readOnly = true)` 경계에서 Repository로 데이터를 조회하고, 필요한 응답 DTO로 변환한다.
- 변경 유스케이스는 쓰기 트랜잭션 안에서 변경할 도메인 객체를 조회한 뒤 그 객체의 의미 있는 행위를 호출한다. 새 객체의 저장과 삭제는 Repository를 사용한다.
- 조회 유스케이스에서 반환하는 Entity를 Controller에 직접 노출하지 않는다. 응답 변환에 필요한 연관 데이터 접근과 fetch 범위를 확인한다.
- 도메인 상태를 바꿀 수 있는지에 대한 판단은 Application Service의 조회·저장 순서와 구분한다. 판단의 소유 기준은 `domain-responsibility.md`를 따른다.
- 잠금, 벌크 변경, 조기 `flush()`처럼 저장소의 명시적인 동작이 필요한 경우에는 목적이 드러나는 Repository 메서드로 표현한다.

### Repository 조회 메서드

- 기본 CRUD는 Spring Data JPA가 제공하는 메서드를 사용한다.
- 단순 조회는 메서드 이름 기반 쿼리를 우선한다.
- 조건이 3개 이상이거나 이름만으로 조회 의미를 이해하기 어려우면 `@Query` 사용을 검토한다.
- `@Query` 메서드는 조회 대상과 목적이 드러나는 이름을 사용한다.

### 연관 데이터 로딩

- 조회·변경 유스케이스에서 매핑된 연관 데이터를 함께 로딩해야 할 때는 `@EntityGraph`를 기본으로 사용한다.
- 같은 Aggregate 내부의 조회 조건·정렬을 위한 일반 join은 필요에 따라 사용한다.
- ID로 참조하는 다른 Aggregate의 데이터는 해당 Repository를 통해 별도로 조회하는 것을 기본으로 한다. `@EntityGraph`를 적용하기 위해 Aggregate 사이의 객체 연관관계를 추가하지 않는다.
- 목록 조회에서는 필요한 ID를 모아 일괄 조회하여 항목별 반복 조회를 피한다.

## 영속성 매핑과 Aggregate 경계

- JPA 연관관계가 존재한다는 사실만으로 같은 Aggregate라고 판단하지 않는다.
- 같은 Aggregate 내부에서는 Root와 Child Entity의 객체 연관관계 및 생명주기에 맞는 cascade를 사용할 수 있다.
- 서로 다른 Aggregate Root 사이에는 객체 연관관계보다 식별자 참조를 우선한다.
- Aggregate Root 사이에 영속성 cascade를 적용하지 않는다.
- 식별자로 참조하더라도 데이터베이스의 외래 키 제약은 유지할 수 있다.
- 다른 Aggregate의 상태가 필요한 변경 유스케이스는 Application Service가 각 Repository를 통해 Root를 명시적으로 조회한다.

### Value Object 영속성 매핑

- 도메인 의미와 규칙을 가진 Value Object는 Entity 필드에서도 해당 타입을 유지한다.
- JPA 매핑을 위해 Value Object를 Entity 내부에서 원시값이나 문자열로 풀어 저장하지 않는다.
- 여러 컬럼으로 표현되는 Value Object는 `@Embeddable`과 `@Embedded`를 기본으로 사용한다.
- 단일 컬럼으로 저장되는 Value Object는 조회 방식과 재사용 범위를 고려하여 `@Embeddable` 또는 `AttributeConverter`를 선택한다.
- 영속 상태에서 Value Object를 복원할 때도 유효성 검증을 우회하지 않는다.

## 트랜잭션과 외부 연동

- 트랜잭션 경계는 기본적으로 외부에서 호출되는 Application Service의 유스케이스 메서드에 둔다. 조회에는 읽기 전용, 변경에는 쓰기 트랜잭션을 사용한다.
- 같은 클래스 내부 호출에 붙은 `@Transactional`이 독립적으로 적용된다고 가정하지 않는다. 같은 Application Service 안에서 짧은 DB 작업의 트랜잭션 경계를 명시해야 할 때는 `TransactionTemplate`을 사용할 수 있다. 기본 전파 설정에서는 기존 트랜잭션에 참여하므로, 사용 자체가 독립적인 트랜잭션을 보장하지 않는다. 프록시의 self-invocation 문제를 피하기 위해서만 별도 서비스를 만들지 않는다.
- 외부 API나 SDK의 요청·응답 모델을 Domain Model의 계약으로 사용하지 않는다.
- 외부 API 호출은 가능한 한 DB 트랜잭션 밖에서 수행하고, 필요한 DB 작업만 짧게 트랜잭션으로 묶는다. 외부 호출이 트랜잭션 밖에 있어야 하는 흐름에서는 상위 호출자가 이미 트랜잭션을 시작했는지도 확인한다.
- 외부 호출과 DB 작업 사이의 부분 실패 및 재시도 가능성을 검토한다.
- 외부 반영이 완료되지 않은 상태를 사용자나 운영자가 알아야 한다면 대기 또는 실패 상태를 명시적으로 표현한다.
