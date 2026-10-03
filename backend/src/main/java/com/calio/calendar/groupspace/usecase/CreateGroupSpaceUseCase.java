package com.calio.calendar.groupspace.usecase;

import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.controller.dto.CreateGroupSpaceRequest;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceDetailResponse;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberNickname;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.domain.GroupSpaceEmoji;
import com.calio.calendar.groupspace.domain.GroupSpaceName;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateGroupSpaceUseCase {

  private final AccountRepository accountRepository;
  private final GroupSpaceRepository groupSpaceRepository;
  private final GroupMemberRepository groupMemberRepository;
  private final TagRepository tagRepository;
  private final Clock clock;

  public CreateGroupSpaceUseCase(
      AccountRepository accountRepository,
      GroupSpaceRepository groupSpaceRepository,
      GroupMemberRepository groupMemberRepository,
      TagRepository tagRepository,
      Clock clock) {
    this.accountRepository = accountRepository;
    this.groupSpaceRepository = groupSpaceRepository;
    this.groupMemberRepository = groupMemberRepository;
    this.tagRepository = tagRepository;
    this.clock = clock;
  }

  @Transactional
  public GroupSpaceDetailResponse create(Long accountId, CreateGroupSpaceRequest request) {
    if (!accountRepository.existsById(accountId)) {
      throw new CalioException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
    GroupSpace groupSpace =
        groupSpaceRepository.saveAndFlush(
            new GroupSpace(
                accountId,
                new GroupSpaceName(request.name()),
                GroupSpaceEmoji.fromNullable(request.emoji())));
    tagRepository.save(Tag.groupDefault(groupSpace.getId()));
    GroupMember membership =
        groupMemberRepository.saveAndFlush(
            new GroupMember(
                groupSpace.getId(),
                accountId,
                new GroupMemberNickname(request.nickname()),
                clock.instant()));
    return GroupSpaceDetailResponse.from(groupSpace, membership, 1);
  }
}
