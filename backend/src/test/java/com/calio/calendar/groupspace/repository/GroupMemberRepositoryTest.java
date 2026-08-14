package com.calio.calendar.groupspace.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:group-member-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class GroupMemberRepositoryTest {

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private GroupSpaceRepository groupSpaceRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("그룹 목록 membership 조회는 GroupSpace를 함께 조회하고 동률이면 최신 그룹부터 정렬한다")
    void listMembershipsLoadsGroupSpaceAndPreservesTieBreakOrder() {
        // given
        Instant now = Instant.parse("2026-08-12T00:00:00Z");
        GroupSpace firstGroup = groupSpaceRepository.saveAndFlush(
                new GroupSpace(1L, "first", null)
        );
        GroupSpace secondGroup = groupSpaceRepository.saveAndFlush(
                new GroupSpace(1L, "second", null)
        );
        groupMemberRepository.saveAllAndFlush(List.of(
                new GroupMember(firstGroup, 1L, "first", now),
                new GroupMember(secondGroup, 1L, "second", now)
        ));
        entityManager.clear();

        // when
        List<GroupMember> memberships = groupMemberRepository
                .findByAccountIdAndStatusOrderByStatusChangedAtDescGroupSpaceIdDesc(
                        1L,
                        GroupMemberStatus.ACTIVE
                );

        // then
        assertThat(memberships)
                .extracting(member -> member.getGroupSpace().getId())
                .containsExactly(secondGroup.getId(), firstGroup.getId());
        assertThat(memberships).allMatch(member -> entityManagerFactory
                .getPersistenceUnitUtil()
                .isLoaded(member, "groupSpace"));
    }

    @Test
    @DisplayName("활성 membership 단건 조회는 GroupSpace를 함께 조회한다")
    void getActiveMembershipLoadsGroupSpace() {
        // given
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(1L, "group", null)
        );
        groupMemberRepository.saveAndFlush(new GroupMember(
                groupSpace,
                1L,
                "owner",
                Instant.parse("2026-08-12T00:00:00Z")
        ));
        entityManager.clear();

        // when
        GroupMember membership = groupMemberRepository
                .findByGroupSpaceIdAndAccountIdAndStatus(
                        groupSpace.getId(),
                        1L,
                        GroupMemberStatus.ACTIVE
                )
                .orElseThrow();

        // then
        assertThat(entityManagerFactory.getPersistenceUnitUtil()
                .isLoaded(membership, "groupSpace")).isTrue();
    }
}
