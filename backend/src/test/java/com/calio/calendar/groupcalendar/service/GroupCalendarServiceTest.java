package com.calio.calendar.groupcalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

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
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.service.RecurrenceEventService;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import com.calio.calendar.sharing.event.domain.PersonalEventGroupShare;
import com.calio.calendar.sharing.event.service.PersonalEventGroupShareQueryService;
import com.calio.calendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.sharing.recurrence.service.PersonalRecurrenceGroupShareQueryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
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
    @Mock private Rfc5545RecurrenceEngine recurrenceEngine;
    @Mock private PersonalEventGroupShareQueryService eventShareQueryService;
    @Mock private PersonalRecurrenceGroupShareQueryService recurrenceShareQueryService;
    @Mock private RecurrenceEventService personalRecurrenceEventService;

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
                recurrenceEngine,
                eventShareQueryService,
                recurrenceShareQueryService,
                personalRecurrenceEventService,
                new SimpleMeterRegistry()
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
        when(eventShareQueryService.listByGroupSpaceId(GROUP_SPACE_ID)).thenReturn(List.of());
        when(recurrenceShareQueryService.listByGroupSpaceId(GROUP_SPACE_ID)).thenReturn(List.of());
        when(personalRecurrenceEventService.listOverridesForRecurrenceIdsInRange(
                org.mockito.ArgumentMatchers.anyCollection(),
                org.mockito.ArgumentMatchers.eq(FROM),
                org.mockito.ArgumentMatchers.eq(TO)
        )).thenReturn(List.of());
        when(recurrenceQueryService.listExpansionCandidates(GROUP_SPACE_ID, TO)).thenReturn(List.of(recurrenceEvent));
        when(overrideQueryService.listMovedInOverrides(GROUP_SPACE_ID, FROM, TO)).thenReturn(List.of());
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
    @DisplayName("개인 단건 공유는 mapping UUID로 식별하고 익명 설정에 따라 원본 내용을 숨긴다")
    void givenAnonymousAndVisibleSharedEvents_whenListItems_thenProjectsExposurePerMapping() {
        var visible = PersonalEventGroupShare.create(
                personalEvent("공개 일정", "공개 설명", FROM.plusSeconds(3600), FROM.plusSeconds(7200)),
                groupSpace,
                false
        );
        var anonymous = PersonalEventGroupShare.create(
                personalEvent("비공개 일정", "비공개 설명", FROM.plusSeconds(10800), FROM.plusSeconds(14400)),
                groupSpace,
                true
        );
        when(eventShareQueryService.listByGroupSpaceId(GROUP_SPACE_ID)).thenReturn(List.of(visible, anonymous));

        List<GroupCalendarItemResponse> items = service.listItems(ACCOUNT_ID, GROUP_SPACE_ID, FROM, TO);

        assertThat(items).extracting(GroupCalendarItemResponse::title)
                .containsExactly("공개 일정", "익명 일정");
        assertThat(items).extracting(GroupCalendarItemResponse::description)
                .containsExactly("공개 설명", null);
        assertThat(items).extracting(GroupCalendarItemResponse::publicItemId)
                .containsExactly(
                        "shared-event:" + visible.getPublicShareId(),
                        "shared-event:" + anonymous.getPublicShareId()
                );
        assertThat(items).allSatisfy(item -> {
            assertThat(item.id()).isNull();
            assertThat(item.recurrenceId()).isNull();
            assertThat(item.tag()).isNull();
        });
    }

    @Test
    @DisplayName("직접 Group Calendar 항목과 반복 회차는 안정적인 namespace 공개 식별자를 가진다")
    void givenDirectItems_whenListItems_thenReturnsStableNamespacedPublicItemIds() {
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
        ReflectionTestUtils.setField(directEvent, "id", 40L);
        RecurrenceOccurrence occurrence = occurrence(FROM.plusSeconds(3600));
        when(eventQueryService.listOverlappingEvents(GROUP_SPACE_ID, FROM, TO)).thenReturn(List.of(directEvent));
        when(recurrenceEngine.expand(recurrenceEvent.toRecurrenceSchedule(), recurrenceEvent.getRecurrenceRules(), FROM, TO))
                .thenReturn(List.of(occurrence));
        when(overrideQueryService.listOverrides(recurrenceEvent.getId(), List.of(occurrence.originStartAt())))
                .thenReturn(List.of());

        List<GroupCalendarItemResponse> items = service.listItems(ACCOUNT_ID, GROUP_SPACE_ID, FROM, TO);

        assertThat(items).extracting(GroupCalendarItemResponse::publicItemId)
                .containsExactly(
                        "group-recurrence:30:" + occurrence.originStartAt(),
                        "group-event:40"
                );
    }

    @Test
    @DisplayName("공유 반복 일정은 원본 override를 적용하고 취소 회차는 제외한다")
    void givenSharedRecurrenceSourceOverrides_whenListItems_thenProjectsSourceStateOnly() {
        RecurrenceEvent source = personalRecurrenceEvent("원본 반복", "원본 설명");
        PersonalRecurrenceGroupShare share = PersonalRecurrenceGroupShare.create(source, groupSpace, false);
        RecurrenceOccurrence changedOccurrence = occurrence(FROM.plusSeconds(3600));
        RecurrenceOccurrence deletedOccurrence = occurrence(FROM.plusSeconds(7200));
        RecurrenceEventOverride changed = RecurrenceEventOverride.active(
                source,
                changedOccurrence.originStartAt(),
                "원본 수정",
                "수정 설명",
                CanonicalSchedule.recurrenceOverride(
                        FROM.plusSeconds(10800),
                        FROM.plusSeconds(14400),
                        false,
                        "UTC"
                )
        );
        RecurrenceEventOverride deleted = RecurrenceEventOverride.deleted(
                source,
                deletedOccurrence.originStartAt(),
                FROM
        );
        when(recurrenceShareQueryService.listByGroupSpaceId(GROUP_SPACE_ID)).thenReturn(List.of(share));
        when(recurrenceEngine.expand(RecurrenceSchedule.from(source), source.getRecurrenceRules(), FROM, TO))
                .thenReturn(List.of(changedOccurrence, deletedOccurrence));
        when(personalRecurrenceEventService.listOverridesForRecurrenceIdsInRange(
                List.of(source.getId()), FROM, TO
        )).thenReturn(List.of(changed, deleted));

        List<GroupCalendarItemResponse> items = service.listItems(ACCOUNT_ID, GROUP_SPACE_ID, FROM, TO);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("원본 수정");
            assertThat(item.description()).isEqualTo("수정 설명");
            assertThat(item.startAt()).isEqualTo(FROM.plusSeconds(10800));
            assertThat(item.publicItemId())
                    .isEqualTo("shared-recurrence:" + share.getPublicShareId() + ":" + changedOccurrence.originStartAt());
            assertThat(item.id()).isNull();
            assertThat(item.recurrenceId()).isNull();
            assertThat(item.tag()).isNull();
        });
    }

    @Test
    @DisplayName("범위 안으로 이동한 공유 반복 일정의 원본 override는 한 번만 포함한다")
    void givenMovedInSharedRecurrenceOverride_whenListItems_thenIncludesItOnce() {
        RecurrenceEvent source = personalRecurrenceEvent("원본 반복", "원본 설명");
        PersonalRecurrenceGroupShare share = PersonalRecurrenceGroupShare.create(source, groupSpace, true);
        Instant origin = FROM.minusSeconds(86400);
        RecurrenceEventOverride movedIn = RecurrenceEventOverride.active(
                source,
                origin,
                "숨겨진 제목",
                "숨겨진 설명",
                CanonicalSchedule.recurrenceOverride(
                        FROM.plusSeconds(3600),
                        FROM.plusSeconds(7200),
                        false,
                        "UTC"
                )
        );
        when(recurrenceShareQueryService.listByGroupSpaceId(GROUP_SPACE_ID)).thenReturn(List.of(share));
        when(recurrenceEngine.expand(RecurrenceSchedule.from(source), source.getRecurrenceRules(), FROM, TO))
                .thenReturn(List.of());
        when(personalRecurrenceEventService.listOverridesForRecurrenceIdsInRange(
                List.of(source.getId()), FROM, TO
        )).thenReturn(List.of(movedIn));

        List<GroupCalendarItemResponse> items = service.listItems(ACCOUNT_ID, GROUP_SPACE_ID, FROM, TO);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("익명 일정");
            assertThat(item.description()).isNull();
            assertThat(item.publicItemId())
                    .isEqualTo("shared-recurrence:" + share.getPublicShareId() + ":" + origin);
        });
    }

    @Test
    @DisplayName("공유 반복 회차가 5,000건을 초과하면 무제한 응답 대신 제한 오류를 반환한다")
    void givenTooManySharedRecurrenceOccurrences_whenListItems_thenRejectsResult() {
        RecurrenceEvent source = personalRecurrenceEvent("원본 반복", "원본 설명");
        PersonalRecurrenceGroupShare share = PersonalRecurrenceGroupShare.create(source, groupSpace, false);
        List<RecurrenceOccurrence> occurrences = IntStream.range(0, 5_001)
                .mapToObj(index -> occurrence(FROM.plusMillis(index)))
                .toList();
        when(recurrenceShareQueryService.listByGroupSpaceId(GROUP_SPACE_ID)).thenReturn(List.of(share));
        when(recurrenceEngine.expand(RecurrenceSchedule.from(source), source.getRecurrenceRules(), FROM, TO))
                .thenReturn(occurrences);
        when(personalRecurrenceEventService.listOverridesForRecurrenceIdsInRange(
                List.of(source.getId()), FROM, TO
        )).thenReturn(List.of());

        assertThatThrownBy(() -> service.listItems(ACCOUNT_ID, GROUP_SPACE_ID, FROM, TO))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_LIMIT_EXCEEDED)
                );
    }

    @Test
    @DisplayName("여러 공유 반복 mapping의 원본 override는 mapping별 조회 없이 한 번에 읽는다")
    void givenMultipleSharedRecurrences_whenListItems_thenBulkLoadsSourceOverridesOnce() {
        RecurrenceEvent firstSource = personalRecurrenceEvent("첫 번째", null);
        RecurrenceEvent secondSource = personalRecurrenceEvent("두 번째", null);
        ReflectionTestUtils.setField(secondSource, "id", 51L);
        when(recurrenceShareQueryService.listByGroupSpaceId(GROUP_SPACE_ID)).thenReturn(List.of(
                PersonalRecurrenceGroupShare.create(firstSource, groupSpace, false),
                PersonalRecurrenceGroupShare.create(secondSource, groupSpace, false)
        ));
        when(personalRecurrenceEventService.listOverridesForRecurrenceIdsInRange(
                List.of(50L, 51L), FROM, TO
        )).thenReturn(List.of());

        service.listItems(ACCOUNT_ID, GROUP_SPACE_ID, FROM, TO);

        verify(personalRecurrenceEventService).listOverridesForRecurrenceIdsInRange(
                List.of(50L, 51L), FROM, TO
        );
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

    private com.calio.calendar.event.domain.Event personalEvent(
            String title,
            String description,
            Instant startAt,
            Instant endAt
    ) {
        return new com.calio.calendar.event.domain.Event(
                title,
                description,
                startAt,
                endAt,
                false,
                "UTC",
                null,
                new Tag(TagType.PERSONAL_DEFAULT, "개인", "#64748B"),
                account
        );
    }

    private RecurrenceEvent personalRecurrenceEvent(String title, String description) {
        RecurrenceEvent recurrence = new RecurrenceEvent(
                title,
                description,
                new RecurrenceSchedule(
                        FROM.plusSeconds(60),
                        FROM.plusSeconds(3660),
                        false,
                        "UTC"
                ),
                List.of("RRULE:FREQ=DAILY"),
                new Tag(TagType.PERSONAL_DEFAULT, "개인", "#64748B"),
                account
        );
        ReflectionTestUtils.setField(recurrence, "id", 50L);
        return recurrence;
    }
}
