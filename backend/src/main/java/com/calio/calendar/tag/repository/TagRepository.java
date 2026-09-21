package com.calio.calendar.tag.repository;

import com.calio.calendar.tag.domain.Tag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, Long> {

  @Query(
      """
            select tag from Tag tag
            where tag.tagType = PERSONAL_DEFAULT
                and tag.accountId is null and tag.groupSpaceId is null
            order by tag.id
            """)
  List<Tag> findPersonalDefaultTags();

  @Query(
      """
            select tag from Tag tag
            where tag.tagType = CUSTOM
                and tag.accountId = :accountId and tag.groupSpaceId is null
            order by tag.id
            """)
  List<Tag> findPersonalCustomTags(@Param("accountId") Long accountId);

  @Query(
      """
            select tag from Tag tag
            where tag.id = :tagId and tag.tagType = PERSONAL_DEFAULT
                and tag.accountId is null and tag.groupSpaceId is null
            """)
  Optional<Tag> findPersonalDefaultTagById(@Param("tagId") Long tagId);

  @Query(
      """
            select tag from Tag tag
            where tag.id = :tagId and tag.tagType = CUSTOM
                and tag.accountId = :accountId and tag.groupSpaceId is null
            """)
  Optional<Tag> findPersonalCustomTagById(
      @Param("accountId") Long accountId, @Param("tagId") Long tagId);

  @Query(
      """
            select tag from Tag tag
            where tag.tagType = PERSONAL_DEFAULT and tag.fallback = true
                and tag.accountId is null and tag.groupSpaceId is null
            """)
  Optional<Tag> findPersonalFallbackTag();

  List<Tag> findByGroupSpaceIdOrderByIdAsc(Long groupSpaceId);

  Optional<Tag> findByIdAndGroupSpaceId(Long tagId, Long groupSpaceId);

  @Query(
      """
            select tag from Tag tag
            where tag.tagType = GROUP_DEFAULT and tag.fallback = true
                and tag.accountId is null and tag.groupSpaceId = :groupSpaceId
            """)
  Optional<Tag> findGroupFallbackTag(@Param("groupSpaceId") Long groupSpaceId);

  @Query(
      """
            select count(tag) > 0 from Tag tag
            where tag.tagType = CUSTOM and tag.title.value = :title
                and tag.groupSpaceId = :groupSpaceId and tag.id <> :tagId
            """)
  boolean existsOtherGroupCustomTagByTitle(
      @Param("title") String title,
      @Param("groupSpaceId") Long groupSpaceId,
      @Param("tagId") Long tagId);

  @Query(
      """
            select count(tag) > 0 from Tag tag
            where tag.tagType = CUSTOM and tag.title.value = :title
                and tag.groupSpaceId = :groupSpaceId
            """)
  boolean existsGroupCustomTagByTitle(
      @Param("title") String title, @Param("groupSpaceId") Long groupSpaceId);

  List<Tag> findByGroupSpaceId(Long groupSpaceId);
}
