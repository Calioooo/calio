package com.calio.calendar.tag.repository;

import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findByTagTypeAndAccountIsNullAndGroupSpaceIsNullOrderByIdAsc(TagType tagType);

    List<Tag> findByTagTypeAndAccount_IdOrderByIdAsc(TagType tagType, Long accountId);

    Optional<Tag> findByIdAndTagTypeAndAccountIsNullAndGroupSpaceIsNull(Long id, TagType tagType);

    Optional<Tag> findByIdAndTagTypeAndAccount_Id(Long id, TagType tagType, Long accountId);

    Optional<Tag> findFirstByTagTypeAndTitleAndAccountIsNullAndGroupSpaceIsNullOrderByIdAsc(TagType tagType, String title);

    List<Tag> findByGroupSpace_IdOrderByIdAsc(Long groupSpaceId);

    Optional<Tag> findByIdAndGroupSpace_Id(Long tagId, Long groupSpaceId);

    Optional<Tag> findByTagTypeAndGroupSpace_Id(TagType tagType, Long groupSpaceId);

    boolean existsByTagTypeAndTitleAndGroupSpace_IdAndIdNot(TagType tagType, String title, Long groupSpaceId, Long tagId);

    boolean existsByTagTypeAndTitleAndGroupSpace_Id(TagType tagType, String title, Long groupSpaceId);

    List<Tag> findByGroupSpace_Id(Long groupSpaceId);
}
