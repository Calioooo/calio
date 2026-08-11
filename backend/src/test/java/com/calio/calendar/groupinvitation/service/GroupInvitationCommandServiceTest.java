package com.calio.calendar.groupinvitation.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

class GroupInvitationCommandServiceTest {

    @Test
    @DisplayName("CommandService의 모든 상태 변경은 트랜잭션 경계 안에서 실행한다")
    void commandServiceUsesTransactionBoundary() {
        // when
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                GroupInvitationCommandService.class,
                Transactional.class
        );

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }
}
