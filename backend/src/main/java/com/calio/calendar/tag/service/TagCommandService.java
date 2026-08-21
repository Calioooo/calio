package com.calio.calendar.tag.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import com.calio.calendar.groupspace.domain.GroupSpace;
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

    public Tag createGroupDefaultTag(GroupSpace groupSpace) {
        return tagRepository.save(new Tag(TagType.GROUP_DEFAULT, "기타", "#64748B", groupSpace));
    }

    public Tag createGroupCustomTag(GroupSpace groupSpace, String title, String colorCode) {
        return tagRepository.save(new Tag(TagType.CUSTOM, title, colorCode, groupSpace));
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
