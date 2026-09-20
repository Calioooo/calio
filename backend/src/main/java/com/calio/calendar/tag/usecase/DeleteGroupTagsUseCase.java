package com.calio.calendar.tag.usecase;

import com.calio.calendar.tag.repository.TagRepository;
import org.springframework.stereotype.Service;

@Service
public class DeleteGroupTagsUseCase {

  private final TagRepository tagRepository;

  public DeleteGroupTagsUseCase(TagRepository tagRepository) {
    this.tagRepository = tagRepository;
  }

  public void deleteAll(Long groupSpaceId) {
    tagRepository.deleteAll(tagRepository.findByGroupSpaceId(groupSpaceId));
  }
}
