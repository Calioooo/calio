//
//  CalendarDayTimelineView.swift
//  Calio
//
//  Created by Codex on 6/12/26.
//

import SwiftUI
import UIKit

struct CalendarDayTimelineView: View {
    private let calendar = Calendar.current
    private let timelineStartHour = 0
    private let timelineEndHour = 23
    
    let items: [CalendarDayItem]
    let referenceDay: DayKey
    let eventLoadState: CalendarEventLoadState
    let onRetryEventLoading: () -> Void
    
    var body: some View {
        GeometryReader { geometry in
            let metrics = timelineMetrics(for: geometry.size)
            
            VStack(spacing: 0) {
                Text(dayTitle)
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(.primary)
                    .frame(
                        width: metrics.totalWidth,
                        height: metrics.titleHeight,
                        alignment: .leading
                    )
                    .padding(.leading, metrics.titleLeadingPadding)

                CalendarEventStatusBannerView(
                    state: eventLoadState,
                    onRetry: onRetryEventLoading
                )
                
                dayHeader(metrics: metrics)
                    .frame(height: metrics.headerHeight, alignment: .top)
                
                fullDayEventRow(metrics: metrics)
                    .frame(height: metrics.fullDayEventRowHeight, alignment: .top)
                
                ScrollView(.vertical) {
                    timelineBody(metrics: metrics)
                        .background(DayTimelineScrollBounceDisabler())
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
    
    private func dayHeader(metrics: DayTimelineMetrics) -> some View {
        ZStack(alignment: .topLeading) {
            Path { path in
                path.move(to: CGPoint(x: metrics.gridStartX, y: metrics.headerGridLineStartY))
                path.addLine(to: CGPoint(x: metrics.gridStartX, y: metrics.headerHeight))
                path.move(to: CGPoint(x: metrics.totalWidth, y: metrics.headerGridLineStartY))
                path.addLine(to: CGPoint(x: metrics.totalWidth, y: metrics.headerHeight))
            }
            .stroke(Color.secondary.opacity(0.28), lineWidth: 0.7)
            
            VStack(spacing: 8) {
                Text(currentItem?.weekday.fullKoreanText ?? "")
                    .font(.system(size: metrics.weekdayFontSize, weight: .medium))
                    .foregroundStyle(dayHeaderColor)
                
                Text("\(referenceDay.day)")
                    .font(.system(size: metrics.dayNumberFontSize, weight: .regular))
                    .foregroundStyle(currentItem?.isToday == true ? .white : dayHeaderColor)
                    .frame(width: metrics.dayCircleSize, height: metrics.dayCircleSize)
                    .background {
                        if currentItem?.isToday == true {
                            Circle()
                                .fill(Color(red: 0.56, green: 0.61, blue: 0.96))
                        }
                    }
            }
            .frame(
                width: metrics.dayColumnWidth,
                height: metrics.headerHeight,
                alignment: .bottom
            )
            .offset(x: metrics.gridStartX)
            .padding(.bottom, 5)
        }
        .frame(
            width: metrics.totalWidth,
            height: metrics.headerHeight,
            alignment: .topLeading
        )
    }
    
    private func fullDayEventRow(metrics: DayTimelineMetrics) -> some View {
        ZStack(alignment: .topLeading) {
            fullDayEventRowGrid(metrics: metrics)
            
            VStack(spacing: 3) {
                ForEach(Array(fullDayEvents.prefix(metrics.maxVisibleFullDayEventCount))) { event in
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
                }
                
                if fullDayEvents.count > metrics.maxVisibleFullDayEventCount {
                    Text("+\(fullDayEvents.count - metrics.maxVisibleFullDayEventCount)")
                        .font(.system(size: metrics.fullDayEventFontSize, weight: .medium))
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 5)
            .frame(
                width: metrics.dayColumnWidth,
                height: metrics.fullDayEventRowHeight,
                alignment: .topLeading
            )
            .offset(x: metrics.gridStartX)
        }
        .frame(
            width: metrics.totalWidth,
            height: metrics.fullDayEventRowHeight,
            alignment: .topLeading
        )
    }
    
    private func fullDayEventRowGrid(metrics: DayTimelineMetrics) -> some View {
        Path { path in
            path.move(to: CGPoint(x: metrics.horizontalLineStartX, y: 0))
            path.addLine(to: CGPoint(x: metrics.totalWidth, y: 0))
            path.move(to: CGPoint(x: metrics.horizontalLineStartX, y: metrics.fullDayEventRowHeight))
            path.addLine(to: CGPoint(x: metrics.totalWidth, y: metrics.fullDayEventRowHeight))
            
            path.move(to: CGPoint(x: metrics.gridStartX, y: 0))
            path.addLine(to: CGPoint(x: metrics.gridStartX, y: metrics.fullDayEventRowHeight))
            path.move(to: CGPoint(x: metrics.totalWidth, y: 0))
            path.addLine(to: CGPoint(x: metrics.totalWidth, y: metrics.fullDayEventRowHeight))
        }
        .stroke(Color.secondary.opacity(0.28), lineWidth: 0.7)
    }
    
    private func timelineBody(metrics: DayTimelineMetrics) -> some View {
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
    
    private func gridLines(metrics: DayTimelineMetrics) -> some View {
        Path { path in
            for index in 0...timelineHours.count {
                let y = metrics.timelineTopInset + CGFloat(index) * metrics.hourHeight
                path.move(to: CGPoint(x: metrics.horizontalLineStartX, y: y))
                path.addLine(to: CGPoint(x: metrics.totalWidth, y: y))
            }
            
            path.move(to: CGPoint(x: metrics.gridStartX, y: 0))
            path.addLine(to: CGPoint(x: metrics.gridStartX, y: metrics.totalGridHeight))
            path.move(to: CGPoint(x: metrics.totalWidth, y: 0))
            path.addLine(to: CGPoint(x: metrics.totalWidth, y: metrics.totalGridHeight))
        }
        .stroke(Color.secondary.opacity(0.28), lineWidth: 0.7)
    }
    
    private func timeLabels(metrics: DayTimelineMetrics) -> some View {
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
    
    private func eventBlocks(metrics: DayTimelineMetrics) -> some View {
        ForEach(eventLayouts(metrics: metrics)) { layout in
            Text(layout.event.title)
                .font(.system(size: metrics.eventFontSize, weight: .semibold))
                .lineLimit(2)
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
                        .fill(Color(hex: layout.event.tag.colorCode))
                )
                .offset(x: layout.x, y: layout.y)
        }
    }
    
    private var currentItem: CalendarDayItem? {
        Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) })[referenceDay]
    }
    
    private var timelineHours: [Int] {
        Array(timelineStartHour...timelineEndHour)
    }
    
    private var dayTitle: String {
        "\(referenceDay.year)년 \(referenceDay.month)월 \(referenceDay.day)일"
    }
    
    private var timedEvents: [Event] {
        (currentItem?.events ?? []).filter { event in
            !shouldShowInFullDayArea(event)
        }
    }
    
    private var fullDayEvents: [Event] {
        (currentItem?.events ?? []).filter(shouldShowInFullDayArea)
    }
    
    private var dayHeaderColor: Color {
        switch currentItem?.weekday {
        case .sunday:
            return .red
        case .saturday:
            return .blue
        default:
            return .primary
        }
    }
    
    private func eventLayouts(metrics: DayTimelineMetrics) -> [DayTimelineEventLayout] {
        timedEvents.compactMap { event in
            makeEventLayout(event: event, metrics: metrics)
        }
    }
    
    private func makeEventLayout(
        event: Event,
        metrics: DayTimelineMetrics
    ) -> DayTimelineEventLayout? {
        guard let displayRange = displayRange(for: event) else {
            return nil
        }
        
        guard let startOffset = hourOffset(from: displayRange.startAt),
              let endOffset = hourOffset(from: displayRange.endAt)
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
        
        let horizontalPadding: CGFloat = 8
        
        return DayTimelineEventLayout(
            event: event,
            x: metrics.gridStartX + horizontalPadding,
            y: metrics.timelineTopInset + clampedStartOffset * metrics.hourHeight,
            width: metrics.dayColumnWidth - (horizontalPadding * 2),
            height: (clampedEndOffset - clampedStartOffset) * metrics.hourHeight
        )
    }
    
    private func displayRange(for event: Event) -> (startAt: Date, endAt: Date)? {
        let dayStart = referenceDay.toDate(calendar: calendar)
        
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
    
    private func shouldShowInFullDayArea(_ event: Event) -> Bool {
        let dayStart = referenceDay.toDate(calendar: calendar)
        
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
    
    private func hourOffset(from date: Date) -> CGFloat? {
        let dayStart = referenceDay.toDate(calendar: calendar)
        
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
    
    private func timelineMetrics(for size: CGSize) -> DayTimelineMetrics {
        let timeColumnWidth = min(max(size.width * 0.14, 44), 64)
        let dayColumnWidth = max(size.width - timeColumnWidth, 1)
        let titleHeight = min(max(size.height * 0.07, 44), 58)
        let headerHeight = min(max(size.height * 0.11, 78), 110)
        let fullDayEventRowHeight = min(max(size.height * 0.07, 46), 58)
        let availableTimelineHeight = max(
            size.height - titleHeight - headerHeight - fullDayEventRowHeight,
            1
        )
        let hourHeight = max(56, availableTimelineHeight / 12)
        
        return DayTimelineMetrics(
            timeColumnWidth: timeColumnWidth,
            dayColumnWidth: dayColumnWidth,
            titleHeight: titleHeight,
            headerHeight: headerHeight,
            fullDayEventRowHeight: fullDayEventRowHeight,
            hourHeight: hourHeight,
            hourCount: timelineHours.count
        )
    }
}

private struct DayTimelineMetrics {
    let timeColumnWidth: CGFloat
    let dayColumnWidth: CGFloat
    let titleHeight: CGFloat
    let headerHeight: CGFloat
    let fullDayEventRowHeight: CGFloat
    let hourHeight: CGFloat
    let hourCount: Int
    
    var gridStartX: CGFloat {
        timeColumnWidth
    }
    
    var titleLeadingPadding: CGFloat {
        20
    }
    
    var timeLabelFontSize: CGFloat {
        14
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
    
    var weekdayFontSize: CGFloat {
        14
    }
    
    var dayNumberFontSize: CGFloat {
        28
    }
    
    var dayCircleSize: CGFloat {
        38
    }
    
    var fullDayEventFontSize: CGFloat {
        12
    }
    
    var fullDayEventHeight: CGFloat {
        22
    }
    
    var maxVisibleFullDayEventCount: Int {
        max(Int((fullDayEventRowHeight - 10) / (fullDayEventHeight + 3)), 1)
    }
    
    var eventFontSize: CGFloat {
        13
    }
    
    var eventHorizontalPadding: CGFloat {
        8
    }
    
    var eventVerticalPadding: CGFloat {
        5
    }
    
    var minimumEventHeight: CGFloat {
        28
    }
    
    var horizontalLineStartX: CGFloat {
        timeColumnWidth - timeLabelTrailingPadding
    }
    
    var totalWidth: CGFloat {
        timeColumnWidth + dayColumnWidth
    }
    
    var totalGridHeight: CGFloat {
        timelineTopInset + hourHeight * CGFloat(hourCount)
    }
}

private struct DayTimelineEventLayout: Identifiable {
    let event: Event
    let x: CGFloat
    let y: CGFloat
    let width: CGFloat
    let height: CGFloat
    
    var id: String {
        event.id
    }
}

private struct DayTimelineScrollBounceDisabler: UIViewRepresentable {
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
    let date = calendar.date(
        from: DateComponents(year: 2026, month: 6, day: 12)
    ) ?? Date()
    let day = DayKey(date: date, calendar: calendar)
    let startOfDay = calendar.startOfDay(for: date)
    let makeEvent: (Int64, String, Int, Int, Int, String) -> Event = { id, title, hour, minute, durationMinutes, colorCode in
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
    let fullDayEndAt = calendar.date(
        byAdding: .day,
        value: 1,
        to: startOfDay
    ) ?? startOfDay
    let item = CalendarDayItem(
        id: day,
        weekday: dateService.getWeekday(from: date),
        monthText: dateService.monthText(from: date),
        dayText: dateService.dayText(from: date),
        isToday: true,
        events: [
            Event(
                id: 1,
                title: "하루 종일",
                description: "",
                startAt: startOfDay,
                endAt: fullDayEndAt,
                tag: .sample(colorCode: "#4F46E5")
            ),
            makeEvent(2, "팀 주간 회의", 10, 30, 90, "#EF4444"),
            makeEvent(3, "제품 리뷰", 14, 0, 120, "#22C55E"),
            makeEvent(4, "저녁 약속", 19, 30, 90, "#F59E0B")
        ]
    )
    
    CalendarDayTimelineView(
        items: [item],
        referenceDay: day,
        eventLoadState: .idle,
        onRetryEventLoading: {}
    )
}
