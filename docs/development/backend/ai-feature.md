# AI 기능 개발 지침

## 적용 범위

이 문서는 Calio의 AI 대화, Prompt 또는 Tool 기능을 변경할 때 적용한다.
개발에 사용하는 AI 에이전트나 코드 리뷰 에이전트의 작업 지침은 다루지 않는다.

## Prompt / Tool 구성

- System prompt는 제품 역할, 사용자 노출 규칙, 권한과 승인 경계처럼 모든 tool에 공통인 정책만 간결하게 소유한다.
- Tool 설명과 입력 schema는 호출 조건, 필요한 입력, 반환 의미와 부작용을 구체적으로 설명한다.

## 대화 맥락과 응답

- 대화 이력은 USER와 ASSISTANT 역할을 보존한 `Message` 목록으로 provider에 전달하며, 역할을 문자열로 합쳐 하나의 user message로 만들지 않는다.
- Assistant response block 같은 내부 상태는 별도의 내부 대화 맥락으로만 전달하고 사용자 응답에 노출하지 않는다.

## 변경 검증

- Prompt 또는 tool 변경은 한 그룹씩 적용하고 동일한 eval과 별도 holdout eval로 일반화 여부를 확인한다.
