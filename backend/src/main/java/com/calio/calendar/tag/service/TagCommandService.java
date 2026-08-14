package com.calio.calendar.tag.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import org.springframework.stereotype.Service;

@Service
public class TagCommandService {

    private final TagRepository tagRepository;

    public TagCommandService(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    public Tag createCustomTag(Account account, String title, String colorCode) {
        return tagRepository.save(new Tag(TagType.CUSTOM, title, colorCode, account));
    }

    public Tag updateCustomTag(Tag tag, String title, String colorCode) {
        tag.update(title, colorCode);
        tagRepository.flush();
        return tag;
    }

    public void deleteTag(Tag tag) {
        tagRepository.delete(tag);
    }
}
