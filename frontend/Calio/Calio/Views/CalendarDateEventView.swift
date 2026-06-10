//
//  CalendarDateEventView.swift
//  Calio
//
//  Created by 김준하 on 6/8/26.
//

import SwiftUI

struct CalendarDateEventView: View {
    private let contentRowHeight: CGFloat = 96
    
    let items: [CalendarDateCellItem]
    let focusedDay: DayKey
    let onFocusedDayChanged: (DayKey) -> Void
    var targetOffset: CalendarScrollTarget?
    var onScrollProgressChanged: (CGFloat, CalendarVisibleIndexRange) -> Void = { _, _ in }
    var onScrollEnded: (CGFloat) -> Void = { _ in }
    
    var body: some View {
        GeometryReader { geometry in
            CalendarOffsetScrollView(
                axis: .vertical,
                showsIndicators: true,
                targetOffset: targetOffset,
                onUserOffsetChanged: { contentOffset, viewportLength in
                    reportScrollProgress(
                        contentOffset: contentOffset,
                        viewportLength: viewportLength
                    )
                },
                onUserScrollEnded: { contentOffset, _ in
                    let progress = CalendarScrollMetrics.progress(
                        contentOffset: contentOffset,
                        itemExtent: CalendarScrollMetrics.eventRowHeight
                    )
                    onScrollEnded(progress)
                }
            ) {
                LazyVStack(spacing: 0) {
                    ForEach(items) { item in
                        CalendarDateEventCellView(
                            weekday: item.weekday,
                            monthText: item.monthText,
                            dayText: item.dayText,
                            isToday: item.isToday,
                            onTap: {
                                reportUserFocusedDay(item.id)
                            },
                            events: item.events
                        )
                        .frame(height: contentRowHeight)
                        .padding(.bottom, CalendarScrollMetrics.eventRowHeight - contentRowHeight)
                        .frame(height: CalendarScrollMetrics.eventRowHeight, alignment: .top)
                        .padding(.horizontal, 16)
                        .clipped()
                        .id(item.id)
                    }
                }
                .padding(.bottom, bottomSnapPadding(viewportHeight: geometry.size.height))
            }
        }
    }
    
    private func reportScrollProgress(
        contentOffset: CGFloat,
        viewportLength: CGFloat
    ) {
        let progress = CalendarScrollMetrics.progress(
            contentOffset: contentOffset,
            itemExtent: CalendarScrollMetrics.eventRowHeight
        )

        guard let visibleRange = CalendarScrollMetrics.visibleIndexRange(
            contentOffset: contentOffset,
            viewportLength: viewportLength,
            itemExtent: CalendarScrollMetrics.eventRowHeight,
            itemCount: items.count
        ) else {
            return
        }

        onScrollProgressChanged(progress, visibleRange)
    }

    private func reportUserFocusedDay(_ day: DayKey) {
        onFocusedDayChanged(day)
    }

    private func bottomSnapPadding(viewportHeight: CGFloat) -> CGFloat {
        max(viewportHeight - CalendarScrollMetrics.eventRowHeight, 24)
    }
}

#Preview {
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
    
    CalendarDateEventView(
        items: items,
        focusedDay: items[0].id,
        onFocusedDayChanged: { _ in }
    )
    .frame(height: 110)
}
