//
//  CalendarDateStripView.swift
//  Calio
//
//  Created by 김준하 on 6/6/26.
//

import SwiftUI

struct CalendarDateStripView: View {
    
    private let dateCellCount = 7
    
    let items: [CalendarDateCellItem]
    let onSelectedDay: (DayKey) -> Void
    
    var body: some View {
        GeometryReader { geometry in
            
            let spacing = min(max(geometry.size.width * 0.01, 3), 8)
             let totalSpacing = spacing * CGFloat(dateCellCount - 1)
             let availableWidth = geometry.size.width - totalSpacing
             let cellWidth = availableWidth / CGFloat(dateCellCount)
            
            HStack(spacing: spacing) {
                ForEach (items) { item in
                    CalendarDateCellView(
                        weekday: item.weekday,
                        dayText: item.dayText,
                        isToday: item.isToday,
                        onTap: {
                            onSelectedDay(item.id)
                        },
                        events: item.events
                    )
                    .frame(width: cellWidth)
                }
            }
            
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
        onSelectedDay: { _ in }
    )
    .frame(height: 110)
}

