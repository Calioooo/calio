package com.calio.calendar.groupinvitation.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

class GroupInvitationQueryServiceTest {

    @Test
    @DisplayName("QueryService의 모든 조회는 readOnly 트랜잭션 경계 안에서 실행한다")
    void queryServiceUsesReadOnlyTransactionBoundary() {
        // when
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                GroupInvitationQueryService.class,
                Transactional.class
        );

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}
