//
//  CalendarDateStripView.swift
//  Calio
//
//  Created by 김준하 on 6/6/26.
//

import SwiftUI

struct CalendarDateStripView: View {
    private let dateCellCount = 7
    private let coordinateSpaceName = "calendar_date_strip_scroll"
    private let programmaticAlignmentDelay: UInt64 = 300_000_000
    
    let items: [CalendarDateCellItem]
    let focusedDay: DayKey
    let onFocusedDayChanged: (DayKey) -> Void
    
    @State private var scrollPosition: DayKey?
    @State private var isProgrammaticAlignment = false
    @State private var lastUserRequestedDay: DayKey?
    @State private var lastReportedDay: DayKey?
    @State private var resetProgrammaticAlignmentTask: Task<Void, Never>?
    
    var body: some View {
        GeometryReader { geometry in
            let spacing = min(max(geometry.size.width * 0.01, 3), 8)
            let totalSpacing = spacing * CGFloat(dateCellCount - 1)
            let availableWidth = geometry.size.width - totalSpacing
            let cellWidth = availableWidth / CGFloat(dateCellCount)
            
            ScrollView(.horizontal) {
                LazyHStack(spacing: spacing) {
                    ForEach(items) { item in
                        CalendarDateCellView(
                            weekday: item.weekday,
                            dayText: item.dayText,
                            isToday: item.isToday,
                            onTap: {
                                reportUserFocusedDay(item.id)
                            },
                            events: item.events
                        )
                        .frame(width: cellWidth)
                        .id(item.id)
                        .background(
                            GeometryReader { proxy in
                                Color.clear.preference(
                                    key: CalendarDateStripCellFramePreferenceKey.self,
                                    value: [item.id: proxy.frame(in: .named(coordinateSpaceName))]
                                )
                            }
                        )
                    }
                }
                .scrollTargetLayout()
            }
            .coordinateSpace(name: coordinateSpaceName)
            .scrollIndicators(.hidden)
            .scrollTargetBehavior(.viewAligned)
            .scrollPosition(id: $scrollPosition, anchor: .leading)
            .onPreferenceChange(CalendarDateStripCellFramePreferenceKey.self) { frames in
                updateFocusedDayFromLeadingCell(frames)
            }
            .onChange(of: focusedDay) { _, newDay in
                alignIfNeeded(to: newDay)
            }
            .onChange(of: items.map(\.id)) { _, _ in
                alignProgrammatically(to: focusedDay)
            }
            .onAppear {
                alignProgrammatically(to: focusedDay)
            }
            .onDisappear {
                resetProgrammaticAlignmentTask?.cancel()
            }
        }
    }
    
    private func updateFocusedDayFromLeadingCell(_ frames: [DayKey: CGRect]) {
        guard !isProgrammaticAlignment else { return }
        guard let day = leadingVisibleDay(in: frames) else { return }
        guard day != lastReportedDay else { return }
        
        reportUserFocusedDay(day)
    }
    
    private func leadingVisibleDay(in frames: [DayKey: CGRect]) -> DayKey? {
        let crossingOrVisibleBeforeLeading = frames.filter { _, frame in
            frame.minX <= 0 && frame.maxX > 0
        }
        
        if let day = crossingOrVisibleBeforeLeading.max(by: { $0.value.minX < $1.value.minX })?.key {
            return day
        }
        
        return frames
            .filter { _, frame in frame.minX > 0 }
            .min(by: { $0.value.minX < $1.value.minX })?
            .key
    }
    
    private func reportUserFocusedDay(_ day: DayKey) {
        lastUserRequestedDay = day
        lastReportedDay = day
        onFocusedDayChanged(day)
    }
    
    private func alignIfNeeded(to day: DayKey) {
        if lastUserRequestedDay == day {
            lastUserRequestedDay = nil
            return
        }
        
        alignProgrammatically(to: day)
    }
    
    private func alignProgrammatically(to day: DayKey) {
        guard items.contains(where: { $0.id == day }) else { return }
        
        isProgrammaticAlignment = true
        scrollPosition = day
        resetProgrammaticAlignmentTask?.cancel()
        resetProgrammaticAlignmentTask = Task {
            try? await Task.sleep(nanoseconds: programmaticAlignmentDelay)
            guard !Task.isCancelled else { return }
            
            await MainActor.run {
                isProgrammaticAlignment = false
            }
        }
    }
}

private struct CalendarDateStripCellFramePreferenceKey: PreferenceKey {
    static var defaultValue: [DayKey: CGRect] = [:]
    
    static func reduce(value: inout [DayKey: CGRect], nextValue: () -> [DayKey: CGRect]) {
        value.merge(nextValue()) { _, next in
            next
        }
    }
}

#Preview("iPhone") {
    let calendar = Calendar.current
    let dateService = CalendarDateService(calendar: calendar)
    let today = Date()
    
    let makeEvent: (Int64, String, Date, Int, String) -> Event = { id, title, date, hour, colorCode in
        let startOfDay = calendar.startOfDay(for: date)
        
        let startAt = calendar.date(
            bySettingHour: hour,
            minute: 0,
            second: 0,
            of: startOfDay
        ) ?? startOfDay
        
        let endAt = calendar.date(
            byAdding: .hour,
            value: 1,
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
            to: today
        ) ?? today
        
        let day = DayKey(date: date, calendar: calendar)
        
        let events: [Event] = switch offset {
        case 0:
            [
                makeEvent(1, "팀 미팅", date, 9, "#4F46E5"),
                makeEvent(2, "제품 리뷰", date, 13, "#059669")
            ]
            
        case 1:
            [
                makeEvent(3, "1:1", date, 10, "#DC2626")
            ]
            
        case 2:
            [
                makeEvent(4, "운동", date, 8, "#D97706"),
                makeEvent(5, "API 정리", date, 15, "#0891B2"),
                makeEvent(6, "저녁 약속", date, 19, "#4F46E5")
            ]
            
        case 4:
            [
                makeEvent(7, "디자인 리뷰", date, 14, "#059669"),
                makeEvent(8, "회고", date, 16, "#DC2626"),
                makeEvent(9, "문서 정리", date, 17, "#D97706"),
                makeEvent(10, "개인 일정", date, 20, "#0891B2"),
                makeEvent(11, "추가 일정", date, 21, "#4F46E5")
            ]
            
        default:
            []
        }
        
        return CalendarDateCellItem(
            id: day,
            weekday: dateService.getWeekday(from: date),
            monthText: dateService.monthText(from: date),
            dayText: dateService.dayText(from: date),
            isToday: calendar.isDateInToday(date),
            isSelected: offset == 0,
            events: events
        )
    }
    
    CalendarDateStripView(
        items: items,
        focusedDay: items[0].id,
        onFocusedDayChanged: { _ in }
    )
    .frame(height: 110)
}
