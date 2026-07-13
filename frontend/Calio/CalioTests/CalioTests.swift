//
//  CalioTests.swift
//  CalioTests
//
//  Created by 김준하 on 6/6/26.
//

import Testing
import Foundation
import SwiftUI
@testable import Calio

@Suite(.serialized)
struct CalioTests {

    @Test func calendarDisplayModeStartsFromWeek() async throws {
        let displayMode = CalendarDisplayMode.week

        #expect(displayMode == .week)
    }

    @Test func drawerDragDownPastThresholdResolvesToMonth() async throws {
        let displayMode = CalendarDisplayMode.week

        #expect(displayMode.resolved(afterDragTranslationHeight: 41) == .month)
    }

    @Test func drawerDragUpPastThresholdResolvesToWeek() async throws {
        let displayMode = CalendarDisplayMode.month

        #expect(displayMode.resolved(afterDragTranslationHeight: -41) == .week)
    }

    @Test func drawerDragInsideThresholdKeepsCurrentDisplayMode() async throws {
        #expect(CalendarDisplayMode.week.resolved(afterDragTranslationHeight: 40) == .week)
        #expect(CalendarDisplayMode.month.resolved(afterDragTranslationHeight: -40) == .month)
    }

    @MainActor
    @Test func scheduleDrawerUsesItemsAndCallbacksWithoutViewModel() async throws {
        let drawer = CalendarScheduleDrawerView(
            items: [],
            referenceDay: DayKey(date: Date()),
            displayMode: .week,
            eventLoadState: .idle,
            onReferenceDayChanged: { _ in },
            onVisibleRangeChanged: { _ in },
            onRetryEventLoading: {},
            onDragEnded: { _ in }
        )

        #expect(drawer.items.isEmpty)
        #expect(drawer.displayMode == .week)
    }
    
    @Test func calendarStateReturnsLoadedDateItemsInDateOrder() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let items = [
            makeDateCellItem(dayOffset: 2, from: baseDate, calendar: calendar),
            makeDateCellItem(dayOffset: 0, from: baseDate, calendar: calendar),
            makeDateCellItem(dayOffset: 1, from: baseDate, calendar: calendar)
        ]
        let state = CalendarState(
            startDate: baseDate,
            endDate: try #require(calendar.date(byAdding: .day, value: 2, to: baseDate)),
            daysByKey: Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) })
        )
        
        let loadedDays = state.loadedDateCellItems(calendar: calendar).map(\.id)
        
        #expect(loadedDays == [
            makeDayKey(dayOffset: 0, from: baseDate, calendar: calendar),
            makeDayKey(dayOffset: 1, from: baseDate, calendar: calendar),
            makeDayKey(dayOffset: 2, from: baseDate, calendar: calendar)
        ])
    }
    
    @Test func calendarStatePreservesEmptyDaysInsideLoadedRange() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let emptyDayItem = makeDateCellItem(dayOffset: 1, from: baseDate, calendar: calendar)
        let state = CalendarState(
            startDate: baseDate,
            endDate: try #require(calendar.date(byAdding: .day, value: 2, to: baseDate)),
            daysByKey: [
                makeDayKey(dayOffset: 0, from: baseDate, calendar: calendar): makeDateCellItem(dayOffset: 0, from: baseDate, calendar: calendar, events: [makeEvent(on: baseDate)]),
                emptyDayItem.id: emptyDayItem,
                makeDayKey(dayOffset: 2, from: baseDate, calendar: calendar): makeDateCellItem(dayOffset: 2, from: baseDate, calendar: calendar)
            ]
        )
        
        let loadedItems = state.loadedDateCellItems(calendar: calendar)
        
        #expect(loadedItems.count == 3)
        #expect(loadedItems[1].id == emptyDayItem.id)
        #expect(loadedItems[1].events.isEmpty)
    }
    
    @Test func calendarStateDoesNotOwnReferenceDay() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let item = makeDateCellItem(dayOffset: 0, from: baseDate, calendar: calendar)
        let state = CalendarState(
            startDate: baseDate,
            endDate: baseDate,
            daysByKey: [item.id: item]
        )
        
        #expect(state.loadedDateCellItems(calendar: calendar).map(\.id) == [item.id])
    }
    
    @MainActor
    @Test func scrollingDateViewsReceiveItemsReferenceDayAndCallbacksWithoutViewModel() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let items = [
            makeDateCellItem(dayOffset: 0, from: baseDate, calendar: calendar),
            makeDateCellItem(dayOffset: 1, from: baseDate, calendar: calendar)
        ]
        
        let strip = CalendarDateStripView(
            items: items,
            referenceDay: items[0].id,
            onReferenceDayChanged: { _ in }
        )
        let eventList = CalendarDateEventView(
            items: items,
            referenceDay: items[0].id,
            eventLoadState: .idle,
            onReferenceDayChanged: { _ in },
            onVisibleRangeChanged: { _ in },
            onRetryEventLoading: {}
        )
        
        #expect(strip.items.count == 2)
        #expect(strip.referenceDay == items[0].id)
        #expect(eventList.items.count == 2)
        #expect(eventList.referenceDay == items[0].id)
    }

    @MainActor
    @Test func dateEventCellSeparatesEventSelectionFromDateSelection() async throws {
        let event = makeEvent(on: Date())
        var selectedEventID: String?
        var dateSelectionCount = 0
        var detailEventID: String?
        let cell = CalendarDateEventCellView(
            day: DayKey(year: 2026, month: 6, day: 28),
            weekday: .monday,
            monthText: "6",
            dayText: "28",
            isToday: false,
            onTap: {
                dateSelectionCount += 1
            },
            selectedEvent: .constant(nil),
            onEventSelected: { event in
                selectedEventID = event.id
            },
            onShowEventDetail: { event in
                detailEventID = event.id
            },
            events: [event]
        )

        cell.onEventSelected(event)
        cell.onShowEventDetail(event)

        #expect(selectedEventID == event.id)
        #expect(detailEventID == event.id)
        #expect(dateSelectionCount == 0)

        cell.onTap()

        #expect(dateSelectionCount == 1)
    }

    @MainActor
    @Test func dateEventCellPlacesHolidayChipsBeforeEventChipsWithoutEventSelection() async throws {
        let event = makeEvent(on: Date())
        let holiday = NationalHoliday(
            id: 10,
            day: DayKey(year: 2026, month: 6, day: 6),
            title: "현충일"
        )
        var selectedEventID: String?
        let cell = CalendarDateEventCellView(
            day: holiday.day,
            weekday: .saturday,
            monthText: "6",
            dayText: "6",
            isToday: false,
            onTap: {},
            selectedEvent: .constant(nil),
            onEventSelected: { event in
                selectedEventID = event.id
            },
            onShowEventDetail: { _ in },
            events: [event],
            holidays: [holiday]
        )

        #expect(cell.calendarChips.map(\.title) == ["현충일", event.title])
        if case .holiday(let firstHoliday) = cell.calendarChips.first?.kind {
            #expect(firstHoliday.id == holiday.id)
        } else {
            Issue.record("holiday chip이 event chip보다 앞에 있어야 합니다.")
        }
        #expect(selectedEventID == nil)
    }

    @Test func dateEventChipLayoutReservesLastVisibleRowForOverflow() async throws {
        let chips = (1...4).map { index in
            CalendarDateEventChip(
                kind: .event(
                    makeEvent(
                        id: Int64(index),
                        title: "긴 일정 제목",
                        on: Date()
                    )
                )
            )
        }
        let builder = CalendarDateEventChipLayoutBuilder(maxVisibleRowCount: 3)

        let layout = builder.make(chips: chips, maxWidth: 40)

        #expect(layout.visibleChips.map(\.id) == ["event:1", "event:2"])
        #expect(layout.hiddenChipCount == 2)
    }

    @Test func dateEventChipLayoutHidesAllChipsWhenWidthIsUnavailable() async throws {
        let chips = [
            CalendarDateEventChip(kind: .event(makeEvent(id: 1, title: "일정", on: Date()))),
            CalendarDateEventChip(kind: .event(makeEvent(id: 2, title: "일정", on: Date())))
        ]
        let builder = CalendarDateEventChipLayoutBuilder(maxVisibleRowCount: 3)

        let layout = builder.make(chips: chips, maxWidth: 0)

        #expect(layout.visibleChips.isEmpty)
        #expect(layout.hiddenChipCount == 2)
    }

    @Test func dateEventPopoverEdgeUsesTopForUpperChipsAndBottomForLowerChips() async throws {
        let resolver = CalendarDateEventPopoverEdgeResolver(lowerScreenThreshold: 0.62)

        #expect(resolver.arrowEdge(for: nil, screenHeight: 1000) == .top)
        #expect(resolver.arrowEdge(
            for: CGRect(x: 0, y: 200, width: 40, height: 20),
            screenHeight: 1000
        ) == .top)
        #expect(resolver.arrowEdge(
            for: CGRect(x: 0, y: 700, width: 40, height: 20),
            screenHeight: 1000
        ) == .bottom)
    }

    @Test func monthEventLayoutCreatesSingleSpanForMultiDayEventInSameWeek() async throws {
        let calendar = fixedCalendar
        let monthStartDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 1)))
        let gridDays = makeMonthGridDays(referenceDate: monthStartDate, calendar: calendar)
        let startAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 3, hour: 9)))
        let endAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 6, hour: 10)))
        let event = makeEvent(
            id: 30,
            title: "여러 날짜 일정",
            startAt: startAt,
            endAt: endAt
        )
        let itemDays = [3, 4, 5, 6].map { day -> CalendarDayItem in
            let date = calendar.date(from: DateComponents(year: 2026, month: 6, day: day)) ?? monthStartDate
            return makeDateCellItem(date: date, calendar: calendar, events: [event])
        }

        let layout = MonthEventLayoutBuilder.make(
            items: itemDays,
            days: gridDays,
            maxVisibleRowCount: 3,
            calendar: calendar
        )
        let span = try #require(layout.spans.first)

        #expect(layout.spans.count == 1)
        #expect(span.chip.id == "event:30")
        #expect(span.weekRowIndex == 0)
        #expect(span.startColumn == 3)
        #expect(span.columnSpan == 4)
        #expect(span.days == [
            DayKey(year: 2026, month: 6, day: 3),
            DayKey(year: 2026, month: 6, day: 4),
            DayKey(year: 2026, month: 6, day: 5),
            DayKey(year: 2026, month: 6, day: 6)
        ])
        #expect(layout.hiddenCountByDay.isEmpty)
    }

    @Test func monthEventLayoutUsesLastVisibleRowForOverflowCount() async throws {
        let calendar = fixedCalendar
        let monthStartDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 1)))
        let gridDays = makeMonthGridDays(referenceDate: monthStartDate, calendar: calendar)
        let eventDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 3, hour: 9)))
        let eventDay = DayKey(year: 2026, month: 6, day: 3)
        let events: [Event] = (0..<4).map { offset in
            let startAt = eventDate.addingTimeInterval(TimeInterval(offset * 3600))
            let endAt = eventDate.addingTimeInterval(TimeInterval((offset + 1) * 3600))

            return makeEvent(
                id: Int64(offset + 1),
                title: "일정",
                startAt: startAt,
                endAt: endAt
            )
        }
        let item = makeDateCellItem(date: eventDate, calendar: calendar, events: events)

        let layout = MonthEventLayoutBuilder.make(
            items: [item],
            days: gridDays,
            maxVisibleRowCount: 3,
            calendar: calendar
        )

        let visibleRowIndexes = layout.spans.map { span in span.eventRowIndex }

        #expect(visibleRowIndexes == [0, 1])
        #expect(layout.hiddenCountByDay[eventDay] == 2)
    }

    @Test func weekTimelineLayoutSplitsTwoOverlappingEventsIntoColumns() async throws {
        let calendar = fixedCalendar
        let dayDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 3)))
        let item = makeDateCellItem(
            date: dayDate,
            calendar: calendar,
            events: [
                makeEvent(
                    id: 1,
                    title: "첫 일정",
                    startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 3, hour: 7))),
                    endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 3, hour: 9)))
                ),
                makeEvent(
                    id: 2,
                    title: "두 번째 일정",
                    startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 3, hour: 8))),
                    endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 3, hour: 10)))
                )
            ]
        )
        let metrics = makeTimelineMetrics()
        let builder = TimelineEventLayoutBuilder(
            calendar: calendar,
            timelineStartHour: 0,
            hourCount: 24
        )

        let layouts = builder.make(items: [item], metrics: metrics)

        #expect(layouts.count == 2)
        #expect(layouts.map(\.title) == ["첫 일정", "두 번째 일정"])
        #expect(layouts[0].width == layouts[1].width)
        #expect(layouts[0].x < layouts[1].x)
    }

    @Test func weekTimelineLayoutUsesOverflowWhenThreeEventsOverlapAtSameTime() async throws {
        let calendar = fixedCalendar
        let dayDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 3)))
        let events = [
            makeEvent(
                id: 1,
                title: "긴 일정",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 3, hour: 7))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 3, hour: 10)))
            ),
            makeEvent(
                id: 2,
                title: "겹친 일정",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 3, hour: 8))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 3, hour: 11)))
            ),
            makeEvent(
                id: 3,
                title: "세 번째 일정",
                startAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 3, hour: 9))),
                endAt: try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 3, hour: 12)))
            )
        ]
        let item = makeDateCellItem(date: dayDate, calendar: calendar, events: events)
        let builder = TimelineEventLayoutBuilder(
            calendar: calendar,
            timelineStartHour: 0,
            hourCount: 24
        )

        let layouts = builder.make(items: [item], metrics: makeTimelineMetrics())

        #expect(builder.maxSimultaneousOverlap(in: events, on: item.id) == 3)
        #expect(layouts.count == 2)
        #expect(layouts.map(\.title) == ["긴 일정", "+2"])
        if case .showOverlapGroup(let overlapEvents) = layouts[1].tapAction {
            #expect(overlapEvents.map(\.backendId) == [1, 2, 3])
        } else {
            Issue.record("overflow layout은 겹친 일정 목록을 보여야 합니다.")
        }
    }

    @Test func nationalHolidayResponseDTOKeepsHolidayDateStringAndMapsToDayKey() async throws {
        let calendar = fixedCalendar
        let responseJSON = """
        {
          "nationalHolidayId": 1,
          "holidayDate": "2026-06-06",
          "holidayTitle": "현충일"
        }
        """.data(using: .utf8)!
        let dto = try JSONDecoder().decode(NationalHolidayResponseDTO.self, from: responseJSON)
        let service = NationalHolidayService(
            repository: RecordingNationalHolidayRepository(fetchResponse: [dto]),
            calendar: calendar
        )

        let holidays = try await service.fetchNationalHolidays(for: YearMonthKey(year: 2026, month: 6))
        let holiday = try #require(holidays.first)

        #expect(dto.holidayDate == "2026-06-06")
        #expect(holiday.id == 1)
        #expect(holiday.day == DayKey(year: 2026, month: 6, day: 6))
        #expect(holiday.title == "현충일")
    }

    @Test func nationalHolidayServiceRejectsInvalidHolidayDate() async throws {
        let service = NationalHolidayService(
            repository: RecordingNationalHolidayRepository(
                fetchResponse: [
                    NationalHolidayResponseDTO(
                        nationalHolidayId: 1,
                        holidayDate: "2026-02-30",
                        holidayTitle: "잘못된 날짜"
                    )
                ]
            ),
            calendar: fixedCalendar
        )
        var thrownError: NationalHolidayServiceError?

        do {
            _ = try await service.fetchNationalHolidays(for: YearMonthKey(year: 2026, month: 2))
        } catch let error as NationalHolidayServiceError {
            thrownError = error
        }

        #expect(thrownError == .invalidHolidayDate)
    }

    @Test func nationalHolidayDisplayRangeUsesLocalCalendarStartOfDay() async throws {
        var kstCalendar = Calendar(identifier: .gregorian)
        kstCalendar.timeZone = TimeZone(secondsFromGMT: 9 * 3600)!
        let holiday = NationalHoliday(
            id: 1,
            day: DayKey(year: 2026, month: 6, day: 6),
            title: "현충일"
        )

        let startComponents = kstCalendar.dateComponents(
            [.year, .month, .day, .hour, .minute],
            from: holiday.displayStartAt(calendar: kstCalendar)
        )
        let endComponents = kstCalendar.dateComponents(
            [.year, .month, .day, .hour, .minute],
            from: holiday.displayEndAt(calendar: kstCalendar)
        )

        #expect(startComponents.year == 2026)
        #expect(startComponents.month == 6)
        #expect(startComponents.day == 6)
        #expect(startComponents.hour == 0)
        #expect(startComponents.minute == 0)
        #expect(endComponents.year == 2026)
        #expect(endComponents.month == 6)
        #expect(endComponents.day == 7)
        #expect(endComponents.hour == 0)
        #expect(endComponents.minute == 0)
    }

    @MainActor
    @Test func sharedEventSummaryPopoverForwardsDetailActionForSelectedEvent() async throws {
        let event = makeEvent(id: 91, on: Date())
        var detailEventID: String?
        let popover = CalendarEventSummaryPopoverView(
            event: event,
            onShowDetail: { detailEvent in
                detailEventID = detailEvent.id
            }
        )

        popover.onShowDetail?(event)

        #expect(popover.event.id == event.id)
        #expect(detailEventID == event.id)
    }

    @Test func eventDetailStatusUsesCanonicalEventFieldsWithoutRawRecurrenceID() async throws {
        let baseDate = Date()
        let repeatedEvent = Event(
            id: 11,
            title: "반복 회의",
            description: "",
            startAt: baseDate,
            endAt: baseDate.addingTimeInterval(3600),
            tag: .sample(colorCode: "#4F46E5"),
            importantEvent: true,
            recurrenceId: 12345,
            isRecurrenceOccurrence: false
        )
        let occurrenceEvent = Event(
            id: 12,
            title: "반복 발생 일정",
            description: "",
            startAt: baseDate,
            endAt: baseDate.addingTimeInterval(3600),
            tag: .sample(colorCode: "#4F46E5"),
            importantEvent: false,
            recurrenceId: nil,
            isRecurrenceOccurrence: true
        )
        let singleEvent = makeEvent(id: 13, on: baseDate)

        #expect(CalendarEventDetailView.importantStatusText(for: repeatedEvent) == "중요 일정")
        #expect(CalendarEventDetailView.recurrenceStatusText(for: repeatedEvent) == "반복 일정")
        #expect(CalendarEventDetailView.recurrenceStatusText(for: occurrenceEvent) == "반복 일정")
        #expect(CalendarEventDetailView.recurrenceStatusText(for: singleEvent) == "반복 없음")
        #expect(!CalendarEventDetailView.recurrenceStatusText(for: repeatedEvent).contains("12345"))
    }
    
    @Test func eventDisplayTextIncludesDatesOnlyForMultiDayRanges() async throws {
        let calendar = Calendar(identifier: .gregorian)
        let startAt = calendar.date(from: DateComponents(year: 2026, month: 7, day: 5, hour: 9))!
        let sameDayEndAt = calendar.date(from: DateComponents(year: 2026, month: 7, day: 5, hour: 11))!
        let nextDayEndAt = calendar.date(from: DateComponents(year: 2026, month: 7, day: 6, hour: 11))!
        let sameDayText = CalendarEventDisplayText.compactDateTimeRange(
            startAt: startAt,
            endAt: sameDayEndAt
        )
        let multiDayText = CalendarEventDisplayText.compactDateTimeRange(
            startAt: startAt,
            endAt: nextDayEndAt
        )
        
        #expect(sameDayText == CalendarEventDisplayText.timeRange(startAt: startAt, endAt: sameDayEndAt))
        #expect(multiDayText != CalendarEventDisplayText.timeRange(startAt: startAt, endAt: nextDayEndAt))
        #expect(multiDayText.contains("7월 5일"))
        #expect(multiDayText.contains("7월 6일"))
    }

    @Test func eventDetailActionsSeparateSingleAndRecurringEvents() async throws {
        let baseDate = Date()
        let singleEvent = makeEvent(id: 21, on: baseDate)
        let recurringEvent = Event(
            id: 22,
            title: "반복 회의",
            description: "",
            startAt: baseDate,
            endAt: baseDate.addingTimeInterval(3600),
            tag: .sample(colorCode: "#4F46E5"),
            recurrenceId: 100,
            isRecurrenceOccurrence: true
        )
        let recurringEventWithoutRecurrenceID = Event(
            id: 23,
            title: "반복 회의",
            description: "",
            startAt: baseDate,
            endAt: baseDate.addingTimeInterval(3600),
            tag: .sample(colorCode: "#4F46E5"),
            recurrenceId: nil,
            isRecurrenceOccurrence: true
        )

        #expect(CalendarEventDetailView.canUpdateSingleEvent(singleEvent))
        #expect(CalendarEventDetailView.canDeleteSingleEvent(singleEvent))
        #expect(!CalendarEventDetailView.canUpdateSingleEvent(recurringEvent))
        #expect(CalendarEventDetailView.canUpdateRecurringEvent(recurringEvent))
        #expect(!CalendarEventDetailView.canDeleteSingleEvent(recurringEvent))
        #expect(CalendarEventDetailView.canDeleteRecurringEvent(recurringEvent))
        #expect(!CalendarEventDetailView.canUpdateRecurringEvent(recurringEventWithoutRecurrenceID))
        #expect(!CalendarEventDetailView.canDeleteRecurringEvent(recurringEventWithoutRecurrenceID))
    }

    @MainActor
    @Test func eventCreationFormReceivesReusableBindingsWithoutOwningSaveAction() async throws {
        let startAt = Date()
        let endAt = startAt.addingTimeInterval(3600)
        let form = CalendarEventFormView(
            eventInput: .constant(
                EventInput(
                    title: "회의",
                    startAt: startAt,
                    endAt: endAt,
                    description: "설명",
                    tag: .sample(colorCode: "#4F46E5")
                )
            ),
            recurrenceInput: .constant(
                RecurrenceInput(
                    isEnabled: false,
                    startDate: startAt,
                    endDate: startAt,
                    startTime: startAt,
                    endTime: endAt,
                    frequency: .daily
                )
            ),
            onRecurrenceEnabled: {}
        )

        #expect(form.title == "회의")
        #expect(form.mode == .create)
        #expect(CalendarEventFormRules.canSave(title: "회의", startAt: startAt, endAt: endAt))
    }

    @MainActor
    @Test func eventEditFormUsesSingleEventModeWithoutRecurrenceFields() async throws {
        let startAt = Date()
        let endAt = startAt.addingTimeInterval(3600)
        let form = CalendarEventFormView(
            eventInput: .constant(
                EventInput(
                    title: "수정할 일정",
                    startAt: startAt,
                    endAt: endAt,
                    description: "설명",
                    tag: .sample(colorCode: "#EF4444")
                )
            ),
            mode: .editSingleEvent,
            onRecurrenceEnabled: {}
        )

        #expect(form.mode == .editSingleEvent)
        #expect(!form.mode.showsRecurrenceFields)
        #expect(form.recurrenceInput == nil)
        #expect(form.title == "수정할 일정")
    }

    @Test func tagManagementRulesSeparateDefaultAndCustomTagsAndPreferEtcFallback() async throws {
        let workTag = CalendarTag(id: 1, title: "업무", colorCode: "#3B82F6", tagType: .defaultTag)
        let etcTag = CalendarTag(id: 2, title: "기타", colorCode: "#64748B", tagType: .defaultTag)
        let customTag = CalendarTag(id: 3, title: "운동", colorCode: "#10B981", tagType: .custom)
        let rules = CalendarTagManagementRules(tags: [workTag, customTag, etcTag])

        #expect(rules.defaultTags == [workTag, etcTag])
        #expect(rules.customTags == [customTag])
        #expect(rules.fallbackTag == etcTag)
    }

    @Test func tagManagementRulesFallbackToFirstTagThenBuiltInFallback() async throws {
        let customTag = CalendarTag(id: 3, title: "운동", colorCode: "#10B981", tagType: .custom)

        #expect(CalendarTagManagementRules(tags: [customTag]).fallbackTag == customTag)
        #expect(CalendarTagManagementRules(tags: []).fallbackTag == .fallback)
    }

    @Test func tagEditInputRulesLimitTrimAndValidateTitle() async throws {
        let rules = CalendarTagEditInputRules(maxTitleLength: 12)
        let longTitle = "123456789012345"
        let validInput = CustomTagInput(title: "  운동  ", colorCode: "#10B981")
        let blankInput = CustomTagInput(title: "   ", colorCode: "#10B981")
        let longInput = CustomTagInput(title: longTitle, colorCode: "#10B981")

        #expect(rules.limitedTitle(longTitle) == "123456789012")
        #expect(rules.saveInput(from: validInput) == CustomTagInput(title: "운동", colorCode: "#10B981"))
        #expect(rules.canSave(validInput))
        #expect(!rules.canSave(blankInput))
        #expect(!rules.canSave(longInput))
    }

    @MainActor
    @Test func recurrenceEditFormModesSeparateOccurrenceAndSeriesFields() async throws {
        let startAt = Date()
        let endAt = startAt.addingTimeInterval(3600)
        let occurrenceForm = CalendarEventFormView(
            eventInput: .constant(
                EventInput(
                    title: "반복 항목 수정",
                    startAt: startAt,
                    endAt: endAt,
                    description: "설명",
                    tag: .sample(colorCode: "#EF4444")
                )
            ),
            mode: .editRecurrenceOccurrence,
            onRecurrenceEnabled: {}
        )
        let seriesMode = CalendarEventFormMode.editRecurrenceSeries

        #expect(!occurrenceForm.mode.showsRecurrenceFields)
        #expect(occurrenceForm.recurrenceInput == nil)
        #expect(seriesMode.showsRecurrenceFields)
        #expect(!seriesMode.allowsRecurrenceToggle)
        #expect(seriesMode.usesRecurrenceDateAndTime(isRecurrenceEnabled: false))
        #expect(seriesMode.showsRecurrenceFrequency(isRecurrenceEnabled: false))
    }

    @MainActor
    @Test func calendarScrollFocusCoordinatorPreparesReferenceDayBeforeRendering() async throws {
        let referenceDay = DayKey(year: 2026, month: 6, day: 23)
        let earlierDay = DayKey(year: 2026, month: 4, day: 23)
        let coordinator = CalendarScrollFocusCoordinator()

        #expect(!coordinator.canRenderContent(referenceDay: referenceDay, itemIDs: [earlierDay, referenceDay]))

        coordinator.prepareContentPosition(referenceDay: referenceDay, itemIDs: [earlierDay, referenceDay])

        #expect(coordinator.scrollPosition == referenceDay)
        #expect(coordinator.canRenderContent(referenceDay: referenceDay, itemIDs: [earlierDay, referenceDay]))
    }

    @MainActor
    @Test func calendarScrollFocusCoordinatorIgnoresInitialNonReferencePositionThenNotifiesUserScroll() async throws {
        let referenceDay = DayKey(year: 2026, month: 6, day: 23)
        let earlierDay = DayKey(year: 2026, month: 4, day: 23)
        let nextDay = DayKey(year: 2026, month: 6, day: 24)
        let coordinator = CalendarScrollFocusCoordinator()
        var notifiedDays: [DayKey] = []

        coordinator.prepareContentPosition(referenceDay: referenceDay, itemIDs: [earlierDay, referenceDay, nextDay])
        coordinator.notifyScrollReferenceDayIfNeeded(
            earlierDay,
            currentReferenceDay: referenceDay,
            onReferenceDayChanged: { notifiedDays.append($0) }
        )
        coordinator.notifyScrollReferenceDayIfNeeded(
            referenceDay,
            currentReferenceDay: referenceDay,
            onReferenceDayChanged: { notifiedDays.append($0) }
        )
        coordinator.notifyScrollReferenceDayIfNeeded(
            nextDay,
            currentReferenceDay: referenceDay,
            onReferenceDayChanged: { notifiedDays.append($0) }
        )

        #expect(notifiedDays == [nextDay])
    }

    @MainActor
    @Test func calendarScrollFocusCoordinatorKeepsContinuousUserScrollAfterReferenceChange() async throws {
        let referenceDay = DayKey(year: 2026, month: 6, day: 23)
        let nextDay = DayKey(year: 2026, month: 6, day: 24)
        let followingDay = DayKey(year: 2026, month: 6, day: 25)
        let itemIDs = [referenceDay, nextDay, followingDay]
        let coordinator = CalendarScrollFocusCoordinator()
        var notifiedDays: [DayKey] = []

        coordinator.prepareContentPosition(referenceDay: referenceDay, itemIDs: itemIDs)
        coordinator.notifyScrollReferenceDayIfNeeded(
            referenceDay,
            currentReferenceDay: referenceDay,
            onReferenceDayChanged: { notifiedDays.append($0) }
        )
        coordinator.notifyScrollReferenceDayIfNeeded(
            nextDay,
            currentReferenceDay: referenceDay,
            onReferenceDayChanged: { notifiedDays.append($0) }
        )
        coordinator.alignAfterReferenceDayChanged(to: nextDay, itemIDs: itemIDs)
        coordinator.notifyScrollReferenceDayIfNeeded(
            followingDay,
            currentReferenceDay: nextDay,
            onReferenceDayChanged: { notifiedDays.append($0) }
        )

        #expect(notifiedDays == [nextDay, followingDay])
    }

    @MainActor
    @Test func calendarHomeViewModelSetReferenceDayUpdatesCanonicalReferenceDay() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let referenceDay = makeDayKey(dayOffset: 3, from: baseDate, calendar: calendar)
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: RecordingEventRepository()),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(dayOffsets: 0...10, from: baseDate, calendar: calendar)
        )

        viewModel.setReferenceDay(referenceDay)

        #expect(viewModel.referenceDay == referenceDay)
    }

    @MainActor
    @Test func calendarHomeViewModelSetReferenceDayInsideLoadedRangePreservesDateCells() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let event = makeEvent(id: 42, on: baseDate)
        let items = (0...10).map { offset in
            makeDateCellItem(
                dayOffset: offset,
                from: baseDate,
                calendar: calendar,
                events: offset == 0 ? [event] : []
            )
        }
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: RecordingEventRepository()),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: CalendarState(
                startDate: baseDate,
                endDate: try #require(calendar.date(byAdding: .day, value: 10, to: baseDate)),
                daysByKey: Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) })
            )
        )

        viewModel.setReferenceDay(makeDayKey(dayOffset: 3, from: baseDate, calendar: calendar))

        #expect(viewModel.state.daysByKey[DayKey(date: baseDate, calendar: calendar)]?.events.map(\.backendId) == [42])
    }

    @MainActor
    @Test func calendarHomeViewModelInitialLoadRequestsReferenceAndAdjacentMonthsOnly() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository()
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialReferenceDate: baseDate
        )

        viewModel.loadInitialIfNeeded()
        let didRequestInitialMonths = await repository.waitForRequestCount(3)

        #expect(didRequestInitialMonths)
        #expect(repository.requestMonthKeys(calendar: calendar) == [
            YearMonthKey(year: 2026, month: 5),
            YearMonthKey(year: 2026, month: 6),
            YearMonthKey(year: 2026, month: 7)
        ])
    }

    @MainActor
    @Test func calendarHomeViewModelSelectYearMonthJumpsImmediatelyWithoutIntermediateFetches() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository(shouldSuspend: true)
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(dayOffsets: 0...10, from: baseDate, calendar: calendar)
        )

        viewModel.selectYearMonth(year: 2036, month: 6)
        let didRequestTargetMonths = await repository.waitForRequestCount(3)

        #expect(viewModel.referenceDay == DayKey(year: 2036, month: 6, day: 8))
        #expect(didRequestTargetMonths)
        #expect(repository.requestMonthKeys(calendar: calendar) == [
            YearMonthKey(year: 2036, month: 5),
            YearMonthKey(year: 2036, month: 6),
            YearMonthKey(year: 2036, month: 7)
        ])
        repository.finishSuspendedRequests()
    }

    @MainActor
    @Test func calendarHomeViewModelSelectYearMonthClampsMissingDayToLastDay() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 1, day: 31)))
        let repository = RecordingEventRepository()
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(dayOffsets: 0...10, from: baseDate, calendar: calendar)
        )

        viewModel.selectYearMonth(year: 2026, month: 2)

        #expect(viewModel.referenceDay == DayKey(year: 2026, month: 2, day: 28))
    }

    @MainActor
    @Test func calendarHomeViewModelSuppressesDuplicateLoadingAndLoadedMonthRequests() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository(shouldSuspend: true)
        let initialState = makeLoadedState(
            dayOffsets: 0...10,
            from: baseDate,
            calendar: calendar,
            monthEventCache: [
                YearMonthKey(year: 2026, month: 6): .loaded([]),
                YearMonthKey(year: 2026, month: 7): .loading
            ]
        )
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: initialState
        )

        viewModel.retryEventLoading()
        viewModel.loadAdditionalEventsIfNeeded(
            visibleRange: CalendarVisibleIndexRange(startIndex: 0, endIndex: 6)
        )
        let didRequestMissingAdjacentMonth = await repository.waitForRequestCount(1)

        #expect(didRequestMissingAdjacentMonth)
        #expect(repository.requestMonthKeys(calendar: calendar) == [
            YearMonthKey(year: 2026, month: 5)
        ])
        repository.finishSuspendedRequests()
    }

    @MainActor
    @Test func calendarHomeViewModelKeepsReferenceAndExposesRetryStateWhenTargetMonthFails() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository(error: EventRepositoryError.network(URLError(.notConnectedToInternet)))
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(dayOffsets: 0...10, from: baseDate, calendar: calendar)
        )

        viewModel.selectYearMonth(year: 2036, month: 6)
        let didRequestTargetMonths = await repository.waitForRequestCount(3)
        let didExposeFailure = await waitUntil {
            viewModel.eventLoadState == .failed("일정을 불러오지 못했습니다. 네트워크 연결을 확인해 주세요.")
        }

        #expect(didRequestTargetMonths)
        #expect(viewModel.referenceDay == DayKey(year: 2036, month: 6, day: 8))
        #expect(didExposeFailure)
    }

    @Test func eventCreationDefaultTimesUseReferenceDayMorningRange() async throws {
        let calendar = fixedCalendar
        let referenceDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 10, hour: 15)))
        let referenceDay = DayKey(date: referenceDate, calendar: calendar)

        let range = CalendarEventCreationView.defaultTimeRange(referenceDay: referenceDay, calendar: calendar)
        let startComponents = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: range.startAt)
        let endComponents = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: range.endAt)

        #expect(startComponents.year == 2026)
        #expect(startComponents.month == 6)
        #expect(startComponents.day == 10)
        #expect(startComponents.hour == 9)
        #expect(startComponents.minute == 0)
        #expect(endComponents.hour == 10)
        #expect(endComponents.minute == 0)
    }

    @Test func eventCreationSaveValidationRequiresTitleAndPositiveTimeRange() async throws {
        let startAt = Date()
        let endAt = startAt.addingTimeInterval(3600)

        #expect(CalendarEventFormRules.canSave(title: "회의", startAt: startAt, endAt: endAt))
        #expect(!CalendarEventFormRules.canSave(title: "   ", startAt: startAt, endAt: endAt))
        #expect(!CalendarEventFormRules.canSave(title: "회의", startAt: startAt, endAt: startAt))
        #expect(!CalendarEventFormRules.canSave(title: "회의", startAt: startAt, endAt: startAt.addingTimeInterval(-1)))
    }

    @Test func eventCreationSaveValidationChecksRecurrenceEndDateWithUTCDate() async throws {
        var kstCalendar = Calendar(identifier: .gregorian)
        kstCalendar.timeZone = TimeZone(secondsFromGMT: 9 * 3600)!
        let startAt = try #require(kstCalendar.date(from: DateComponents(year: 2026, month: 8, day: 1, hour: 9)))
        let endAt = try #require(kstCalendar.date(from: DateComponents(year: 2026, month: 8, day: 1, hour: 10)))
        let sameUTCDate = startAt
        let previousUTCDate = try #require(kstCalendar.date(from: DateComponents(year: 2026, month: 7, day: 31, hour: 8)))

        #expect(CalendarEventFormRules.canSave(
            title: "반복 회의",
            startAt: startAt,
            endAt: endAt,
            isRecurrenceEnabled: true,
            recurrenceStartDate: startAt,
            recurrenceEndDate: sameUTCDate,
            recurrenceStartTime: startAt,
            recurrenceEndTime: endAt
        ))
        #expect(!CalendarEventFormRules.canSave(
            title: "반복 회의",
            startAt: startAt,
            endAt: endAt,
            isRecurrenceEnabled: true,
            recurrenceStartDate: startAt,
            recurrenceEndDate: previousUTCDate,
            recurrenceStartTime: startAt,
            recurrenceEndTime: endAt
        ))
        #expect(!CalendarEventFormRules.canSave(
            title: "반복 회의",
            startAt: startAt,
            endAt: endAt,
            isRecurrenceEnabled: true,
            recurrenceStartDate: startAt,
            recurrenceEndDate: sameUTCDate,
            recurrenceStartTime: startAt,
            recurrenceEndTime: startAt
        ))
    }

    @Test func createEventRequestDTOEncodesOnlyBackendContractFields() async throws {
        let startAt = Date(timeIntervalSince1970: 1_780_000_000)
        let endAt = startAt.addingTimeInterval(3600)
        let request = CreateEventRequestDTO(
            title: "저녁 약속",
            description: "식당 예약",
            startAt: startAt,
            endAt: endAt
        )

        let data = try EventJSONCoding.makeEncoder().encode(request)
        let object = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])

        #expect(Set(object.keys) == ["title", "description", "startAt", "endAt"])
        #expect(object["title"] as? String == "저녁 약속")
        #expect(object["description"] as? String == "식당 예약")
        #expect(object["selectedColorCode"] == nil)
        #expect(object["colorCode"] == nil)
        #expect((object["startAt"] as? String)?.hasSuffix("Z") == true)
    }

    @Test func updateEventRequestDTOEncodesOnlyBackendContractFields() async throws {
        let startAt = Date(timeIntervalSince1970: 1_780_000_000)
        let endAt = startAt.addingTimeInterval(3600)
        let request = UpdateEventRequestDTO(
            title: "수정 일정",
            description: "수정 메모",
            startAt: startAt,
            endAt: endAt
        )

        let data = try EventJSONCoding.makeEncoder().encode(request)
        let object = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])

        #expect(Set(object.keys) == ["title", "description", "startAt", "endAt"])
        #expect(object["title"] as? String == "수정 일정")
        #expect(object["description"] as? String == "수정 메모")
        #expect(object["importantEvent"] == nil)
        #expect(object["recurrenceId"] == nil)
        #expect(object["isRecurrenceOccurrence"] == nil)
        #expect(object["colorCode"] == nil)
    }

    @Test func createRecurrenceEventRequestDTOEncodesOnlyBackendContractFields() async throws {
        let request = CreateRecurrenceEventRequestDTO(
            recurrenceTitle: "매일 스탠드업",
            recurrenceDescription: "팀 동기화",
            recurrenceStartDate: "2026-08-01",
            recurrenceEndDate: "2026-08-31",
            recurrenceStartTime: "00:00:00",
            recurrenceEndTime: "00:30:00",
            recurrenceFrequency: .daily
        )

        let data = try EventJSONCoding.makeEncoder().encode(request)
        let object = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])

        #expect(Set(object.keys) == [
            "recurrenceTitle",
            "recurrenceDescription",
            "recurrenceStartDate",
            "recurrenceEndDate",
            "recurrenceStartTime",
            "recurrenceEndTime",
            "recurrenceFrequency"
        ])
        #expect(object["recurrenceFrequency"] as? String == "DAILY")
        #expect(object["colorCode"] == nil)
        #expect(object["selectedColorCode"] == nil)
    }

    @Test func updateRecurrenceEventRequestDTOEncodesOnlyBackendContractFields() async throws {
        let request = UpdateRecurrenceEventRequestDTO(
            title: "수정 반복 일정",
            description: "수정 설명",
            startDate: "2026-08-01",
            endDate: "2026-08-31",
            startTime: "09:00:00",
            endTime: "10:00:00",
            recurrenceFrequency: .weekly
        )

        let data = try EventJSONCoding.makeEncoder().encode(request)
        let object = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])

        #expect(Set(object.keys) == ["title", "description", "startDate", "endDate", "startTime", "endTime", "recurrenceFrequency"])
        #expect(object["title"] as? String == "수정 반복 일정")
        #expect(object["startDate"] as? String == "2026-08-01")
        #expect(object["startTime"] as? String == "09:00:00")
        #expect(object["recurrenceFrequency"] as? String == "WEEKLY")
        #expect(object["isImportant"] == nil)
        #expect(object["colorCode"] == nil)
    }

    @Test func updateRecurrenceOccurrenceRequestDTOEncodesOnlyBackendContractFields() async throws {
        let originStartAt = Date(timeIntervalSince1970: 1_779_996_400)
        let startAt = Date(timeIntervalSince1970: 1_780_000_000)
        let endAt = startAt.addingTimeInterval(3600)
        let request = UpdateRecurrenceOccurrenceRequestDTO(
            originStartAt: originStartAt,
            startAt: startAt,
            endAt: endAt
        )

        let data = try EventJSONCoding.makeEncoder().encode(request)
        let object = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])

        #expect(Set(object.keys) == ["originStartAt", "startAt", "endAt"])
        #expect(object["originStartAt"] as? String == EventJSONCoding.string(from: originStartAt))
        #expect(object["title"] == nil)
        #expect(object["isImportant"] == nil)
        #expect(object["importantEvent"] == nil)
        #expect(object["recurrenceFrequency"] == nil)
    }

    @Test func eventResponseDTODecodingPreservesCanonicalRecurrenceFields() async throws {
        let responseJSON = """
        {
          "id": 12,
          "title": "반복 occurrence",
          "description": "backend canonical fields",
          "startAt": "2026-08-01T00:00:00Z",
          "endAt": "2026-08-01T01:00:00Z",
          "importantEvent": true,
          "recurrenceId": 44,
          "isRecurrenceOccurrence": true,
          "tag": {
            "id": 1,
            "title": "업무",
            "colorCode": "#4F46E5",
            "tagType": "DEFAULT"
          },
          "createdAt": "2026-08-01T00:00:00Z",
          "updatedAt": "2026-08-01T00:00:00Z"
        }
        """.data(using: .utf8)!

        let dto = try EventJSONCoding.makeDecoder().decode(EventResponseDTO.self, from: responseJSON)
        let service = EventService(repository: RecordingEventRepository(fetchResponse: [dto]))
        let events = try await service.fetchEvents(from: dto.startAt, to: dto.endAt)
        let event = try #require(events.first)

        #expect(dto.importantEvent)
        #expect(dto.recurrenceId == 44)
        #expect(dto.isRecurrenceOccurrence)
        #expect(event.importantEvent)
        #expect(event.recurrenceId == 44)
        #expect(event.isRecurrenceOccurrence)
    }

    @Test func eventServiceCreateEventMapsRepositoryResponseToAppEvent() async throws {
        let calendar = fixedCalendar
        let startAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 10, hour: 9)))
        let endAt = startAt.addingTimeInterval(3600)
        let repository = RecordingEventRepository(
            createResponse: EventResponseDTO(
                id: 77,
                title: "제품 리뷰",
                description: nil,
                startAt: startAt,
                endAt: endAt,
                tag: TagResponseDTO(
                    id: 1,
                    title: "업무",
                    colorCode: "#4F46E5",
                    tagType: .defaultTag
                ),
                createdAt: startAt,
                updatedAt: startAt
            )
        )
        let service = EventService(repository: repository)

        let event = try await service.createEvent(
            EventCreateInput(
                title: "제품 리뷰",
                description: "",
                startAt: startAt,
                endAt: endAt
            )
        )

        #expect(event.backendId == 77)
        #expect(event.title == "제품 리뷰")
        #expect(event.description == "")
        #expect(event.startAt == startAt)
        #expect(event.endAt == endAt)
        #expect(event.tag.colorCode == "#4F46E5")
        #expect(repository.createRequests.count == 1)
    }

    @Test func eventServiceUpdateEventMapsRepositoryResponseAndPreservesCanonicalFields() async throws {
        let calendar = fixedCalendar
        let startAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 10, hour: 9)))
        let endAt = startAt.addingTimeInterval(3600)
        let repository = RecordingEventRepository(
            updateResponse: EventResponseDTO(
                id: 90,
                title: "수정된 반복 항목",
                description: "수정 설명",
                startAt: startAt,
                endAt: endAt,
                importantEvent: true,
                recurrenceId: 700,
                isRecurrenceOccurrence: true,
                createdAt: startAt,
                updatedAt: endAt
            )
        )
        let service = EventService(repository: repository)

        let event = try await service.updateEvent(
            eventId: 90,
            input: EventUpdateInput(
                title: "수정된 반복 항목",
                description: "수정 설명",
                startAt: startAt,
                endAt: endAt
            )
        )

        #expect(event.backendId == 90)
        #expect(event.importantEvent)
        #expect(event.recurrenceId == 700)
        #expect(event.isRecurrenceOccurrence)
        #expect(repository.updateRequests.first?.eventId == 90)
        #expect(repository.updateRequests.first?.request.title == "수정된 반복 항목")
    }

    @Test func eventServiceCreateRecurrenceEventMapsSeparateUTCDateAndTimeIntoRepositoryRequest() async throws {
        var kstCalendar = Calendar(identifier: .gregorian)
        kstCalendar.timeZone = TimeZone(secondsFromGMT: 9 * 3600)!
        let recurrenceStartDate = try #require(kstCalendar.date(from: DateComponents(year: 2026, month: 8, day: 1, hour: 9)))
        let recurrenceEndDate = try #require(kstCalendar.date(from: DateComponents(year: 2026, month: 8, day: 31, hour: 9)))
        let recurrenceStartTime = try #require(kstCalendar.date(from: DateComponents(year: 2026, month: 9, day: 2, hour: 9)))
        let recurrenceEndTime = try #require(kstCalendar.date(from: DateComponents(year: 2026, month: 9, day: 2, hour: 10, minute: 30)))
        let repository = RecordingEventRepository()
        let service = EventService(repository: repository)

        try await service.createRecurrenceEvent(
            RecurrenceEventCreateInput(
                title: "아침 루틴",
                description: "반복 설명",
                recurrenceStartDate: recurrenceStartDate,
                recurrenceEndDate: recurrenceEndDate,
                recurrenceStartTime: recurrenceStartTime,
                recurrenceEndTime: recurrenceEndTime,
                recurrenceFrequency: .weekly
            )
        )
        let request = try #require(repository.recurrenceCreateRequests.first)

        #expect(request.recurrenceTitle == "아침 루틴")
        #expect(request.recurrenceDescription == "반복 설명")
        #expect(request.recurrenceStartDate == "2026-08-01")
        #expect(request.recurrenceEndDate == "2026-08-31")
        #expect(request.recurrenceStartTime == "00:00:00")
        #expect(request.recurrenceEndTime == "01:30:00")
        #expect(request.recurrenceFrequency == .weekly)
        #expect(repository.createRequests.isEmpty)
    }

    @Test func eventServiceFetchesRecurrenceEventFromCanonicalResponse() async throws {
        let repository = RecordingEventRepository(
            fetchRecurrenceResponse: RecurrenceEventResponseDTO(
                recurrenceId: 700,
                recurrenceTitle: "반복 회의",
                recurrenceDescription: "설명",
                recurrenceStartDate: "2026-08-01",
                recurrenceEndDate: "2026-08-31",
                recurrenceStartTime: "00:00:00",
                recurrenceEndTime: "01:30:00",
                recurrenceFrequency: .weekly
            )
        )
        let service = EventService(repository: repository)

        let details = try await service.fetchRecurrenceEvent(recurrenceId: 700)

        #expect(repository.fetchRecurrenceEventIDs == [700])
        #expect(details.title == "반복 회의")
        #expect(details.description == "설명")
        #expect(CalendarDateService.utcDateString(from: details.recurrenceStartDate) == "2026-08-01")
        #expect(CalendarDateService.utcTimeString(from: details.recurrenceEndTime) == "01:30:00")
        #expect(details.recurrenceFrequency == .weekly)
    }

    @Test func eventServiceUpdatesRecurrenceOccurrenceWithOriginStartAtOnly() async throws {
        let calendar = fixedCalendar
        let originStartAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 8, day: 1, hour: 8)))
        let startAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 8, day: 1, hour: 9)))
        let endAt = startAt.addingTimeInterval(3600)
        let repository = RecordingEventRepository()
        let service = EventService(repository: repository)

        _ = try await service.updateRecurrenceOccurrence(
            recurrenceId: 700,
            originStartAt: originStartAt,
            input: RecurrenceOccurrenceUpdateInput(
                startAt: startAt,
                endAt: endAt
            )
        )
        let request = try #require(repository.updateRecurrenceOccurrenceRequests.first)

        #expect(request.recurrenceId == 700)
        #expect(request.request.originStartAt == originStartAt)
        #expect(request.request.startAt == startAt)
        #expect(request.request.endAt == endAt)
    }

    @Test func eventServiceMapsRecurrenceUpdateInvalidTimeRangeLikeExistingInvalidRange() async throws {
        let repository = RecordingEventRepository(
            updateRecurrenceError: EventRepositoryError.backend(
                statusCode: 400,
                response: ErrorResponseDTO(
                    errorCode: "RECURRENCE_UPDATE_TIME_RANGE_INVALID",
                    message: "invalid"
                )
            )
        )
        let service = EventService(repository: repository)
        var thrownError: EventServiceError?

        do {
            _ = try await service.updateRecurrenceEvent(
                recurrenceId: 700,
                input: RecurrenceEventUpdateInput(
                    title: "반복 수정",
                    description: "",
                    recurrenceStartDate: Date(timeIntervalSince1970: 0),
                    recurrenceEndDate: Date(timeIntervalSince1970: 0),
                    recurrenceStartTime: Date(timeIntervalSince1970: 0),
                    recurrenceEndTime: Date(timeIntervalSince1970: 3600),
                    recurrenceFrequency: .daily
                )
            )
        } catch let error as EventServiceError {
            thrownError = error
        } catch {
            thrownError = .unexpected
        }

        #expect(thrownError == .invalidTimeRange)
    }

    @Test func urlSessionEventRepositoryCreatesEventWithInjectedBaseURLAndContractBody() async throws {
        let calendar = fixedCalendar
        let startAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 10, hour: 9)))
        let endAt = startAt.addingTimeInterval(3600)
        let responseJSON = """
        {
          "id": 88,
          "title": "새 일정",
          "description": "메모",
          "startAt": "2026-06-10T09:00:00Z",
          "endAt": "2026-06-10T10:00:00Z",
          "tag": {
            "id": 1,
            "title": "업무",
            "colorCode": "#3B82F6",
            "tagType": "DEFAULT"
          },
          "createdAt": "2026-06-10T09:00:00Z",
          "updatedAt": "2026-06-10T09:00:00Z"
        }
        """.data(using: .utf8)!
        var capturedRequest: URLRequest?
        MockURLProtocol.requestHandler = { request in
            capturedRequest = request
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 201,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, responseJSON)
        }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let repository = URLSessionEventRepository(
            baseURL: URL(string: "https://example.test")!,
            session: session
        )

        let event = try await repository.createEvent(
            CreateEventRequestDTO(
                title: "새 일정",
                description: "메모",
                startAt: startAt,
                endAt: endAt
            )
        )
        let request = try #require(capturedRequest)
        let body = try #require(requestBodyData(from: request))
        let object = try #require(JSONSerialization.jsonObject(with: body) as? [String: Any])

        #expect(event.id == 88)
        #expect(request.url?.absoluteString == "https://example.test/api/events")
        #expect(request.httpMethod == "POST")
        #expect(request.value(forHTTPHeaderField: "Content-Type") == "application/json")
        #expect(Set(object.keys) == ["title", "description", "startAt", "endAt"])
        #expect(object["title"] as? String == "새 일정")
        #expect(object["selectedColorCode"] == nil)
    }

    @Test func urlSessionEventRepositoryCreatesRecurrenceEventWithInjectedBaseURLAndContractBody() async throws {
        let responseJSON = """
        {
          "recurrenceId": 123,
          "recurrenceTitle": "반복 일정",
          "recurrenceDescription": "설명",
          "recurrenceStartDate": "2026-08-01",
          "recurrenceEndDate": "2026-08-31",
          "recurrenceStartTime": "00:00:00",
          "recurrenceEndTime": "01:00:00",
          "recurrenceFrequency": "MONTHLY",
          "tag": {
            "id": 1,
            "title": "업무",
            "colorCode": "#3B82F6",
            "tagType": "DEFAULT"
          }
        }
        """.data(using: .utf8)!
        var capturedRequest: URLRequest?
        MockURLProtocol.requestHandler = { request in
            capturedRequest = request
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 201,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, responseJSON)
        }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let repository = URLSessionEventRepository(
            baseURL: URL(string: "https://example.test")!,
            session: session
        )

        let response = try await repository.createRecurrenceEvent(
            CreateRecurrenceEventRequestDTO(
                recurrenceTitle: "반복 일정",
                recurrenceDescription: "설명",
                recurrenceStartDate: "2026-08-01",
                recurrenceEndDate: "2026-08-31",
                recurrenceStartTime: "00:00:00",
                recurrenceEndTime: "01:00:00",
                recurrenceFrequency: .monthly
            )
        )
        let request = try #require(capturedRequest)
        let body = try #require(requestBodyData(from: request))
        let object = try #require(JSONSerialization.jsonObject(with: body) as? [String: Any])

        #expect(response.recurrenceId == 123)
        #expect(request.url?.absoluteString == "https://example.test/api/recurrence-events")
        #expect(request.httpMethod == "POST")
        #expect(Set(object.keys) == [
            "recurrenceTitle",
            "recurrenceDescription",
            "recurrenceStartDate",
            "recurrenceEndDate",
            "recurrenceStartTime",
            "recurrenceEndTime",
            "recurrenceFrequency"
        ])
        #expect(object["recurrenceFrequency"] as? String == "MONTHLY")
        #expect(object["colorCode"] == nil)
    }

    @Test func urlSessionEventRepositoryUpdatesEventWithInjectedBaseURLAndContractBody() async throws {
        let calendar = fixedCalendar
        let startAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 10, hour: 9)))
        let endAt = startAt.addingTimeInterval(3600)
        let responseJSON = """
        {
          "id": 88,
          "title": "수정 일정",
          "description": "수정 메모",
          "startAt": "2026-06-10T09:00:00Z",
          "endAt": "2026-06-10T10:00:00Z",
          "tag": {
            "id": 1,
            "title": "업무",
            "colorCode": "#3B82F6",
            "tagType": "DEFAULT"
          },
          "createdAt": "2026-06-10T09:00:00Z",
          "updatedAt": "2026-06-10T10:00:00Z"
        }
        """.data(using: .utf8)!
        var capturedRequest: URLRequest?
        MockURLProtocol.requestHandler = { request in
            capturedRequest = request
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, responseJSON)
        }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let repository = URLSessionEventRepository(
            baseURL: URL(string: "https://example.test")!,
            session: session
        )

        let event = try await repository.updateEvent(
            eventId: 88,
            request: UpdateEventRequestDTO(
                title: "수정 일정",
                description: "수정 메모",
                startAt: startAt,
                endAt: endAt
            )
        )
        let request = try #require(capturedRequest)
        let body = try #require(requestBodyData(from: request))
        let object = try #require(JSONSerialization.jsonObject(with: body) as? [String: Any])

        #expect(event.id == 88)
        #expect(request.url?.absoluteString == "https://example.test/api/events/88")
        #expect(request.httpMethod == "PUT")
        #expect(Set(object.keys) == ["title", "description", "startAt", "endAt"])
        #expect(object["colorCode"] == nil)
    }

    @Test func urlSessionEventRepositoryUpdatesRecurrenceEndpointsWithBackendContractBodies() async throws {
        let recurrenceResponseJSON = """
        {
          "recurrenceId": 700,
          "recurrenceTitle": "수정 반복 일정",
          "recurrenceDescription": "설명",
          "recurrenceStartDate": "2026-08-01",
          "recurrenceEndDate": "2026-08-31",
          "recurrenceStartTime": "09:00:00",
          "recurrenceEndTime": "10:00:00",
          "recurrenceFrequency": "WEEKLY",
          "tag": {
            "id": 1,
            "title": "업무",
            "colorCode": "#3B82F6",
            "tagType": "DEFAULT"
          }
        }
        """.data(using: .utf8)!
        let occurrenceResponseJSON = """
        {
          "id": 701,
          "title": "수정 반복 항목",
          "description": "설명",
          "startAt": "2026-08-01T09:00:00Z",
          "endAt": "2026-08-01T10:00:00Z",
          "importantEvent": true,
          "recurrenceId": 700,
          "isRecurrenceOccurrence": true,
          "tag": {
            "id": 1,
            "title": "업무",
            "colorCode": "#3B82F6",
            "tagType": "DEFAULT"
          },
          "createdAt": "2026-08-01T09:00:00Z",
          "updatedAt": "2026-08-01T10:00:00Z"
        }
        """.data(using: .utf8)!
        var capturedRequests: [URLRequest] = []
        MockURLProtocol.requestHandler = { request in
            capturedRequests.append(request)
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: nil
            )!
            let isOccurrenceUpdate = request.url?.path.hasSuffix("/occurrences") == true
            let data = isOccurrenceUpdate ? occurrenceResponseJSON : recurrenceResponseJSON
            return (response, data)
        }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let repository = URLSessionEventRepository(
            baseURL: URL(string: "https://example.test")!,
            session: session
        )

        _ = try await repository.updateRecurrenceEvent(
            recurrenceId: 700,
            request: UpdateRecurrenceEventRequestDTO(
                title: "수정 반복 일정",
                description: "설명",
                startDate: "1970-01-01",
                endDate: "1970-01-01",
                startTime: "00:00:00",
                endTime: "01:00:00",
                recurrenceFrequency: .weekly
            )
        )
        _ = try await repository.updateRecurrenceOccurrence(
            recurrenceId: 700,
            request: UpdateRecurrenceOccurrenceRequestDTO(
                originStartAt: Date(timeIntervalSince1970: 0),
                startAt: Date(timeIntervalSince1970: 0),
                endAt: Date(timeIntervalSince1970: 3600)
            )
        )

        #expect(capturedRequests.map { $0.url?.absoluteString } == [
            "https://example.test/api/recurrence-events/700",
            "https://example.test/api/recurrence-events/700/occurrences"
        ])
        #expect(capturedRequests.map(\.httpMethod) == ["PUT", "PATCH"])
    }

    @Test func urlSessionEventRepositoryDeletesWithoutRequestBodiesAndAcceptsNoContent() async throws {
        var capturedRequests: [URLRequest] = []
        MockURLProtocol.requestHandler = { request in
            capturedRequests.append(request)
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 204,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, Data())
        }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let repository = URLSessionEventRepository(
            baseURL: URL(string: "https://example.test")!,
            session: session
        )

        try await repository.deleteEvent(eventId: 1)
        try await repository.deleteRecurrenceEvent(recurrenceId: 2)
        try await repository.deleteRecurrenceOccurrence(
            recurrenceId: 2,
            originStartAt: Date(timeIntervalSince1970: 0)
        )

        #expect(capturedRequests.map { $0.url?.absoluteString } == [
            "https://example.test/api/events/1",
            "https://example.test/api/recurrence-events/2",
            "https://example.test/api/recurrence-events/2/occurrences?originStartAt=1970-01-01T00:00:00Z"
        ])
        #expect(capturedRequests.allSatisfy { $0.httpMethod == "DELETE" })
        #expect(capturedRequests.allSatisfy { requestBodyData(from: $0) == nil })
    }

    @Test func urlSessionTagRepositoryManagesCustomTagsWithInjectedBaseURLAndContractBody() async throws {
        let tagResponseJSON = """
        {
          "id": 9,
          "title": "운동",
          "colorCode": "#10B981",
          "tagType": "CUSTOM"
        }
        """.data(using: .utf8)!
        var capturedRequests: [URLRequest] = []
        MockURLProtocol.requestHandler = { request in
            capturedRequests.append(request)
            let statusCode = request.httpMethod == "POST" ? 201 : 200
            let data = request.httpMethod == "DELETE" ? Data() : tagResponseJSON
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: request.httpMethod == "DELETE" ? 204 : statusCode,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, data)
        }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let repository = URLSessionTagRepository(
            baseURL: URL(string: "https://example.test")!,
            session: session
        )

        let createdTag = try await repository.createCustomTag(
            CustomTagRequestDTO(title: "운동", colorCode: "#10B981")
        )
        let updatedTag = try await repository.updateCustomTag(
            tagId: 9,
            request: CustomTagRequestDTO(title: "운동", colorCode: "#10B981")
        )
        try await repository.deleteCustomTag(tagId: 9)

        #expect(createdTag.tagType == .custom)
        #expect(updatedTag.id == 9)
        #expect(capturedRequests.map { $0.url?.absoluteString } == [
            "https://example.test/api/custom-tags",
            "https://example.test/api/custom-tags/9",
            "https://example.test/api/custom-tags/9"
        ])
        #expect(capturedRequests.map { $0.httpMethod ?? "" } == ["POST", "PUT", "DELETE"])

        let createBody = try #require(requestBodyData(from: capturedRequests[0]))
        let updateBody = try #require(requestBodyData(from: capturedRequests[1]))
        let createObject = try #require(JSONSerialization.jsonObject(with: createBody) as? [String: Any])
        let updateObject = try #require(JSONSerialization.jsonObject(with: updateBody) as? [String: Any])

        #expect(createObject["title"] as? String == "운동")
        #expect(createObject["colorCode"] as? String == "#10B981")
        #expect(Set(createObject.keys) == ["title", "colorCode"])
        #expect(updateObject["title"] as? String == "운동")
        #expect(updateObject["colorCode"] as? String == "#10B981")
        #expect(requestBodyData(from: capturedRequests[2]) == nil)
    }

    @Test func urlSessionNationalHolidayRepositoryFetchesWithLocalDateQuery() async throws {
        let responseJSON = """
        [
          {
            "nationalHolidayId": 1,
            "holidayDate": "2026-06-06",
            "holidayTitle": "현충일"
          }
        ]
        """.data(using: .utf8)!
        var capturedRequest: URLRequest?
        MockURLProtocol.requestHandler = { request in
            capturedRequest = request
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, responseJSON)
        }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let repository = URLSessionNationalHolidayRepository(
            baseURL: URL(string: "https://example.test")!,
            session: session
        )

        let response = try await repository.fetchNationalHolidays(
            from: DayKey(year: 2026, month: 6, day: 1),
            to: DayKey(year: 2026, month: 6, day: 30)
        )
        let request = try #require(capturedRequest)

        #expect(response.map(\.holidayTitle) == ["현충일"])
        #expect(request.url?.absoluteString == "https://example.test/api/national-holidays?from=2026-06-01&to=2026-06-30")
        #expect(request.httpMethod == "GET")
        #expect(request.value(forHTTPHeaderField: "Accept") == "application/json")
    }

    @MainActor
    @Test func calendarHomeViewModelLoadsHolidaysIntoSeparateDateCellItems() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let holidayRepository = RecordingNationalHolidayRepository(
            responsesByMonth: [
                YearMonthKey(year: 2026, month: 6): [
                    NationalHolidayResponseDTO(
                        nationalHolidayId: 1,
                        holidayDate: "2026-06-06",
                        holidayTitle: "현충일"
                    ),
                    NationalHolidayResponseDTO(
                        nationalHolidayId: 2,
                        holidayDate: "2026-06-06",
                        holidayTitle: "대체 공휴일"
                    )
                ]
            ]
        )
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: RecordingEventRepository()),
            nationalHolidayService: NationalHolidayService(
                repository: holidayRepository,
                calendar: calendar
            ),
            initialReferenceDate: baseDate
        )

        viewModel.loadInitialIfNeeded()
        let didRequestHolidayMonths = await holidayRepository.waitForRequestCount(3)
        let didLoadHolidays = await waitUntil {
            viewModel.state.daysByKey[DayKey(year: 2026, month: 6, day: 6)]?.holidays.count == 2
        }
        let holidayDayItem = viewModel.state.daysByKey[DayKey(year: 2026, month: 6, day: 6)]

        #expect(didRequestHolidayMonths)
        #expect(holidayRepository.requestMonthKeys == [
            YearMonthKey(year: 2026, month: 5),
            YearMonthKey(year: 2026, month: 6),
            YearMonthKey(year: 2026, month: 7)
        ])
        #expect(didLoadHolidays)
        #expect(holidayDayItem?.events.isEmpty == true)
        #expect(holidayDayItem?.holidays.map(\.title) == ["현충일", "대체 공휴일"])
    }

    @MainActor
    @Test func calendarHomeViewModelHolidayFailureKeepsEventAreaUsable() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let event = makeEvent(id: 44, on: baseDate)
        let eventRepository = RecordingEventRepository(fetchResponse: [makeEventResponse(from: event)])
        let holidayRepository = RecordingNationalHolidayRepository(
            error: NationalHolidayRepositoryError.network(URLError(.notConnectedToInternet))
        )
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: eventRepository),
            nationalHolidayService: NationalHolidayService(
                repository: holidayRepository,
                calendar: calendar
            ),
            initialReferenceDate: baseDate
        )

        viewModel.loadInitialIfNeeded()
        let didLoadEvents = await waitUntil {
            viewModel.state.daysByKey[DayKey(date: baseDate, calendar: calendar)]?.events.map(\.backendId) == [44]
        }
        let didStoreHolidayFailure = await waitUntil {
            if case .failed = viewModel.state.monthHolidayCache[YearMonthKey(year: 2026, month: 6)] {
                return true
            }

            return false
        }

        #expect(didLoadEvents)
        #expect(didStoreHolidayFailure)
        #expect(viewModel.eventLoadState == .idle)
        #expect(viewModel.state.daysByKey[DayKey(date: baseDate, calendar: calendar)]?.holidays.isEmpty == true)
    }

    @MainActor
    @Test func calendarHomeViewModelInsertsCreatedEventIntoStartAtMonthCache() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let startAt = try #require(calendar.date(byAdding: DateComponents(day: 1, hour: 9), to: baseDate))
        let createdEvent = makeEvent(id: 99, title: "생성된 일정", on: startAt)
        let repository = RecordingEventRepository(createResponse: makeEventResponse(from: createdEvent))
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(dayOffsets: 0...2, from: baseDate, calendar: calendar)
        )

        let didCreate = await viewModel.createEvent(
            EventCreateInput(
                title: createdEvent.title,
                description: createdEvent.description,
                startAt: createdEvent.startAt,
                endAt: createdEvent.endAt
            )
        )
        let eventDay = DayKey(date: createdEvent.startAt, calendar: calendar)

        #expect(didCreate)
        #expect(viewModel.state.daysByKey[eventDay]?.events.map(\.backendId) == [99])
        #expect(viewModel.createState == .idle)
    }

    @MainActor
    @Test func calendarHomeViewModelDoesNotTreatCreatedEventInIdleMonthAsLoadedCache() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let startAt = try #require(calendar.date(byAdding: DateComponents(day: 5, hour: 9), to: baseDate))
        let createdEvent = makeEvent(id: 100, title: "범위 밖 일정", on: startAt)
        let repository = RecordingEventRepository(createResponse: makeEventResponse(from: createdEvent))
        let initialState = makeLoadedState(dayOffsets: 0...2, from: baseDate, calendar: calendar)
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: initialState
        )

        let didCreate = await viewModel.createEvent(
            EventCreateInput(
                title: createdEvent.title,
                description: createdEvent.description,
                startAt: createdEvent.startAt,
                endAt: createdEvent.endAt
            )
        )
        let monthKey = YearMonthKey(date: createdEvent.startAt, calendar: calendar)

        #expect(didCreate)
        #expect(repository.requests.isEmpty)
        #expect(viewModel.state.startDate == initialState.startDate)
        #expect(viewModel.state.endDate == initialState.endDate)
        #expect(viewModel.state.daysByKey.count == initialState.daysByKey.count)
        #expect(!viewModel.state.monthEventCache.keys.contains(monthKey))
    }

    @MainActor
    @Test func calendarHomeViewModelDoesNotReplaceLoadingMonthCacheWhenCreatedEventArrives() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let startAt = try #require(calendar.date(byAdding: DateComponents(day: 1, hour: 9), to: baseDate))
        let createdEvent = makeEvent(id: 101, title: "로딩 중 생성된 일정", on: startAt)
        let monthKey = YearMonthKey(date: createdEvent.startAt, calendar: calendar)
        let repository = RecordingEventRepository(createResponse: makeEventResponse(from: createdEvent))
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(
                dayOffsets: 0...2,
                from: baseDate,
                calendar: calendar,
                monthEventCache: [monthKey: .loading]
            )
        )

        let didCreate = await viewModel.createEvent(
            EventCreateInput(
                title: createdEvent.title,
                description: createdEvent.description,
                startAt: createdEvent.startAt,
                endAt: createdEvent.endAt
            )
        )

        #expect(didCreate)
        #expect(viewModel.state.monthEventCache[monthKey]?.isLoading == true)
        #expect(viewModel.state.monthEventCache[monthKey]?.loadedEvents.isEmpty == true)
        #expect(viewModel.state.daysByKey[DayKey(date: createdEvent.startAt, calendar: calendar)]?.events.map(\.backendId) == [101])
    }

    @MainActor
    @Test func calendarHomeViewModelKeepsFailureStateAndMapsBackendCreateError() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository(
            createError: EventRepositoryError.backend(
                statusCode: 400,
                response: ErrorResponseDTO(
                    errorCode: "INVALID_TIME_RANGE",
                    message: "invalid"
                )
            )
        )
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(dayOffsets: 0...2, from: baseDate, calendar: calendar)
        )

        let didCreate = await viewModel.createEvent(
            EventCreateInput(
                title: "실패 일정",
                description: "입력은 보존되어야 함",
                startAt: baseDate,
                endAt: baseDate
            )
        )

        #expect(!didCreate)
        #expect(viewModel.createState.failureMessage == "종료 시각은 시작 시각보다 늦어야 합니다.")
    }

    @MainActor
    @Test func calendarHomeViewModelClearsFailureOnRetryAndBlocksDuplicateCreateWhileSaving() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository(
            createResponse: makeEventResponse(from: makeEvent(on: baseDate)),
            createError: EventRepositoryError.network(URLError(.notConnectedToInternet)),
            shouldSuspendCreate: true
        )
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(dayOffsets: 0...2, from: baseDate, calendar: calendar)
        )
        let input = EventCreateInput(
            title: "재시도 일정",
            description: "",
            startAt: baseDate,
            endAt: baseDate.addingTimeInterval(3600)
        )

        let didFailCreate = await viewModel.createEvent(input)
        repository.setCreateError(nil)

        let createTask = Task {
            await viewModel.createEvent(input)
        }
        let didStartCreate = await repository.waitForCreateRequestCount(2)
        let didDuplicateCreate = await viewModel.createEvent(input)

        #expect(!didFailCreate)
        #expect(didStartCreate)
        #expect(viewModel.createState == .saving)
        #expect(!didDuplicateCreate)
        #expect(repository.createRequests.count == 2)

        repository.finishSuspendedCreateRequests()
        _ = await createTask.value
    }

    @MainActor
    @Test func calendarHomeViewModelRefetchesDefaultRangeAfterRecurrenceCreateWithoutSynthesizingEvents() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let cachedEvent = makeEvent(id: 10, title: "기존 일정", on: baseDate)
        let repository = RecordingEventRepository()
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(
                dayOffsets: 0...2,
                from: baseDate,
                calendar: calendar,
                monthEventCache: [
                    YearMonthKey(year: 2026, month: 5): .loaded([]),
                    YearMonthKey(year: 2026, month: 6): .loaded([cachedEvent]),
                    YearMonthKey(year: 2026, month: 7): .loaded([])
                ]
            )
        )

        let didCreate = await viewModel.createEvent(
            .recurring(
                RecurrenceEventCreateInput(
                    title: "반복 회의",
                    description: "설명",
                    recurrenceStartDate: baseDate,
                    recurrenceEndDate: baseDate.addingTimeInterval(86400),
                    recurrenceStartTime: baseDate,
                    recurrenceEndTime: baseDate.addingTimeInterval(3600),
                    recurrenceFrequency: .daily
                )
            )
        )
        let didRequestDefaultRange = await repository.waitForRequestCount(3)
        await Task.yield()

        #expect(didCreate)
        #expect(didRequestDefaultRange)
        #expect(repository.createRequests.isEmpty)
        #expect(repository.recurrenceCreateRequests.count == 1)
        #expect(repository.requestMonthKeys(calendar: calendar) == [
            YearMonthKey(year: 2026, month: 5),
            YearMonthKey(year: 2026, month: 6),
            YearMonthKey(year: 2026, month: 7)
        ])
        #expect(viewModel.state.daysByKey[DayKey(date: baseDate, calendar: calendar)]?.events.isEmpty == true)
    }

    @MainActor
    @Test func calendarHomeViewModelRefetchesOriginalAndUpdatedMonthsAfterSingleEventUpdate() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let updatedStartAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 2, hour: 9)))
        let originalEvent = makeEvent(id: 501, title: "월 이동 일정", on: baseDate)
        let updatedEvent = Event(
            id: originalEvent.backendId,
            title: "수정된 일정",
            description: "수정",
            startAt: updatedStartAt,
            endAt: updatedStartAt.addingTimeInterval(3600),
            tag: .sample(colorCode: "#4F46E5")
        )
        let repository = RecordingEventRepository(
            updateResponse: makeEventResponse(from: updatedEvent)
        )
        let initialState = makeLoadedState(
            dayOffsets: 0...40,
            from: baseDate,
            calendar: calendar,
            monthEventCache: [
                YearMonthKey(year: 2026, month: 6): .loaded([originalEvent]),
                YearMonthKey(year: 2026, month: 7): .loaded([])
            ]
        )
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: initialState
        )

        let didUpdate = await viewModel.updateSingleEvent(
            originalEvent,
            input: EventUpdateInput(
                title: updatedEvent.title,
                description: updatedEvent.description,
                startAt: updatedEvent.startAt,
                endAt: updatedEvent.endAt
            )
        )
        let didRequestAffectedMonths = await repository.waitForRequestCount(2)

        #expect(didUpdate)
        #expect(didRequestAffectedMonths)
        #expect(repository.updateRequests.count == 1)
        #expect(repository.requestMonthKeys(calendar: calendar) == [
            YearMonthKey(year: 2026, month: 6),
            YearMonthKey(year: 2026, month: 7)
        ])
        #expect(viewModel.mutationState == .idle)
    }

    @MainActor
    @Test func calendarHomeViewModelEventMutationDoesNotInvalidateLoadedHolidayCache() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let event = makeEvent(id: 601, title: "수정 대상", on: baseDate)
        let holiday = NationalHoliday(
            id: 1,
            day: DayKey(date: baseDate, calendar: calendar),
            title: "공휴일"
        )
        let eventRepository = RecordingEventRepository(
            updateResponse: makeEventResponse(from: event)
        )
        let holidayRepository = RecordingNationalHolidayRepository()
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: eventRepository),
            nationalHolidayService: NationalHolidayService(
                repository: holidayRepository,
                calendar: calendar
            ),
            initialState: makeLoadedState(
                dayOffsets: 0...2,
                from: baseDate,
                calendar: calendar,
                monthEventCache: [YearMonthKey(year: 2026, month: 6): .loaded([event])],
                monthHolidayCache: [YearMonthKey(year: 2026, month: 6): .loaded([holiday])]
            )
        )

        let didUpdate = await viewModel.updateSingleEvent(
            event,
            input: EventUpdateInput(
                title: "수정 대상",
                description: "",
                startAt: event.startAt,
                endAt: event.endAt
            )
        )
        let didRequestEventMonth = await eventRepository.waitForRequestCount(1)

        #expect(didUpdate)
        #expect(didRequestEventMonth)
        #expect(holidayRepository.requestCount == 0)
        #expect(viewModel.state.monthHolidayCache[YearMonthKey(year: 2026, month: 6)]?.loadedHolidays.map(\.id) == [1])
        #expect(viewModel.state.daysByKey[holiday.day]?.holidays.map(\.id) == [1])
    }

    @MainActor
    @Test func calendarHomeViewModelRefetchesOriginalMonthAfterSingleEventDelete() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let event = makeEvent(id: 502, title: "삭제 일정", on: baseDate)
        let repository = RecordingEventRepository()
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(
                dayOffsets: 0...2,
                from: baseDate,
                calendar: calendar,
                monthEventCache: [YearMonthKey(year: 2026, month: 6): .loaded([event])]
            )
        )

        let didDelete = await viewModel.deleteSingleEvent(event)
        let didRequestOriginalMonth = await repository.waitForRequestCount(1)

        #expect(didDelete)
        #expect(didRequestOriginalMonth)
        #expect(repository.deleteEventIDs == [502])
        #expect(repository.requestMonthKeys(calendar: calendar) == [
            YearMonthKey(year: 2026, month: 6)
        ])
    }

    @MainActor
    @Test func calendarHomeViewModelRefetchesDefaultRangeAfterRecurrenceDelete() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let recurrenceEvent = Event(
            id: 503,
            title: "반복 일정",
            description: "",
            startAt: baseDate,
            endAt: baseDate.addingTimeInterval(3600),
            tag: .sample(colorCode: "#4F46E5"),
            recurrenceId: 700,
            isRecurrenceOccurrence: true,
            originStartAt: baseDate
        )
        let repository = RecordingEventRepository()
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(
                dayOffsets: 0...2,
                from: baseDate,
                calendar: calendar,
                monthEventCache: [
                    YearMonthKey(year: 2026, month: 5): .loaded([]),
                    YearMonthKey(year: 2026, month: 6): .loaded([recurrenceEvent]),
                    YearMonthKey(year: 2026, month: 7): .loaded([])
                ]
            )
        )

        let didDeleteOccurrence = await viewModel.deleteRecurrenceOccurrence(recurrenceEvent)
        let didRequestDefaultRange = await repository.waitForRequestCount(3)

        #expect(didDeleteOccurrence)
        #expect(didRequestDefaultRange)
        #expect(repository.deleteRecurrenceOccurrenceRequests.count == 1)
        #expect(repository.deleteRecurrenceOccurrenceRequests.first?.originStartAt == baseDate)
        #expect(repository.requestMonthKeys(calendar: calendar) == [
            YearMonthKey(year: 2026, month: 5),
            YearMonthKey(year: 2026, month: 6),
            YearMonthKey(year: 2026, month: 7)
        ])
        #expect(viewModel.state.daysByKey[DayKey(date: baseDate, calendar: calendar)]?.events.isEmpty == true)
    }

    @MainActor
    @Test func calendarHomeViewModelFetchesRecurrenceRuleBeforeSeriesEdit() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository(
            fetchRecurrenceResponse: RecurrenceEventResponseDTO(
                recurrenceId: 700,
                recurrenceTitle: "반복 회의",
                recurrenceDescription: "설명",
                recurrenceStartDate: "2026-08-01",
                recurrenceEndDate: "2026-08-31",
                recurrenceStartTime: "00:00:00",
                recurrenceEndTime: "01:00:00",
                recurrenceFrequency: .weekly
            )
        )
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(dayOffsets: 0...2, from: baseDate, calendar: calendar)
        )

        let details = await viewModel.fetchRecurrenceEvent(recurrenceId: 700)

        #expect(repository.fetchRecurrenceEventIDs == [700])
        #expect(details?.title == "반복 회의")
        #expect(details?.recurrenceFrequency == .weekly)
        #expect(viewModel.mutationState == .idle)
    }

    @MainActor
    @Test func calendarHomeViewModelRecurrenceOccurrenceUpdateSendsOriginStartAtAndRefetchesDefaultRange() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let event = Event(
            id: 701,
            title: "반복 항목",
            description: "",
            startAt: baseDate,
            endAt: baseDate.addingTimeInterval(3600),
            tag: .sample(colorCode: "#4F46E5"),
            importantEvent: true,
            recurrenceId: 700,
            isRecurrenceOccurrence: true,
            originStartAt: baseDate
        )
        let repository = RecordingEventRepository()
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(
                dayOffsets: 0...2,
                from: baseDate,
                calendar: calendar,
                monthEventCache: [
                    YearMonthKey(year: 2026, month: 5): .loaded([]),
                    YearMonthKey(year: 2026, month: 6): .loaded([event]),
                    YearMonthKey(year: 2026, month: 7): .loaded([])
                ]
            )
        )

        let didUpdate = await viewModel.updateRecurrenceOccurrence(
            event,
            input: EventUpdateInput(
                title: "수정 반복 항목",
                description: "설명",
                startAt: baseDate,
                endAt: baseDate.addingTimeInterval(7200)
            )
        )
        let didRequestDefaultRange = await repository.waitForRequestCount(3)
        let request = try #require(repository.updateRecurrenceOccurrenceRequests.first)

        #expect(didUpdate)
        #expect(didRequestDefaultRange)
        #expect(request.recurrenceId == 700)
        #expect(request.request.originStartAt == baseDate)
        #expect(request.request.endAt == baseDate.addingTimeInterval(7200))
        #expect(repository.requestMonthKeys(calendar: calendar) == [
            YearMonthKey(year: 2026, month: 5),
            YearMonthKey(year: 2026, month: 6),
            YearMonthKey(year: 2026, month: 7)
        ])
    }

    @MainActor
    @Test func calendarHomeViewModelRecurrenceSeriesUpdateComposesUTCDateTimeAndRefetchesDefaultRange() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository()
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(
                dayOffsets: 0...2,
                from: baseDate,
                calendar: calendar,
                monthEventCache: [
                    YearMonthKey(year: 2026, month: 5): .loaded([]),
                    YearMonthKey(year: 2026, month: 6): .loaded([]),
                    YearMonthKey(year: 2026, month: 7): .loaded([])
                ]
            )
        )
        let startDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 8, day: 1)))
        let endDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 8, day: 31)))
        let startTime = try #require(calendar.date(from: DateComponents(year: 1970, month: 1, day: 1, hour: 9)))
        let endTime = try #require(calendar.date(from: DateComponents(year: 1970, month: 1, day: 1, hour: 10, minute: 30)))

        let didUpdate = await viewModel.updateRecurrenceSeries(
            recurrenceId: 700,
            input: RecurrenceEventSeriesEditInput(
                title: "수정 반복 일정",
                description: "설명",
                recurrenceStartDate: startDate,
                recurrenceEndDate: endDate,
                recurrenceStartTime: startTime,
                recurrenceEndTime: endTime,
                recurrenceFrequency: .monthly
            )
        )
        let didRequestDefaultRange = await repository.waitForRequestCount(3)
        let request = try #require(repository.updateRecurrenceEventRequests.first)

        #expect(didUpdate)
        #expect(didRequestDefaultRange)
        #expect(request.recurrenceId == 700)
        #expect(request.request.title == "수정 반복 일정")
        #expect(request.request.recurrenceFrequency == .monthly)
        #expect(request.request.startDate == "2026-08-01")
        #expect(request.request.endDate == "2026-08-31")
        #expect(request.request.startTime == "09:00:00")
        #expect(request.request.endTime == "10:30:00")
    }
    
    private var fixedCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    @MainActor
    private func waitUntil(
        timeoutNanoseconds: UInt64 = 5_000_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async -> Bool {
        let retryIntervalNanoseconds: UInt64 = 10_000_000
        let maxAttemptCount = Int(timeoutNanoseconds / retryIntervalNanoseconds)

        for _ in 0...maxAttemptCount {
            if condition() {
                return true
            }

            try? await Task.sleep(nanoseconds: retryIntervalNanoseconds)
        }

        return condition()
    }

    private func makeDayKey(dayOffset: Int, from baseDate: Date, calendar: Calendar) -> DayKey {
        let date = calendar.date(byAdding: .day, value: dayOffset, to: baseDate) ?? baseDate
        return DayKey(date: date, calendar: calendar)
    }
    
    private func makeDateCellItem(
        dayOffset: Int,
        from baseDate: Date,
        calendar: Calendar,
        events: [Event] = [],
        holidays: [NationalHoliday] = []
    ) -> CalendarDayItem {
        let date = calendar.date(byAdding: .day, value: dayOffset, to: baseDate) ?? baseDate
        let dateService = CalendarDateService(calendar: calendar)
        
        return CalendarDayItem(
            id: DayKey(date: date, calendar: calendar),
            weekday: dateService.getWeekday(from: date),
            monthText: dateService.monthText(from: date),
            dayText: dateService.dayText(from: date),
            isToday: false,
            events: events,
            holidays: holidays
        )
    }

    private func makeDateCellItem(
        date: Date,
        calendar: Calendar,
        events: [Event] = [],
        holidays: [NationalHoliday] = []
    ) -> CalendarDayItem {
        let dateService = CalendarDateService(calendar: calendar)

        return CalendarDayItem(
            id: DayKey(date: date, calendar: calendar),
            weekday: dateService.getWeekday(from: date),
            monthText: dateService.monthText(from: date),
            dayText: dateService.dayText(from: date),
            isToday: false,
            events: events,
            holidays: holidays
        )
    }

    private func makeMonthGridDays(
        referenceDate: Date,
        calendar: Calendar
    ) -> [DayKey] {
        let monthComponents = calendar.dateComponents([.year, .month], from: referenceDate)
        guard let firstDayOfMonth = calendar.date(from: monthComponents) else {
            return []
        }

        let firstWeekdayIndex = calendar.component(.weekday, from: firstDayOfMonth) - 1
        guard let gridStartDate = calendar.date(
            byAdding: .day,
            value: firstWeekdayIndex * -1,
            to: firstDayOfMonth
        ) else {
            return []
        }

        return (0..<42).compactMap { offset in
            guard let date = calendar.date(
                byAdding: .day,
                value: offset,
                to: gridStartDate
            ) else {
                return nil
            }

            return DayKey(date: date, calendar: calendar)
        }
    }

    private func makeTimelineMetrics() -> TimelineMetrics {
        TimelineMetrics(
            timeColumnWidth: 56,
            dayColumnWidth: 100,
            topBarHeight: 50,
            headerHeight: 90,
            fullDayEventRowHeight: 52,
            hourHeight: 60,
            visibleDayCount: 5,
            hourCount: 24
        )
    }

    private func makeLoadedState(
        dayOffsets: ClosedRange<Int>,
        from baseDate: Date,
        calendar: Calendar,
        monthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry] = [:],
        monthHolidayCache: [YearMonthKey: CalendarMonthHolidayCacheEntry] = [:]
    ) -> CalendarState {
        let holidaysByDay = monthHolidayCache.values
            .flatMap(\.loadedHolidays)
            .reduce(into: [DayKey: [NationalHoliday]]()) { result, holiday in
                result[holiday.day, default: []].append(holiday)
            }
        let items = dayOffsets.map { offset in
            let day = makeDayKey(dayOffset: offset, from: baseDate, calendar: calendar)
            return makeDateCellItem(
                dayOffset: offset,
                from: baseDate,
                calendar: calendar,
                holidays: holidaysByDay[day] ?? []
            )
        }
        let startDate = calendar.date(byAdding: .day, value: dayOffsets.lowerBound, to: baseDate) ?? baseDate
        let endDate = calendar.date(byAdding: .day, value: dayOffsets.upperBound, to: baseDate) ?? baseDate

        return CalendarState(
            startDate: startDate,
            endDate: endDate,
            daysByKey: Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) }),
            monthEventCache: monthEventCache,
            monthHolidayCache: monthHolidayCache
        )
    }
    
    private func makeEvent(
        id: Int64 = 1,
        title: String = "테스트 일정",
        on date: Date
    ) -> Event {
        Event(
            id: id,
            title: title,
            description: "",
            startAt: date,
            endAt: date.addingTimeInterval(3600),
            tag: .sample(colorCode: "#4F46E5")
        )
    }

    private func makeEvent(
        id: Int64,
        title: String,
        startAt: Date,
        endAt: Date
    ) -> Event {
        Event(
            id: id,
            title: title,
            description: "",
            startAt: startAt,
            endAt: endAt,
            tag: .sample(colorCode: "#4F46E5")
        )
    }

    private func makeEventResponse(from event: Event) -> EventResponseDTO {
        EventResponseDTO(
            id: event.backendId,
            title: event.title,
            description: event.description,
            startAt: event.startAt,
            endAt: event.endAt,
            tag: TagResponseDTO(
                id: event.tag.id,
                title: event.tag.title,
                colorCode: event.tag.colorCode,
                tagType: event.tag.tagType
            ),
            createdAt: event.startAt,
            updatedAt: event.startAt
        )
    }

    private func requestBodyData(from request: URLRequest) -> Data? {
        if let httpBody = request.httpBody {
            return httpBody
        }

        guard let stream = request.httpBodyStream else {
            return nil
        }

        stream.open()
        defer {
            stream.close()
        }

        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 1024)

        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)

            guard count > 0 else {
                break
            }

            data.append(buffer, count: count)
        }

        return data
    }

    private func makeNationalHolidayService(calendar: Calendar) -> NationalHolidayService {
        NationalHolidayService(
            repository: RecordingNationalHolidayRepository(),
            calendar: calendar
        )
    }

}

private final class RecordingNationalHolidayRepository: NationalHolidayRepository {
    private let lock = NSLock()
    private var storedRequests: [(startDay: DayKey, endDay: DayKey)] = []
    private var requestCountWaiters: [CountWaiter] = []
    private let fetchResponse: [NationalHolidayResponseDTO]
    private let responsesByMonth: [YearMonthKey: [NationalHolidayResponseDTO]]
    private let error: Error?

    init(
        fetchResponse: [NationalHolidayResponseDTO] = [],
        responsesByMonth: [YearMonthKey: [NationalHolidayResponseDTO]] = [:],
        error: Error? = nil
    ) {
        self.fetchResponse = fetchResponse
        self.responsesByMonth = responsesByMonth
        self.error = error
    }

    var requestCount: Int {
        locked {
            storedRequests.count
        }
    }

    var requestMonthKeys: [YearMonthKey] {
        locked {
            storedRequests.map { YearMonthKey(day: $0.startDay) }.sorted()
        }
    }

    func fetchNationalHolidays(
        from startDay: DayKey,
        to endDay: DayKey
    ) async throws -> [NationalHolidayResponseDTO] {
        recordRequest(startDay: startDay, endDay: endDay)

        if let error {
            throw error
        }

        return responsesByMonth[YearMonthKey(day: startDay)] ?? fetchResponse
    }

    func waitForRequestCount(
        _ count: Int,
        timeoutNanoseconds: UInt64 = 5_000_000_000
    ) async -> Bool {
        let waiterID = UUID()

        return await withCheckedContinuation { continuation in
            let shouldWait = locked {
                guard storedRequests.count < count else {
                    return false
                }

                requestCountWaiters.append(
                    CountWaiter(
                        id: waiterID,
                        count: count,
                        continuation: continuation
                    )
                )
                return true
            }

            guard shouldWait else {
                continuation.resume(returning: true)
                return
            }

            Task {
                try? await Task.sleep(nanoseconds: timeoutNanoseconds)
                completeWaiterIfNeeded(id: waiterID)
            }
        }
    }

    private func recordRequest(startDay: DayKey, endDay: DayKey) {
        let continuations = locked {
            storedRequests.append((startDay, endDay))
            return readyWaiters(currentCount: storedRequests.count)
        }
        continuations.forEach { $0.resume(returning: true) }
    }

    private func completeWaiterIfNeeded(id: UUID) {
        let continuation = locked {
            guard let index = requestCountWaiters.firstIndex(where: { $0.id == id }) else {
                return nil as CheckedContinuation<Bool, Never>?
            }

            return requestCountWaiters.remove(at: index).continuation
        }

        continuation?.resume(returning: false)
    }

    private func readyWaiters(currentCount: Int) -> [CheckedContinuation<Bool, Never>] {
        let readyWaiters = requestCountWaiters.filter { currentCount >= $0.count }
        requestCountWaiters.removeAll { currentCount >= $0.count }
        return readyWaiters.map(\.continuation)
    }

    private func locked<T>(_ work: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return work()
    }

    private struct CountWaiter {
        let id: UUID
        let count: Int
        let continuation: CheckedContinuation<Bool, Never>
    }
}

private final class RecordingEventRepository: EventRepository {
    private let lock = NSLock()
    private var storedRequests: [(startDate: Date, endDate: Date)] = []
    private var storedCreateRequests: [CreateEventRequestDTO] = []
    private var storedRecurrenceCreateRequests: [CreateRecurrenceEventRequestDTO] = []
    private var storedFetchRecurrenceEventIDs: [Int64] = []
    private var storedUpdateRequests: [(eventId: Int64, request: UpdateEventRequestDTO)] = []
    private var storedUpdateRecurrenceEventRequests: [(recurrenceId: Int64, request: UpdateRecurrenceEventRequestDTO)] = []
    private var storedUpdateRecurrenceOccurrenceRequests: [(recurrenceId: Int64, request: UpdateRecurrenceOccurrenceRequestDTO)] = []
    private var storedDeleteEventIDs: [Int64] = []
    private var storedDeleteRecurrenceEventIDs: [Int64] = []
    private var storedDeleteRecurrenceOccurrenceRequests: [(recurrenceId: Int64, originStartAt: Date)] = []
    private var suspendedContinuations: [CheckedContinuation<[EventResponseDTO], Error>] = []
    private var suspendedCreateContinuations: [CheckedContinuation<EventResponseDTO, Error>] = []
    private var requestCountWaiters: [CountWaiter] = []
    private var createRequestCountWaiters: [CountWaiter] = []
    private let shouldSuspend: Bool
    private let shouldSuspendCreate: Bool
    private let error: Error?
    private var createError: Error?
    private let createResponse: EventResponseDTO
    private let updateResponse: EventResponseDTO
    private let updateError: Error?
    private let deleteError: Error?
    private let recurrenceDeleteError: Error?
    private let recurrenceOccurrenceDeleteError: Error?
    private let fetchResponse: [EventResponseDTO]
    private let recurrenceCreateResponse: RecurrenceEventResponseDTO
    private let recurrenceCreateError: Error?
    private let fetchRecurrenceResponse: RecurrenceEventResponseDTO
    private let fetchRecurrenceError: Error?
    private let updateRecurrenceResponse: RecurrenceEventResponseDTO
    private let updateRecurrenceError: Error?
    private let updateRecurrenceOccurrenceResponse: EventResponseDTO
    private let updateRecurrenceOccurrenceError: Error?

    init(
        shouldSuspend: Bool = false,
        error: Error? = nil,
        fetchResponse: [EventResponseDTO] = [],
        createResponse: EventResponseDTO = EventResponseDTO(
            id: 1,
            title: "생성된 일정",
            description: "",
            startAt: Date(timeIntervalSince1970: 0),
            endAt: Date(timeIntervalSince1970: 3600),
            createdAt: Date(timeIntervalSince1970: 0),
            updatedAt: Date(timeIntervalSince1970: 0)
        ),
        updateResponse: EventResponseDTO = EventResponseDTO(
            id: 1,
            title: "수정된 일정",
            description: "",
            startAt: Date(timeIntervalSince1970: 0),
            endAt: Date(timeIntervalSince1970: 3600),
            createdAt: Date(timeIntervalSince1970: 0),
            updatedAt: Date(timeIntervalSince1970: 3600)
        ),
        createError: Error? = nil,
        updateError: Error? = nil,
        deleteError: Error? = nil,
        shouldSuspendCreate: Bool = false,
        recurrenceCreateResponse: RecurrenceEventResponseDTO = RecurrenceEventResponseDTO(
            recurrenceId: 1,
            recurrenceTitle: "반복 일정",
            recurrenceDescription: "",
            recurrenceStartDate: "1970-01-01",
            recurrenceEndDate: "1970-01-01",
            recurrenceStartTime: "00:00:00",
            recurrenceEndTime: "01:00:00",
            recurrenceFrequency: .daily
        ),
        recurrenceCreateError: Error? = nil,
        fetchRecurrenceResponse: RecurrenceEventResponseDTO = RecurrenceEventResponseDTO(
            recurrenceId: 1,
            recurrenceTitle: "반복 일정",
            recurrenceDescription: "",
            recurrenceStartDate: "1970-01-01",
            recurrenceEndDate: "1970-01-01",
            recurrenceStartTime: "00:00:00",
            recurrenceEndTime: "01:00:00",
            recurrenceFrequency: .daily
        ),
        fetchRecurrenceError: Error? = nil,
        updateRecurrenceResponse: RecurrenceEventResponseDTO = RecurrenceEventResponseDTO(
            recurrenceId: 1,
            recurrenceTitle: "수정된 반복 일정",
            recurrenceDescription: "",
            recurrenceStartDate: "1970-01-01",
            recurrenceEndDate: "1970-01-01",
            recurrenceStartTime: "00:00:00",
            recurrenceEndTime: "01:00:00",
            recurrenceFrequency: .daily
        ),
        updateRecurrenceError: Error? = nil,
        updateRecurrenceOccurrenceResponse: EventResponseDTO = EventResponseDTO(
            id: 1,
            title: "수정된 반복 항목",
            description: "",
            startAt: Date(timeIntervalSince1970: 0),
            endAt: Date(timeIntervalSince1970: 3600),
            createdAt: Date(timeIntervalSince1970: 0),
            updatedAt: Date(timeIntervalSince1970: 3600)
        ),
        updateRecurrenceOccurrenceError: Error? = nil,
        recurrenceDeleteError: Error? = nil,
        recurrenceOccurrenceDeleteError: Error? = nil
    ) {
        self.shouldSuspend = shouldSuspend
        self.shouldSuspendCreate = shouldSuspendCreate
        self.error = error
        self.fetchResponse = fetchResponse
        self.createError = createError
        self.createResponse = createResponse
        self.updateResponse = updateResponse
        self.updateError = updateError
        self.deleteError = deleteError
        self.recurrenceCreateResponse = recurrenceCreateResponse
        self.recurrenceCreateError = recurrenceCreateError
        self.fetchRecurrenceResponse = fetchRecurrenceResponse
        self.fetchRecurrenceError = fetchRecurrenceError
        self.updateRecurrenceResponse = updateRecurrenceResponse
        self.updateRecurrenceError = updateRecurrenceError
        self.updateRecurrenceOccurrenceResponse = updateRecurrenceOccurrenceResponse
        self.updateRecurrenceOccurrenceError = updateRecurrenceOccurrenceError
        self.recurrenceDeleteError = recurrenceDeleteError
        self.recurrenceOccurrenceDeleteError = recurrenceOccurrenceDeleteError
    }

    var requests: [(startDate: Date, endDate: Date)] {
        locked {
            storedRequests
        }
    }

    var createRequests: [CreateEventRequestDTO] {
        locked {
            storedCreateRequests
        }
    }

    var recurrenceCreateRequests: [CreateRecurrenceEventRequestDTO] {
        locked {
            storedRecurrenceCreateRequests
        }
    }

    var fetchRecurrenceEventIDs: [Int64] {
        locked {
            storedFetchRecurrenceEventIDs
        }
    }

    var updateRequests: [(eventId: Int64, request: UpdateEventRequestDTO)] {
        locked {
            storedUpdateRequests
        }
    }

    var updateRecurrenceEventRequests: [(recurrenceId: Int64, request: UpdateRecurrenceEventRequestDTO)] {
        locked {
            storedUpdateRecurrenceEventRequests
        }
    }

    var updateRecurrenceOccurrenceRequests: [(recurrenceId: Int64, request: UpdateRecurrenceOccurrenceRequestDTO)] {
        locked {
            storedUpdateRecurrenceOccurrenceRequests
        }
    }

    var deleteEventIDs: [Int64] {
        locked {
            storedDeleteEventIDs
        }
    }

    var deleteRecurrenceEventIDs: [Int64] {
        locked {
            storedDeleteRecurrenceEventIDs
        }
    }

    var deleteRecurrenceOccurrenceRequests: [(recurrenceId: Int64, originStartAt: Date)] {
        locked {
            storedDeleteRecurrenceOccurrenceRequests
        }
    }

    var requestCount: Int {
        locked {
            storedRequests.count
        }
    }

    func fetchEvents(from startDate: Date, to endDate: Date) async throws -> [EventResponseDTO] {
        recordRequest(startDate: startDate, endDate: endDate)

        if let error {
            throw error
        }

        if shouldSuspend {
            return try await withCheckedThrowingContinuation { continuation in
                locked {
                    suspendedContinuations.append(continuation)
                }
            }
        }

        return fetchResponse
    }

    func createEvent(_ request: CreateEventRequestDTO) async throws -> EventResponseDTO {
        recordCreateRequest(request)

        if let createError = locked({ createError }) {
            throw createError
        }

        if shouldSuspendCreate {
            return try await withCheckedThrowingContinuation { continuation in
                locked {
                    suspendedCreateContinuations.append(continuation)
                }
            }
        }

        return createResponse
    }

    func createRecurrenceEvent(_ request: CreateRecurrenceEventRequestDTO) async throws -> RecurrenceEventResponseDTO {
        locked {
            storedRecurrenceCreateRequests.append(request)
        }

        if let recurrenceCreateError {
            throw recurrenceCreateError
        }

        return recurrenceCreateResponse
    }

    func fetchRecurrenceEvent(recurrenceId: Int64) async throws -> RecurrenceEventResponseDTO {
        locked {
            storedFetchRecurrenceEventIDs.append(recurrenceId)
        }

        if let fetchRecurrenceError {
            throw fetchRecurrenceError
        }

        return fetchRecurrenceResponse
    }

    func updateEvent(eventId: Int64, request: UpdateEventRequestDTO) async throws -> EventResponseDTO {
        locked {
            storedUpdateRequests.append((eventId, request))
        }

        if let updateError {
            throw updateError
        }

        return updateResponse
    }

    func updateRecurrenceEvent(
        recurrenceId: Int64,
        request: UpdateRecurrenceEventRequestDTO
    ) async throws -> RecurrenceEventResponseDTO {
        locked {
            storedUpdateRecurrenceEventRequests.append((recurrenceId, request))
        }

        if let updateRecurrenceError {
            throw updateRecurrenceError
        }

        return updateRecurrenceResponse
    }

    func updateRecurrenceOccurrence(
        recurrenceId: Int64,
        request: UpdateRecurrenceOccurrenceRequestDTO
    ) async throws -> EventResponseDTO {
        locked {
            storedUpdateRecurrenceOccurrenceRequests.append((recurrenceId, request))
        }

        if let updateRecurrenceOccurrenceError {
            throw updateRecurrenceOccurrenceError
        }

        return updateRecurrenceOccurrenceResponse
    }

    func deleteEvent(eventId: Int64) async throws {
        locked {
            storedDeleteEventIDs.append(eventId)
        }

        if let deleteError {
            throw deleteError
        }
    }

    func deleteRecurrenceEvent(recurrenceId: Int64) async throws {
        locked {
            storedDeleteRecurrenceEventIDs.append(recurrenceId)
        }

        if let recurrenceDeleteError {
            throw recurrenceDeleteError
        }
    }

    func deleteRecurrenceOccurrence(recurrenceId: Int64, originStartAt: Date) async throws {
        locked {
            storedDeleteRecurrenceOccurrenceRequests.append((recurrenceId, originStartAt))
        }

        if let recurrenceOccurrenceDeleteError {
            throw recurrenceOccurrenceDeleteError
        }
    }

    func setCreateError(_ error: Error?) {
        locked {
            createError = error
        }
    }

    func waitForRequestCount(
        _ count: Int,
        timeoutNanoseconds: UInt64 = 5_000_000_000
    ) async -> Bool {
        await waitForCount(
            count,
            timeoutNanoseconds: timeoutNanoseconds,
            kind: .fetch
        )
    }

    func finishSuspendedRequests() {
        let continuations = locked {
            let continuations = suspendedContinuations
            suspendedContinuations.removeAll()
            return continuations
        }
        continuations.forEach { continuation in
            continuation.resume(returning: [])
        }
    }

    func waitForCreateRequestCount(
        _ count: Int,
        timeoutNanoseconds: UInt64 = 5_000_000_000
    ) async -> Bool {
        await waitForCount(
            count,
            timeoutNanoseconds: timeoutNanoseconds,
            kind: .create
        )
    }

    func finishSuspendedCreateRequests() {
        let continuations = locked {
            let continuations = suspendedCreateContinuations
            suspendedCreateContinuations.removeAll()
            return continuations
        }
        continuations.forEach { continuation in
            continuation.resume(returning: createResponse)
        }
    }

    func requestMonthKeys(calendar: Calendar) -> [YearMonthKey] {
        requests.map { request in
            YearMonthKey(date: request.startDate, calendar: calendar)
        }.sorted()
    }

    private func recordRequest(startDate: Date, endDate: Date) {
        let continuations = locked {
            storedRequests.append((startDate, endDate))
            return readyWaiters(from: &requestCountWaiters, currentCount: storedRequests.count)
        }
        continuations.forEach { $0.resume(returning: true) }
    }

    private func recordCreateRequest(_ request: CreateEventRequestDTO) {
        let continuations = locked {
            storedCreateRequests.append(request)
            return readyWaiters(
                from: &createRequestCountWaiters,
                currentCount: storedCreateRequests.count
            )
        }
        continuations.forEach { $0.resume(returning: true) }
    }

    private func waitForCount(
        _ count: Int,
        timeoutNanoseconds: UInt64,
        kind: CountWaiterKind
    ) async -> Bool {
        let waiterID = UUID()

        return await withCheckedContinuation { continuation in
            let shouldWait = locked {
                guard currentCount(for: kind) < count else {
                    return false
                }

                appendWaiter(
                    CountWaiter(
                        id: waiterID,
                        count: count,
                        continuation: continuation
                    ),
                    for: kind
                )
                return true
            }

            guard shouldWait else {
                continuation.resume(returning: true)
                return
            }

            Task {
                try? await Task.sleep(nanoseconds: timeoutNanoseconds)
                completeWaiterIfNeeded(id: waiterID, kind: kind)
            }
        }
    }

    private func completeWaiterIfNeeded(
        id: UUID,
        kind: CountWaiterKind
    ) {
        let continuation = locked {
            let waiters = waiters(for: kind)
            guard let index = waiters.firstIndex(where: { $0.id == id }) else {
                return nil as CheckedContinuation<Bool, Never>?
            }

            return removeWaiter(at: index, for: kind).continuation
        }

        continuation?.resume(returning: false)
    }

    private func currentCount(for kind: CountWaiterKind) -> Int {
        switch kind {
        case .fetch:
            return storedRequests.count
        case .create:
            return storedCreateRequests.count
        }
    }

    private func waiters(for kind: CountWaiterKind) -> [CountWaiter] {
        switch kind {
        case .fetch:
            return requestCountWaiters
        case .create:
            return createRequestCountWaiters
        }
    }

    private func appendWaiter(_ waiter: CountWaiter, for kind: CountWaiterKind) {
        switch kind {
        case .fetch:
            requestCountWaiters.append(waiter)
        case .create:
            createRequestCountWaiters.append(waiter)
        }
    }

    private func removeWaiter(at index: Int, for kind: CountWaiterKind) -> CountWaiter {
        switch kind {
        case .fetch:
            return requestCountWaiters.remove(at: index)
        case .create:
            return createRequestCountWaiters.remove(at: index)
        }
    }

    private func readyWaiters(
        from waiters: inout [CountWaiter],
        currentCount: Int
    ) -> [CheckedContinuation<Bool, Never>] {
        let readyWaiters = waiters.filter { currentCount >= $0.count }
        waiters.removeAll { currentCount >= $0.count }
        return readyWaiters.map(\.continuation)
    }

    private func locked<T>(_ work: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return work()
    }

    private struct CountWaiter {
        let id: UUID
        let count: Int
        let continuation: CheckedContinuation<Bool, Never>
    }

    private enum CountWaiterKind {
        case fetch
        case create
    }
}

private final class MockURLProtocol: URLProtocol {
    static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        guard let handler = MockURLProtocol.requestHandler else {
            return
        }

        do {
            let (response, data) = try handler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}
