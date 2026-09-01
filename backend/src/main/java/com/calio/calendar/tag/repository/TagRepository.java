package com.calio.calendar.tag.repository;

import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, Long> {

    @Query("""
            select tag from Tag tag
            where tag.tagType = :tagType and tag.account is null and tag.groupSpace is null
            order by tag.id
            """)
    List<Tag> findByTagTypeAndAccountIsNullAndGroupSpaceIsNullOrderByIdAsc(@Param("tagType") TagType tagType);

    @Query("""
            select tag from Tag tag
            where tag.tagType = :tagType and tag.account.id = :accountId and tag.groupSpace is null
            order by tag.id
            """)
    List<Tag> findByTagTypeAndAccount_IdOrderByIdAsc(
            @Param("tagType") TagType tagType, @Param("accountId") Long accountId
    );

    @Query("""
            select tag from Tag tag
            where tag.id = :id and tag.tagType = :tagType and tag.account is null and tag.groupSpace is null
            """)
    Optional<Tag> findByIdAndTagTypeAndAccountIsNullAndGroupSpaceIsNull(
            @Param("id") Long id, @Param("tagType") TagType tagType
    );

    @Query("""
            select tag from Tag tag
            where tag.id = :id and tag.tagType = :tagType and tag.account.id = :accountId and tag.groupSpace is null
            """)
    Optional<Tag> findByIdAndTagTypeAndAccount_Id(
            @Param("id") Long id, @Param("tagType") TagType tagType, @Param("accountId") Long accountId
    );

    @Query("""
            select tag from Tag tag
            where tag.tagType = :tagType and tag.title = :title and tag.account is null and tag.groupSpace is null
            order by tag.id
            limit 1
            """)
    Optional<Tag> findFirstByTagTypeAndTitleAndAccountIsNullAndGroupSpaceIsNullOrderByIdAsc(
            @Param("tagType") TagType tagType, @Param("title") String title
    );

    @Query("select tag from Tag tag where tag.groupSpace.id = :groupSpaceId order by tag.id")
    List<Tag> findByGroupSpace_IdOrderByIdAsc(@Param("groupSpaceId") Long groupSpaceId);

    @Query("select tag from Tag tag where tag.id = :tagId and tag.groupSpace.id = :groupSpaceId")
    Optional<Tag> findByIdAndGroupSpace_Id(@Param("tagId") Long tagId, @Param("groupSpaceId") Long groupSpaceId);

    @Query("select tag from Tag tag where tag.tagType = :tagType and tag.groupSpace.id = :groupSpaceId")
    Optional<Tag> findByTagTypeAndGroupSpace_Id(
            @Param("tagType") TagType tagType, @Param("groupSpaceId") Long groupSpaceId
    );

    @Query("""
            select count(tag) > 0 from Tag tag
            where tag.tagType = :tagType and tag.title = :title and tag.groupSpace.id = :groupSpaceId and tag.id <> :tagId
            """)
    boolean existsByTagTypeAndTitleAndGroupSpace_IdAndIdNot(
            @Param("tagType") TagType tagType, @Param("title") String title,
            @Param("groupSpaceId") Long groupSpaceId, @Param("tagId") Long tagId
    );

    @Query("""
            select count(tag) > 0 from Tag tag
            where tag.tagType = :tagType and tag.title = :title and tag.groupSpace.id = :groupSpaceId
            """)
    boolean existsByTagTypeAndTitleAndGroupSpace_Id(
            @Param("tagType") TagType tagType, @Param("title") String title, @Param("groupSpaceId") Long groupSpaceId
    );

    @Query("select tag from Tag tag where tag.groupSpace.id = :groupSpaceId")
    List<Tag> findByGroupSpace_Id(@Param("groupSpaceId") Long groupSpaceId);
}
