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
    private let timelineCoordinateSpace = "calendarWeekTimeline"
    
    let items: [CalendarDayItem]
    let tags: [CalendarTag]
    let referenceDay: DayKey
    let eventLoadState: CalendarEventLoadState
    let onSelectedDay: (DayKey) -> Void
    let onVisibleRangeChanged: (CalendarVisibleIndexRange) -> Void
    let onSelectedYearMonth: (Int, Int) -> Void
    let showsTodayButton: Bool
    let onTodayTapped: () -> Void
    let onGoogleCalendarConnectTapped: () -> Void
    let onCreateTapped: () -> Void
    let onRetryEventLoading: () -> Void
    let isEventMutating: Bool
    let isTagMutating: Bool
    let eventMutationFailureMessage: String?
    let tagMutationFailureMessage: String?
    let onResetEventMutation: () -> Void
    let onResetTagMutation: () -> Void
    let onFetchRecurrenceEvent: (Int64) async -> RecurrenceEventDetails?
    let onUpdateSingleEvent: (Event, EventUpdateInput) async -> Bool
    let onUpdateRecurrenceOccurrence: (Event, EventUpdateInput) async -> Bool
    let onUpdateRecurrenceSeries: (Int64, RecurrenceEventSeriesEditInput) async -> Bool
    let onDeleteSingleEvent: (Event) async -> Bool
    let onDeleteRecurrenceOccurrence: (Event) async -> Bool
    let onDeleteRecurrenceSeries: (Event) async -> Bool
    let onCreateCustomTag: (CustomTagInput) async -> Bool
    let onUpdateCustomTag: (CalendarTag, CustomTagInput) async -> Bool
    let onDeleteCustomTag: (CalendarTag) async -> Bool

    init(
        items: [CalendarDayItem],
        tags: [CalendarTag] = [],
        referenceDay: DayKey,
        eventLoadState: CalendarEventLoadState,
        onSelectedDay: @escaping (DayKey) -> Void,
        onVisibleRangeChanged: @escaping (CalendarVisibleIndexRange) -> Void,
        onSelectedYearMonth: @escaping (Int, Int) -> Void,
        showsTodayButton: Bool = false,
        onTodayTapped: @escaping () -> Void = {},
        onGoogleCalendarConnectTapped: @escaping () -> Void = {},
        onCreateTapped: @escaping () -> Void = {},
        onRetryEventLoading: @escaping () -> Void,
        isEventMutating: Bool = false,
        isTagMutating: Bool = false,
        eventMutationFailureMessage: String? = nil,
        tagMutationFailureMessage: String? = nil,
        onResetEventMutation: @escaping () -> Void = {},
        onResetTagMutation: @escaping () -> Void = {},
        onFetchRecurrenceEvent: @escaping (Int64) async -> RecurrenceEventDetails? = { _ in nil },
        onUpdateSingleEvent: @escaping (Event, EventUpdateInput) async -> Bool = { _, _ in true },
        onUpdateRecurrenceOccurrence: @escaping (Event, EventUpdateInput) async -> Bool = { _, _ in true },
        onUpdateRecurrenceSeries: @escaping (Int64, RecurrenceEventSeriesEditInput) async -> Bool = { _, _ in true },
        onDeleteSingleEvent: @escaping (Event) async -> Bool = { _ in true },
        onDeleteRecurrenceOccurrence: @escaping (Event) async -> Bool = { _ in true },
        onDeleteRecurrenceSeries: @escaping (Event) async -> Bool = { _ in true },
        onCreateCustomTag: @escaping (CustomTagInput) async -> Bool = { _ in false },
        onUpdateCustomTag: @escaping (CalendarTag, CustomTagInput) async -> Bool = { _, _ in false },
        onDeleteCustomTag: @escaping (CalendarTag) async -> Bool = { _ in false }
    ) {
        self.items = items
        self.tags = tags
        self.referenceDay = referenceDay
        self.eventLoadState = eventLoadState
        self.onSelectedDay = onSelectedDay
        self.onVisibleRangeChanged = onVisibleRangeChanged
        self.onSelectedYearMonth = onSelectedYearMonth
        self.showsTodayButton = showsTodayButton
        self.onTodayTapped = onTodayTapped
        self.onGoogleCalendarConnectTapped = onGoogleCalendarConnectTapped
        self.onCreateTapped = onCreateTapped
        self.onRetryEventLoading = onRetryEventLoading
        self.isEventMutating = isEventMutating
        self.isTagMutating = isTagMutating
        self.eventMutationFailureMessage = eventMutationFailureMessage
        self.tagMutationFailureMessage = tagMutationFailureMessage
        self.onResetEventMutation = onResetEventMutation
        self.onResetTagMutation = onResetTagMutation
        self.onFetchRecurrenceEvent = onFetchRecurrenceEvent
        self.onUpdateSingleEvent = onUpdateSingleEvent
        self.onUpdateRecurrenceOccurrence = onUpdateRecurrenceOccurrence
        self.onUpdateRecurrenceSeries = onUpdateRecurrenceSeries
        self.onDeleteSingleEvent = onDeleteSingleEvent
        self.onDeleteRecurrenceOccurrence = onDeleteRecurrenceOccurrence
        self.onDeleteRecurrenceSeries = onDeleteRecurrenceSeries
        self.onCreateCustomTag = onCreateCustomTag
        self.onUpdateCustomTag = onUpdateCustomTag
        self.onDeleteCustomTag = onDeleteCustomTag
    }
    
    @State private var headerScrollPosition: DayKey?
    @State private var lastVisibleRange: CalendarVisibleIndexRange?
    @State private var selectedFullDayEvent: Event?
    @State private var activeTimelinePopover: TimelinePopoverPresentation?
    @State private var detailEvent: Event?
    @State private var timelineEventFrames: [String: CGRect] = [:]
    
    var body: some View {
        GeometryReader { geometry in
            let metrics = timelineMetrics(for: geometry.size)
            
            ZStack(alignment: .topLeading) {
                VStack(spacing: 0) {
                    CalendarTopBarView(
                        referenceDay: referenceDay,
                        showsTodayButton: showsTodayButton,
                        onSelectedYearMonth: onSelectedYearMonth,
                        onTodayTapped: onTodayTapped,
                        onGoogleCalendarConnectTapped: onGoogleCalendarConnectTapped,
                        onCreateTapped: onCreateTapped
                    )
                        .frame(
                            width: metrics.totalWidth,
                            height: metrics.topBarHeight,
                            alignment: .leading
                        )

                    CalendarEventStatusBannerView(
                        state: eventLoadState,
                        onRetry: onRetryEventLoading
                    )
                    
                    timelineHeader(metrics: metrics)
                        .frame(height: metrics.headerHeight, alignment: .top)
                    
                    fullDayEventRow(metrics: metrics)
                        .frame(height: metrics.fullDayEventRowHeight, alignment: .top)
                    
                    ScrollView(.vertical) {
                        timelineBody(
                            metrics: metrics,
                            containerSize: geometry.size
                        )
                            .background(TimelineScrollBounceDisabler())
                    }
                }
                
                if let activeTimelinePopover {
                    timelinePopoverOverlay(
                        selection: activeTimelinePopover,
                        containerSize: geometry.size
                    )
                }
            }
            .frame(
                width: geometry.size.width,
                height: geometry.size.height,
                alignment: .top
            )
            .background(Color(uiColor: .systemBackground))
            .coordinateSpace(name: timelineCoordinateSpace)
            .onPreferenceChange(TimelineEventFramePreferenceKey.self) { frames in
                timelineEventFrames = frames
            }
            .animation(.easeOut(duration: 0.16), value: activeTimelinePopover?.id)
        }
        .sheet(item: $detailEvent) { event in
            CalendarEventDetailView(
                event: event,
                tags: tags,
                isMutating: isEventMutating,
                isTagMutating: isTagMutating,
                mutationFailureMessage: eventMutationFailureMessage,
                tagMutationFailureMessage: tagMutationFailureMessage,
                onFetchRecurrenceEvent: onFetchRecurrenceEvent,
                onUpdateSingleEvent: onUpdateSingleEvent,
                onUpdateRecurrenceOccurrence: onUpdateRecurrenceOccurrence,
                onUpdateRecurrenceSeries: onUpdateRecurrenceSeries,
                onDeleteSingleEvent: onDeleteSingleEvent,
                onDeleteRecurrenceOccurrence: onDeleteRecurrenceOccurrence,
                onDeleteRecurrenceSeries: onDeleteRecurrenceSeries,
                onResetTagMutation: onResetTagMutation,
                onCreateCustomTag: onCreateCustomTag,
                onUpdateCustomTag: onUpdateCustomTag,
                onDeleteCustomTag: onDeleteCustomTag
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
    
    private func dayHeader(for item: CalendarDayItem, metrics: TimelineMetrics) -> some View {
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
        for item: CalendarDayItem,
        metrics: TimelineMetrics
    ) -> some View {
        let chips = fullDayChips(in: item)
        
        return VStack(spacing: 3) {
            ForEach(Array(chips.prefix(metrics.maxVisibleFullDayEventCount))) { chip in
                switch chip.kind {
                case .holiday(let holiday):
                    fullDayHolidayChip(holiday, metrics: metrics)
                case .event(let event):
                    fullDayEventChip(event, metrics: metrics)
                }
            }
            
            if chips.count > metrics.maxVisibleFullDayEventCount {
                Text("+\(chips.count - metrics.maxVisibleFullDayEventCount)")
                    .font(.system(size: metrics.fullDayEventFontSize, weight: .medium))
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 5)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }

    private func fullDayHolidayChip(
        _ holiday: NationalHoliday,
        metrics: TimelineMetrics
    ) -> some View {
        Text(holiday.title)
            .font(.system(size: metrics.fullDayEventFontSize, weight: .semibold))
            .lineLimit(1)
            .foregroundStyle(.white)
            .padding(.horizontal, metrics.eventHorizontalPadding)
            .frame(maxWidth: .infinity, minHeight: metrics.fullDayEventHeight)
            .background(
                RoundedRectangle(cornerRadius: 5)
                    .fill(Color.calendarHoliday)
            )
            .accessibilityLabel("\(holiday.title) 공휴일")
    }

    private func fullDayEventChip(
        _ event: Event,
        metrics: TimelineMetrics
    ) -> some View {
        Text(event.title)
            .font(.system(size: metrics.fullDayEventFontSize, weight: .semibold))
            .lineLimit(1)
            .foregroundStyle(.white)
            .padding(.horizontal, metrics.eventHorizontalPadding)
            .frame(maxWidth: .infinity, minHeight: metrics.fullDayEventHeight)
            .background(
                RoundedRectangle(cornerRadius: 5)
                    .fill(Color(hex: event.tag.colorCode))
            )
            .contentShape(Rectangle())
            .onTapGesture {
                selectedFullDayEvent = event
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
    
    private func timelineBody(metrics: TimelineMetrics, containerSize: CGSize) -> some View {
        ZStack(alignment: .topLeading) {
            gridLines(metrics: metrics)
            timeLabels(metrics: metrics)
            eventBlocks(metrics: metrics, containerSize: containerSize)
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
    
    private func eventBlocks(metrics: TimelineMetrics, containerSize: CGSize) -> some View {
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
                .background {
                    GeometryReader { proxy in
                        Color.clear.preference(
                            key: TimelineEventFramePreferenceKey.self,
                            value: [
                                layout.id: proxy.frame(in: .named(timelineCoordinateSpace))
                            ]
                        )
                    }
                }
                .contentShape(Rectangle())
                .gesture(
                    SpatialTapGesture()
                        .onEnded { value in
                            selectTimelineLayout(
                                layout,
                                tapX: value.location.x,
                                tapY: value.location.y,
                                metrics: metrics,
                                containerSize: containerSize
                            )
                        }
                )
                .offset(x: layout.x, y: layout.y)
        }
    }
    
    @ViewBuilder
    private func timelinePopoverOverlay(
        selection: TimelinePopoverPresentation,
        containerSize: CGSize
    ) -> some View {
        ZStack(alignment: .topLeading) {
            Color.clear
                .contentShape(Rectangle())
                .onTapGesture {
                    activeTimelinePopover = nil
                }
            
            timelinePopoverContent(selection: selection)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color(uiColor: .secondarySystemBackground))
                        .shadow(color: .black.opacity(0.16), radius: 16, x: 0, y: 8)
                )
                .overlay(alignment: selection.arrowAlignment) {
                    TimelinePopoverArrow(direction: selection.arrowDirection)
                        .fill(Color(uiColor: .secondarySystemBackground))
                        .frame(
                            width: selection.arrowSize.width,
                            height: selection.arrowSize.height
                        )
                        .offset(selection.arrowOffset)
                }
                .position(popoverCenter(for: selection, in: containerSize))
                .transition(.scale(scale: 0.96).combined(with: .opacity))
        }
        .frame(width: containerSize.width, height: containerSize.height)
        .zIndex(20)
    }
    
    @ViewBuilder
    private func timelinePopoverContent(selection: TimelinePopoverPresentation) -> some View {
        switch selection.content {
        case .event(let event):
            CalendarEventSummaryPopoverView(
                event: event,
                onShowDetail: showEventDetail
            )
        case .overlapGroup(let events):
            CalendarTimelineOverlapPopoverView(
                events: events,
                onShowDetail: showOverlapEventDetail
            )
        }
    }
    
    private func popoverCenter(
        for selection: TimelinePopoverPresentation,
        in containerSize: CGSize
    ) -> CGPoint {
        let size = selection.estimatedSize
        let edgePadding = TimelinePopoverPresentation.edgePadding
        let arrowGap = TimelinePopoverPresentation.arrowGap
        let minX = edgePadding + size.width / 2
        let maxX = max(minX, containerSize.width - edgePadding - size.width / 2)
        let minY = edgePadding + size.height / 2
        let maxY = max(minY, containerSize.height - edgePadding - size.height / 2)
        
        switch selection.placement {
        case .above:
            return CGPoint(
                x: min(max(selection.anchor.x, minX), maxX),
                y: max(minY, selection.anchor.y - arrowGap - size.height / 2)
            )
        case .below:
            return CGPoint(
                x: min(max(selection.anchor.x, minX), maxX),
                y: min(maxY, selection.anchor.y + arrowGap + size.height / 2)
            )
        case .left:
            return CGPoint(
                x: max(minX, selection.anchor.x - arrowGap - size.width / 2),
                y: min(max(selection.anchor.y, minY), maxY)
            )
        case .right:
            return CGPoint(
                x: min(maxX, selection.anchor.x + arrowGap + size.width / 2),
                y: min(max(selection.anchor.y, minY), maxY)
            )
        }
    }
    
    private var visibleItems: [CalendarDayItem] {
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
        TimelineEventLayoutBuilder(
            calendar: calendar,
            timelineStartHour: timelineStartHour,
            hourCount: timelineHours.count
        )
        .make(items: visibleItems, metrics: metrics)
    }
    
    private func isShowingEventPopover(for event: Event) -> Binding<Bool> {
        Binding(
            get: {
                selectedFullDayEvent?.id == event.id
            },
            set: { isPresented in
                if !isPresented {
                    selectedFullDayEvent = nil
                }
            }
        )
    }
    
    private func selectTimelineLayout(
        _ layout: TimelineEventLayout,
        tapX: CGFloat,
        tapY: CGFloat,
        metrics: TimelineMetrics,
        containerSize: CGSize
    ) {
        let clampedTapX = max(0, min(tapX, max(layout.width, 1)))
        let clampedTapY = max(0, min(tapY, max(layout.height, 1)))
        let anchor = timelinePopoverAnchor(
            for: layout,
            tapX: clampedTapX,
            tapY: clampedTapY
        )
        
        switch layout.tapAction {
        case .showEvent(let event):
            let content = TimelinePopoverContent.event(event)
            
            activeTimelinePopover = TimelinePopoverPresentation(
                id: layout.id,
                anchor: anchor,
                placement: timelinePopoverPlacement(
                    anchor: anchor,
                    content: content,
                    metrics: metrics,
                    containerSize: containerSize
                ),
                content: content
            )
        case .showOverlapGroup(let events):
            let content = TimelinePopoverContent.overlapGroup(events)
            
            activeTimelinePopover = TimelinePopoverPresentation(
                id: layout.id,
                anchor: anchor,
                placement: timelinePopoverPlacement(
                    anchor: anchor,
                    content: content,
                    metrics: metrics,
                    containerSize: containerSize
                ),
                content: content
            )
        }
    }
    
    private func timelinePopoverAnchor(
        for layout: TimelineEventLayout,
        tapX: CGFloat,
        tapY: CGFloat
    ) -> CGPoint {
        guard let frame = timelineEventFrames[layout.id] else {
            return CGPoint(x: layout.x + tapX, y: layout.y + tapY)
        }
        
        return CGPoint(
            x: frame.minX + tapX,
            y: frame.minY + tapY
        )
    }
    
    private func timelinePopoverPlacement(
        anchor: CGPoint,
        content: TimelinePopoverContent,
        metrics: TimelineMetrics,
        containerSize: CGSize
    ) -> TimelinePopoverPlacement {
        let size = TimelinePopoverPresentation.estimatedSize(for: content)
        let edgePadding = TimelinePopoverPresentation.edgePadding
        let arrowGap = TimelinePopoverPresentation.arrowGap
        let topEdge = anchor.y - arrowGap - size.height
        let bottomEdge = anchor.y + arrowGap + size.height
        let leftEdge = anchor.x - arrowGap - size.width
        let rightEdge = anchor.x + arrowGap + size.width
        let canCenterHorizontally = anchor.x - size.width / 2 >= edgePadding
            && anchor.x + size.width / 2 <= containerSize.width - edgePadding
        let canShowAbove = topEdge >= metrics.timelineScrollTopY
        let canShowBelow = bottomEdge <= containerSize.height - edgePadding
        let canShowLeft = leftEdge >= edgePadding
        let canShowRight = rightEdge <= containerSize.width - edgePadding
        
        if canCenterHorizontally {
            return canShowAbove || !canShowBelow ? .above : .below
        }
        
        if anchor.x < containerSize.width / 2 {
            if canShowRight {
                return .right
            }
            
            return canShowAbove || !canShowBelow ? .above : .below
        }
        
        if canShowLeft {
            return .left
        }
        
        return canShowAbove || !canShowBelow ? .above : .below
    }

    private func showEventDetail(_ event: Event) {
        selectedFullDayEvent = nil
        activeTimelinePopover = nil
        onResetEventMutation()
        detailEvent = event
    }
    
    private func showOverlapEventDetail(_ event: Event) {
        activeTimelinePopover = nil
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
    
    private func fullDayEvents(in item: CalendarDayItem) -> [Event] {
        item.events.filter { event in
            shouldShowInFullDayArea(event, on: item.id)
        }
    }

    private func fullDayChips(in item: CalendarDayItem) -> [WeekFullDayChip] {
        item.holidays.map { WeekFullDayChip(kind: .holiday($0)) }
            + fullDayEvents(in: item).map { WeekFullDayChip(kind: .event($0)) }
    }
    
    private func shouldShowInFullDayArea(_ event: Event, on day: DayKey) -> Bool {
        TimelineEventLayoutBuilder(
            calendar: calendar,
            timelineStartHour: timelineStartHour,
            hourCount: timelineHours.count
        )
        .shouldShowInFullDayArea(event, on: day)
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

private struct WeekFullDayChip: Identifiable {
    let kind: WeekFullDayChipKind

    var id: String {
        switch kind {
        case .holiday(let holiday):
            return "holiday-\(holiday.id)"
        case .event(let event):
            return event.id
        }
    }
}

private enum WeekFullDayChipKind {
    case holiday(NationalHoliday)
    case event(Event)
}

private struct TimelineEventFramePreferenceKey: PreferenceKey {
    static var defaultValue: [String: CGRect] = [:]
    
    static func reduce(
        value: inout [String: CGRect],
        nextValue: () -> [String: CGRect]
    ) {
        value.merge(nextValue()) { _, newValue in
            newValue
        }
    }
}

private struct TimelinePopoverPresentation: Identifiable {
    static let edgePadding: CGFloat = 12
    static let arrowGap: CGFloat = 12
    static let eventEstimatedHeight: CGFloat = 180
    
    let id: String
    let anchor: CGPoint
    let placement: TimelinePopoverPlacement
    let content: TimelinePopoverContent
    
    var estimatedSize: CGSize {
        Self.estimatedSize(for: content)
    }
    
    static func estimatedSize(for content: TimelinePopoverContent) -> CGSize {
        switch content {
        case .event:
            return CGSize(width: 260, height: Self.eventEstimatedHeight)
        case .overlapGroup(let events):
            let rowHeight = CGFloat(events.count) * 52
            let height = min(max(rowHeight + 54, 130), 320)
            return CGSize(width: 260, height: height)
        }
    }
    
    var arrowDirection: TimelinePopoverArrowDirection {
        switch placement {
        case .above:
            return .down
        case .below:
            return .up
        case .left:
            return .right
        case .right:
            return .left
        }
    }
    
    var arrowAlignment: Alignment {
        switch placement {
        case .above:
            return .bottom
        case .below:
            return .top
        case .left:
            return .trailing
        case .right:
            return .leading
        }
    }
    
    var arrowOffset: CGSize {
        switch placement {
        case .above:
            return CGSize(width: 0, height: 8)
        case .below:
            return CGSize(width: 0, height: -8)
        case .left:
            return CGSize(width: 8, height: 0)
        case .right:
            return CGSize(width: -8, height: 0)
        }
    }
    
    var arrowSize: CGSize {
        switch placement {
        case .above, .below:
            return CGSize(width: 18, height: 10)
        case .left, .right:
            return CGSize(width: 10, height: 18)
        }
    }
}

private enum TimelinePopoverPlacement {
    case above
    case below
    case left
    case right
}

private enum TimelinePopoverContent {
    case event(Event)
    case overlapGroup([Event])
}

private enum TimelinePopoverArrowDirection {
    case up
    case down
    case left
    case right
}

private struct TimelinePopoverArrow: Shape {
    let direction: TimelinePopoverArrowDirection
    
    func path(in rect: CGRect) -> Path {
        Path { path in
            switch direction {
            case .up:
                path.move(to: CGPoint(x: rect.midX, y: rect.minY))
                path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
                path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
            case .down:
                path.move(to: CGPoint(x: rect.minX, y: rect.minY))
                path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
                path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
            case .left:
                path.move(to: CGPoint(x: rect.minX, y: rect.midY))
                path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
                path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
            case .right:
                path.move(to: CGPoint(x: rect.minX, y: rect.minY))
                path.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
                path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
            }
            
            path.closeSubpath()
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
                                .fill(Color(hex: event.tag.colorCode))
                                .frame(width: 5, height: 34)
                            
                            VStack(alignment: .leading, spacing: 3) {
                                Text(event.title)
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(.primary)
                                    .lineLimit(1)
                                
                                Text(
                                    CalendarEventDisplayText.compactDateTimeRange(
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

private extension DayKey {
    var idValue: String {
        "\(year)-\(month)-\(day)"
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
            tag: .sample(colorCode: colorCode)
        )
    }
    
    let items: [CalendarDayItem] = (0..<7).map { offset in
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
        
        return CalendarDayItem(
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
        eventLoadState: .idle,
        onSelectedDay: { _ in },
        onVisibleRangeChanged: { _ in },
        onSelectedYearMonth: { _, _ in },
        onRetryEventLoading: {}
    )
}
