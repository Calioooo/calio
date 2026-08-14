package com.calio.calendar.groupspace.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupinvitation.service.GroupInvitationCommandService;
import com.calio.calendar.groupinvitation.service.GroupInvitationQueryService;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupMembershipServiceBoundaryTest {

    @Test
    @DisplayName("GroupMembershipService는 초대 repository 대신 QueryService와 CommandService에 위임한다")
    void delegatesInvitationPersistenceToInvitationServices() {
        // when
        var dependencyTypes = Arrays.stream(GroupMembershipService.class.getDeclaredFields())
                .map(Field::getType)
                .toList();

        // then
        assertThat(dependencyTypes)
                .contains(GroupInvitationQueryService.class, GroupInvitationCommandService.class)
                .doesNotContain(GroupInvitationRepository.class);
    }
}
