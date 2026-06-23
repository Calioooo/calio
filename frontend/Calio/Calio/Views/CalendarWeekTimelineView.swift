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
    let focusedDay: DayKey
    let eventAreaState: CalendarEventAreaState
    let onSelectedDay: (DayKey) -> Void
    let onVisibleRangeChanged: (CalendarVisibleIndexRange) -> Void
    let onSelectedYearMonth: (Int, Int) -> Void
    let onRetryEvents: () -> Void
    
    @State private var headerScrollPosition: DayKey?
    @State private var lastVisibleRange: CalendarVisibleIndexRange?
    @State private var selectedEvent: Event?
    
    var body: some View {
        GeometryReader { geometry in
            let metrics = timelineMetrics(for: geometry.size)
            
            VStack(spacing: 0) {
                CalendarYearMonthTitleView(
                    focusedDay: focusedDay,
                    onSelectedYearMonth: onSelectedYearMonth
                )
                    .frame(
                        width: metrics.totalWidth,
                        height: metrics.monthTitleHeight,
                        alignment: .leading
                    )
                    .padding(.leading, metrics.monthTitleLeadingPadding)

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
                updateFocusedDayFromHeaderScrollPosition(newDay)
            }
            .onChange(of: focusedDay) { _, newDay in
                alignHeader(to: newDay)
            }
            .onChange(of: items.count) { _, _ in
                alignHeader(to: focusedDay)
            }
            .onAppear {
                alignHeader(to: focusedDay)
                notifyVisibleRangeChanged(around: focusedDay)
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
        let isFocused = item.id == focusedDay
        
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
                        isFocused
                            ? Color(red: 0.56, green: 0.61, blue: 0.96)
                            : Color.clear
                    )
                    .frame(
                        width: metrics.focusedDayIndicatorWidth,
                        height: metrics.focusedDayIndicatorHeight
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
                        CalendarEventSummaryPopoverView(event: event)
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
            Text(layout.event.title)
                .font(.system(size: metrics.eventFontSize, weight: .semibold))
                .lineLimit(1)
                .foregroundStyle(.white)
                .padding(.horizontal, metrics.eventHorizontalPadding)
                .padding(.vertical, metrics.eventVerticalPadding)
                .frame(
                    width: max(layout.width, 0),
                    height: max(layout.height, metrics.minimumEventHeight),
                    alignment: .topLeading
                )
                .background(
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Color(hex: layout.event.colorCode))
                )
                .contentShape(Rectangle())
                .onTapGesture {
                    selectedEvent = layout.event
                }
                .popover(
                    isPresented: isShowingEventPopover(for: layout.event),
                    attachmentAnchor: .rect(.bounds),
                    arrowEdge: .top
                ) {
                    CalendarEventSummaryPopoverView(event: layout.event)
                        .presentationCompactAdaptation(.popover)
                }
                .offset(x: layout.x, y: layout.y)
        }
    }
    
    private var visibleItems: [CalendarDateCellItem] {
        guard let focusedIndex = items.firstIndex(where: { $0.id == focusedDay }) else {
            return []
        }
        
        let endIndex = min(items.endIndex, focusedIndex + visibleDayCount)
        return Array(items[focusedIndex..<endIndex])
    }
    
    private var timelineHours: [Int] {
        Array(timelineStartHour...timelineEndHour)
    }
    
    private func eventLayouts(metrics: TimelineMetrics) -> [TimelineEventLayout] {
        visibleItems.enumerated().flatMap { dayIndex, item in
            let events = timedEvents(in: item)
            
            return events.compactMap { event in
                makeEventLayout(
                    event: event,
                    dayIndex: dayIndex,
                    metrics: metrics
                )
            }
        }
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
    
    private func updateFocusedDayFromHeaderScrollPosition(_ day: DayKey?) {
        guard let day else { return }
        
        notifyVisibleRangeChanged(around: day)
        guard day != focusedDay else { return }
        
        onSelectedDay(day)
    }
    
    private func alignHeader(to day: DayKey) {
        guard items.contains(where: { $0.id == day }) else { return }
        guard headerScrollPosition != day else { return }
        
        headerScrollPosition = day
        notifyVisibleRangeChanged(around: day)
    }
    
    private func notifyVisibleRangeChanged(around day: DayKey) {
        guard let focusedIndex = items.firstIndex(where: { $0.id == day }) else {
            return
        }
        
        let endIndex = min(
            items.count - 1,
            focusedIndex + visibleDayCount - 1
        )
        
        let visibleRange = CalendarVisibleIndexRange(
            startIndex: focusedIndex,
            endIndex: endIndex
        )
        
        guard visibleRange != lastVisibleRange else {
            return
        }
        
        lastVisibleRange = visibleRange
        onVisibleRangeChanged(visibleRange)
    }
    
    private func makeEventLayout(
        event: Event,
        dayIndex: Int,
        metrics: TimelineMetrics
    ) -> TimelineEventLayout? {
        let startComponents = calendar.dateComponents([.hour, .minute], from: event.startAt)
        let endComponents = calendar.dateComponents([.hour, .minute], from: event.endAt)
        
        guard let startHour = startComponents.hour,
              let startMinute = startComponents.minute,
              let endHour = endComponents.hour,
              let endMinute = endComponents.minute
        else {
            return nil
        }
        
        let startOffset = hourOffset(hour: startHour, minute: startMinute)
        let endOffset = hourOffset(hour: endHour, minute: endMinute)
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
        
        let horizontalPadding: CGFloat = 6
        let x = metrics.gridStartX + CGFloat(dayIndex) * metrics.dayColumnWidth + horizontalPadding
        let y = metrics.timelineTopInset + clampedStartOffset * metrics.hourHeight
        let width = metrics.dayColumnWidth - (horizontalPadding * 2)
        let height = (clampedEndOffset - clampedStartOffset) * metrics.hourHeight
        
        return TimelineEventLayout(
            event: event,
            x: x,
            y: y,
            width: width,
            height: height
        )
    }
    
    private func timedEvents(in item: CalendarDateCellItem) -> [Event] {
        item.events.filter { event in
            !isFullDayEvent(event)
        }
    }
    
    private func fullDayEvents(in item: CalendarDateCellItem) -> [Event] {
        item.events.filter(isFullDayEvent)
    }
    
    private func isFullDayEvent(_ event: Event) -> Bool {
        let startOfStartDay = calendar.startOfDay(for: event.startAt)
        
        guard let startOfNextDay = calendar.date(
            byAdding: .day,
            value: 1,
            to: startOfStartDay
        ) else {
            return false
        }
        
        return event.startAt <= startOfStartDay && event.endAt >= startOfNextDay
    }
    
    private func hourOffset(hour: Int, minute: Int) -> CGFloat {
        CGFloat(hour - timelineStartHour) + CGFloat(minute) / 60
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
        let monthTitleHeight = min(max(size.height * 0.07, 44), 58)
        let headerHeight = min(max(size.height * 0.11, 78), 110)
        let fullDayEventRowHeight = min(max(size.height * 0.07, 46), 58)
        let availableTimelineHeight = max(
            size.height - monthTitleHeight - headerHeight - fullDayEventRowHeight,
            1
        )
        let hourHeight = max(56, availableTimelineHeight / 12)
        
        return TimelineMetrics(
            timeColumnWidth: timeColumnWidth,
            dayColumnWidth: dayColumnWidth,
            monthTitleHeight: monthTitleHeight,
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
    let monthTitleHeight: CGFloat
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
    
    var monthTitleLeadingPadding: CGFloat {
        20
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
    
    var weekdayFontSize: CGFloat {
        dayColumnWidth < 44 ? 9 : 12
    }
    
    var dayNumberFontSize: CGFloat {
        dayColumnWidth < 44 ? 18 : 24
    }
    
    var dayCircleSize: CGFloat {
        min(max(dayColumnWidth * 0.5, 30), 30)
    }
    
    var focusedDayIndicatorWidth: CGFloat {
        min(max(dayColumnWidth * 0.28, 18), 24)
    }
    
    var focusedDayIndicatorHeight: CGFloat {
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

private struct TimelineEventLayout: Identifiable {
    let event: Event
    let x: CGFloat
    let y: CGFloat
    let width: CGFloat
    let height: CGFloat
    
    var id: Int64 {
        event.id
    }
}

private struct CalendarEventSummaryPopoverView: View {
    let event: Event
    
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 8) {
                RoundedRectangle(cornerRadius: 3)
                    .fill(Color(hex: event.colorCode))
                    .frame(width: 6, height: 32)
                
                VStack(alignment: .leading, spacing: 4) {
                    Text(event.title)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(2)
                    
                    Text(eventTimeText)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(.secondary)
                }
            }
            
            if hasDescription {
                Text(event.description)
                    .font(.system(size: 14, weight: .regular))
                    .foregroundStyle(.primary)
                    .lineLimit(4)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(14)
        .frame(width: 260, alignment: .leading)
    }
    
    private var hasDescription: Bool {
        !event.description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    
    private var eventTimeText: String {
        let startText = event.startAt.formatted(date: .omitted, time: .shortened)
        let endText = event.endAt.formatted(date: .omitted, time: .shortened)
        
        return "\(startText) - \(endText)"
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
            isSelected: offset == 0,
            events: events
        )
    }
    
    CalendarWeekTimelineView(
        items: items,
        focusedDay: items[0].id,
        eventAreaState: .idle,
        onSelectedDay: { _ in },
        onVisibleRangeChanged: { _ in },
        onSelectedYearMonth: { _, _ in },
        onRetryEvents: {}
    )
}
