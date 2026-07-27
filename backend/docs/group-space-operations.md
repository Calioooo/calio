# Group Space 운영 인계

이번 backend 변경은 application 내부 계약만 구현하며 ingress, APM, trace exporter와
분산 rate limiter 설정은 변경하지 않는다.

release 전 다음 항목을 운영 환경에서 확인해야 한다.

- ingress와 access log가 `/invite/{token}`의 token path segment를 기록하지 않거나 masking한다.
- APM과 trace가 invitation preview/accept request의 `credential` 및 invitation 발급 response의
  `inviteUrl`, `inviteCode`를 수집하지 않는다.
- rate limiting이 운영 계층에 존재하면 invitation 발급, preview와 accept 경로에 적용되는지 확인한다.
- production profile의 `GROUP_SPACE_INVITATION_BASE_URL`은 absolute HTTPS URI이며 query와
  fragment를 포함하지 않는다.

현재 group schedule aggregate는 존재하지 않는다. 후속 aggregate는 일반 member 탈퇴 또는
강퇴 시 해당 creator의 group 일정을, group 삭제 시 해당 group의 모든 일정을 현재
membership/group lifecycle과 같은 transaction에서 hard-delete해야 한다. 개인 `Event`와
`RecurrenceEvent`는 이 cleanup 대상이 아니다.
