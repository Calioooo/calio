package com.calio.calendar.repository;

import com.calio.calendar.repository.entity.Tag;
import com.calio.calendar.repository.entity.TagType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findByTagTypeAndAccountIsNullOrderByIdAsc(TagType tagType);

    List<Tag> findByTagTypeAndAccount_IdOrderByIdAsc(TagType tagType, Long accountId);

    Optional<Tag> findByIdAndTagTypeAndAccountIsNull(Long id, TagType tagType);

    Optional<Tag> findByIdAndTagTypeAndAccount_Id(Long id, TagType tagType, Long accountId);

    Optional<Tag> findFirstByTagTypeAndTitleAndAccountIsNullOrderByIdAsc(TagType tagType, String title);
}
