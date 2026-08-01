package com.calio.calendar.groupspace.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.common.domain.BaseEntity;
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
    @DisplayName("재가입은 BaseEntity timestamp와 독립적으로 상태 변경 시각과 nickname을 갱신한다")
    void reactivationUpdatesLifecycleFields() {
        // given
        Instant joinedAt = Instant.parse("2026-07-29T08:00:00.123456789Z");
        GroupMember member = new GroupMember(
                new GroupSpace(1L, "가족", null),
                1L,
                "before",
                joinedAt
        );
        Instant initialStatusChangedAt = member.getStatusChangedAt();
        Instant leftAt = joinedAt.plusSeconds(1);
        member.deactivate(GroupMemberStatus.LEFT, leftAt);

        assertThat(member.getStatusChangedAt()).isEqualTo(Instant.parse("2026-07-29T08:00:01.123456Z"));
        assertThat(member.getStatusChangedAt()).isNotEqualTo(initialStatusChangedAt);

        // when
        member.reactivate("after", joinedAt.plusSeconds(2));

        // then
        assertThat(member.getStatus()).isEqualTo(GroupMemberStatus.ACTIVE);
        assertThat(member.getNickname()).isEqualTo("after");
        assertThat(member.getStatusChangedAt()).isEqualTo(Instant.parse("2026-07-29T08:00:02.123456Z"));
        assertThat(GroupMember.class.getSuperclass()).isEqualTo(BaseEntity.class);
    }
}
