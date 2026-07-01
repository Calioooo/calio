package com.calio.calendar.repository;

import com.calio.calendar.repository.entity.Tag;
import com.calio.calendar.repository.entity.TagType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findByTagTypeOrderByIdAsc(TagType tagType);

    Optional<Tag> findByIdAndTagType(Long id, TagType tagType);

    Optional<Tag> findFirstByTagTypeAndTitleOrderByIdAsc(TagType tagType, String title);
}
