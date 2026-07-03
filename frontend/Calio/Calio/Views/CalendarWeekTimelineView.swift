//
//  CalendarWeekTimelineView.swift
//  Calio
//
//  Created by Codex on 6/12/26.
//

import SwiftUI
import UIKit

struct CalendarWeekTimelineView: View {
    private let calendar = Calendar.current
    private let visibleDayCount = 5
    private let timelineStartHour = 0
    private let timelineEndHour = 23
    
    let items: [CalendarDateCellItem]
    let referenceDay: DayKey
    let eventAreaState: CalendarEventAreaState
    let onSelectedDay: (DayKey) -> Void
    let onVisibleRangeChanged: (CalendarVisibleIndexRange) -> Void
    let onSelectedYearMonth: (Int, Int) -> Void
    let showsTodayButton: Bool
    let onTodayTapped: () -> Void
    let onCreateTapped: () -> Void
    let onRetryEvents: () -> Void
    let isEventMutating: Bool
    let eventMutationFailureMessage: String?
    let onResetEventMutation: () -> Void
    let onFetchRecurrenceEvent: (Int64) async -> RecurrenceEventDetails?
    let onUpdateSingleEvent: (Event, EventUpdateInput) async -> Bool
    let onUpdateRecurrenceOccurrence: (Event, EventUpdateInput) async -> Bool
    let onUpdateRecurrenceSeries: (Int64, RecurrenceEventSeriesEditInput) async -> Bool
    let onDeleteSingleEvent: (Event) async -> Bool
    let onDeleteRecurrenceOccurrence: (Event) async -> Bool
    let onDeleteRecurrenceSeries: (Event) async -> Bool

    init(
        items: [CalendarDateCellItem],
        referenceDay: DayKey,
        eventAreaState: CalendarEventAreaState,
        onSelectedDay: @escaping (DayKey) -> Void,
        onVisibleRangeChanged: @escaping (CalendarVisibleIndexRange) -> Void,
        onSelectedYearMonth: @escaping (Int, Int) -> Void,
        showsTodayButton: Bool = false,
        onTodayTapped: @escaping () -> Void = {},
        onCreateTapped: @escaping () -> Void = {},
        onRetryEvents: @escaping () -> Void,
        isEventMutating: Bool = false,
        eventMutationFailureMessage: String? = nil,
        onResetEventMutation: @escaping () -> Void = {},
        onFetchRecurrenceEvent: @escaping (Int64) async -> RecurrenceEventDetails? = { _ in nil },
        onUpdateSingleEvent: @escaping (Event, EventUpdateInput) async -> Bool = { _, _ in true },
        onUpdateRecurrenceOccurrence: @escaping (Event, EventUpdateInput) async -> Bool = { _, _ in true },
        onUpdateRecurrenceSeries: @escaping (Int64, RecurrenceEventSeriesEditInput) async -> Bool = { _, _ in true },
        onDeleteSingleEvent: @escaping (Event) async -> Bool = { _ in true },
        onDeleteRecurrenceOccurrence: @escaping (Event) async -> Bool = { _ in true },
        onDeleteRecurrenceSeries: @escaping (Event) async -> Bool = { _ in true }
    ) {
        self.items = items
        self.referenceDay = referenceDay
        self.eventAreaState = eventAreaState
        self.onSelectedDay = onSelectedDay
        self.onVisibleRangeChanged = onVisibleRangeChanged
        self.onSelectedYearMonth = onSelectedYearMonth
        self.showsTodayButton = showsTodayButton
        self.onTodayTapped = onTodayTapped
        self.onCreateTapped = onCreateTapped
        self.onRetryEvents = onRetryEvents
        self.isEventMutating = isEventMutating
        self.eventMutationFailureMessage = eventMutationFailureMessage
        self.onResetEventMutation = onResetEventMutation
        self.onFetchRecurrenceEvent = onFetchRecurrenceEvent
        self.onUpdateSingleEvent = onUpdateSingleEvent
        self.onUpdateRecurrenceOccurrence = onUpdateRecurrenceOccurrence
        self.onUpdateRecurrenceSeries = onUpdateRecurrenceSeries
        self.onDeleteSingleEvent = onDeleteSingleEvent
        self.onDeleteRecurrenceOccurrence = onDeleteRecurrenceOccurrence
        self.onDeleteRecurrenceSeries = onDeleteRecurrenceSeries
    }
    
    @State private var headerScrollPosition: DayKey?
    @State private var lastVisibleRange: CalendarVisibleIndexRange?
    @State private var selectedEvent: Event?
    @State private var selectedOverlapGroup: TimelineOverlapSelection?
    @State private var detailEvent: Event?
    
    var body: some View {
        GeometryReader { geometry in
            let metrics = timelineMetrics(for: geometry.size)
            
            VStack(spacing: 0) {
                CalendarTopBarView(
                    referenceDay: referenceDay,
                    showsTodayButton: showsTodayButton,
                    onSelectedYearMonth: onSelectedYearMonth,
                    onTodayTapped: onTodayTapped,
                    onCreateTapped: onCreateTapped
                )
                    .frame(
                        width: metrics.totalWidth,
                        height: metrics.topBarHeight,
                        alignment: .leading
                    )

                CalendarEventStatusBannerView(
                    state: eventAreaState,
                    onRetry: onRetryEvents
                )
                
                timelineHeader(metrics: metrics)
                    .frame(height: metrics.headerHeight, alignment: .top)
                
                fullDayEventRow(metrics: metrics)
                    .frame(height: metrics.fullDayEventRowHeight, alignment: .top)
                
                ScrollView(.vertical) {
                    timelineBody(metrics: metrics)
                        .background(TimelineScrollBounceDisabler())
                }
            }
            .frame(
                width: geometry.size.width,
                height: geometry.size.height,
                alignment: .top
            )
            .background(Color(uiColor: .systemBackground))
        }
        .sheet(item: $detailEvent) { event in
            CalendarEventDetailView(
                event: event,
                isMutating: isEventMutating,
                mutationFailureMessage: eventMutationFailureMessage,
                onFetchRecurrenceEvent: onFetchRecurrenceEvent,
                onUpdateSingleEvent: onUpdateSingleEvent,
                onUpdateRecurrenceOccurrence: onUpdateRecurrenceOccurrence,
                onUpdateRecurrenceSeries: onUpdateRecurrenceSeries,
                onDeleteSingleEvent: onDeleteSingleEvent,
                onDeleteRecurrenceOccurrence: onDeleteRecurrenceOccurrence,
                onDeleteRecurrenceSeries: onDeleteRecurrenceSeries
            )
        }
    }
    
    private func timelineHeader(metrics: TimelineMetrics) -> some View {
        HStack(spacing: 0) {
            Color.clear
                .frame(width: metrics.gridStartX)
            
            ScrollView(.horizontal) {
                LazyHStack(spacing: 0) {
                    ForEach(items) { item in
                        dayHeader(for: item, metrics: metrics)
                            .frame(
                                width: metrics.dayColumnWidth,
                                height: metrics.headerHeight
                            )
                            .id(item.id)
                            .overlay(alignment: .leading) {
                                verticalHeaderDivider(metrics: metrics)
                            }
                    }
                    
                    verticalHeaderDivider(metrics: metrics)
                        .frame(width: 0, height: metrics.headerHeight)
                }
                .scrollTargetLayout()
            }
            .scrollIndicators(.hidden)
            .scrollTargetBehavior(.viewAligned)
            .scrollPosition(id: $headerScrollPosition, anchor: .leading)
            .onChange(of: headerScrollPosition) { _, newDay in
                updateReferenceDayFromHeaderScrollPosition(newDay)
            }
            .onChange(of: referenceDay) { _, newDay in
                alignHeader(to: newDay)
            }
            .onChange(of: items.count) { _, _ in
                alignHeader(to: referenceDay)
            }
            .onAppear {
                alignHeader(to: referenceDay)
                notifyVisibleRangeChanged(around: referenceDay)
            }
        }
        .frame(
            width: metrics.totalWidth,
            height: metrics.headerHeight,
            alignment: .topLeading
        )
    }
    
    private func verticalHeaderDivider(metrics: TimelineMetrics) -> some View {
        Path { path in
            path.move(to: CGPoint(x: 0, y: metrics.headerGridLineStartY))
            path.addLine(to: CGPoint(x: 0, y: metrics.headerHeight))
        }
        .stroke(Color.secondary.opacity(0.28), lineWidth: 0.7)
    }
    
    private func dayHeader(for item: CalendarDateCellItem, metrics: TimelineMetrics) -> some View {
        let isReferenceDay = item.id == referenceDay
        
        return Button {
            onSelectedDay(item.id)
        } label: {
            VStack(spacing: 6) {
                Text(item.weekday.fullKoreanText)
                    .font(.system(size: metrics.weekdayFontSize, weight: .medium))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .foregroundStyle(dayHeaderColor(for: item.weekday))
                    .frame(maxWidth: .infinity, alignment: .center)
                
                Text("\(item.id.day)")
                    .font(.system(size: metrics.dayNumberFontSize, weight: .regular))
                    .foregroundStyle(item.isToday ? .white : dayHeaderColor(for: item.weekday))
                    .frame(width: metrics.dayCircleSize, height: metrics.dayCircleSize)
                    .background {
                        if item.isToday {
                            Circle()
                                .fill(Color(red: 0.56, green: 0.61, blue: 0.96))
                        }
                    }
                
                Capsule()
                    .fill(
                        isReferenceDay
                            ? Color(red: 0.56, green: 0.61, blue: 0.96)
                            : Color.clear
                    )
                    .frame(
                        width: metrics.referenceDayIndicatorWidth,
                        height: metrics.referenceDayIndicatorHeight
                    )
            }
            .frame(
                maxWidth: .infinity,
                maxHeight: .infinity,
                alignment: .bottom
            )
            .padding(.bottom, 5)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
    
    private func fullDayEventRow(metrics: TimelineMetrics) -> some View {
        ZStack(alignment: .topLeading) {
            fullDayEventRowGrid(metrics: metrics)
            
            HStack(spacing: 0) {
                ForEach(visibleItems) { item in
                    fullDayEventCell(for: item, metrics: metrics)
                        .frame(
                            width: metrics.dayColumnWidth,
                            height: metrics.fullDayEventRowHeight
                        )
                }
            }
            .offset(x: metrics.gridStartX)
        }
        .frame(
            width: metrics.totalWidth,
            height: metrics.fullDayEventRowHeight,
            alignment: .topLeading
        )
    }
    
    private func fullDayEventRowGrid(metrics: TimelineMetrics) -> some View {
        Path { path in
            path.move(to: CGPoint(x: metrics.horizontalLineStartX, y: 0))
            path.addLine(to: CGPoint(x: metrics.totalWidth, y: 0))
            path.move(to: CGPoint(x: metrics.horizontalLineStartX, y: metrics.fullDayEventRowHeight))
            path.addLine(to: CGPoint(x: metrics.totalWidth, y: metrics.fullDayEventRowHeight))
            
            for index in 0...visibleDayCount {
                let x = metrics.gridStartX + CGFloat(index) * metrics.dayColumnWidth
                path.move(to: CGPoint(x: x, y: 0))
                path.addLine(to: CGPoint(x: x, y: metrics.fullDayEventRowHeight))
            }
        }
        .stroke(Color.secondary.opacity(0.28), lineWidth: 0.7)
    }
    
    private func fullDayEventCell(
        for item: CalendarDateCellItem,
        metrics: TimelineMetrics
    ) -> some View {
        let events = fullDayEvents(in: item)
        
        return VStack(spacing: 3) {
            ForEach(Array(events.prefix(metrics.maxVisibleFullDayEventCount))) { event in
                Text(event.title)
                    .font(.system(size: metrics.fullDayEventFontSize, weight: .semibold))
                    .lineLimit(1)
                    .foregroundStyle(.white)
                    .padding(.horizontal, metrics.eventHorizontalPadding)
                    .frame(maxWidth: .infinity, minHeight: metrics.fullDayEventHeight)
                    .background(
                        RoundedRectangle(cornerRadius: 5)
                            .fill(Color(hex: event.colorCode))
                    )
                    .contentShape(Rectangle())
                    .onTapGesture {
                        selectedEvent = event
                    }
                    .popover(
                        isPresented: isShowingEventPopover(for: event),
                        attachmentAnchor: .rect(.bounds),
                        arrowEdge: .top
                    ) {
                        CalendarEventSummaryPopoverView(
                            event: event,
                            onShowDetail: showEventDetail
                        )
                            .presentationCompactAdaptation(.popover)
                    }
            }
            
            if events.count > metrics.maxVisibleFullDayEventCount {
                Text("+\(events.count - metrics.maxVisibleFullDayEventCount)")
                    .font(.system(size: metrics.fullDayEventFontSize, weight: .medium))
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 5)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }
    
    private func timelineBody(metrics: TimelineMetrics) -> some View {
        ZStack(alignment: .topLeading) {
            gridLines(metrics: metrics)
            timeLabels(metrics: metrics)
            eventBlocks(metrics: metrics)
        }
        .frame(
            width: metrics.totalWidth,
            height: metrics.totalGridHeight,
            alignment: .topLeading
        )
    }
    
    private func timeLabels(metrics: TimelineMetrics) -> some View {
        ForEach(Array(timelineHours.enumerated()), id: \.element) { index, hour in
            Text(timeText(for: hour))
                .font(.system(size: metrics.timeLabelFontSize, weight: .regular))
                .foregroundStyle(.primary)
                .frame(
                    width: metrics.timeLabelWidth,
                    height: metrics.timeLabelHeight,
                    alignment: .trailing
                )
                .position(
                    x: metrics.timeLabelCenterX,
                    y: metrics.timelineTopInset + CGFloat(index) * metrics.hourHeight
                )
        }
    }
    
    private func gridLines(metrics: TimelineMetrics) -> some View {
        ZStack(alignment: .topLeading) {
            Path { path in
                for index in 0...timelineHours.count {
                    let y = metrics.timelineTopInset + CGFloat(index) * metrics.hourHeight
                    path.move(to: CGPoint(x: metrics.horizontalLineStartX, y: y))
                    path.addLine(to: CGPoint(x: metrics.totalWidth, y: y))
                }
                
                for index in 0...visibleDayCount {
                    let x = metrics.gridStartX + CGFloat(index) * metrics.dayColumnWidth
                    path.move(to: CGPoint(x: x, y: 0))
                    path.addLine(to: CGPoint(x: x, y: metrics.totalGridHeight))
                }
            }
            .stroke(Color.secondary.opacity(0.28), lineWidth: 0.7)
        }
        .frame(
            width: metrics.totalWidth,
            height: metrics.totalGridHeight,
            alignment: .topLeading
        )
    } 
    
    private func eventBlocks(metrics: TimelineMetrics) -> some View {
        ForEach(eventLayouts(metrics: metrics)) { layout in
            Text(layout.title)
                .font(.system(size: metrics.eventFontSize, weight: .semibold))
                .lineLimit(1)
                .foregroundStyle(layout.foregroundColor)
                .padding(.horizontal, metrics.eventHorizontalPadding)
                .padding(.vertical, metrics.eventVerticalPadding)
                .frame(
                    width: max(layout.width, 0),
                    height: max(layout.height, metrics.minimumEventHeight),
                    alignment: .topLeading
                )
                .background(
                    RoundedRectangle(cornerRadius: 6)
                        .fill(layout.backgroundColor)
                )
                .contentShape(Rectangle())
                .onTapGesture {
                    selectTimelineLayout(layout)
                }
                .popover(
                    isPresented: isShowingTimelinePopover(for: layout),
                    attachmentAnchor: .rect(.bounds),
                    arrowEdge: .top
                ) {
                    switch layout.tapAction {
                    case .showEvent(let event):
                        CalendarEventSummaryPopoverView(
                            event: event,
                            onShowDetail: showEventDetail
                        )
                            .presentationCompactAdaptation(.popover)
                    case .showOverlapGroup:
                        if let selection = selectedOverlapGroup {
                            CalendarTimelineOverlapPopoverView(
                                events: selection.events,
                                onShowDetail: showOverlapEventDetail
                            )
                                .presentationCompactAdaptation(.popover)
                        }
                    }
                }
                .offset(x: layout.x, y: layout.y)
        }
    }
    
    private var visibleItems: [CalendarDateCellItem] {
        guard let referenceIndex = items.firstIndex(where: { $0.id == referenceDay }) else {
            return []
        }
        
        let endIndex = min(items.endIndex, referenceIndex + visibleDayCount)
        return Array(items[referenceIndex..<endIndex])
    }
    
    private var timelineHours: [Int] {
        Array(timelineStartHour...timelineEndHour)
    }
    
    private func eventLayouts(metrics: TimelineMetrics) -> [TimelineEventLayout] {
        visibleItems.enumerated().flatMap { dayIndex, item in
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
    
    private func isShowingTimelinePopover(for layout: TimelineEventLayout) -> Binding<Bool> {
        Binding(
            get: {
                switch layout.tapAction {
                case .showEvent(let event):
                    return selectedEvent?.id == event.id
                case .showOverlapGroup:
                    return selectedOverlapGroup?.id == layout.id
                }
            },
            set: { isPresented in
                if !isPresented {
                    switch layout.tapAction {
                    case .showEvent:
                        selectedEvent = nil
                    case .showOverlapGroup:
                        selectedOverlapGroup = nil
                    }
                }
            }
        )
    }
    
    private func isShowingEventPopover(for event: Event) -> Binding<Bool> {
        Binding(
            get: {
                selectedEvent?.id == event.id
            },
            set: { isPresented in
                if !isPresented {
                    selectedEvent = nil
                }
            }
        )
    }
    
    private func selectTimelineLayout(_ layout: TimelineEventLayout) {
        switch layout.tapAction {
        case .showEvent(let event):
            selectedOverlapGroup = nil
            selectedEvent = event
        case .showOverlapGroup(let events):
            selectedEvent = nil
            selectedOverlapGroup = TimelineOverlapSelection(
                id: layout.id,
                events: events
            )
        }
    }

    private func showEventDetail(_ event: Event) {
        selectedEvent = nil
        onResetEventMutation()
        detailEvent = event
    }
    
    private func showOverlapEventDetail(_ event: Event) {
        selectedOverlapGroup = nil
        showEventDetail(event)
    }
    
    private func updateReferenceDayFromHeaderScrollPosition(_ day: DayKey?) {
        guard let day else { return }
        
        notifyVisibleRangeChanged(around: day)
        guard day != referenceDay else { return }
        
        onSelectedDay(day)
    }
    
    private func alignHeader(to day: DayKey) {
        guard items.contains(where: { $0.id == day }) else { return }
        guard headerScrollPosition != day else { return }
        
        headerScrollPosition = day
        notifyVisibleRangeChanged(around: day)
    }
    
    private func notifyVisibleRangeChanged(around day: DayKey) {
        guard let referenceIndex = items.firstIndex(where: { $0.id == day }) else {
            return
        }
        
        let endIndex = min(
            items.count - 1,
            referenceIndex + visibleDayCount - 1
        )
        
        let visibleRange = CalendarVisibleIndexRange(
            startIndex: referenceIndex,
            endIndex: endIndex
        )
        
        guard visibleRange != lastVisibleRange else {
            return
        }
        
        lastVisibleRange = visibleRange
        onVisibleRangeChanged(visibleRange)
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
    
    private func shouldPlaceEarlier(_ earlierCandidate: TimelineEventInterval, _ laterCandidate: TimelineEventInterval) -> Bool {
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
            guard let event = group.events.first,
                  let frame = makeEventFrame(
                    event: event,
                    day: day,
                    dayIndex: dayIndex,
                    metrics: metrics
                  )
            else {
                return []
            }
            
            return [
                TimelineEventLayout(
                    id: "event-\(event.id)",
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
                id: "event-\(interval.event.id)-column-\(columnIndex)",
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
    
    private func maxSimultaneousOverlap(in events: [Event], on day: DayKey) -> Int {
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
        
        let groupID = group.events.map(\.id).map(String.init).joined(separator: "-")
        let gap = metrics.overlapEventGap
        let blockWidth = max((metrics.eventContentWidth - gap) / 2, 1)
        let hiddenEventCount = group.events.count - 1
        
        return [
            TimelineEventLayout(
                id: "group-\(groupID)-representative",
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
                id: "group-\(groupID)-overflow",
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
        
        guard let firstFrame = frames.first
        else {
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
            CGFloat(timelineHours.count),
            max(endOffset, clampedStartOffset + 0.25)
        )
        
        guard clampedEndOffset > 0,
              clampedStartOffset < CGFloat(timelineHours.count)
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
    
    private func timedEvents(in item: CalendarDateCellItem) -> [Event] {
        item.events.filter { event in
            !shouldShowInFullDayArea(event, on: item.id)
        }
    }
    
    private func fullDayEvents(in item: CalendarDateCellItem) -> [Event] {
        item.events.filter { event in
            shouldShowInFullDayArea(event, on: item.id)
        }
    }
    
    private func shouldShowInFullDayArea(_ event: Event, on day: DayKey) -> Bool {
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
    
    private func hourOffset(hour: Int, minute: Int) -> CGFloat {
        CGFloat(hour - timelineStartHour) + CGFloat(minute) / 60
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
            return CGFloat(timelineHours.count)
        }
        
        let components = calendar.dateComponents([.hour, .minute], from: date)
        
        guard let hour = components.hour,
              let minute = components.minute
        else {
            return nil
        }
        
        return hourOffset(hour: hour, minute: minute)
    }
    
    private func timeText(for hour: Int) -> String {
        let normalizedHour = hour % 24
        let displayHour = switch normalizedHour {
        case 0:
            12
        case 13...23:
            normalizedHour - 12
        default:
            normalizedHour
        }
        let suffix = normalizedHour >= 12 ? "PM" : "AM"
        
        return "\(displayHour) \(suffix)"
    }
    
    private func dayHeaderColor(for weekday: CalendarWeekday) -> Color {
        switch weekday {
        case .sunday:
            return .red
        case .saturday:
            return .blue
        default:
            return .primary
        }
    }
    
    private func timelineMetrics(for size: CGSize) -> TimelineMetrics {
        let timeColumnWidth = min(max(size.width * 0.14, 44), 64)
        let dayColumnWidth = max(
            (size.width - timeColumnWidth) / CGFloat(visibleDayCount),
            1
        )
        let topBarHeight = min(max(size.height * 0.07, 44), 58)
        let headerHeight = min(max(size.height * 0.11, 78), 110)
        let fullDayEventRowHeight = min(max(size.height * 0.07, 46), 58)
        let availableTimelineHeight = max(
            size.height - topBarHeight - headerHeight - fullDayEventRowHeight,
            1
        )
        let hourHeight = max(56, availableTimelineHeight / 12)
        
        return TimelineMetrics(
            timeColumnWidth: timeColumnWidth,
            dayColumnWidth: dayColumnWidth,
            topBarHeight: topBarHeight,
            headerHeight: headerHeight,
            fullDayEventRowHeight: fullDayEventRowHeight,
            hourHeight: hourHeight,
            visibleDayCount: visibleDayCount,
            hourCount: timelineHours.count
        )
    }
}

private struct TimelineMetrics {
    let timeColumnWidth: CGFloat
    let dayColumnWidth: CGFloat
    let topBarHeight: CGFloat
    let headerHeight: CGFloat
    let fullDayEventRowHeight: CGFloat
    let hourHeight: CGFloat
    let visibleDayCount: Int
    let hourCount: Int
    
    var gridStartX: CGFloat {
        timeColumnWidth
    }
    
    var timeLabelFontSize: CGFloat {
        dayColumnWidth < 44 ? 11 : 14
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
        dayColumnWidth < 44 ? 9 : 11
    }
    
    var fullDayEventHeight: CGFloat {
        dayColumnWidth < 44 ? 18 : 22
    }
    
    var maxVisibleFullDayEventCount: Int {
        max(Int((fullDayEventRowHeight - 10) / (fullDayEventHeight + 3)), 1)
    }
    
    var eventFontSize: CGFloat {
        dayColumnWidth < 44 ? 9 : 11
    }
    
    var eventHorizontalPadding: CGFloat {
        dayColumnWidth < 44 ? 3 : 5
    }
    
    var eventVerticalPadding: CGFloat {
        dayColumnWidth < 44 ? 3 : 4
    }
    
    var minimumEventHeight: CGFloat {
        dayColumnWidth < 44 ? 20 : 24
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
        dayColumnWidth < 44 ? 9 : 12
    }
    
    var dayNumberFontSize: CGFloat {
        dayColumnWidth < 44 ? 18 : 24
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

private struct TimelineOverlapSelection: Identifiable {
    let id: String
    let events: [Event]
}

private enum TimelineEventLayoutStyle {
    case event
    case overflow
}

private enum TimelineEventLayoutAction {
    case showEvent(Event)
    case showOverlapGroup([Event])
}

private struct TimelineEventLayout: Identifiable {
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
            return Color(hex: event.colorCode)
        case .overflow:
            return Color(uiColor: .secondarySystemBackground)
        }
    }
    
    var foregroundColor: Color {
        switch style {
        case .event:
            return .white
        case .overflow:
            return Color.accentColor
        }
    }
}

private struct CalendarTimelineOverlapPopoverView: View {
    let events: [Event]
    let onShowDetail: (Event) -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("일정 \(events.count)개")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.primary)
            
            VStack(spacing: 0) {
                ForEach(events) { event in
                    Button {
                        onShowDetail(event)
                    } label: {
                        HStack(alignment: .top, spacing: 8) {
                            RoundedRectangle(cornerRadius: 3)
                                .fill(Color(hex: event.colorCode))
                                .frame(width: 5, height: 34)
                            
                            VStack(alignment: .leading, spacing: 3) {
                                Text(event.title)
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(.primary)
                                    .lineLimit(1)
                                
                                Text(
                                    CalendarEventDisplayText.timeRange(
                                        startAt: event.startAt,
                                        endAt: event.endAt
                                    )
                                )
                                .font(.system(size: 12, weight: .medium))
                                .foregroundStyle(.secondary)
                            }
                            
                            Spacer(minLength: 0)
                        }
                        .contentShape(Rectangle())
                        .padding(.vertical, 8)
                    }
                    .buttonStyle(.plain)
                    
                    if event.id != events.last?.id {
                        Divider()
                    }
                }
            }
        }
        .padding(14)
        .frame(width: 260, alignment: .leading)
    }
}

private struct TimelineScrollBounceDisabler: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        UIView(frame: .zero)
    }
    
    func updateUIView(_ uiView: UIView, context: Context) {
        DispatchQueue.main.async {
            guard let scrollView = uiView.enclosingScrollView else { return }
            scrollView.bounces = false
            scrollView.alwaysBounceVertical = false
        }
    }
}

private extension UIView {
    var enclosingScrollView: UIScrollView? {
        if let scrollView = superview as? UIScrollView {
            return scrollView
        }
        
        return superview?.enclosingScrollView
    }
}

#Preview {
    let calendar = Calendar.current
    let dateService = CalendarDateService(calendar: calendar)
    let baseDate = calendar.date(
        from: DateComponents(year: 2026, month: 6, day: 3)
    ) ?? Date()
    
    let makeEvent: (Int64, String, Date, Int, Int, Int, String) -> Event = { id, title, date, hour, minute, durationMinutes, colorCode in
        let startOfDay = calendar.startOfDay(for: date)
        let startAt = calendar.date(
            bySettingHour: hour,
            minute: minute,
            second: 0,
            of: startOfDay
        ) ?? startOfDay
        let endAt = calendar.date(
            byAdding: .minute,
            value: durationMinutes,
            to: startAt
        ) ?? startAt
        
        return Event(
            id: id,
            title: title,
            description: "",
            startAt: startAt,
            endAt: endAt,
            colorCode: colorCode
        )
    }
    
    let items: [CalendarDateCellItem] = (0..<7).map { offset in
        let date = calendar.date(
            byAdding: .day,
            value: offset,
            to: baseDate
        ) ?? baseDate
        let day = DayKey(date: date, calendar: calendar)
        
        let events: [Event] = switch offset {
        case 0:
            [
                makeEvent(1, "프로젝트 마감", date, 10, 30, 90, "#EF4444"),
                makeEvent(2, "팀 주간 회의", date, 13, 0, 60, "#4F46E5"),
                makeEvent(3, "저녁 약속", date, 19, 30, 90, "#F59E0B")
            ]
        case 1:
            [
                makeEvent(4, "1:1 미팅", date, 11, 0, 30, "#4F46E5")
            ]
        case 2:
            [
                makeEvent(5, "부모님 생신", date, 9, 30, 60, "#F59E0B"),
                makeEvent(6, "헬스 PT", date, 18, 0, 60, "#22C55E")
            ]
        case 3:
            [
                makeEvent(7, "제품 리뷰", date, 14, 0, 120, "#4F46E5"),
                makeEvent(8, "치과 정기검진", date, 17, 30, 30, "#22C55E")
            ]
        case 5:
            [
                makeEvent(9, "Sprint Review", date, 15, 0, 90, "#EF4444")
            ]
        default:
            []
        }
        
        return CalendarDateCellItem(
            id: day,
            weekday: dateService.getWeekday(from: date),
            monthText: dateService.monthText(from: date),
            dayText: dateService.dayText(from: date),
            isToday: offset == 0,
            events: events
        )
    }
    
    CalendarWeekTimelineView(
        items: items,
        referenceDay: items[0].id,
        eventAreaState: .idle,
        onSelectedDay: { _ in },
        onVisibleRangeChanged: { _ in },
        onSelectedYearMonth: { _, _ in },
        onRetryEvents: {}
    )
}
