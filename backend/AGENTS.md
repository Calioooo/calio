# 백엔드 개발 지침

## 기술 맥락

- Backend는 Java 21과 Spring Boot 기반 서비스다.
- 프로젝트 공통 계약, 도메인 용어와 테스트 원칙은 Root `AGENTS.md`를 따른다.

## 상세 개발 지침

Backend 코드를 변경하기 전에 변경과 관련된 문서를 확인한다. 아래 경로는 repository root를 기준으로 한다.

- 클래스·메서드 작성, 객체 생성, DTO, 입력 검증, 예외, 컬렉션, Spring 구성과 로그: `docs/development/backend/conventions.md`
- 계층 구성, Repository, JPA 매핑, 트랜잭션과 외부 연동: `docs/development/backend/architecture.md`
- 비즈니스 판단, 상태 변경, Entity·Value Object·Aggregate와 Domain Service: `docs/development/backend/domain-responsibility.md`
- 동작·API 계약 변경, 테스트 작성·수정과 테스트 환경 구성: `docs/development/backend/testing.md`
- Calio의 AI 대화, Prompt 또는 Tool 기능 변경: `docs/development/backend/ai-feature.md`

여러 범위에 해당하면 관련 문서를 모두 확인한다. 기존 코드가 지침과 다르면 차이와 영향 범위를 먼저 설명한다.

## 환경 변수와 검증

- Backend 애플리케이션이 요구하는 환경 변수와 예제값은 `.env.example`에 정리하고, 비밀값은 자리표시자로 표현한다.
- 실제 비밀값은 커밋하지 않는다.
- 실제 환경값이 필요한 검증은 기본 테스트와 구분하고, 필요한 secret은 실행 시점에 안전하게 주입한다.

## 완료 전 확인

- 최종 변경 내용을 검토하고 적용 가능한 테스트와 정적 검증을 실행한다.
- 실행하지 못한 검증과 남은 위험을 설명한다.
