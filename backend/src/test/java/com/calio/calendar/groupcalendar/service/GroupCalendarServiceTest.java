package com.calio.calendar.groupcalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.controller.dto.GroupCalendarItemResponse;
import com.calio.calendar.groupcalendar.event.domain.GroupCalendarEvent;
import com.calio.calendar.groupcalendar.event.service.GroupCalendarEventQueryService;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceEvent;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceOverride;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceOverrideQueryService;
import com.calio.calendar.groupcalendar.recurrence.service.GroupCalendarRecurrenceQueryService;
import com.calio.calendar.groupcalendar.sharing.event.domain.PersonalEventGroupShare;
import com.calio.calendar.groupcalendar.sharing.event.service.PersonalEventGroupShareQueryService;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareOccurrenceOverride;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareScope;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareSelectedOrigin;
import com.calio.calendar.groupcalendar.sharing.recurrence.service.PersonalRecurrenceGroupShareQueryService;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.service.RecurrenceEventQueryService;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupCalendarServiceTest {

    private static final Long GROUP_SPACE_ID = 10L;
    private static final Long ACCOUNT_ID = 20L;
    private static final Instant FROM = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-08T00:00:00Z");

    @Mock private GroupMembershipQueryService membershipQueryService;
    @Mock private GroupCalendarEventQueryService eventQueryService;
    @Mock private GroupCalendarRecurrenceQueryService recurrenceQueryService;
    @Mock private GroupCalendarRecurrenceOverrideQueryService overrideQueryService;
    @Mock private PersonalEventGroupShareQueryService personalEventShareQueryService;
    @Mock private PersonalRecurrenceGroupShareQueryService personalRecurrenceShareQueryService;
    @Mock private RecurrenceEventQueryService personalRecurrenceQueryService;
    @Mock private Rfc5545RecurrenceEngine recurrenceEngine;

    private GroupCalendarService service;
    private GroupSpace groupSpace;
    private Account account;
    private GroupCalendarRecurrenceEvent recurrenceEvent;

    @BeforeEach
    void setUp() {
        service = new GroupCalendarService(
                membershipQueryService,
                eventQueryService,
                recurrenceQueryService,
                overrideQueryService,
                personalEventShareQueryService,
                personalRecurrenceShareQueryService,
                personalRecurrenceQueryService,
                recurrenceEngine
        );
        groupSpace = new GroupSpace(ACCOUNT_ID, "group", null);
        ReflectionTestUtils.setField(groupSpace, "id", GROUP_SPACE_ID);
        account = new Account();
        ReflectionTestUtils.setField(account, "id", ACCOUNT_ID);
        Tag tag = new Tag(TagType.GROUP_DEFAULT, "기타", "#64748B", groupSpace);
        recurrenceEvent = new GroupCalendarRecurrenceEvent(
                groupSpace,
                account,
                tag,
                "반복",
                null,
                new RecurrenceSchedule(FROM, FROM.plusSeconds(3600), false, "UTC"),
                List.of("RRULE:FREQ=DAILY")
        );
        ReflectionTestUtils.setField(recurrenceEvent, "id", 30L);
        GroupMember member = new GroupMember(groupSpace, ACCOUNT_ID, "작성자", FROM);
        when(membershipQueryService.getActiveMembership(GROUP_SPACE_ID, ACCOUNT_ID)).thenReturn(member);
        when(membershipQueryService.listActiveMembers(GROUP_SPACE_ID)).thenReturn(List.of(member));
        when(eventQueryService.listOverlappingEvents(GROUP_SPACE_ID, FROM, TO)).thenReturn(List.of());
        when(recurrenceQueryService.listExpansionCandidates(GROUP_SPACE_ID, TO)).thenReturn(List.of(recurrenceEvent));
        when(overrideQueryService.listMovedInOverrides(GROUP_SPACE_ID, FROM, TO)).thenReturn(List.of());
        when(personalEventShareQueryService.listSharesInGroupSpace(GROUP_SPACE_ID)).thenReturn(List.of());
        when(personalRecurrenceShareQueryService.listSharesInGroupSpace(GROUP_SPACE_ID)).thenReturn(List.of());
    }

    @Test
    @DisplayName("공유 단건 일정은 mapping 공개 표현과 범위 겹침으로 Group Calendar에 표시한다")
    void givenSharedOneOffEvent_whenListItems_thenAppliesPublicRepresentationAndRange() {
        // given
        com.calio.calendar.event.domain.Event sourceEvent = new com.calio.calendar.event.domain.Event(
                "원본 제목",
                "원본 설명",
                FROM.plusSeconds(3600),
                FROM.plusSeconds(7200),
                false,
                "UTC",
                null,
                recurrenceEvent.getTag(),
                account
        );
        PersonalEventGroupShare share = new PersonalEventGroupShare(sourceEvent, groupSpace);
        share.updateRepresentation(false, "공개 제목", null, null, null);
        when(personalEventShareQueryService.listSharesInGroupSpace(GROUP_SPACE_ID)).thenReturn(List.of(share));

        // when
        List<GroupCalendarItemResponse> items = service.listItems(ACCOUNT_ID, GROUP_SPACE_ID, FROM, TO);

        // then
        assertThat(items).extracting(GroupCalendarItemResponse::title).containsExactly("공개 제목");
        assertThat(items.getFirst().description()).isNull();
        assertThat(items.getFirst().isSharedPersonalSchedule()).isTrue();
        assertThat(items.getFirst().id()).isNull();
    }

    @Test
    @DisplayName("공유 반복 회차는 원본 override 뒤에 회차별 share override를 적용한다")
    void givenSharedRecurrenceOverrides_whenListItems_thenAppliesDeterministicPrecedence() {
        // given
        RecurrenceEvent sourceRecurrence = new RecurrenceEvent(
                "원본 반복 제목",
                "원본 반복 설명",
                new RecurrenceSchedule(FROM, FROM.plusSeconds(3600), false, "UTC"),
                List.of("RRULE:FREQ=DAILY"),
                recurrenceEvent.getTag(),
                account
        );
        ReflectionTestUtils.setField(sourceRecurrence, "id", 40L);
        PersonalRecurrenceGroupShare share = new PersonalRecurrenceGroupShare(
                sourceRecurrence,
                groupSpace,
                PersonalRecurrenceGroupShareScope.WHOLE_SERIES
        );
        ReflectionTestUtils.setField(share, "id", 50L);
        share.updateRepresentation(false, "기본 공개 제목", null, null, null);
        RecurrenceOccurrence occurrence = occurrence(FROM.plusSeconds(3600));
        RecurrenceEventOverride sourceOverride = RecurrenceEventOverride.active(
                sourceRecurrence,
                occurrence.originStartAt(),
                "원본 수정 제목",
                "원본 수정 설명",
                CanonicalSchedule.recurrenceOverride(
                        occurrence.startAt(),
                        occurrence.endAt(),
                        false,
                        "UTC"
                )
        );
        PersonalRecurrenceGroupShareOccurrenceOverride shareOverride =
                new PersonalRecurrenceGroupShareOccurrenceOverride(share, occurrence.originStartAt());
        shareOverride.updateRepresentation("회차 공개 제목", null, null, null);
        when(personalRecurrenceShareQueryService.listSharesInGroupSpace(GROUP_SPACE_ID))
                .thenReturn(List.of(share));
        when(recurrenceQueryService.listExpansionCandidates(GROUP_SPACE_ID, TO)).thenReturn(List.of());
        when(recurrenceEngine.expand(
                RecurrenceSchedule.from(sourceRecurrence),
                sourceRecurrence.getRecurrenceRules(),
                FROM,
                TO
        )).thenReturn(List.of(occurrence));
        when(personalRecurrenceQueryService.listOverrides(40L, List.of(occurrence.originStartAt())))
                .thenReturn(List.of(sourceOverride));
        when(personalRecurrenceQueryService.listActiveOverlappingOverridesForRecurrence(40L, FROM, TO))
                .thenReturn(List.of());
        when(personalRecurrenceShareQueryService.listOccurrenceOverrides(50L)).thenReturn(List.of(shareOverride));

        // when
        List<GroupCalendarItemResponse> items = service.listItems(ACCOUNT_ID, GROUP_SPACE_ID, FROM, TO);

        // then
        assertThat(items).extracting(GroupCalendarItemResponse::title).containsExactly("회차 공개 제목");
        assertThat(items.getFirst().description()).isNull();
        assertThat(items.getFirst().isSharedPersonalSchedule()).isTrue();
        assertThat(items.getFirst().recurrenceId()).isNull();
    }

    @Test
    @DisplayName("더 이상 반복 규칙이 만들지 않는 선택 origin은 공유 결과에서 제외한다")
    void givenStaleSelectedOrigin_whenListItems_thenHidesOccurrence() {
        // given
        RecurrenceEvent sourceRecurrence = new RecurrenceEvent(
                "선택 반복 제목",
                null,
                new RecurrenceSchedule(FROM, FROM.plusSeconds(3600), false, "UTC"),
                List.of("RRULE:FREQ=DAILY"),
                recurrenceEvent.getTag(),
                account
        );
        ReflectionTestUtils.setField(sourceRecurrence, "id", 41L);
        PersonalRecurrenceGroupShare share = new PersonalRecurrenceGroupShare(
                sourceRecurrence,
                groupSpace,
                PersonalRecurrenceGroupShareScope.SELECTED_OCCURRENCES
        );
        ReflectionTestUtils.setField(share, "id", 51L);
        RecurrenceOccurrence occurrence = occurrence(FROM.plusSeconds(3600));
        when(personalRecurrenceShareQueryService.listSharesInGroupSpace(GROUP_SPACE_ID))
                .thenReturn(List.of(share));
        when(recurrenceQueryService.listExpansionCandidates(GROUP_SPACE_ID, TO)).thenReturn(List.of());
        when(recurrenceEngine.expand(
                RecurrenceSchedule.from(sourceRecurrence),
                sourceRecurrence.getRecurrenceRules(),
                FROM,
                TO
        )).thenReturn(List.of(occurrence));
        when(personalRecurrenceShareQueryService.listSelectedOrigins(51L)).thenReturn(List.of(
                new PersonalRecurrenceGroupShareSelectedOrigin(share, occurrence.originStartAt())
        ));
        when(recurrenceEngine.containsOrigin(
                RecurrenceSchedule.from(sourceRecurrence),
                sourceRecurrence.getRecurrenceRules(),
                occurrence.originStartAt()
        )).thenReturn(false);
        when(personalRecurrenceQueryService.listOverrides(41L, List.of(occurrence.originStartAt())))
                .thenReturn(List.of());
        when(personalRecurrenceQueryService.listActiveOverlappingOverridesForRecurrence(41L, FROM, TO))
                .thenReturn(List.of());
        when(personalRecurrenceShareQueryService.listOccurrenceOverrides(51L)).thenReturn(List.of());

        // when
        List<GroupCalendarItemResponse> items = service.listItems(ACCOUNT_ID, GROUP_SPACE_ID, FROM, TO);

        // then
        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("동일 share의 확장 회차와 범위 안으로 이동한 원본 override는 한 번만 표시한다")
    void givenExpandedAndMovedInSharedOccurrence_whenListItems_thenDeduplicatesByShareAndOrigin() {
        // given
        RecurrenceEvent sourceRecurrence = new RecurrenceEvent(
                "원본 반복 제목",
                null,
                new RecurrenceSchedule(FROM, FROM.plusSeconds(3600), false, "UTC"),
                List.of("RRULE:FREQ=DAILY"),
                recurrenceEvent.getTag(),
                account
        );
        ReflectionTestUtils.setField(sourceRecurrence, "id", 42L);
        PersonalRecurrenceGroupShare share = new PersonalRecurrenceGroupShare(
                sourceRecurrence,
                groupSpace,
                PersonalRecurrenceGroupShareScope.WHOLE_SERIES
        );
        ReflectionTestUtils.setField(share, "id", 52L);
        RecurrenceOccurrence occurrence = occurrence(FROM.plusSeconds(3600));
        RecurrenceEventOverride sourceOverride = RecurrenceEventOverride.active(
                sourceRecurrence,
                occurrence.originStartAt(),
                "변경된 원본 제목",
                null,
                CanonicalSchedule.recurrenceOverride(
                        occurrence.startAt(),
                        occurrence.endAt(),
                        false,
                        "UTC"
                )
        );
        when(recurrenceQueryService.listExpansionCandidates(GROUP_SPACE_ID, TO)).thenReturn(List.of());
        when(personalRecurrenceShareQueryService.listSharesInGroupSpace(GROUP_SPACE_ID))
                .thenReturn(List.of(share));
        when(recurrenceEngine.expand(
                RecurrenceSchedule.from(sourceRecurrence),
                sourceRecurrence.getRecurrenceRules(),
                FROM,
                TO
        )).thenReturn(List.of(occurrence));
        when(personalRecurrenceQueryService.listOverrides(42L, List.of(occurrence.originStartAt())))
                .thenReturn(List.of(sourceOverride));
        when(personalRecurrenceQueryService.listActiveOverlappingOverridesForRecurrence(42L, FROM, TO))
                .thenReturn(List.of(sourceOverride));
        when(personalRecurrenceShareQueryService.listOccurrenceOverrides(52L)).thenReturn(List.of());

        // when
        List<GroupCalendarItemResponse> items = service.listItems(ACCOUNT_ID, GROUP_SPACE_ID, FROM, TO);

        // then
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().isSharedPersonalSchedule()).isTrue();
    }

    @Test
    @DisplayName("삭제된 override가 있는 반복 회차는 통합 캘린더 결과에서 제외된다")
    void givenDeletedOverride_whenListItems_thenExcludesOccurrence() {
        // given
        RecurrenceOccurrence occurrence = occurrence(FROM.plusSeconds(3600));
        GroupCalendarRecurrenceOverride deleted = GroupCalendarRecurrenceOverride.deleted(
                recurrenceEvent,
                occurrence.originStartAt(),
                FROM
        );
        when(recurrenceEngine.expand(recurrenceEvent.toRecurrenceSchedule(), recurrenceEvent.getRecurrenceRules(), FROM, TO))
                .thenReturn(List.of(occurrence));
        when(overrideQueryService.listOverrides(recurrenceEvent.getId(), List.of(occurrence.originStartAt())))
                .thenReturn(List.of(deleted));

        // when
        List<GroupCalendarItemResponse> items = service.listItems(ACCOUNT_ID, GROUP_SPACE_ID, FROM, TO);

        // then
        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("범위 밖으로 이동한 override는 제외하고 범위 안으로 이동한 override는 포함한다")
    void givenMovedOverrides_whenListItems_thenAppliesRangeToOverrideSchedule() {
        // given
        RecurrenceOccurrence origin = occurrence(FROM.plusSeconds(3600));
        GroupCalendarRecurrenceOverride movedOut = GroupCalendarRecurrenceOverride.active(
                recurrenceEvent,
                origin.originStartAt(),
                "밖으로 이동",
                null,
                CanonicalSchedule.recurrenceOverride(TO, TO.plusSeconds(3600), false, "UTC")
        );
        GroupCalendarRecurrenceOverride movedIn = GroupCalendarRecurrenceOverride.active(
                recurrenceEvent,
                FROM.minusSeconds(86400),
                "안으로 이동",
                null,
                CanonicalSchedule.recurrenceOverride(FROM.plusSeconds(7200), FROM.plusSeconds(10800), false, "UTC")
        );
        when(recurrenceEngine.expand(recurrenceEvent.toRecurrenceSchedule(), recurrenceEvent.getRecurrenceRules(), FROM, TO))
                .thenReturn(List.of(origin));
        when(overrideQueryService.listOverrides(recurrenceEvent.getId(), List.of(origin.originStartAt())))
                .thenReturn(List.of(movedOut));
        when(overrideQueryService.listMovedInOverrides(GROUP_SPACE_ID, FROM, TO)).thenReturn(List.of(movedIn));

        // when
        List<GroupCalendarItemResponse> items = service.listItems(ACCOUNT_ID, GROUP_SPACE_ID, FROM, TO);

        // then
        assertThat(items).extracting(GroupCalendarItemResponse::title).containsExactly("안으로 이동");
    }

    @Test
    @DisplayName("같은 recurrenceId와 originStartAt 조합은 한 번만 통합 캘린더에 나타난다")
    void givenExpandedAndMovedInSameOccurrence_whenListItems_thenDeduplicatesOccurrence() {
        // given
        RecurrenceOccurrence occurrence = occurrence(FROM.plusSeconds(3600));
        GroupCalendarRecurrenceOverride override = GroupCalendarRecurrenceOverride.active(
                recurrenceEvent,
                occurrence.originStartAt(),
                "변경",
                null,
                CanonicalSchedule.recurrenceOverride(occurrence.startAt(), occurrence.endAt(), false, "UTC")
        );
        when(recurrenceEngine.expand(recurrenceEvent.toRecurrenceSchedule(), recurrenceEvent.getRecurrenceRules(), FROM, TO))
                .thenReturn(List.of(occurrence));
        when(overrideQueryService.listOverrides(recurrenceEvent.getId(), List.of(occurrence.originStartAt())))
                .thenReturn(List.of(override));
        when(overrideQueryService.listMovedInOverrides(GROUP_SPACE_ID, FROM, TO)).thenReturn(List.of(override));

        // when
        List<GroupCalendarItemResponse> items = service.listItems(ACCOUNT_ID, GROUP_SPACE_ID, FROM, TO);

        // then
        assertThat(items).hasSize(1);
    }

    @Test
    @DisplayName("통합 캘린더 결과는 직접 일정과 반복 회차를 startAt 오름차순으로 정렬한다")
    void givenDirectEventAndOccurrence_whenListItems_thenSortsByStartAt() {
        // given
        GroupCalendarEvent directEvent = new GroupCalendarEvent(
                groupSpace,
                account,
                recurrenceEvent.getTag(),
                "직접 일정",
                null,
                FROM.plusSeconds(7200),
                FROM.plusSeconds(10800),
                false,
                "UTC"
        );
        RecurrenceOccurrence occurrence = occurrence(FROM.plusSeconds(3600));
        when(eventQueryService.listOverlappingEvents(GROUP_SPACE_ID, FROM, TO)).thenReturn(List.of(directEvent));
        when(recurrenceEngine.expand(recurrenceEvent.toRecurrenceSchedule(), recurrenceEvent.getRecurrenceRules(), FROM, TO))
                .thenReturn(List.of(occurrence));
        when(overrideQueryService.listOverrides(recurrenceEvent.getId(), List.of(occurrence.originStartAt())))
                .thenReturn(List.of());

        // when
        List<GroupCalendarItemResponse> items = service.listItems(ACCOUNT_ID, GROUP_SPACE_ID, FROM, TO);

        // then
        assertThat(items).extracting(GroupCalendarItemResponse::title).containsExactly("반복", "직접 일정");
    }

    @Test
    @DisplayName("from이 to와 같거나 이후이면 INVALID_TIME_RANGE를 반환한다")
    void givenInvalidRange_whenListItems_thenThrowsInvalidTimeRange() {
        // given, when, then
        assertThatThrownBy(() -> service.listItems(ACCOUNT_ID, GROUP_SPACE_ID, TO, TO))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TIME_RANGE)
                );
    }

    @Test
    @DisplayName("366일을 넘는 조회 범위는 EVENT_QUERY_RANGE_TOO_LARGE를 반환한다")
    void givenRangeOver366Days_whenListItems_thenThrowsRangeTooLarge() {
        // given, when, then
        assertThatThrownBy(() -> service.listItems(
                ACCOUNT_ID,
                GROUP_SPACE_ID,
                FROM,
                FROM.plusSeconds(367L * 86400)
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EVENT_QUERY_RANGE_TOO_LARGE)
        );
    }

    private RecurrenceOccurrence occurrence(Instant startAt) {
        return new RecurrenceOccurrence(startAt, startAt, startAt.plusSeconds(3600));
    }
}
