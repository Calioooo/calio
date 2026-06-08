//
//  CalendarDateEventCellView.swift
//  Calio
//
//  Created by 김준하 on 6/8/26.
//

import SwiftUI

struct CalendarDateEventCellView: View {
    
    let weekday: CalendarWeekday
    let monthText: String
    let dayText: String
    let isToday: Bool
    let onTap: () -> Void
    let events: [Event]
    
    var body: some View {
        VStack {
            Text("\(monthText) / \(dayText)")
                .font(.system(size: 18, weight: .medium))
                .frame(maxWidth: .infinity, alignment: .leading)
            FlowLayout() {
                ForEach(events, id: \.id) { event in
                    Text(event.title)
                        .font(.system(size: 13, weight: .medium))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(
                            RoundedRectangle(cornerRadius: 6)
                                .fill(Color(hex: event.colorCode))
                        )
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
    }
}

#Preview {
    CalendarDateEventCellView(
        weekday: .monday,
        monthText: "6",
        dayText: "27",
        isToday: false,
        onTap: {
            return
        },
        events: [
            Event(id: 1, title: "제목123", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), colorCode: "#FDDDDD"),
            Event(id: 2, title: "제목2345", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), colorCode: "#5DDDDD"),
            Event(id: 3, title: "제목3456", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), colorCode: "#BDDDDD"),
            Event(id: 4, title: "제목457", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), colorCode: "#ADDDDD"),
            Event(id: 5, title: "제목88", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), colorCode: "#AD9DDD"),
            Event(id: 6, title: "제목22", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), colorCode: "#B3DD6D"),
            Event(id: 7, title: "제목asd", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), colorCode: "#BDDDDD"),
            Event(id: 8, title: "제목313", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), colorCode: "#ADDDDD")
        ]
    )
}
