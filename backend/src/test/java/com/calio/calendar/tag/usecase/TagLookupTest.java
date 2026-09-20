package com.calio.calendar.tag.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagLookupTest {

  @Mock private TagRepository tagRepository;

  @InjectMocks private TagLookup tagLookup;

  @Test
  @DisplayName("유효한 tagId는 PERSONAL_DEFAULT 또는 CUSTOM 태그를 resolve한다")
  void givenExistingTagId_whenResolveTag_thenReturnsTag() {
    // given
    Tag defaultTag = Tag.personalDefault("업무", "#2563eb");
    Tag customTag = Tag.personalCustom(1L, "사용자", "#8b5cf6");
    when(tagRepository.findPersonalDefaultTagById(1L))
        .thenReturn(Optional.of(defaultTag));
    when(tagRepository.findPersonalDefaultTagById(2L))
        .thenReturn(Optional.empty());
    when(tagRepository.findPersonalCustomTagById(1L, 2L))
        .thenReturn(Optional.of(customTag));

    // when
    Tag resolvedDefaultTag = tagLookup.getPersonalTagOrDefault(1L, 1L);
    Tag resolvedCustomTag = tagLookup.getPersonalTagOrDefault(1L, 2L);

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
    when(tagRepository.findPersonalDefaultTagById(1L))
        .thenReturn(Optional.empty());
    when(tagRepository.findPersonalCustomTagById(1L, 1L))
        .thenReturn(Optional.empty());

    // when, then
    assertThatThrownBy(() -> tagLookup.getPersonalTagOrDefault(1L, 1L))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TAG_NOT_FOUND));
  }

  @Test
  @DisplayName("tagId가 null이면 PERSONAL_DEFAULT 기타 fallback 태그를 조회한다")
  void givenNullTagId_whenResolveDefaultTag_thenReturnsFallbackTag() {
    // given
    Tag fallbackTag = Tag.personalDefault("기타", "#64748B");
    when(tagRepository.findFirstPersonalDefaultTagByTitle("기타"))
        .thenReturn(Optional.of(fallbackTag));

    // when
    Tag resolvedTag = tagLookup.getPersonalTagOrDefault(1L, null);

    // then
    assertThat(resolvedTag).isSameAs(fallbackTag);
  }

  @Test
  @DisplayName("fallback PERSONAL_DEFAULT 기타 태그가 없으면 DEFAULT_TAG_NOT_FOUND로 실패한다")
  void givenMissingFallbackTag_whenResolveDefaultTag_thenThrowsDefaultTagNotFound() {
    // given
    when(tagRepository.findFirstPersonalDefaultTagByTitle("기타"))
        .thenReturn(Optional.empty());

    // when, then
    assertThatThrownBy(() -> tagLookup.getPersonalTagOrDefault(1L, null))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DEFAULT_TAG_NOT_FOUND));
  }

  @Test
  @DisplayName("colorCode는 #RRGGBB 형식만 허용한다")
  void givenInvalidColorCode_whenCreateTag_thenThrowsInvalidTagColorCode() {
    // when, then
    assertThatThrownBy(() -> Tag.personalDefault("잘못된 색상", "2563EB"))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TAG_COLOR_CODE));
  }
}
