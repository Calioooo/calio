package com.calio.calendar.tag.usecase;

import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.tag.controller.dto.TagResponse;
import com.calio.calendar.tag.repository.TagRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListGroupTagsUseCase {

  private final GroupMembershipQueryService groupMembershipQueryService;
  private final TagRepository tagRepository;

  public ListGroupTagsUseCase(
      GroupMembershipQueryService groupMembershipQueryService, TagRepository tagRepository) {
    this.groupMembershipQueryService = groupMembershipQueryService;
    this.tagRepository = tagRepository;
  }

  @Transactional(readOnly = true)
  public List<TagResponse> list(Long accountId, Long groupSpaceId) {
    groupMembershipQueryService.getActiveMembership(groupSpaceId, accountId);
    return tagRepository.findByGroupSpace_IdOrderByIdAsc(groupSpaceId).stream()
        .map(TagResponse::from)
        .toList();
  }
}
