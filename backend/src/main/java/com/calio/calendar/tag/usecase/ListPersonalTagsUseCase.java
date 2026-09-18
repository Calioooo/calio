package com.calio.calendar.tag.usecase;

import com.calio.calendar.tag.controller.dto.TagResponse;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListPersonalTagsUseCase {

  private final TagRepository tagRepository;

  public ListPersonalTagsUseCase(TagRepository tagRepository) {
    this.tagRepository = tagRepository;
  }

  @Transactional(readOnly = true)
  public List<TagResponse> list(Long accountId) {
    return Stream.concat(
            tagRepository
                .findByTagTypeAndAccountIsNullAndGroupSpaceIsNullOrderByIdAsc(
                    TagType.PERSONAL_DEFAULT)
                .stream(),
            tagRepository
                .findByTagTypeAndAccount_IdOrderByIdAsc(TagType.CUSTOM, accountId)
                .stream())
        .sorted(Comparator.comparing(Tag::getId))
        .map(TagResponse::from)
        .toList();
  }
}
