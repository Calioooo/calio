package com.calio.calendar.tag.usecase;

import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import org.springframework.stereotype.Service;

@Service
public class CreateGroupDefaultTagUseCase {

  private final TagRepository tagRepository;

  public CreateGroupDefaultTagUseCase(TagRepository tagRepository) {
    this.tagRepository = tagRepository;
  }

  public Tag create(GroupSpace groupSpace) {
    return tagRepository.save(Tag.groupDefault(groupSpace));
  }
}
