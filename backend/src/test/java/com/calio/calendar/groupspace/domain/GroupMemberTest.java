package com.calio.calendar.groupspace.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Id;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupMemberTest {

    @Test
    @DisplayName("GroupMember는 자체 JPA 식별자를 선언한다")
    void declaresJpaIdentifier() throws NoSuchFieldException {
        // when, then
        assertThat(GroupMember.class.getDeclaredField("id").isAnnotationPresent(Id.class)).isTrue();
    }

    @Test
    @DisplayName("재가입은 기존 행의 생성 시각을 보존하고 상태 변경 시각과 nickname만 갱신한다")
    void reactivationPreservesCreatedAtAndUpdatesLifecycleFields() {
        // given
        Instant joinedAt = Instant.parse("2026-07-29T08:00:00.123456789Z");
        GroupMember member = new GroupMember(
                new GroupSpace(1L, "가족", null),
                1L,
                "before",
                joinedAt
        );
        member.deactivate(GroupMemberStatus.LEFT, joinedAt.plusSeconds(1));

        // when
        member.reactivate("after", joinedAt.plusSeconds(2));

        // then
        assertThat(member.getStatus()).isEqualTo(GroupMemberStatus.ACTIVE);
        assertThat(member.getNickname()).isEqualTo("after");
        assertThat(member.getCreatedAt()).isEqualTo(Instant.parse("2026-07-29T08:00:00.123456Z"));
        assertThat(member.getStatusChangedAt()).isEqualTo(Instant.parse("2026-07-29T08:00:02.123456Z"));
    }
}
