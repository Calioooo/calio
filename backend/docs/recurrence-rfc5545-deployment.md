# RFC 5545 recurrence 배포 조건

`V5__rfc5545_recurrence.sql`은 legacy frequency row를 자동 변환하지 않는다. migration을 적용하기 전에
`recurrence_event_overrides`와 `recurrence_events`의 기존 row를 수동으로 정리해야 한다.

새 요청 계약은 `recurrenceFrequency`를 제거하고 RFC content line 목록인 `recurrence`를 사용한다.
`EventResponse`에는 `allDay`가 추가된다. 이 변경은 새 계약을 지원하는 frontend와 함께 출시하며 backend를
먼저 독립 배포하지 않는다.

PR에서는 기존 backend CI의 `./gradlew test`를 유지한다. 이 명령에 포함된
`RecurrenceMigrationTest`가 빈 legacy recurrence table에 Flyway schema가 적용되는 경로를 검증한다.
