//
//  CalendarDateEventCellView.swift
//  Calio
//
//  Created by 김준하 on 6/8/26.
//

import SwiftUI
import UIKit

struct CalendarDateEventCellView: View {
    private let maxVisibleEventRowCount = 3
    private let eventChipHorizontalPadding: CGFloat = 20
    private let eventChipSpacing: CGFloat = 8
    private let eventChipFont = UIFont.systemFont(ofSize: 13, weight: .medium)
    
    let weekday: CalendarWeekday
    let monthText: String
    let dayText: String
    let isToday: Bool
    let onTap: () -> Void
    let events: [Event]
    
    var body: some View {
        GeometryReader { geometry in
            let chipLayout = eventChipLayout(maxWidth: geometry.size.width)
            
            VStack(spacing: 8) {
                Text("\(monthText) / \(dayText)")
                    .font(.system(size: 18, weight: .medium))
                    .frame(maxWidth: .infinity, alignment: .leading)
                FlowLayout(spacing: eventChipSpacing) {
                    ForEach(chipLayout.visibleEvents, id: \.id) { event in
                        Text(event.title)
                            .font(.system(size: 13, weight: .medium))
                            .lineLimit(1)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(
                                RoundedRectangle(cornerRadius: 6)
                                    .fill(Color(hex: event.colorCode))
                            )
                    }
                    
                    if chipLayout.hiddenEventCount > 0 {
                        Text("+\(chipLayout.hiddenEventCount) more")
                            .font(.system(size: 13, weight: .medium))
                            .lineLimit(1)
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(
                                RoundedRectangle(cornerRadius: 6)
                                    .fill(Color.secondary.opacity(0.12))
                            )
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
    }
    
    private func eventChipLayout(maxWidth: CGFloat) -> EventChipLayout {
        guard maxWidth > 0 else {
            return EventChipLayout(visibleEvents: [], hiddenEventCount: events.count)
        }
        
        var visibleEvents: [Event] = []
        
        for event in events {
            let candidateEvents = visibleEvents + [event]
            let hiddenEventCount = events.count - candidateEvents.count
            
            guard eventChipsFit(
                visibleEvents: candidateEvents,
                hiddenEventCount: hiddenEventCount,
                maxWidth: maxWidth
            ) else {
                break
            }
            
            visibleEvents = candidateEvents
        }
        
        return EventChipLayout(
            visibleEvents: visibleEvents,
            hiddenEventCount: events.count - visibleEvents.count
        )
    }
    
    private func eventChipsFit(
        visibleEvents: [Event],
        hiddenEventCount: Int,
        maxWidth: CGFloat
    ) -> Bool {
        let eventWidths = visibleEvents.map { chipWidth(text: $0.title, maxWidth: maxWidth) }
        let hiddenWidth = hiddenEventCount > 0
            ? [chipWidth(text: "+\(hiddenEventCount) more", maxWidth: maxWidth)]
            : []
        
        return rowCount(for: eventWidths + hiddenWidth, maxWidth: maxWidth) <= maxVisibleEventRowCount
    }
    
    private func chipWidth(text: String, maxWidth: CGFloat) -> CGFloat {
        let textWidth = (text as NSString).size(withAttributes: [.font: eventChipFont]).width
        
        return min(textWidth + eventChipHorizontalPadding, maxWidth)
    }
    
    private func rowCount(for widths: [CGFloat], maxWidth: CGFloat) -> Int {
        widths.reduce(into: EventChipRows()) { rows, width in
            rows.append(width: width, maxWidth: maxWidth, spacing: eventChipSpacing)
        }
        .count
    }
}

private struct EventChipLayout {
    let visibleEvents: [Event]
    let hiddenEventCount: Int
}

private struct EventChipRows {
    private(set) var count = 0
    private var currentRowWidth: CGFloat = 0
    
    mutating func append(width: CGFloat, maxWidth: CGFloat, spacing: CGFloat) {
        guard count > 0 else {
            count = 1
            currentRowWidth = width
            return
        }
        
        let nextRowWidth = currentRowWidth + spacing + width
        guard nextRowWidth <= maxWidth else {
            count += 1
            currentRowWidth = width
            return
        }
        
        currentRowWidth = nextRowWidth
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
