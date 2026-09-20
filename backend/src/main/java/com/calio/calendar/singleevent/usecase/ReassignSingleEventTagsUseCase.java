package com.calio.calendar.singleevent.usecase;

import com.calio.calendar.singleevent.repository.SingleEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReassignSingleEventTagsUseCase {

  private final SingleEventRepository eventRepository;

  public ReassignSingleEventTagsUseCase(SingleEventRepository eventRepository) {
    this.eventRepository = eventRepository;
  }

  @Transactional
  public void reassign(Long accountId, Long sourceTagId, Long fallbackTagId) {
    eventRepository.reassignAllByTagAndAccountId(sourceTagId, fallbackTagId, accountId);
  }
}
