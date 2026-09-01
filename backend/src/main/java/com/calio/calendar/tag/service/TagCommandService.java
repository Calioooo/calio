package com.calio.calendar.tag.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.tag.domain.Tag;
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
        return tagRepository.save(Tag.personalCustom(account, title, colorCode));
    }

    public Tag createGroupDefaultTag(GroupSpace groupSpace) {
        return tagRepository.save(Tag.groupDefault(groupSpace));
    }

    public Tag createGroupCustomTag(GroupSpace groupSpace, String title, String colorCode) {
        return tagRepository.save(Tag.groupCustom(groupSpace, title, colorCode));
    }

    public Tag updateCustomTag(Tag tag, String title, String colorCode) {
        tag.update(title, colorCode);
        tagRepository.flush();
        return tag;
    }

    public void deleteTag(Tag tag) {
        tagRepository.delete(tag);
    }

    public void deleteGroupTags(Long groupSpaceId) {
        tagRepository.deleteAll(tagRepository.findByGroupSpace_Id(groupSpaceId));
    }
}
