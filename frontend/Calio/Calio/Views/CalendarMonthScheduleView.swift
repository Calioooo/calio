//
//  CalendarMonthScheduleView.swift
//  Calio
//
//  Created by Codex on 7/2/26.
//

import SwiftUI

struct CalendarMonthScheduleView: View {
    private let calendar = Calendar.current
    private let columnCount = 7
    private let rowCount = 6
    private let topBarHeight: CGFloat = 58
    private let weekdayHeaderHeight: CGFloat = 28
    private let monthSwipeMinimumDistance: CGFloat = 10
    private let monthSwipeThreshold: CGFloat = 22

    let items: [CalendarDateCellItem]
    let referenceDay: DayKey
    let onSelectedDay: (DayKey) -> Void
    let onMonthChanged: (Int) -> Void
    let onSelectedYearMonth: (Int, Int) -> Void
    let onCreateTapped: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            topBar
            weekdayHeader
            monthGrid
        }
        .background(Color(uiColor: .systemBackground))
        .gesture(monthSwipeGesture)
        .accessibilityIdentifier("calendar_month_schedule")
    }

    private var topBar: some View {
        HStack(spacing: 12) {
            CalendarYearMonthTitleView(
                referenceDay: referenceDay,
                onSelectedYearMonth: onSelectedYearMonth
            )
            .frame(maxWidth: .infinity, alignment: .leading)

            Button(action: onCreateTapped) {
                Image(systemName: "plus")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.primary)
                    .frame(width: 36, height: 36)
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("일정 추가")
        }
        .padding(.horizontal, 20)
        .frame(height: topBarHeight)
    }

    private var weekdayHeader: some View {
        HStack(spacing: 0) {
            ForEach(CalendarWeekday.allCases) { weekday in
                Text(weekday.shortKoreanText)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(weekdayTextColor(weekday))
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 8)
        .frame(height: weekdayHeaderHeight)
    }

    private var monthGrid: some View {
        GeometryReader { geometry in
            let cellWidth = geometry.size.width / CGFloat(columnCount)
            let cellHeight = geometry.size.height / CGFloat(rowCount)
            let itemsByDay = Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) })
            let maxEventRowCount = visibleEventRowCount(for: cellHeight)

            LazyVGrid(
                columns: Array(
                    repeating: GridItem(.flexible(), spacing: 0),
                    count: columnCount
                ),
                spacing: 0
            ) {
                ForEach(monthGridDays, id: \.self) { day in
                    CalendarMonthScheduleDayCellView(
                        day: day,
                        item: itemsByDay[day],
                        referenceDay: referenceDay,
                        maxVisibleEventRowCount: maxEventRowCount,
                        onSelectedDay: onSelectedDay
                    )
                    .frame(width: cellWidth, height: cellHeight)
                }
            }
            .id(monthIdentifier)
        }
        .padding(.horizontal, 8)
        .padding(.bottom, 8)
    }

    private var monthSwipeGesture: some Gesture {
        DragGesture(minimumDistance: monthSwipeMinimumDistance)
            .onEnded { value in
                let horizontal = value.translation.width
                let vertical = value.translation.height

                guard abs(horizontal) > abs(vertical) else {
                    return
                }

                if horizontal < -monthSwipeThreshold {
                    onMonthChanged(1)
                } else if horizontal > monthSwipeThreshold {
                    onMonthChanged(-1)
                }
            }
    }

    private var monthGridDays: [DayKey] {
        let referenceDate = referenceDay.toDate(calendar: calendar)
        let monthComponents = calendar.dateComponents([.year, .month], from: referenceDate)

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
        "\(referenceDay.year)-\(referenceDay.month)"
    }

    private func visibleEventRowCount(for cellHeight: CGFloat) -> Int {
        let verticalPadding: CGFloat = 9
        let dayHeaderHeight: CGFloat = 22
        let dayHeaderBottomSpacing: CGFloat = 3
        let eventRowHeight: CGFloat = 16
        let availableHeight = cellHeight - verticalPadding - dayHeaderHeight - dayHeaderBottomSpacing

        return max(1, Int(availableHeight / eventRowHeight))
    }

    private func weekdayTextColor(_ weekday: CalendarWeekday) -> Color {
        switch weekday {
        case .sunday:
            return .red
        case .saturday:
            return .blue
        default:
            return .secondary
        }
    }
}

private struct CalendarMonthScheduleDayCellView: View {
    let day: DayKey
    let item: CalendarDateCellItem?
    let referenceDay: DayKey
    let maxVisibleEventRowCount: Int
    let onSelectedDay: (DayKey) -> Void

    private var events: [Event] {
        item?.events ?? []
    }

    private var isReferenceDay: Bool {
        day == referenceDay
    }

    private var isCurrentMonth: Bool {
        day.year == referenceDay.year && day.month == referenceDay.month
    }

    private var visibleTitleCount: Int {
        guard events.count > maxVisibleEventRowCount else {
            return min(events.count, maxVisibleEventRowCount)
        }

        return max(maxVisibleEventRowCount - 1, 0)
    }

    private var hiddenEventCount: Int {
        max(events.count - visibleTitleCount, 0)
    }

    var body: some View {
        Button {
            onSelectedDay(day)
        } label: {
            VStack(alignment: .leading, spacing: 3) {
                dayHeader
                eventTitles
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 4)
            .padding(.top, 5)
            .padding(.bottom, 4)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .background(cellBackground)
        .overlay(cellBorder)
    }

    private var dayHeader: some View {
        Text("\(day.day)")
            .font(.system(size: 14, weight: isReferenceDay ? .semibold : .regular))
            .foregroundStyle(dayTextColor)
            .frame(width: 24, height: 22, alignment: .center)
            .background {
                if item?.isToday == true {
                    Circle()
                        .fill(Color(red: 0.56, green: 0.60, blue: 0.96))
                }
            }
    }

    private var eventTitles: some View {
        VStack(spacing: 2) {
            ForEach(Array(events.prefix(visibleTitleCount).enumerated()), id: \.offset) { _, event in
                Text(event.title)
                    .font(.system(size: 10, weight: .medium))
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .foregroundStyle(.black.opacity(0.85))
                    .padding(.horizontal, 4)
                    .frame(maxWidth: .infinity, minHeight: 14, maxHeight: 14, alignment: .leading)
                    .background(Color(hex: event.colorCode))
                    .clipShape(RoundedRectangle(cornerRadius: 3))
            }

            if hiddenEventCount > 0 {
                Text("+\(hiddenEventCount)")
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, minHeight: 14, maxHeight: 14, alignment: .leading)
            }
        }
    }

    private var cellBackground: some View {
        ZStack {
            if isReferenceDay {
                Color(red: 0.56, green: 0.60, blue: 0.96).opacity(0.16)
            } else if !isCurrentMonth {
                Color(uiColor: .secondarySystemBackground).opacity(0.45)
            } else {
                Color(uiColor: .systemBackground)
            }
        }
    }

    private var cellBorder: some View {
        Rectangle()
            .strokeBorder(Color.secondary.opacity(0.18), lineWidth: 0.6)
    }

    private var dayTextColor: Color {
        if item?.isToday == true {
            return .white
        }

        guard isCurrentMonth else {
            return .secondary.opacity(0.6)
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
    CalendarMonthScheduleView(
        items: [],
        referenceDay: DayKey(date: Date()),
        onSelectedDay: { _ in },
        onMonthChanged: { _ in },
        onSelectedYearMonth: { _, _ in },
        onCreateTapped: {}
    )
}
