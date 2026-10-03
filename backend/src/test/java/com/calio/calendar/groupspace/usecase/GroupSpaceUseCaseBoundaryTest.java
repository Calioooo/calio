package com.calio.calendar.groupspace.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class GroupSpaceUseCaseBoundaryTest {

  @Test
  @DisplayName("Group Space 조회 유스케이스는 읽기 전용 트랜잭션을 사용한다")
  void readUseCasesUseReadOnlyTransactions() {
    assertTransactionReadOnly(
        List.of(
            ListMyGroupSpacesUseCase.class,
            GetGroupSpaceUseCase.class,
            ListGroupMembersUseCase.class));
  }

  @Test
  @DisplayName("Group Space 변경 유스케이스는 쓰기 트랜잭션을 사용한다")
  void writeUseCasesUseWriteTransactions() {
    assertTransactionWritable(
        List.of(
            CreateGroupSpaceUseCase.class,
            UpdateGroupSpaceUseCase.class,
            DeleteGroupSpaceUseCase.class,
            ChangeMemberAnonymousSharingUseCase.class,
            TransferGroupOwnershipUseCase.class,
            LeaveGroupSpaceUseCase.class,
            KickGroupMemberUseCase.class));
  }

  private void assertTransactionReadOnly(List<Class<?>> useCaseTypes) {
    useCaseTypes.forEach(
        useCaseType -> {
          Transactional transactional = transactionalMethod(useCaseType);
          assertThat(transactional.readOnly()).isTrue();
        });
  }

  private void assertTransactionWritable(List<Class<?>> useCaseTypes) {
    useCaseTypes.forEach(
        useCaseType -> {
          Transactional transactional = transactionalMethod(useCaseType);
          assertThat(transactional.readOnly()).isFalse();
        });
  }

  private Transactional transactionalMethod(Class<?> useCaseType) {
    Transactional transactional =
        java.util.Arrays.stream(useCaseType.getDeclaredMethods())
            .map(method -> method.getAnnotation(Transactional.class))
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .orElse(null);
    assertThat(transactional).as("%s의 유스케이스 메서드", useCaseType.getSimpleName()).isNotNull();
    return transactional;
  }
}
