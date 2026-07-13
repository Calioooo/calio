//
//  CalendarDateCellView.swift
//  Calio
//
//  Created by 김준하 on 6/6/26.
//

import SwiftUI

struct CalendarDateCellView: View {
    private let dateTopOffset: CGFloat = 5
    private let dateNumberSize: CGFloat = 30
    private let indicatorTopSpacing: CGFloat = 6
    private let maxVisibleEventCount = 4
    
    let weekday: CalendarWeekday
    let dayText: String
    let isToday: Bool
    let onTap: () -> Void
    let events: [Event]
    
    init(weekday: CalendarWeekday, dayText: String, isToday: Bool, onTap: @escaping () -> Void, events: [Event]) {
        self.weekday = weekday
        self.dayText = dayText
        self.isToday = isToday
        self.onTap = onTap
        self.events = events
    }
    
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 5) {
                dateNumberText
                eventIndicatorBars
                if hiddenEventCount > 0 {
                    hiddenEventText
                }
            }
            .padding(.top, 5)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
    
    private var dateNumberText: some View {
        Text(dayText)
            .font(.system(size: 17))
            .fontWeight(.medium)
            .foregroundStyle(isToday ? .white : weekdayTextColor)
            .frame(width: 30, height: 30)
            .background{
                if isToday {
                    Circle()
                        .fill(Color(red: 0.56, green: 0.76, blue: 0.96))
                    
                }
            }
    }
    
    private var eventIndicatorBars: some View {
        VStack(spacing: 5) {
            ForEach(Array(visibleEvents.enumerated()), id: \.offset) { _, event in
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color(hex: event.tag.colorCode))
                    .frame(height: 8)
            }
        }
    }
    
    private var visibleEvents: [Event] {
        let eventLimit = events.count > maxVisibleEventCount
        ? maxVisibleEventCount
        : events.count
        
        return Array(events.prefix(eventLimit))
    }
    
    private var hiddenEventCount: Int {
        events.count - visibleEvents.count
    }
    
    private var hiddenEventText: some View {
        return Text("+\(hiddenEventCount)")
            .font(.system(size: 12, weight: .medium))
            .foregroundStyle(.secondary)
    }
    
    private var weekdayTextColor: Color {
        switch weekday {
        case .saturday:
            return .blue
        case .sunday:
            return .red
        default:
            return .secondary
        }
    }
}



#Preview {
    CalendarDateCellView(
        weekday: .monday,
        dayText: "15",
        isToday: true,
        onTap: {
            return
        },
        events: [
            Event(title: "제목", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#FDDDDD")),
            Event(title: "제목", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#RDDDDD")),
            Event(title: "제목", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#BDDDDD")),
            Event(title: "제목", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#ADDDDD"))
        ]
    )
}

#Preview {
    CalendarDateCellView(
        weekday: .monday,
        dayText: "27",
        isToday: false,
        onTap: {
            return
        },
        events: [
            Event(title: "제목", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#FDDDDD")),
            Event(title: "제목", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#RDDDDD")),
            Event(title: "제목", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#BDDDDD")),
            Event(title: "제목", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#ADDDDD")),
            Event(title: "제목", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#FDDDDD")),
            Event(title: "제목", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#RDDDDD")),
            Event(title: "제목", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#BDDDDD")),
            Event(title: "제목", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#ADDDDD"))
        ]
    )
}

#Preview {
    CalendarDateCellView(
        weekday: .saturday,
        dayText: "29",
        isToday: false,
        onTap: {
            return
        },
        events: []
    )
}

#Preview {
    CalendarDateCellView(
        weekday: .sunday,
        dayText: "30",
        isToday: true,
        onTap: {
            return
        },
        events: []
    )
}
