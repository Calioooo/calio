package com.calio.calendar.groupspace.service;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupSpaceCommandService {

    private final GroupSpaceRepository groupSpaceRepository;
    private final GroupMemberRepository groupMemberRepository;

    public GroupSpaceCommandService(
            GroupSpaceRepository groupSpaceRepository,
            GroupMemberRepository groupMemberRepository
    ) {
        this.groupSpaceRepository = groupSpaceRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    public GroupSpace create(Long ownerAccountId, String name, String emoji) {
        return groupSpaceRepository.saveAndFlush(new GroupSpace(ownerAccountId, name, emoji));
    }

    public GroupMember createOwnerMembership(
            GroupSpace groupSpace,
            Long accountId,
            String nickname,
            Instant now
    ) {
        return groupMemberRepository.saveAndFlush(
                new GroupMember(groupSpace, accountId, nickname, now)
        );
    }

    public void update(GroupSpace groupSpace, String name, String emoji) {
        groupSpace.update(name, emoji);
        groupSpaceRepository.flush();
    }

    public void delete(GroupSpace groupSpace) {
        groupMemberRepository.deleteAllByGroupSpaceId(groupSpace.getId());
        groupSpaceRepository.delete(groupSpace);
    }
}
