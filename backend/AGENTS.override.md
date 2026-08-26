# 백엔드 가이드

## 기술 맥락

- backend는 Java(21) + Spring boot(4.0.6) 기반 서비스다.
- Spring MVC / Controller /Service / Repository 경계를 명확히 유지한다.
- 비즈니스 의미와 상태 판단은 transport layer가 아니라 service layer에서 처리한다.
- 구조는 기능 도메인을 최상위 패키지로 두고, 각 도메인 안에 `controller`, `service`, `repository`, `domain` 경계를 두는 기능 중심 구조를 따른다.

## 구조 원칙

- 패키지는 `account`, `auth`, `event`, `recurrence`, `task`, `tag`, `holiday` 같은 기능 도메인 기준으로 나눈다.
- 각 기능 도메인 안에서 필요에 따라 `controller`, `service`, `repository`, `domain`, `client`, `scheduler` 하위 패키지를 사용한다.
- request / response DTO는 transport 경계 모델이므로 해당 도메인의 `controller/dto` 아래에 둔다.
- 외부 API 요청·응답 DTO는 해당 도메인의 `client/dto` 아래에 둔다.
- persistence model을 포함한 도메인 모델과 닫힌 enum은 해당 도메인의 `domain` 아래에 둔다.
- 공통 persistence 기반 모델은 `common/persistence` 아래에 둔다.
- 외부 연동 client와 해당 client에 전속된 properties는 기능 도메인의 `client` 아래에 둔다.
- 주기적인 job은 해당 도메인의 `scheduler` 아래에 두고, 실제 업무 흐름은 service에 위임한다.
- 기능 도메인에 속하지 않는 공통 설정, 오류, persistence 기반 기능만 `common` 아래에 둔다.
- 인증·인가 파이프라인은 `security` 패키지가 소유하며, 파일 수와 책임이 커지면 그 안에서 계층적으로 분리한다.
- controller는 transport layer로 유지하고 business logic을 넣지 않는다.
- service는 유스케이스 흐름과 비즈니스 규칙 적용의 중심이 된다.
- repository는 persistence 접근만 담당한다.
- 서로 다른 도메인이 필요 없이 내부 구현을 소유하거나 DTO를 공유하지 않도록 한다.
- 공통 로직은 무분별하게 퍼뜨리지 않고, 실제로 공통성이 확인된 경우에만 `common`으로 올린다.
- 새 레이어나 공용 패키지를 추가하기보다 기존 기능 도메인의 구조를 우선 확장한다.

## 구현 규칙

- 기존 조회/수정 흐름을 먼저 확장하고 새 endpoint 추가는 마지막 수단으로 본다.
- backend가 API 계약의 source of truth가 되도록 유지한다.
- 전역 mapper 레이어는 두지 않는다.
- request DTO는 controller 경계에서 사용한다.
- response DTO 변환은 DTO의 `static from(...)` 메서드에서 처리한다.
- DTO 변환은 단순 필드 매핑과 표현용 값 조합에 한정한다.
- service는 response DTO를 반환할 수 있다
- entity는 controller로 직접 노출하지 않는다
- 정책 판단, 외부 조회, 비즈니스 규칙은 DTO 변환에 넣지 않는다
- 닫힌 상태 집합은 silent fallback으로 넓히지 않는다.
- unsupported 상태는 명시적으로 surface한다.
- 외부 응답 계약은 additive extension을 우선한다.
- nullable contract를 불필요하게 늘리지 않는다.

## 오류 처리 규칙

- 예상 가능한 비즈니스 실패는 custom exception과 `ErrorCode` enum으로 표현한다.
- 클라이언트가 분기해야 하는 실패는 안정적인 `errorCode` 계약으로 제공한다.
- HTTP status는 `ErrorCode` 기준으로 일관되게 결정한다.
- controller는 예외를 임시로 삼키지 않고, 전역 예외 처리기에서 응답으로 변환한다.
- controller에서 개별 `try/catch`로 비즈니스 예외를 처리하지 않는다.
- 메시지는 설명용으로 제공하고, 클라이언트 분기 기준은 `errorCode`를 우선한다.
- 예상하지 못한 내부 오류는 공통 internal error 응답으로 처리하고, 내부 상세 구현을 외부 계약에 노출하지 않는다.

## 트랜잭션 규칙

- transaction boundary는 service layer에서 관리한다.
- 하나의 유스케이스는 가능한 한 하나의 명확한 transaction boundary 안에서 처리한다.
- 읽기/쓰기 책임이 다른 경우 이를 의식적으로 분리한다

## 로그 및 관측성 규칙

- 운영상 중요한 상태 전이와 실패는 추적 가능한 로그를 남긴다.
- 민감한 값이나 원문 사용자 입력은 로그에 직접 남기지 않는다.
- 디버깅용 로그와 운영용 로그를 혼합하지 않는다.

## Spring 규칙

- 의존성 주입은 생성자 주입을 우선한다.
- configuration, client, persistence 책임을 혼합하지 않는다.
- request validation은 transport boundary에서 처리하고, 정책 판단과 섞지 않는다.

## 검증 규칙

- request validation은 controller에서 request DTO를 받을 때 처리한다.
- 형식 검증, 필수값 검증, 기본적인 입력 제약은 request DTO와 controller 경계에서 처리한다.
- 비즈니스 규칙 판단은 service에서 처리한다.

## API / 계약 규칙

- enum 값, 상태 값, 에러 코드는 임의로 변경하지 않는다.
- not-found, validation failure, unsupported-state 같은 경계 동작은 일관되게 유지한다.
- 기존 클라이언트를 깨는 rename/remove보다 additive field 추가를 우선한다.
- 응답 구조와 필드 의미는 backend가 일관되게 관리한다

## AI Agent Prompt / Tool 규칙

- system prompt는 제품 역할, 사용자 노출 규칙, 권한과 승인 경계처럼 모든 tool에 공통인 정책만 간결하게 소유한다.
- tool 설명과 입력 schema는 해당 tool의 호출 조건, 필요한 입력, 반환 의미, 부작용을 구체적으로 설명한다.
- 대화 이력은 USER와 ASSISTANT 역할을 보존한 `Message` 목록으로 provider에 전달하며, 역할을 문자열로 합쳐 하나의 user message로 만들지 않는다.
- assistant response block 같은 내부 상태는 별도의 내부 대화 맥락으로만 전달하고 사용자 응답에 노출하지 않는다.
- prompt 또는 tool 변경은 한 그룹씩 적용하고, 동일한 eval과 별도 holdout eval로 일반화 여부를 확인한다.

## 테스트 기대치

- 테스트는 `unit test`와 `integration test` 두 종류로 구분한다.
- 테스트 패키지는 검증 대상 production 패키지를 따르고, 기능 도메인과 레이어 경계를 동일하게 유지한다.
- package-private 테스트 지점을 사용하기 위해 production 가시성을 넓히지 않고, 테스트를 검증 대상과 같은 패키지에 둔다.
- unit test는 핵심 비즈니스 규칙과 정책 로직을 빠르게 검증하는 데 사용한다.
- integration test는 controller, service, repository, validation, 예외 처리, 응답 계약이 함께 동작하는 흐름을 검증하는 데
사용한다.
- API 계약 변경에는 integration test를 추가한다.
- 핵심 비즈니스 규칙과 회귀 위험이 큰 로직은 unit test로 고정한다.
- mocking은 경계 분리와 테스트 목적이 분명할 때만 사용한다.
- 과도한 mocking으로 구현 세부에 강하게 결합되는 테스트는 피한다
- not-found, validation failure, conflict, unsupported-state 같은 경계 케이스는 integration test 또는 적절한 테스트로 명시적으로 검증한다.
- 테스트는 기대 동작과 실패 동작이 읽히는 형태로 작성한다.
- 테스트는 기능 명세처럼 읽히도록 작성하고, Java/Spring 테스트는 `@DisplayName`으로 기대 동작을 명확히 드러낸다.
- 테스트 본문은 필요한 경우 `// given`, `// when`, `// then` 주석으로 준비, 실행, 검증 구간을 구분한다.
- Spring integration test는 운영 DB나 실제 외부 secret에 의존하지 않고, test profile, `src/test/resources`, embedded DB, testcontainer 중 하나로 독립 실행 가능해야 한다.

## 환경 변수 / Verify 규칙

- 실제 secret 값은 git에 커밋하지 않고, repo에는 필요한 환경 변수 목록을 설명하는 `.env.example`만 둔다.
- unit test와 기본 integration test는 실제 secret 없이 실행 가능해야 한다.
- 실제 env 값이 필요한 검증은 verify 단계에서만 pipeline/project secret으로 런타임 주입한다.
- domain별 verify command가 필요한 경우 대상 repo의 `.orchestrator/domain-map.json`에 `workdir`와 `verifyCommand`를 명시한다.


## 코드 구성 원칙

- 함수는 하나의 역할만 수행하도록 작성한다.
- 함수는 가능한 한 짧고 읽기 쉽게 유지하며, 기본적으로 15줄 내외를 목표로 한다.
- 깊은 들여쓰기와 중첩 조건문을 피하고, guard clause와 early return을 우선 사용한다.
- 조건 분기가 복잡해지면 의미 있는 보조 함수나 predicate로 분리한다.
- 분리된 함수 이름만 읽어도 상위 흐름이 이해되도록 작성한다.
- helper 함수는 단순 줄 수 감소가 아니라 책임 분리를 위해서만 도입한다.

## 가독성 원칙

- 코드는 가능한 한 선언적으로 읽히도록 작성한다.
- 무엇을 판단하는지, 무엇을 반환하는지, 어떤 조건에서 중단하는지가 구조만 봐도 드러나야 한다.
- 컬렉션 변환, 필터링, 정렬은 읽기 쉬운 경우에 한해 선언형 표현을 우선한다.
- 선언형 표현이 오히려 이해를 어렵게 만들면 단순한 명령형 코드를 사용한다.
- 여러 단계의 조건 분기보다 이름 있는 조건과 작은 함수 조합을 우선한다.
- 들여쓰기 레벨이 깊어지기 시작하면 구조를 다시 나누는 것을 우선 검토한다.
- 한 함수 안에서 3단계 이상 중첩이 생기면 구조 분리를 우선 검토한다.
- `if/else` 사슬보다 빠른 실패 반환과 명시적 분기를 우선한다.
- boolean 조건은 인라인 복합식보다 의미 있는 이름으로 드러낸다.

## 네이밍 규칙

- 함수명과 필드명은 역할과 의미가 이름만으로 직관적으로 드러나야 한다.
- 추상적인 이름보다 구체적인 동사와 대상을 사용한다.
- `process`, `handle`, `execute`, `data`, `flag`, `manager`, `helper`, `util` 같은 포괄적 이름은 특별한 이유가 없으면 피한다.
- boolean 필드는 `is`, `has`, `can` 접두를 사용해 의미가 드러나게 작성한다.
- 컬렉션 이름은 복수형으로 작성하고, 타입명보다 의미를 우선한다.
- DTO, 응답 모델, 예외 코드 이름은 사용 맥락과 역할이 바로 드러나야 한다.
- exact 의미가 중요한 값은 축약보다 명확성을 우선한다.
