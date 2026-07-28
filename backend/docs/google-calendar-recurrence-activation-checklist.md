# Google Calendar recurrence activation 수동 확인

이 문서는 recurrence sync foundation의 자동 테스트 결과와 분리된 Activation 전 수동 gate다.
Foundation 배포 시점에는 아래 항목을 실행하지 않아도 되며, 기존 page sync에 recurrence 처리를 연결하지 않는다.

- 실제 Google Calendar에서 timed/all-day recurring master payload를 수집해 fixture와 필드 shape를 비교한다.
- moved exception과 cancelled minimum exception에 `recurringEventId` 및 `originalStartTime`이 제공되는지 확인한다.
- all-day/timed 양방향 cross-type exception payload를 확인한다.
- offset이 있는 `dateTime`과 offsetless `dateTime`에서 `timeZone` 제공 형태를 확인한다.
- DST gap/overlap에 해당하는 payload의 offset 및 IANA timezone 조합을 확인한다.
- 1024자 범위의 opaque event ID와 `etag`가 list/get 응답에서 손실 없이 유지되는지 확인한다.
- configured `primary` collection의 parent 단건 조회에서 200, 404, 401, 403 및 rate-limit 응답을 확인한다.
- 수동 확인에 사용한 calendar, payload 유형과 수행 일시를 기록하되 access/refresh token과 원문 사용자 입력은 기록하지 않는다.

현재 foundation 구현 단계의 실제 Google credential characterization 상태: 미수행.
