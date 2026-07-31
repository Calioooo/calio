package com.calio.calendar.groupinvitation.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupInvitationPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("cleanup은 한 번의 실행에서 최대 1000개 batch까지 허용한다")
    void acceptsMaximumCleanupBatchesPerRun() {
        // given
        GroupInvitationProperties properties = new GroupInvitationProperties();
        properties.setCleanupMaxBatchesPerRun(1000);

        // when, then
        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    @DisplayName("cleanup은 한 번의 실행에서 1000개를 초과하는 batch 설정을 거부한다")
    void rejectsCleanupBatchesPerRunAboveMaximum() {
        // given
        GroupInvitationProperties properties = new GroupInvitationProperties();
        properties.setCleanupMaxBatchesPerRun(1001);

        // when
        Set<ConstraintViolation<GroupInvitationProperties>> violations =
                validator.validate(properties);

        // then
        assertThat(violations)
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.getPropertyPath().toString())
                            .isEqualTo("cleanupMaxBatchesPerRun");
                    assertThat(violation.getMessage())
                            .isEqualTo("group-invitation.cleanup-max-batches-per-run must not exceed 1000");
                });
    }

    @Test
    @DisplayName("ttl은 양수여야 하며 잘못된 설정 이름을 검증 오류에 포함한다")
    void rejectsNonPositiveTtlWithPropertySpecificMessage() {
        // given
        GroupInvitationProperties properties = new GroupInvitationProperties();
        properties.setTtl(Duration.ZERO);

        // when
        Set<ConstraintViolation<GroupInvitationProperties>> violations =
                validator.validate(properties);

        // then
        assertThat(violations)
                .singleElement()
                .satisfies(violation -> assertThat(violation.getMessage())
                        .isEqualTo("group-invitation.ttl must be positive"));
    }
}
