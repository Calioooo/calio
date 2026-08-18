import Testing
import Foundation
import SwiftUI
@testable import Calio

@Suite(.serialized)
struct CalendarViewTests {

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

    @Test func weekTimelineHeaderAccessibilityKeepsTodayAndSelectionDistinct() async throws {
        #expect(
            CalendarWeekTimelineView.dayHeaderAccessibilityLabel(
                weekday: .monday,
                day: 17,
                isToday: true,
                isSelected: true
            ) == "월요일 17일, 오늘, 선택됨"
        )
        #expect(
            CalendarWeekTimelineView.dayHeaderAccessibilityLabel(
                weekday: .sunday,
                day: 23,
                isToday: false,
                isSelected: false
            ) == "일요일 23일"
        )
    }

    @Test func weekTimelineOverflowAccessibilityDescribesTheExistingOverlapAction() async throws {
        let event = makeEvent(id: 1, title: "디자인 검토", on: Date())
        let layout = TimelineEventLayout(
            id: "overflow",
            event: event,
            title: "+2",
            x: 0,
            y: 0,
            width: 20,
            height: 20,
            style: .overflow,
            tapAction: .showOverlapGroup([event, event, event])
        )

        #expect(layout.accessibilityLabel == "겹친 일정 3개 보기")
    }
}
