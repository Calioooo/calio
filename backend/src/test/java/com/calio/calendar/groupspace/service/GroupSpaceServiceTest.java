package com.calio.calendar.groupspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.groupinvitation.service.GroupInvitationService;
import com.calio.calendar.groupspace.controller.dto.CreateGroupSpaceRequest;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:group-space-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(GroupSpaceServiceTest.InvitationServiceTestConfig.class)
class GroupSpaceServiceTest {

    @Autowired
    private GroupSpaceService groupSpaceService;

    @Autowired
    private GroupSpaceRepository groupSpaceRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private GroupInvitationService invitationService;

    private Account account;

    @BeforeEach
    void setUp() {
        reset(invitationService);
        groupMemberRepository.deleteAll();
        groupSpaceRepository.deleteAll();
        account = accountRepository.saveAndFlush(new Account());
    }

    @Test
    @DisplayName("하나의 Account는 여러 Group Space의 OWNER가 될 수 있다")
    void sameAccountCanOwnMultipleGroupSpaces() {
        // when
        var first = groupSpaceService.create(
                account.getId(),
                new CreateGroupSpaceRequest("First", null, "first")
        );
        var second = groupSpaceService.create(
                account.getId(),
                new CreateGroupSpaceRequest("Second", null, "second")
        );

        // then
        assertThat(first.groupSpaceId()).isNotEqualTo(second.groupSpaceId());
        assertThat(groupSpaceRepository.findAll())
                .allMatch(groupSpace -> groupSpace.getOwnerAccountId().equals(account.getId()));
        assertThat(groupMemberRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("dependent cleanup 실패는 membership과 Group Space 삭제 전체를 rollback한다")
    void cleanupFailureRollsBackDelete() {
        // given
        var created = groupSpaceService.create(
                account.getId(),
                new CreateGroupSpaceRequest("Rollback", null, "owner")
        );
        doThrow(new IllegalStateException("simulated cleanup failure"))
                .when(invitationService)
                .deleteAllByGroupSpaceId(created.groupSpaceId());

        // when, then
        assertThatThrownBy(() -> groupSpaceService.delete(account.getId(), created.groupSpaceId()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(groupSpaceRepository.existsById(created.groupSpaceId())).isTrue();
        assertThat(groupMemberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("OWNER delete는 membership을 먼저 삭제하고 Group Space를 hard-delete한다")
    void ownerDeleteRemovesMembershipAndGroupSpace() {
        // given
        var created = groupSpaceService.create(
                account.getId(),
                new CreateGroupSpaceRequest("Delete", null, "owner")
        );

        // when
        groupSpaceService.delete(account.getId(), created.groupSpaceId());

        // then
        assertThat(groupMemberRepository.count()).isZero();
        assertThat(groupSpaceRepository.existsById(created.groupSpaceId())).isFalse();
    }

    @TestConfiguration
    static class InvitationServiceTestConfig {

        @Bean
        @Primary
        GroupInvitationService groupInvitationService() {
            return mock(GroupInvitationService.class);
        }
    }
}
