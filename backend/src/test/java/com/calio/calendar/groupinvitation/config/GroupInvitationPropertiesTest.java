package com.calio.calendar.groupinvitation.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupInvitationPropertiesTest {

    @Test
    @DisplayName("cleanup은 한 번의 실행에서 최대 1000개 batch까지 허용한다")
    void acceptsMaximumCleanupBatchesPerRun() {
        // given
        GroupInvitationProperties properties = new GroupInvitationProperties();
        properties.setCleanupMaxBatchesPerRun(1000);

        // when, then
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("cleanup은 한 번의 실행에서 1000개를 초과하는 batch 설정을 거부한다")
    void rejectsCleanupBatchesPerRunAboveMaximum() {
        // given
        GroupInvitationProperties properties = new GroupInvitationProperties();
        properties.setCleanupMaxBatchesPerRun(1001);

        // when, then
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid group invitation configuration.");
    }
}
