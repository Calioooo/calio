package com.calio.calendar.tag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.account.domain.Account;
import com.calio.calendar.tag.repository.TagRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private TagQueryService tagQueryService;

    @Test
    @DisplayName("유효한 tagId는 PERSONAL_DEFAULT 또는 CUSTOM 태그를 resolve한다")
    void givenExistingTagId_whenResolveTag_thenReturnsTag() {
        // given
        Tag defaultTag = new Tag(TagType.PERSONAL_DEFAULT, "업무", "#2563eb");
        Tag customTag = new Tag(TagType.CUSTOM, "사용자", "#8b5cf6", mock(Account.class));
        when(tagRepository.findByIdAndTagTypeAndAccountIsNullAndGroupSpaceIsNull(1L, TagType.PERSONAL_DEFAULT))
                .thenReturn(Optional.of(defaultTag));
        when(tagRepository.findByIdAndTagTypeAndAccountIsNullAndGroupSpaceIsNull(2L, TagType.PERSONAL_DEFAULT))
                .thenReturn(Optional.empty());
        when(tagRepository.findByIdAndTagTypeAndAccount_Id(2L, TagType.CUSTOM, 1L))
                .thenReturn(Optional.of(customTag));

        // when
        Tag resolvedDefaultTag = tagQueryService.getTagOrDefault(1L, 1L);
        Tag resolvedCustomTag = tagQueryService.getTagOrDefault(1L, 2L);

        // then
        assertThat(resolvedDefaultTag).isSameAs(defaultTag);
        assertThat(resolvedDefaultTag.getColorCode()).isEqualTo("#2563EB");
        assertThat(resolvedCustomTag).isSameAs(customTag);
        assertThat(resolvedCustomTag.getColorCode()).isEqualTo("#8B5CF6");
    }

    @Test
    @DisplayName("올바르지 않은 tagId는 TAG_NOT_FOUND 예외를 반환한다")
    void givenMissingTagId_whenResolveTag_thenThrowsTagNotFound() {
        // given
        when(tagRepository.findByIdAndTagTypeAndAccountIsNullAndGroupSpaceIsNull(1L, TagType.PERSONAL_DEFAULT))
                .thenReturn(Optional.empty());
        when(tagRepository.findByIdAndTagTypeAndAccount_Id(1L, TagType.CUSTOM, 1L))
                .thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> tagQueryService.getTagOrDefault(1L, 1L))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TAG_NOT_FOUND)
                );
    }

    @Test
    @DisplayName("tagId가 null이면 PERSONAL_DEFAULT 기타 fallback 태그를 조회한다")
    void givenNullTagId_whenResolveDefaultTag_thenReturnsFallbackTag() {
        // given
        Tag fallbackTag = new Tag(TagType.PERSONAL_DEFAULT, "기타", "#64748B");
        when(tagRepository.findFirstByTagTypeAndTitleAndAccountIsNullAndGroupSpaceIsNullOrderByIdAsc(TagType.PERSONAL_DEFAULT, "기타"))
                .thenReturn(Optional.of(fallbackTag));

        // when
        Tag resolvedTag = tagQueryService.getTagOrDefault(1L, null);

        // then
        assertThat(resolvedTag).isSameAs(fallbackTag);
    }

    @Test
    @DisplayName("fallback PERSONAL_DEFAULT 기타 태그가 없으면 DEFAULT_TAG_NOT_FOUND로 실패한다")
    void givenMissingFallbackTag_whenResolveDefaultTag_thenThrowsDefaultTagNotFound() {
        // given
        when(tagRepository.findFirstByTagTypeAndTitleAndAccountIsNullAndGroupSpaceIsNullOrderByIdAsc(TagType.PERSONAL_DEFAULT, "기타"))
                .thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> tagQueryService.getTagOrDefault(1L, null))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DEFAULT_TAG_NOT_FOUND)
                );
    }

    @Test
    @DisplayName("colorCode는 #RRGGBB 형식만 허용한다")
    void givenInvalidColorCode_whenCreateTag_thenThrowsInvalidTagColorCode() {
        // when, then
        assertThatThrownBy(() -> new Tag(TagType.PERSONAL_DEFAULT, "잘못된 색상", "2563EB"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TAG_COLOR_CODE)
                );
    }
}
