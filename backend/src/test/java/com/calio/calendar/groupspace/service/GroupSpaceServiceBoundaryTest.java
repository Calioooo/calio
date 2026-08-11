package com.calio.calendar.groupspace.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

class GroupSpaceServiceBoundaryTest {

    @Test
    @DisplayName("GroupSpace와 GroupMembership QueryService는 읽기 전용 트랜잭션을 사용한다")
    void queryServicesUseReadOnlyTransactions() {
        assertThat(List.of(GroupSpaceQueryService.class, GroupMembershipQueryService.class))
                .allSatisfy(serviceType -> {
                    Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                            serviceType,
                            Transactional.class
                    );
                    assertThat(transactional).isNotNull();
                    assertThat(transactional.readOnly()).isTrue();
                });
    }

    @Test
    @DisplayName("GroupSpace와 GroupMembership CommandService는 쓰기 트랜잭션을 사용한다")
    void commandServicesUseWriteTransactions() {
        assertThat(List.of(GroupSpaceCommandService.class, GroupMembershipCommandService.class))
                .allSatisfy(serviceType -> {
                    Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                            serviceType,
                            Transactional.class
                    );
                    assertThat(transactional).isNotNull();
                    assertThat(transactional.readOnly()).isFalse();
                });
    }
}
