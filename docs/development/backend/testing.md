# 백엔드 테스트 지침

## 목적

이 문서는 backend 테스트의 작성 방식과 테스트 환경 기준을 정한다.
테스트의 공통 원칙은 repository root의 `AGENTS.md`를 따른다.

## 테스트 범위

- 테스트는 unit test와 integration test로 구분한다. Unit test는 핵심 비즈니스 규칙과 정책을, integration test는 Controller·Service·Repository·입력 검증·예외 처리·응답 계약이 함께 동작하는 흐름을 검증한다.
- API 계약 변경에는 integration test를 추가하고 핵심 비즈니스 규칙과 회귀 위험이 큰 로직은 unit test로 고정한다.
- Not-found, validation failure, conflict와 unsupported-state 같은 경계 동작을 명시적으로 검증한다.

## 테스트 작성

- 테스트 패키지는 검증 대상 production 패키지를 따른다. 테스트를 위해 production 가시성을 넓히지 않고 package-private 접근이 필요하면 같은 패키지에 둔다.
- Mocking은 경계 분리와 테스트 목적이 명확할 때 사용하고 구현 세부에 과도하게 결합되지 않도록 한다.
- Java/Spring 테스트는 `@DisplayName`으로 기대 동작을 설명한다. 필요한 경우 `// given`, `// when`, `// then`으로 준비·실행·검증을 구분한다.

## 테스트 환경

- Unit test와 기본 integration test는 실제 secret 없이 실행 가능해야 한다.
- Integration test는 운영 DB나 실제 외부 secret에 의존하지 않고 test profile, 테스트 리소스와 독립적인 테스트 DB 등을 사용한다.
