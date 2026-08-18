//
//  CalendarWeekTimelineLayout.swift
//  Calio
//
//  Created by Codex on 7/7/26.
//

import SwiftUI

struct TimelineMetrics {
    let timeColumnWidth: CGFloat
    let dayColumnWidth: CGFloat
    let topBarHeight: CGFloat
    let headerHeight: CGFloat
    let fullDayEventRowHeight: CGFloat
    let hourHeight: CGFloat
    let visibleDayCount: Int
    let hourCount: Int
    let textScale: CGFloat

    var gridStartX: CGFloat {
        timeColumnWidth
    }

    var timeLabelFontSize: CGFloat {
        (dayColumnWidth < 44 ? 11 : 14) * textScale
    }

    var timeLabelTrailingPadding: CGFloat {
        timeColumnWidth < 54 ? 6 : 10
    }

    var timeLabelWidth: CGFloat {
        max(timeColumnWidth - timeLabelTrailingPadding, 1)
    }

    var timeLabelCenterX: CGFloat {
        timeLabelWidth / 2
    }

    var timeLabelHeight: CGFloat {
        timeLabelFontSize + 4
    }

    var timelineTopInset: CGFloat {
        timeLabelHeight / 2
    }

    var headerGridLineStartY: CGFloat {
        max(12, headerHeight * 0.18)
    }

    var fullDayEventFontSize: CGFloat {
        (dayColumnWidth < 44 ? 9 : 11) * textScale
    }

    var fullDayEventHeight: CGFloat {
        (dayColumnWidth < 44 ? 18 : 22) * textScale
    }

    var maxVisibleFullDayEventCount: Int {
        max(Int((fullDayEventRowHeight - 10) / (fullDayEventHeight + 3)), 1)
    }

    var eventFontSize: CGFloat {
        (dayColumnWidth < 44 ? 9 : 11) * textScale
    }

    var eventHorizontalPadding: CGFloat {
        dayColumnWidth < 44 ? 3 : 5
    }

    var eventVerticalPadding: CGFloat {
        dayColumnWidth < 44 ? 3 : 4
    }

    var minimumEventHeight: CGFloat {
        (dayColumnWidth < 44 ? 20 : 24) * textScale
    }

    var eventHorizontalMargin: CGFloat {
        6
    }

    var eventContentWidth: CGFloat {
        max(dayColumnWidth - (eventHorizontalMargin * 2), 1)
    }

    var overlapEventGap: CGFloat {
        dayColumnWidth < 44 ? 1 : 2
    }

    var weekdayFontSize: CGFloat {
        (dayColumnWidth < 44 ? 9 : 12) * textScale
    }

    var dayNumberFontSize: CGFloat {
        (dayColumnWidth < 44 ? 18 : 24) * textScale
    }

    var dayCircleSize: CGFloat {
        min(max(dayColumnWidth * 0.5, 30), 30)
    }

    var referenceDayIndicatorWidth: CGFloat {
        min(max(dayColumnWidth * 0.28, 18), 24)
    }

    var referenceDayIndicatorHeight: CGFloat {
        3
    }

    var horizontalLineStartX: CGFloat {
        timeColumnWidth - timeLabelTrailingPadding
    }

    var totalGridWidth: CGFloat {
        dayColumnWidth * CGFloat(visibleDayCount)
    }

    var totalWidth: CGFloat {
        timeColumnWidth + totalGridWidth
    }

    var totalGridHeight: CGFloat {
        timelineTopInset + hourHeight * CGFloat(hourCount)
    }

    var timelineScrollTopY: CGFloat {
        topBarHeight + headerHeight + fullDayEventRowHeight
    }
}

struct TimelineEventLayoutBuilder {
    let calendar: Calendar
    let timelineStartHour: Int
    let hourCount: Int

    func make(
        items: [CalendarDayItem],
        metrics: TimelineMetrics
    ) -> [TimelineEventLayout] {
        items.enumerated().flatMap { dayIndex, item in
            let groups = overlapGroups(in: timedEvents(in: item), on: item.id)

            return groups.flatMap { group in
                makeEventLayouts(
                    group: group,
                    day: item.id,
                    dayIndex: dayIndex,
                    metrics: metrics
                )
            }
        }
    }

    func maxSimultaneousOverlap(
        in events: [Event],
        on day: DayKey
    ) -> Int {
        let points = events
            .compactMap { event -> (startAt: Date, endAt: Date)? in
                displayRange(for: event, on: day)
            }
            .flatMap { range in
                [
                    TimelineOverlapPoint(date: range.startAt, change: 1),
                    TimelineOverlapPoint(date: range.endAt, change: -1)
                ]
            }
            .sorted { lhs, rhs in
                if lhs.date != rhs.date {
                    return lhs.date < rhs.date
                }

                return lhs.change < rhs.change
            }

        var activeCount = 0
        var maxActiveCount = 0

        for point in points {
            activeCount += point.change
            maxActiveCount = max(maxActiveCount, activeCount)
        }

        return maxActiveCount
    }

    func shouldShowInFullDayArea(_ event: Event, on day: DayKey) -> Bool {
        let dayStart = day.toDate(calendar: calendar)

        guard let nextDayStart = calendar.date(
            byAdding: .day,
            value: 1,
            to: dayStart
        ) else {
            return false
        }

        return event.startAt <= dayStart && event.endAt >= nextDayStart
    }

    private func timedEvents(in item: CalendarDayItem) -> [Event] {
        item.events.filter { event in
            !shouldShowInFullDayArea(event, on: item.id)
        }
    }

    private func overlapGroups(in events: [Event], on day: DayKey) -> [TimelineOverlapGroup] {
        let sortedIntervals = events
            .compactMap { event -> TimelineEventInterval? in
                guard let displayRange = displayRange(for: event, on: day) else {
                    return nil
                }

                return TimelineEventInterval(
                    event: event,
                    startAt: displayRange.startAt,
                    endAt: displayRange.endAt
                )
            }
            .sorted(by: shouldPlaceEarlier)

        guard let firstInterval = sortedIntervals.first else { return [] }

        var groups: [TimelineOverlapGroup] = []
        var currentEvents: [Event] = [firstInterval.event]
        var currentEndAt = firstInterval.endAt

        for interval in sortedIntervals.dropFirst() {
            if interval.startAt < currentEndAt {
                currentEvents.append(interval.event)
                currentEndAt = max(currentEndAt, interval.endAt)
            } else {
                groups.append(TimelineOverlapGroup(events: currentEvents))
                currentEvents = [interval.event]
                currentEndAt = interval.endAt
            }
        }

        groups.append(TimelineOverlapGroup(events: currentEvents))
        return groups
    }

    private func shouldPlaceEarlier(
        _ earlierCandidate: TimelineEventInterval,
        _ laterCandidate: TimelineEventInterval
    ) -> Bool {
        if earlierCandidate.startAt != laterCandidate.startAt {
            return earlierCandidate.startAt < laterCandidate.startAt
        }

        let earlierDuration = earlierCandidate.endAt.timeIntervalSince(earlierCandidate.startAt)
        let laterDuration = laterCandidate.endAt.timeIntervalSince(laterCandidate.startAt)
        if earlierDuration != laterDuration {
            return earlierDuration > laterDuration
        }

        return earlierCandidate.event.id < laterCandidate.event.id
    }

    private func makeEventLayouts(
        group: TimelineOverlapGroup,
        day: DayKey,
        dayIndex: Int,
        metrics: TimelineMetrics
    ) -> [TimelineEventLayout] {
        switch group.events.count {
        case 0:
            return []
        case 1:
            return makeSingleEventLayout(
                event: group.events[0],
                day: day,
                dayIndex: dayIndex,
                metrics: metrics
            )
        case 2:
            return makeColumnLayouts(
                group: group,
                day: day,
                dayIndex: dayIndex,
                metrics: metrics
            )
        default:
            if maxSimultaneousOverlap(in: group.events, on: day) <= 2 {
                return makeColumnLayouts(
                    group: group,
                    day: day,
                    dayIndex: dayIndex,
                    metrics: metrics
                )
            }

            return makeOverflowLayouts(
                group: group,
                day: day,
                dayIndex: dayIndex,
                metrics: metrics
            )
        }
    }

    private func makeSingleEventLayout(
        event: Event,
        day: DayKey,
        dayIndex: Int,
        metrics: TimelineMetrics
    ) -> [TimelineEventLayout] {
        guard let frame = makeEventFrame(
            event: event,
            day: day,
            dayIndex: dayIndex,
            metrics: metrics
        ) else {
            return []
        }

        return [
            TimelineEventLayout(
                id: "event-\(day.idValue)-\(event.id)",
                event: event,
                title: event.title,
                x: frame.x,
                y: frame.y,
                width: frame.width,
                height: frame.height,
                style: .event,
                tapAction: .showEvent(event)
            )
        ]
    }

    private func makeColumnLayouts(
        group: TimelineOverlapGroup,
        day: DayKey,
        dayIndex: Int,
        metrics: TimelineMetrics
    ) -> [TimelineEventLayout] {
        let intervals = group.events
            .compactMap { event -> TimelineEventInterval? in
                guard let displayRange = displayRange(for: event, on: day) else {
                    return nil
                }

                return TimelineEventInterval(
                    event: event,
                    startAt: displayRange.startAt,
                    endAt: displayRange.endAt
                )
            }
            .sorted(by: shouldPlaceEarlier)
        let gap = metrics.overlapEventGap
        let blockWidth = max((metrics.eventContentWidth - gap) / 2, 1)
        var columnEndDates = Array(repeating: Date.distantPast, count: 2)

        return intervals.compactMap { interval in
            guard let columnIndex = columnEndDates.firstIndex(where: { $0 <= interval.startAt }),
                  let frame = makeEventFrame(
                    event: interval.event,
                    day: day,
                    dayIndex: dayIndex,
                    metrics: metrics
                  )
            else {
                return nil
            }

            columnEndDates[columnIndex] = interval.endAt

            return TimelineEventLayout(
                id: "event-\(day.idValue)-\(interval.event.id)-column-\(columnIndex)",
                event: interval.event,
                title: interval.event.title,
                x: frame.x + CGFloat(columnIndex) * (blockWidth + gap),
                y: frame.y,
                width: blockWidth,
                height: frame.height,
                style: .event,
                tapAction: .showEvent(interval.event)
            )
        }
    }

    private func makeOverflowLayouts(
        group: TimelineOverlapGroup,
        day: DayKey,
        dayIndex: Int,
        metrics: TimelineMetrics
    ) -> [TimelineEventLayout] {
        guard let representativeEvent = group.events.first,
              let frame = makeGroupFrame(
                events: group.events,
                day: day,
                dayIndex: dayIndex,
                metrics: metrics
              )
        else {
            return []
        }

        let groupID = group.events.map(\.id).joined(separator: "-")
        let gap = metrics.overlapEventGap
        let blockWidth = max((metrics.eventContentWidth - gap) / 2, 1)
        let hiddenEventCount = group.events.count - 1

        return [
            TimelineEventLayout(
                id: "group-\(day.idValue)-\(groupID)-representative",
                event: representativeEvent,
                title: representativeEvent.title,
                x: frame.x,
                y: frame.y,
                width: blockWidth,
                height: frame.height,
                style: .event,
                tapAction: .showOverlapGroup(group.events)
            ),
            TimelineEventLayout(
                id: "group-\(day.idValue)-\(groupID)-overflow",
                event: representativeEvent,
                title: "+\(hiddenEventCount)",
                x: frame.x + blockWidth + gap,
                y: frame.y,
                width: blockWidth,
                height: frame.height,
                style: .overflow,
                tapAction: .showOverlapGroup(group.events)
            )
        ]
    }

    private func makeGroupFrame(
        events: [Event],
        day: DayKey,
        dayIndex: Int,
        metrics: TimelineMetrics
    ) -> TimelineEventFrame? {
        let frames = events.compactMap { event in
            makeEventFrame(
                event: event,
                day: day,
                dayIndex: dayIndex,
                metrics: metrics
            )
        }

        guard let firstFrame = frames.first else {
            return nil
        }

        let minY = frames.map(\.y).min() ?? firstFrame.y
        let maxY = frames.map { $0.y + $0.height }.max() ?? (firstFrame.y + firstFrame.height)

        return TimelineEventFrame(
            x: firstFrame.x,
            y: minY,
            width: firstFrame.width,
            height: maxY - minY
        )
    }

    private func makeEventFrame(
        event: Event,
        day: DayKey,
        dayIndex: Int,
        metrics: TimelineMetrics
    ) -> TimelineEventFrame? {
        guard let displayRange = displayRange(for: event, on: day) else {
            return nil
        }

        return makeFrame(
            startAt: displayRange.startAt,
            endAt: displayRange.endAt,
            day: day,
            dayIndex: dayIndex,
            metrics: metrics
        )
    }

    private func displayRange(
        for event: Event,
        on day: DayKey
    ) -> (startAt: Date, endAt: Date)? {
        let dayStart = day.toDate(calendar: calendar)

        guard let nextDayStart = calendar.date(
            byAdding: .day,
            value: 1,
            to: dayStart
        ) else {
            return nil
        }

        let displayStartAt = max(event.startAt, dayStart)
        let displayEndAt = min(event.endAt, nextDayStart)

        guard displayStartAt < displayEndAt else {
            return nil
        }

        return (startAt: displayStartAt, endAt: displayEndAt)
    }

    private func makeFrame(
        startAt: Date,
        endAt: Date,
        day: DayKey,
        dayIndex: Int,
        metrics: TimelineMetrics
    ) -> TimelineEventFrame? {
        guard let startOffset = hourOffset(from: startAt, on: day),
              let endOffset = hourOffset(from: endAt, on: day)
        else {
            return nil
        }

        let clampedStartOffset = max(0, startOffset)
        let clampedEndOffset = min(
            CGFloat(hourCount),
            max(endOffset, clampedStartOffset + 0.25)
        )

        guard clampedEndOffset > 0,
              clampedStartOffset < CGFloat(hourCount)
        else {
            return nil
        }

        let x = metrics.gridStartX + CGFloat(dayIndex) * metrics.dayColumnWidth + metrics.eventHorizontalMargin
        let y = metrics.timelineTopInset + clampedStartOffset * metrics.hourHeight
        let width = metrics.eventContentWidth
        let height = (clampedEndOffset - clampedStartOffset) * metrics.hourHeight

        return TimelineEventFrame(
            x: x,
            y: y,
            width: width,
            height: height
        )
    }

    private func hourOffset(from date: Date, on day: DayKey) -> CGFloat? {
        let dayStart = day.toDate(calendar: calendar)

        guard let nextDayStart = calendar.date(
            byAdding: .day,
            value: 1,
            to: dayStart
        ) else {
            return nil
        }

        if date == nextDayStart {
            return CGFloat(hourCount)
        }

        let components = calendar.dateComponents([.hour, .minute], from: date)

        guard let hour = components.hour,
              let minute = components.minute
        else {
            return nil
        }

        return CGFloat(hour - timelineStartHour) + CGFloat(minute) / 60
    }
}

private struct TimelineOverlapGroup {
    let events: [Event]
}

private struct TimelineEventInterval {
    let event: Event
    let startAt: Date
    let endAt: Date
}

private struct TimelineOverlapPoint {
    let date: Date
    let change: Int
}

private struct TimelineEventFrame {
    let x: CGFloat
    let y: CGFloat
    let width: CGFloat
    let height: CGFloat
}

enum TimelineEventLayoutStyle {
    case event
    case overflow
}

enum TimelineEventLayoutAction {
    case showEvent(Event)
    case showOverlapGroup([Event])
}

struct TimelineEventLayout: Identifiable {
    let id: String
    let event: Event
    let title: String
    let x: CGFloat
    let y: CGFloat
    let width: CGFloat
    let height: CGFloat
    let style: TimelineEventLayoutStyle
    let tapAction: TimelineEventLayoutAction

    var backgroundColor: Color {
        switch style {
        case .event:
            return Color(hex: event.tag.colorCode)
        case .overflow:
            return .calioSelection
        }
    }

    var foregroundColor: Color {
        switch style {
        case .event:
            return .white
        case .overflow:
            return .calioPrimary
        }
    }

    var accessibilityLabel: String {
        switch tapAction {
        case .showEvent(let event):
            return "\(event.title), 일정 상세 보기"
        case .showOverlapGroup(let events):
            return "겹친 일정 \(events.count)개 보기"
        }
    }
}

private extension DayKey {
    var idValue: String {
        "\(year)-\(month)-\(day)"
    }
}
