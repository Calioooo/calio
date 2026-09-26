package com.calio.calendar.tag.usecase;

import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.tag.controller.dto.TagResponse;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreatePersonalCustomTagUseCase {

  private final AccountRepository accountRepository;
  private final TagRepository tagRepository;

  public CreatePersonalCustomTagUseCase(
      AccountRepository accountRepository, TagRepository tagRepository) {
    this.accountRepository = accountRepository;
    this.tagRepository = tagRepository;
  }

  @Transactional
  public TagResponse create(Long accountId, String title, String colorCode) {
    if (!accountRepository.existsById(accountId)) {
      throw new CalioException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
    return TagResponse.from(tagRepository.save(Tag.personalCustom(accountId, title, colorCode)));
  }
}
