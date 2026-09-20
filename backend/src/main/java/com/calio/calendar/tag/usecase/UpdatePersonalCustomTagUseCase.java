package com.calio.calendar.tag.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.tag.controller.dto.TagResponse;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdatePersonalCustomTagUseCase {

  private final TagRepository tagRepository;

  public UpdatePersonalCustomTagUseCase(TagRepository tagRepository) {
    this.tagRepository = tagRepository;
  }

  @Transactional
  public TagResponse update(Long accountId, Long tagId, String title, String colorCode) {
    Tag tag =
        tagRepository
            .findPersonalCustomTagById(accountId, tagId)
            .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
    tag.update(title, colorCode);
    return TagResponse.from(tag);
  }
}
