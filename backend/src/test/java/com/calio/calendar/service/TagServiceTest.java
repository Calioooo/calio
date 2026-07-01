package com.calio.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.repository.TagRepository;
import com.calio.calendar.repository.entity.Tag;
import com.calio.calendar.repository.entity.TagType;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private TagService tagService;

    @Test
    @DisplayName("explicit tagId는 DEFAULT 태그만 resolve한다")
    void givenDefaultTagId_whenResolveDefaultTag_thenReturnsTag() {
        // given
        Tag tag = new Tag(TagType.DEFAULT, "업무", "#2563eb");
        when(tagRepository.findByIdAndTagType(1L, TagType.DEFAULT)).thenReturn(Optional.of(tag));

        // when
        Tag resolvedTag = tagService.resolveDefaultTag(1L);

        // then
        assertThat(resolvedTag).isSameAs(tag);
        assertThat(resolvedTag.getColorCode()).isEqualTo("#2563EB");
    }

    @Test
    @DisplayName("missing 또는 CUSTOM tagId는 TAG_NOT_FOUND로 실패한다")
    void givenUnsupportedTagId_whenResolveDefaultTag_thenThrowsTagNotFound() {
        // given
        when(tagRepository.findByIdAndTagType(1L, TagType.DEFAULT)).thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> tagService.resolveDefaultTag(1L))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TAG_NOT_FOUND)
                );
    }

    @Test
    @DisplayName("tagId가 null이면 DEFAULT 기타 fallback 태그를 조회한다")
    void givenNullTagId_whenResolveDefaultTag_thenReturnsFallbackTag() {
        // given
        Tag fallbackTag = new Tag(TagType.DEFAULT, "기타", "#64748B");
        when(tagRepository.findFirstByTagTypeAndTitleOrderByIdAsc(TagType.DEFAULT, "기타"))
                .thenReturn(Optional.of(fallbackTag));

        // when
        Tag resolvedTag = tagService.resolveDefaultTag(null);

        // then
        assertThat(resolvedTag).isSameAs(fallbackTag);
    }

    @Test
    @DisplayName("fallback DEFAULT 기타 태그가 없으면 DEFAULT_TAG_NOT_FOUND로 실패한다")
    void givenMissingFallbackTag_whenResolveDefaultTag_thenThrowsDefaultTagNotFound() {
        // given
        when(tagRepository.findFirstByTagTypeAndTitleOrderByIdAsc(TagType.DEFAULT, "기타"))
                .thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> tagService.resolveDefaultTag(null))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DEFAULT_TAG_NOT_FOUND)
                );
    }

    @Test
    @DisplayName("colorCode는 #RRGGBB 형식만 허용한다")
    void givenInvalidColorCode_whenCreateTag_thenThrowsInvalidTagColorCode() {
        // when, then
        assertThatThrownBy(() -> new Tag(TagType.DEFAULT, "잘못된 색상", "2563EB"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TAG_COLOR_CODE)
                );
    }
}
