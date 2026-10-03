package com.calio.calendar.groupspace.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.groupspace.controller.dto.CreateGroupSpaceRequest;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.groupspace.service.GroupScheduleShareCleanupPort;
import com.calio.calendar.tag.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:group-space-usecase-test;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
@Import(GroupSpaceUseCaseTest.ScheduleShareCleanupTestConfig.class)
class GroupSpaceUseCaseTest {

  @Autowired private CreateGroupSpaceUseCase createGroupSpaceUseCase;

  @Autowired private DeleteGroupSpaceUseCase deleteGroupSpaceUseCase;

  @Autowired private GroupSpaceRepository groupSpaceRepository;

  @Autowired private GroupMemberRepository groupMemberRepository;

  @Autowired private TagRepository tagRepository;

  @Autowired private AccountRepository accountRepository;

  @Autowired private GroupScheduleShareCleanupPort scheduleShareCleanupPort;

  private Account account;

  @BeforeEach
  void setUp() {
    reset(scheduleShareCleanupPort);
    groupMemberRepository.deleteAll();
    tagRepository.deleteAll();
    groupSpaceRepository.deleteAll();
    account = accountRepository.saveAndFlush(new Account());
  }

  @Test
  @DisplayName("하나의 Account는 여러 Group Space의 OWNER가 될 수 있다")
  void sameAccountCanOwnMultipleGroupSpaces() {
    var first =
        createGroupSpaceUseCase.create(
            account.getId(), new CreateGroupSpaceRequest("First", null, "first"));
    var second =
        createGroupSpaceUseCase.create(
            account.getId(), new CreateGroupSpaceRequest("Second", null, "second"));

    assertThat(first.groupSpaceId()).isNotEqualTo(second.groupSpaceId());
    assertThat(groupSpaceRepository.findAll())
        .allMatch(groupSpace -> groupSpace.getOwnerAccountId().equals(account.getId()));
    assertThat(groupMemberRepository.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("공유 매핑 정리 실패는 membership과 Group Space 삭제 전체를 rollback한다")
  void cleanupFailureRollsBackDelete() {
    var created =
        createGroupSpaceUseCase.create(
            account.getId(), new CreateGroupSpaceRequest("Rollback", null, "owner"));
    doThrow(new IllegalStateException("simulated cleanup failure"))
        .when(scheduleShareCleanupPort)
        .cleanupGroupShares(created.groupSpaceId());

    assertThatThrownBy(
            () -> deleteGroupSpaceUseCase.delete(account.getId(), created.groupSpaceId()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(groupSpaceRepository.existsById(created.groupSpaceId())).isTrue();
    assertThat(groupMemberRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("OWNER delete는 membership을 먼저 삭제하고 Group Space를 hard-delete한다")
  void ownerDeleteRemovesMembershipAndGroupSpace() {
    var created =
        createGroupSpaceUseCase.create(
            account.getId(), new CreateGroupSpaceRequest("Delete", null, "owner"));

    deleteGroupSpaceUseCase.delete(account.getId(), created.groupSpaceId());

    assertThat(groupMemberRepository.count()).isZero();
    assertThat(groupSpaceRepository.existsById(created.groupSpaceId())).isFalse();
  }

  @TestConfiguration
  static class ScheduleShareCleanupTestConfig {

    @Bean
    @Primary
    GroupScheduleShareCleanupPort groupScheduleShareCleanupPort() {
      return mock(GroupScheduleShareCleanupPort.class);
    }
  }
}
