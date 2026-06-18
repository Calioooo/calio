//
//  CalendarMonthView.swift
//  Calio
//
//  Created by Codex on 6/8/26.
//

import SwiftUI

struct CalendarMonthView: View {
    private let calendar = Calendar.current
    private let columnCount = 7
    private let rowCount = 6
    private let maxVisibleEventDotCount = 3
    
    let items: [CalendarDateCellItem]
    let focusedDay: DayKey
    let onSelectedDay: (DayKey) -> Void
    let onMonthChanged: (Int) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                ForEach(CalendarWeekday.allCases) { weekday in
                    Text(weekday.shortKoreanText)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity)
                }
            }

            monthGrid
        }
        .padding(.horizontal, 20)
        .padding(.top, 4)
        .padding(.bottom, 18)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .accessibilityIdentifier("calendar_header_month")
        .gesture(
              DragGesture(minimumDistance: 20)
                  .onEnded { value in
                      let horizontal = value.translation.width
                      let vertical = value.translation.height

                      guard abs(horizontal) > abs(vertical) else { return }

                      if horizontal < -40 {
                          onMonthChanged(1)
                      } else if horizontal > 40 {
                          onMonthChanged(-1)
                      }
                  }
          )
    }

    private var monthGrid: some View {
        GeometryReader { geometry in
            let cellWidth = geometry.size.width / CGFloat(columnCount)
            let cellHeight = geometry.size.height / CGFloat(rowCount)
            
            LazyVGrid(
                columns: Array(
                    repeating: GridItem(.flexible(), spacing: 0),
                    count: columnCount
                ),
                spacing: 0
            ) {
                ForEach(monthGridDays, id: \.self) { day in
                    monthCell(for: day)
                        .frame(width: cellWidth, height: cellHeight)
                }
            }
            .id(monthIdentifier)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    private func monthCell(for day: DayKey) -> some View {
        let item = itemsByDay[day]
        let isFocused = day == focusedDay
        let isCurrentMonth = day.month == focusedDay.month && day.year == focusedDay.year
        let events = item?.events ?? []
        
        return Button {
            onSelectedDay(day)
        } label: {
            VStack(spacing: 0) {
                Text("\(day.day)")
                    .font(.system(size: 17, weight: isFocused ? .semibold : .regular))
                    .foregroundStyle(dayTextColor(for: item, isCurrentMonth: isCurrentMonth))
                    .frame(width: 28, height: 28)
                    .background {
                        if item?.isToday == true {
                            Circle()
                                .fill(Color(red: 0.56, green: 0.76, blue: 0.96))
                        }
                    }
                
                HStack(spacing: 2) {
                    ForEach(Array(events.prefix(maxVisibleEventDotCount).enumerated()), id: \.offset) { _, event in
                        Circle()
                            .fill(Color(hex: event.colorCode))
                            .frame(width: 7, height: 7)
                    }
                    
                    if events.count > maxVisibleEventDotCount {
                        Text("+\(events.count - maxVisibleEventDotCount)")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .background {
            if isFocused {
                Rectangle()
                    .fill(Color(red: 0.56, green: 0.76, blue: 0.96).opacity(0.18))
            }
        }
    }
    
    private var itemsByDay: [DayKey: CalendarDateCellItem] {
        Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) })
    }
    
    private var monthGridDays: [DayKey] {
        let focusedDate = focusedDay.toDate(calendar: calendar)
        let monthComponents = calendar.dateComponents([.year, .month], from: focusedDate)
        
        guard let firstDayOfMonth = calendar.date(from: monthComponents) else {
            return []
        }
        
        let firstWeekdayIndex = calendar.component(.weekday, from: firstDayOfMonth) - 1
        
        guard let gridStartDate = calendar.date(
            byAdding: .day,
            value: firstWeekdayIndex * -1,
            to: firstDayOfMonth
        ) else {
            return []
        }
        
        return (0..<(columnCount * rowCount)).compactMap { offset in
            guard let date = calendar.date(
                byAdding: .day,
                value: offset,
                to: gridStartDate
            ) else {
                return nil
            }
            
            return DayKey(date: date, calendar: calendar)
        }
    }
    
    private var monthIdentifier: String {
        "\(focusedDay.year)-\(focusedDay.month)"
    }
    
    private func dayTextColor(
        for item: CalendarDateCellItem?,
        isCurrentMonth: Bool
    ) -> Color {
        if item?.isToday == true {
            return .white
        }
        
        guard isCurrentMonth else {
            return .secondary.opacity(0.55)
        }
        
        switch item?.weekday {
        case .sunday:
            return .red
        case .saturday:
            return .blue
        default:
            return .primary
        }
    }
}

#Preview {
    CalendarMonthView(
        items: [],
        focusedDay: DayKey(date: Date()),
        onSelectedDay: { _ in },
        onMonthChanged: { _ in }
    )
}
