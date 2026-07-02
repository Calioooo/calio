//
//  CalendarMonthScheduleView.swift
//  Calio
//
//  Created by Codex on 7/2/26.
//

import SwiftUI
import UIKit

struct CalendarMonthScheduleView: View {
    private let calendar = Calendar.current
    private let columnCount = 7
    private let rowCount = 6
    private let weekdayHeaderHeight: CGFloat = 28
    private let monthSwipeMinimumDistance: CGFloat = 10
    private let monthSwipeThreshold: CGFloat = 22
    private let rangeSelectionActivationInterval: TimeInterval = 0.3
    private let rangeSelectionActivationDelay: UInt64 = 300_000_000
    private let dayTapMaximumDistance: CGFloat = 10
    private let monthGridCoordinateSpace = "calendarMonthScheduleGrid"

    let items: [CalendarDateCellItem]
    let referenceDay: DayKey
    let onSelectedDay: (DayKey) -> Void
    let onMonthChanged: (Int) -> Void
    let onSelectedYearMonth: (Int, Int) -> Void
    let showsTodayButton: Bool
    let onTodayTapped: () -> Void
    let onCreateTapped: () -> Void
    let onCreateInRangeTapped: (CalendarDateRange) -> Void
    let onCreateInDayTapped: (DayKey) -> Void

    @State private var activeDateRange: CalendarDateRange?
    @State private var confirmedDateRange: CalendarDateRange?
    @State private var detailPanelDay: DayKey?
    @State private var rangeDragStartTime: Date?
    @State private var rangeDragStartLocation: CGPoint?
    @State private var rangeDragCurrentLocation: CGPoint?
    @State private var isRangeDragActive = false
    @State private var rangeSelectionTask: Task<Void, Never>?

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                VStack(spacing: 0) {
                    topBar
                    weekdayHeader
                    monthGrid
                }
                .background(Color(uiColor: .systemBackground))
                .gesture(monthSwipeGesture)
                .onDisappear {
                    cancelRangeSelection()
                }

                dayDetailPanel(in: geometry)
            }
        }
        .accessibilityIdentifier("calendar_month_schedule")
    }

    private var topBar: some View {
        CalendarTopBarView(
            referenceDay: referenceDay,
            showsTodayButton: showsTodayButton,
            onSelectedYearMonth: onSelectedYearMonth,
            onTodayTapped: onTodayTapped,
            onCreateTapped: onCreateTapped
        )
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

            ZStack(alignment: .topLeading) {
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
                            selectedDateRange: visibleDateRange,
                            maxVisibleEventRowCount: maxEventRowCount
                        )
                        .frame(width: cellWidth, height: cellHeight)
                    }
                }
                .id(monthIdentifier)
                .coordinateSpace(name: monthGridCoordinateSpace)
                .contentShape(Rectangle())
                .simultaneousGesture(
                    rangeSelectionGesture(cellWidth: cellWidth, cellHeight: cellHeight)
                )

                rangeActionPopover(
                    gridSize: geometry.size,
                    cellWidth: cellWidth,
                    cellHeight: cellHeight
                )
            }
        }
        .padding(.horizontal, 8)
        .padding(.bottom, 8)
    }

    private var monthSwipeGesture: some Gesture {
        DragGesture(minimumDistance: monthSwipeMinimumDistance)
            .onEnded { value in
                guard confirmedDateRange == nil && !isRangeDragActive else {
                    return
                }

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

    private var visibleDateRange: CalendarDateRange? {
        activeDateRange ?? confirmedDateRange
    }

    private func selectDay(_ day: DayKey) {
        clearDateRangeSelection()
        onSelectedDay(day)
        detailPanelDay = day
    }

    private func visibleEventRowCount(for cellHeight: CGFloat) -> Int {
        let verticalPadding: CGFloat = 9
        let dayHeaderHeight: CGFloat = 22
        let dayHeaderBottomSpacing: CGFloat = 3
        let eventRowHeight: CGFloat = 16
        let availableHeight = cellHeight - verticalPadding - dayHeaderHeight - dayHeaderBottomSpacing

        return max(1, Int(availableHeight / eventRowHeight))
    }

    private func rangeSelectionGesture(
        cellWidth: CGFloat,
        cellHeight: CGFloat
    ) -> some Gesture {
        DragGesture(
            minimumDistance: 0,
            coordinateSpace: .named(monthGridCoordinateSpace)
        )
            .onChanged { value in
                updateDateRangeSelection(
                    value,
                    cellWidth: cellWidth,
                    cellHeight: cellHeight
                )
            }
            .onEnded { value in
                finishDateRangeSelection(
                    value,
                    cellWidth: cellWidth,
                    cellHeight: cellHeight
                )
            }
    }

    private func updateDateRangeSelection(
        _ value: DragGesture.Value,
        cellWidth: CGFloat,
        cellHeight: CGFloat
    ) {
        if rangeDragStartLocation == nil {
            rangeDragStartTime = Date()
            rangeDragStartLocation = value.startLocation
            rangeDragCurrentLocation = value.location
            scheduleRangeSelectionActivation(
                cellWidth: cellWidth,
                cellHeight: cellHeight
            )
            return
        }

        rangeDragCurrentLocation = value.location

        guard isRangeDragActive,
              let startLocation = rangeDragStartLocation,
              let dateRange = dateRange(
                  from: startLocation,
                  to: value.location,
                  cellWidth: cellWidth,
                  cellHeight: cellHeight
              )
        else {
            return
        }

        activeDateRange = dateRange
        confirmedDateRange = nil
    }

    private func finishDateRangeSelection(
        _ value: DragGesture.Value,
        cellWidth: CGFloat,
        cellHeight: CGFloat
    ) {
        defer {
            resetRangeDragState()
        }

        guard didReachRangeSelectionDelay() else {
            selectDayIfTap(
                value,
                cellWidth: cellWidth,
                cellHeight: cellHeight
            )
            activeDateRange = nil
            return
        }

        guard let startLocation = rangeDragStartLocation,
              let dateRange = dateRange(
                  from: startLocation,
                  to: value.location,
                  cellWidth: cellWidth,
                  cellHeight: cellHeight
              )
        else {
            activeDateRange = nil
            return
        }

        activeDateRange = nil
        confirmedDateRange = dateRange
    }

    private func selectDayIfTap(
        _ value: DragGesture.Value,
        cellWidth: CGFloat,
        cellHeight: CGFloat
    ) {
        guard abs(value.translation.width) <= dayTapMaximumDistance,
              abs(value.translation.height) <= dayTapMaximumDistance,
              let day = day(
                  at: value.location,
                  cellWidth: cellWidth,
                  cellHeight: cellHeight
              )
        else {
            return
        }

        selectDay(day)
    }

    private func didReachRangeSelectionDelay() -> Bool {
        if isRangeDragActive {
            return true
        }

        guard let rangeDragStartTime else {
            return false
        }

        return Date().timeIntervalSince(rangeDragStartTime) >= rangeSelectionActivationInterval
    }

    private func scheduleRangeSelectionActivation(
        cellWidth: CGFloat,
        cellHeight: CGFloat
    ) {
        rangeSelectionTask?.cancel()
        rangeSelectionTask = Task {
            try? await Task.sleep(nanoseconds: rangeSelectionActivationDelay)
            guard !Task.isCancelled else { return }

            await MainActor.run {
                activateRangeSelection(
                    cellWidth: cellWidth,
                    cellHeight: cellHeight
                )
            }
        }
    }

    private func activateRangeSelection(
        cellWidth: CGFloat,
        cellHeight: CGFloat
    ) {
        guard let startLocation = rangeDragStartLocation,
              let currentLocation = rangeDragCurrentLocation,
              let dateRange = dateRange(
                  from: startLocation,
                  to: currentLocation,
                  cellWidth: cellWidth,
                  cellHeight: cellHeight
              )
        else {
            return
        }

        detailPanelDay = nil
        isRangeDragActive = true
        activeDateRange = dateRange
        confirmedDateRange = nil
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    private func resetRangeDragState() {
        rangeSelectionTask?.cancel()
        rangeSelectionTask = nil
        rangeDragStartTime = nil
        rangeDragStartLocation = nil
        rangeDragCurrentLocation = nil
        isRangeDragActive = false
    }

    private func clearDateRangeSelection() {
        cancelRangeSelection()
        activeDateRange = nil
        confirmedDateRange = nil
    }

    private func cancelRangeSelection() {
        resetRangeDragState()
    }

    @ViewBuilder
    private func dayDetailPanel(in geometry: GeometryProxy) -> some View {
        if let detailPanelDay {
            let itemsByDay = Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) })

            ZStack {
                Color.black.opacity(0.32)
                    .ignoresSafeArea()
                    .onTapGesture {
                        self.detailPanelDay = nil
                    }

                CalendarDayDetailFloatingPanelView(
                    day: detailPanelDay,
                    item: itemsByDay[detailPanelDay],
                    onCreateTapped: {
                        self.detailPanelDay = nil
                        onCreateInDayTapped(detailPanelDay)
                    },
                    onCloseTapped: {
                        self.detailPanelDay = nil
                    }
                )
                .frame(
                    width: geometry.size.width * 0.9,
                    height: geometry.size.height * 0.8
                )
                .transition(.scale(scale: 0.96).combined(with: .opacity))
            }
            .animation(.spring(response: 0.26, dampingFraction: 0.84), value: detailPanelDay)
            .zIndex(2)
        }
    }

    private func dateRange(
        from startLocation: CGPoint,
        to currentLocation: CGPoint,
        cellWidth: CGFloat,
        cellHeight: CGFloat
    ) -> CalendarDateRange? {
        guard let startDay = day(at: startLocation, cellWidth: cellWidth, cellHeight: cellHeight),
              let endDay = day(at: currentLocation, cellWidth: cellWidth, cellHeight: cellHeight)
        else {
            return nil
        }

        return CalendarDateRange(startDay: startDay, endDay: endDay)
    }

    private func day(
        at location: CGPoint,
        cellWidth: CGFloat,
        cellHeight: CGFloat
    ) -> DayKey? {
        guard cellWidth > 0 && cellHeight > 0 else {
            return nil
        }

        guard location.x >= 0 && location.y >= 0 else {
            return nil
        }

        let column = Int(location.x / cellWidth)
        let row = Int(location.y / cellHeight)
        let index = row * columnCount + column

        guard (0..<monthGridDays.count).contains(index) else {
            return nil
        }

        return monthGridDays[index]
    }

    @ViewBuilder
    private func rangeActionPopover(
        gridSize: CGSize,
        cellWidth: CGFloat,
        cellHeight: CGFloat
    ) -> some View {
        if let confirmedDateRange {
            CalendarMonthRangeActionPopover(
                dateRange: confirmedDateRange,
                arrowOffsetX: popoverArrowOffsetX(
                    for: confirmedDateRange,
                    gridSize: gridSize,
                    cellWidth: cellWidth
                ),
                onCreateTapped: {
                    onCreateInRangeTapped(confirmedDateRange)
                    self.confirmedDateRange = nil
                },
                onCancelTapped: {
                    self.confirmedDateRange = nil
                }
            )
            .position(
                popoverPosition(
                    for: confirmedDateRange,
                    gridSize: gridSize,
                    cellWidth: cellWidth,
                    cellHeight: cellHeight
                )
            )
            .transition(.scale(scale: 0.94, anchor: .bottom).combined(with: .opacity))
            .animation(.spring(response: 0.24, dampingFraction: 0.82), value: confirmedDateRange)
            .zIndex(1)
        }
    }

    private func popoverPosition(
        for dateRange: CalendarDateRange,
        gridSize: CGSize,
        cellWidth: CGFloat,
        cellHeight: CGFloat
    ) -> CGPoint {
        let anchorDay = dateRange.endDay
        let index = monthGridDays.firstIndex(of: anchorDay) ?? 0
        let column = index % columnCount
        let row = index / columnCount
        let rawX = CGFloat(column) * cellWidth + cellWidth / 2
        let rawY: CGFloat

        if row == 0 {
            rawY = cellHeight + 48
        } else {
            rawY = CGFloat(row) * cellHeight - 10
        }

        return CGPoint(
            x: min(max(rawX, 96), max(gridSize.width - 96, 96)),
            y: min(max(rawY, 48), max(gridSize.height - 48, 48))
        )
    }

    private func popoverArrowOffsetX(
        for dateRange: CalendarDateRange,
        gridSize: CGSize,
        cellWidth: CGFloat
    ) -> CGFloat {
        let anchorDay = dateRange.endDay
        let index = monthGridDays.firstIndex(of: anchorDay) ?? 0
        let column = index % columnCount
        let anchorX = CGFloat(column) * cellWidth + cellWidth / 2
        let popoverCenterX = min(max(anchorX, 96), max(gridSize.width - 96, 96))

        return anchorX - popoverCenterX
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
    let selectedDateRange: CalendarDateRange?
    let maxVisibleEventRowCount: Int

    private var events: [Event] {
        item?.events ?? []
    }

    private var isReferenceDay: Bool {
        day == referenceDay
    }

    private var isCurrentMonth: Bool {
        day.year == referenceDay.year && day.month == referenceDay.month
    }

    private var isSelectedInRange: Bool {
        selectedDateRange?.contains(day) == true
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
            if isSelectedInRange {
                Color(red: 0.56, green: 0.60, blue: 0.96).opacity(0.22)
            } else if isReferenceDay {
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

private struct CalendarMonthRangeActionPopover: View {
    let dateRange: CalendarDateRange
    let arrowOffsetX: CGFloat
    let onCreateTapped: () -> Void
    let onCancelTapped: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            popoverContent

            Triangle()
                .fill(.regularMaterial)
                .frame(width: 18, height: 10)
                .offset(x: arrowOffsetX)
        }
        .shadow(color: .black.opacity(0.16), radius: 12, x: 0, y: 6)
    }

    private var popoverContent: some View {
        VStack(spacing: 8) {
            Text(rangeText)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.primary)

            HStack(spacing: 0) {
                Button(action: onCreateTapped) {
                    Text("일정 생성")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(.blue)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                Divider()
                    .frame(height: 28)

                Button(action: onCancelTapped) {
                    Text("취소")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(.red)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            .frame(maxWidth: .infinity)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
        .frame(width: 220)
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var rangeText: String {
        guard dateRange.startDay != dateRange.endDay else {
            return "\(dateRange.startDay.month)/\(dateRange.startDay.day)"
        }

        return "\(dateRange.startDay.month)/\(dateRange.startDay.day) - \(dateRange.endDay.month)/\(dateRange.endDay.day)"
    }
}

private struct CalendarDayDetailFloatingPanelView: View {
    let day: DayKey
    let item: CalendarDateCellItem?
    let onCreateTapped: () -> Void
    let onCloseTapped: () -> Void

    private var events: [Event] {
        item?.events ?? []
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            panelHeader

            Divider()

            if events.isEmpty {
                emptyState
            } else {
                eventList
            }
        }
        .background(Color(uiColor: .systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .shadow(color: .black.opacity(0.22), radius: 24, x: 0, y: 14)
    }

    private var panelHeader: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 5) {
                Text(dayTitle)
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundStyle(.primary)

                Text(eventCountText)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Button(action: onCreateTapped) {
                Image(systemName: "plus")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Color.accentColor)
                    .frame(width: 34, height: 34)
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("일정 추가")

            Button(action: onCloseTapped) {
                Image(systemName: "xmark")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.secondary)
                    .frame(width: 34, height: 34)
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("닫기")
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .padding(.bottom, 16)
    }

    private var eventList: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(events) { event in
                    CalendarDayDetailEventRow(event: event)

                    if event.id != events.last?.id {
                        Divider()
                            .padding(.leading, 44)
                    }
                }
            }
            .padding(.vertical, 4)
        }
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            Spacer()

            Text("일정이 없습니다")
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(.secondary)

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var dayTitle: String {
        guard let weekday = item?.weekday else {
            return "\(day.month)월 \(day.day)일"
        }

        return "\(day.month)월 \(day.day)일 \(weekday.fullKoreanText)"
    }

    private var eventCountText: String {
        "\(events.count)개의 일정"
    }
}

private struct CalendarDayDetailEventRow: View {
    let event: Event

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            RoundedRectangle(cornerRadius: 3)
                .fill(Color(hex: event.colorCode))
                .frame(width: 5, height: 42)

            VStack(alignment: .leading, spacing: 5) {
                Text(eventTimeText)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.secondary)

                Text(event.title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(2)

                if hasDescription {
                    Text(event.description)
                        .font(.system(size: 13, weight: .regular))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }

    private var hasDescription: Bool {
        !event.description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var eventTimeText: String {
        CalendarEventDisplayText.timeRange(
            startAt: event.startAt,
            endAt: event.endAt
        )
    }
}

private struct Triangle: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.closeSubpath()
        return path
    }
}

#Preview {
    CalendarMonthScheduleView(
        items: [],
        referenceDay: DayKey(date: Date()),
        onSelectedDay: { _ in },
        onMonthChanged: { _ in },
        onSelectedYearMonth: { _, _ in },
        showsTodayButton: true,
        onTodayTapped: {},
        onCreateTapped: {},
        onCreateInRangeTapped: { _ in },
        onCreateInDayTapped: { _ in }
    )
}
