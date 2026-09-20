# 백엔드 도메인 책임 규칙

## 목적

이 문서는 비즈니스 판단과 상태 변경을 Application Service, Domain Model, Domain Service 중 어디에 둘지 판단하는 기준을 정한다.

계층의 구성과 트랜잭션 경계는 `architecture.md`를 따른다. 이 문서는 기능 영역마다 고정된 Aggregate 목록이나 모델 구조를 정의하지 않는다.

## 책임 소유자 판단

비즈니스 규칙을 구현할 때는 그 규칙이 보호하는 상태와 행동의 소유자를 먼저 확인한다.

- 한 객체의 상태를 확인하고 그 상태를 변경하는 규칙은 가능한 한 해당 객체가 소유한다.
- 상태 변경이 항상 만족해야 하는 조건은 변경을 수행하는 객체가 함께 검증한다.
- 객체에서 값을 꺼내 외부에서 변경 가능 여부를 판단한 뒤 다시 값을 설정하는 흐름은 해당 객체의 행위로 표현할 수 있는지 검토한다.
- 단순한 값 조회나 모든 조건 분기를 기계적으로 도메인 메서드로 옮기지는 않는다. 해당 판단이 비즈니스 상태와 불변식을 보호하는지가 기준이다.

## Application Service의 책임

Application Service는 유스케이스를 수행하기 위해 필요한 객체를 조회하고, 협력 대상의 호출 순서와 트랜잭션을 조율한다.

사용자 권한 확인에 필요한 정보 조회, 외부 시스템 호출, 여러 객체의 작업 순서 결정은 유스케이스의 조율에 해당할 수 있다. 다만 조회한 도메인 객체의 상태를 근거로 그 객체의 변경 가능 여부를 결정하고 상태를 직접 조작하는 정책은 자연스러운 도메인 책임 소유자가 있는지 먼저 확인한다.

## Entity, Value Object와 Aggregate

- Entity는 식별성과 생명주기를 가진다. 자신의 상태에 관한 행동과 조건을 소유할 수 있다.
- Value Object는 독립적인 식별성보다 값의 의미와 유효성이 중요한 경우에 사용한다.
- Aggregate 경계는 JPA 연관관계만으로 결정하지 않는다. 함께 일관성을 지켜야 하는 상태와 변경 경계를 기준으로 판단한다.
- `@Entity`로 매핑되어 있다는 사실만으로 독립적인 Aggregate라고 판단하거나, 반대로 Value Object로 바꿔야 한다고 판단하지 않는다. 식별성, 생명주기, 변경 방식과 영속성 제약을 함께 검토한다.

### Value Object의 유지

- 도메인 의미, 유효성 검증 또는 생성 규칙을 가진 값은 Entity 내부에서도 Value Object 타입으로 유지한다.
- Entity는 Value Object를 원시값이나 문자열로 풀어 저장하여 Value Object의 규칙을 우회하지 않는다.
- 별도의 도메인 의미나 규칙이 없는 단순 타입 별칭은 Value Object로 만들지 않는다.

## Aggregate와 Aggregate Root

- Aggregate는 하나의 트랜잭션에서 함께 일관성을 보호해야 하는 상태의 경계다.
- Aggregate에는 하나의 Aggregate Root가 있으며, 외부의 변경 요청은 Root가 제공하는 행위를 통해 진입한다.
- Aggregate 내부 Entity와 Value Object의 상태를 외부에서 직접 변경해 Root의 불변식을 우회하지 않는다.
- Child Entity는 자신의 로컬 규칙을 소유할 수 있지만 외부의 변경 진입점이 되지 않는다. Root는 Aggregate 전체의 불변식을 확인하고 필요한 작업을 내부 객체에 위임한다.
- 변경을 위한 조회와 저장은 Aggregate Root를 기준으로 한다. Child Entity를 직접 변경하기 위한 Repository를 두지 않는다.
- 내부 Entity를 독립적으로 변경해야 하는 요구가 반복되면 직접 변경을 허용하기보다 별도 Aggregate 경계가 필요한지 검토한다.
- 조회 전용 데이터 접근은 허용하되, 조회한 Child Entity를 직접 변경하는 진입점으로 사용하지 않는다.

## Aggregate 사이의 참조

- 같은 Aggregate 내부의 Root와 Child Entity는 객체 연관관계로 구성할 수 있다.
- 서로 다른 Aggregate는 원칙적으로 Aggregate Root의 식별자로 참조한다.
- 조회 편의를 위한 데이터 접근과 Aggregate의 변경 경계를 동일하게 취급하지 않는다.

## 여러 Aggregate가 참여하는 유스케이스

- Application Service는 유스케이스에 필요한 Aggregate Root를 조회하고 각 Root의 행위를 호출한다.
- 하나의 Aggregate Root가 다른 Aggregate의 내부 상태를 직접 변경하지 않는다.
- 여러 Aggregate에 걸친 도메인 정책이 특정 Root에 자연스럽게 속하지 않으면 Domain Service를 검토할 수 있다.
- 변경된 Aggregate의 불변식은 트랜잭션 커밋 시점에 만족해야 하며, 이를 후속 처리로 미루지 않는다.
- 서로 다른 Aggregate 사이의 즉시 일관성 여부는 비즈니스 요구를 기준으로 판단한다.
- 하나의 변경 트랜잭션은 가능한 한 하나의 Aggregate를 일관성 경계로 다룬다.
- 여러 Aggregate를 하나의 트랜잭션에서 변경해야 한다면 즉시 일관성이 실제로 필요한지와 Aggregate 경계가 적절한지 먼저 검토한다.
- Aggregate 사이의 일시적인 불일치를 비즈니스가 허용하면 도메인 이벤트와 재시도 가능한 후속 처리를 통한 최종적 일관성을 검토한다.

## Domain Service 도입 기준

정책이 도메인 규칙이지만 특정 Entity, Value Object 또는 Aggregate가 자연스럽게 소유하지 않을 때 Domain Service를 검토한다.

- 코드가 복잡하거나 길다는 이유만으로 Domain Service를 만들지 않는다.
- 여러 도메인 객체의 정보를 함께 사용한다는 이유만으로 Domain Service를 만들지 않는다. 먼저 하나의 객체가 해당 결정을 소유할 수 있는지 확인한다.
- 객체를 조회하고 호출 순서를 정하는 일만 있다면 Application Service의 조율로 둔다.
- 특정 객체의 상태 전이를 대신 수행하기 위해 Domain Service를 만들지 않는다.
- Domain Service에 인터페이스를 둘 경우, 외부 구현 기술이 아니라 도메인의 요구와 용어를 기준으로 계약을 정의한다.
- 도메인에 필요한 계산이나 정책을 외부 라이브러리로 구현할 때는 도메인 타입으로 계약을 표현하는 인터페이스로 기술 의존성을 분리한다. 이 인터페이스를 둔다는 이유만으로 Domain Service로 분류하지 않는다.
- HTTP, 외부 API 요청·응답 모델, 외부 라이브러리 타입과 기술 예외를 Domain Model이나 Domain Service 인터페이스에 노출하지 않는다.
- Domain Service의 판단이 Aggregate 상태 전이의 조건이라면 Root 메서드의 협력 객체로 전달할 수 있다.
- Aggregate는 Domain Service를 영속 상태나 필드 의존성으로 보관하지 않는다.
- 상태 변경과 무관한 조회나 계산에서는 Application Service가 Domain Service를 직접 호출할 수 있다.

## 책임 배치 확인 질문

- 이 판단이 보호하는 상태는 무엇이며, 그 상태의 소유자는 누구인가?
- Application Service가 도메인 객체가 소유해야 할 변경 가능 여부를 대신 판단하고 있지는 않은가?
- 상태를 변경하는 공개 메서드를 다른 호출자가 사용해도 불변식이 유지되는가?
- Aggregate 내부 객체를 Root를 거치지 않고 직접 변경하고 있지는 않은가?
- 하나의 트랜잭션에서 여러 Aggregate를 변경해야 하는 비즈니스 근거가 있는가?
- 최종적 일관성을 선택했다면 Aggregate 내부가 아니라 경계 사이의 일시적인 불일치만 허용하는가?
- Domain Service가 필요한 정책인가, 아니면 객체의 행동 또는 유스케이스 조율인가?
- Domain Service가 단순히 복잡한 코드를 옮겨 놓은 클래스이거나 외부 기술을 Domain Model에 노출하고 있지는 않은가?
- Entity와 Value Object의 구분이 실제 식별성과 생명주기를 반영하는가?
