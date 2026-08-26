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
    private let monthCellHorizontalPadding: CGFloat = 4
    private let monthCellTopPadding: CGFloat = 5
    private let monthCellDayHeaderHeight: CGFloat = 22
    private let monthCellDayHeaderBottomSpacing: CGFloat = 3
    private let monthEventRowHeight: CGFloat = 16
    private let monthEventChipHeight: CGFloat = 14
    @Environment(\.sizeCategory) private var sizeCategory

    let items: [CalendarDayItem]
    let referenceDay: DayKey
    let onSelectedDay: (DayKey) -> Void
    let onMonthChanged: (Int) -> Void
    let onSelectedYearMonth: (Int, Int) -> Void
    let showsTodayButton: Bool
    let onTodayTapped: () -> Void
    let onGoogleCalendarConnectTapped: () -> Void
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
                .background(Color.calioBackground)
                .gesture(monthSwipeGesture)
                .allowsHitTesting(detailPanelDay == nil)
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
            onGoogleCalendarConnectTapped: onGoogleCalendarConnectTapped,
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
        .background(Color.calioBackground)
    }

    private var monthGrid: some View {
        GeometryReader { geometry in
            let metrics = monthGridMetrics(for: geometry.size)
            let itemsByDay = Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) })
            let eventLayout = MonthEventLayoutBuilder.make(
                items: items,
                days: monthGridDays,
                maxVisibleRowCount: metrics.maxEventRowCount,
                calendar: calendar
            )

            ZStack(alignment: .topLeading) {
                monthDayGrid(
                    metrics: metrics,
                    itemsByDay: itemsByDay
                )

                monthEventSpans(
                    eventLayout.spans,
                    metrics: metrics
                )
                .allowsHitTesting(false)

                monthEventOverflowLabels(
                    eventLayout.hiddenCountByDay,
                    metrics: metrics
                )
                .allowsHitTesting(false)

                rangeActionDismissLayer(gridSize: geometry.size)

                rangeActionPopover(
                    gridSize: geometry.size,
                    cellWidth: metrics.cellWidth,
                    cellHeight: metrics.cellHeight
                )
            }
        }
        .padding(.horizontal, 8)
        .padding(.bottom, 8)
    }
    
    private func monthGridMetrics(for size: CGSize) -> MonthGridMetrics {
        let cellWidth = size.width / CGFloat(columnCount)
        let cellHeight = size.height / CGFloat(rowCount)
        let textScale = sizeCategory.isAccessibilityCategory ? 1.12 : 1
        let maxEventRowCount = visibleEventRowCount(for: cellHeight, textScale: textScale)
        
        return MonthGridMetrics(
            cellWidth: cellWidth,
            cellHeight: cellHeight,
            maxEventRowCount: maxEventRowCount,
            textScale: textScale
        )
    }
    
    private func monthDayGrid(
        metrics: MonthGridMetrics,
        itemsByDay: [DayKey: CalendarDayItem]
    ) -> some View {
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
                    selectedDateRange: visibleDateRange
                )
                .frame(width: metrics.cellWidth, height: metrics.cellHeight)
            }
        }
        .id(monthIdentifier)
        .coordinateSpace(name: monthGridCoordinateSpace)
        .contentShape(Rectangle())
        .simultaneousGesture(
            rangeSelectionGesture(
                cellWidth: metrics.cellWidth,
                cellHeight: metrics.cellHeight
            )
        )
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

    private func visibleEventRowCount(for cellHeight: CGFloat, textScale: CGFloat) -> Int {
        let verticalPadding = monthCellTopPadding + 4
        let availableHeight = cellHeight
            - verticalPadding
            - monthCellDayHeaderHeight
            - monthCellDayHeaderBottomSpacing

        return max(1, Int(availableHeight / (monthEventRowHeight * textScale)))
    }

    private func monthEventSpans(
        _ spans: [MonthEventSpanItem],
        metrics: MonthGridMetrics
    ) -> some View {
        ForEach(spans) { span in
            MonthEventSpanView(
                span: span,
                metrics: metrics,
                horizontalPadding: monthCellHorizontalPadding,
                topOffset: eventAreaTopOffset,
                rowHeight: monthEventRowHeight * metrics.textScale,
                chipHeight: monthEventChipHeight * metrics.textScale
            )
        }
    }

    private func monthEventOverflowLabels(
        _ hiddenCountByDay: [DayKey: Int],
        metrics: MonthGridMetrics
    ) -> some View {
        ForEach(monthEventOverflowLabelItems(hiddenCountByDay)) { label in
            MonthEventOverflowLabelView(
                label: label,
                metrics: metrics,
                columnCount: columnCount,
                horizontalPadding: monthCellHorizontalPadding,
                topOffset: eventAreaTopOffset,
                rowHeight: monthEventRowHeight * metrics.textScale,
                chipHeight: monthEventChipHeight * metrics.textScale
            )
        }
    }
    
    private func monthEventOverflowLabelItems(
        _ hiddenCountByDay: [DayKey: Int]
    ) -> [MonthEventOverflowLabelItem] {
        monthGridDays.enumerated().compactMap { index, day in
            guard let hiddenCount = hiddenCountByDay[day], hiddenCount > 0 else {
                return nil
            }
            
            return MonthEventOverflowLabelItem(
                day: day,
                dayIndex: index,
                hiddenCount: hiddenCount
            )
        }
    }

    private var eventAreaTopOffset: CGFloat {
        monthCellTopPadding + monthCellDayHeaderHeight + monthCellDayHeaderBottomSpacing
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
            if handleMonthSwipeIfNeeded(value) {
                activeDateRange = nil
                return
            }
            
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
    
    private func handleMonthSwipeIfNeeded(_ value: DragGesture.Value) -> Bool {
        guard confirmedDateRange == nil && !isRangeDragActive else {
            return false
        }
        
        let horizontal = value.translation.width
        let vertical = value.translation.height
        
        guard abs(horizontal) > abs(vertical) else {
            return false
        }
        
        if horizontal < -monthSwipeThreshold {
            onMonthChanged(1)
            return true
        }
        
        if horizontal > monthSwipeThreshold {
            onMonthChanged(-1)
            return true
        }
        
        return false
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
                    .contentShape(Rectangle())
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
            .frame(
                width: geometry.size.width,
                height: geometry.size.height
            )
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
    private func rangeActionDismissLayer(gridSize: CGSize) -> some View {
        if confirmedDateRange != nil {
            Color.black.opacity(0.001)
                .frame(width: gridSize.width, height: gridSize.height)
                .contentShape(Rectangle())
                .onTapGesture {
                    confirmedDateRange = nil
                }
                .accessibilityHidden(true)
                .zIndex(0.5)
        }
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
            return .calioCalendarSunday
        case .saturday:
            return .calioBrand
        default:
            return .calioTextSecondary
        }
    }
}

private struct CalendarMonthScheduleDayCellView: View {
    let day: DayKey
    let item: CalendarDayItem?
    let referenceDay: DayKey
    let selectedDateRange: CalendarDateRange?

    private var isReferenceDay: Bool {
        day == referenceDay
    }

    private var isCurrentMonth: Bool {
        day.year == referenceDay.year && day.month == referenceDay.month
    }

    private var isSelectedInRange: Bool {
        selectedDateRange?.contains(day) == true
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            dayHeader
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 4)
        .padding(.top, 5)
        .padding(.bottom, 4)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .contentShape(Rectangle())
        .background(cellBackground)
        .overlay(cellBorder)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            MonthCalendarDayAccessibility.label(
                day: day,
                weekday: item?.weekday,
                isToday: item?.isToday == true,
                isSelected: isReferenceDay,
                isInSelectedRange: isSelectedInRange,
                isCurrentMonth: isCurrentMonth,
                hasHoliday: item?.hasHoliday == true,
                eventCount: item?.events.count ?? 0
            )
        )
        .accessibilityIdentifier("month_day_\(day.idValue)")
    }

    private var dayHeader: some View {
        Text("\(day.day)")
            .font(.system(size: 14, weight: isReferenceDay ? .semibold : .regular))
            .foregroundStyle(dayTextColor)
            .frame(width: 24, height: 22, alignment: .center)
            .background {
                if item?.isToday == true {
                    Circle()
                        .fill(Color.calioBrand)
                }
            }
    }

    private var cellBackground: some View {
        ZStack {
            if isSelectedInRange {
                Color.calioSelection
            } else if isReferenceDay {
                Color.calioSelection.opacity(0.72)
            } else if !isCurrentMonth {
                Color.calioDivider.opacity(0.34)
            } else {
                Color.calioSurface
            }
        }
    }

    private var cellBorder: some View {
        Rectangle()
            .strokeBorder(
                isReferenceDay || isSelectedInRange ? Color.calioBrand.opacity(0.58) : Color.calioDivider,
                lineWidth: isReferenceDay || isSelectedInRange ? 1 : 0.6
            )
    }

    private var dayTextColor: Color {
        if item?.isToday == true {
            return .white
        }

        guard isCurrentMonth else {
            return .secondary.opacity(0.6)
        }

        if item?.hasHoliday == true {
            return Color.calendarHoliday
        }
        
        switch item?.weekday {
        case .sunday:
            return .calioCalendarSunday
        case .saturday:
            return .calioBrand
        default:
            return .calioTextPrimary
        }
    }
}

private struct MonthGridMetrics {
    let cellWidth: CGFloat
    let cellHeight: CGFloat
    let maxEventRowCount: Int
    let textScale: CGFloat
    
    var overflowRowIndex: Int {
        max(maxEventRowCount - 1, 0)
    }
}

private struct MonthEventSpanView: View {
    let span: MonthEventSpanItem
    let metrics: MonthGridMetrics
    let horizontalPadding: CGFloat
    let topOffset: CGFloat
    let rowHeight: CGFloat
    let chipHeight: CGFloat
    
    var body: some View {
        Text(span.chip.title)
            .font(.system(size: 10 * metrics.textScale, weight: .medium))
            .lineLimit(1)
            .truncationMode(.tail)
            .foregroundStyle(Color.calioTextPrimary)
            .padding(.horizontal, 4)
            .frame(
                width: chipWidth,
                height: chipHeight,
                alignment: .leading
            )
            .background(span.chip.color)
            .clipShape(RoundedRectangle(cornerRadius: 3))
            .offset(x: xOffset, y: yOffset)
            .accessibilityLabel("\(span.chip.title), \(span.chip.accessibilityKind)")
            .accessibilityIdentifier("month_event_\(span.id)")
    }
    
    private var chipWidth: CGFloat {
        max(CGFloat(span.columnSpan) * metrics.cellWidth - (horizontalPadding * 2), 1)
    }
    
    private var xOffset: CGFloat {
        CGFloat(span.startColumn) * metrics.cellWidth + horizontalPadding
    }
    
    private var yOffset: CGFloat {
        CGFloat(span.weekRowIndex) * metrics.cellHeight
            + topOffset
            + CGFloat(span.eventRowIndex) * rowHeight
    }
}

private struct MonthEventOverflowLabelItem: Identifiable {
    let day: DayKey
    let dayIndex: Int
    let hiddenCount: Int

    var id: DayKey {
        day
    }
}

private struct MonthEventOverflowLabelView: View {
    let label: MonthEventOverflowLabelItem
    let metrics: MonthGridMetrics
    let columnCount: Int
    let horizontalPadding: CGFloat
    let topOffset: CGFloat
    let rowHeight: CGFloat
    let chipHeight: CGFloat
    
    var body: some View {
        Text("+\(label.hiddenCount)")
            .font(.system(size: 10 * metrics.textScale, weight: .semibold))
            .foregroundStyle(.calioTextSecondary)
            .frame(
                width: labelWidth,
                height: chipHeight,
                alignment: .leading
            )
            .offset(x: xOffset, y: yOffset)
            .accessibilityLabel("숨겨진 일정 \(label.hiddenCount)개")
            .accessibilityIdentifier("month_overflow_\(label.day.idValue)")
    }
    
    private var labelWidth: CGFloat {
        max(metrics.cellWidth - (horizontalPadding * 2), 1)
    }
    
    private var xOffset: CGFloat {
        CGFloat(label.dayIndex % columnCount) * metrics.cellWidth + horizontalPadding
    }
    
    private var yOffset: CGFloat {
        CGFloat(label.dayIndex / columnCount) * metrics.cellHeight
            + topOffset
            + CGFloat(metrics.overflowRowIndex) * rowHeight
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
                .fill(Color.calioSurface)
                .frame(width: 18, height: 10)
                .offset(x: arrowOffsetX)
        }
        .shadow(color: .black.opacity(0.16), radius: 12, x: 0, y: 6)
    }

    private var popoverContent: some View {
        VStack(spacing: 8) {
            Text(rangeText)
                .font(.headline)
                .foregroundStyle(.calioTextPrimary)

            HStack(spacing: 0) {
                Button(action: onCreateTapped) {
                    Text("일정 생성")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.calioBrand)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                Divider()
                    .frame(height: 28)

                Button(action: onCancelTapped) {
                    Text("취소")
                        .font(.body.weight(.medium))
                        .foregroundStyle(.calioCalendarSunday)
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
        .background(Color.calioSurface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityIdentifier("month_range_action")
    }

    private var rangeText: String {
        guard dateRange.startDay != dateRange.endDay else {
            return dateText(for: dateRange.startDay)
        }

        return "\(dateText(for: dateRange.startDay)) - \(dateText(for: dateRange.endDay))"
    }

    private func dateText(for day: DayKey) -> String {
        "\(day.month)월 \(day.day)일"
    }
}

struct CalendarDayDetailFloatingPanelView: View {
    let day: DayKey
    let item: CalendarDayItem?
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
        .background(Color.calioSurface)
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .shadow(color: .black.opacity(0.22), radius: 24, x: 0, y: 14)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("month_day_detail_panel")
    }

    private var panelHeader: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 5) {
                Text(dayTitle)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.calioTextPrimary)

                Text(eventCountText)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.calioTextSecondary)
            }

            Spacer()

            Button(action: onCreateTapped) {
                Image(systemName: "plus")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Color.calioBrand)
                    .frame(width: 34, height: 34)
                    .contentShape(Circle())
                    .background(Circle().fill(Color.calioSelection))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("일정 추가")
            .accessibilityIdentifier("month_detail_add_event")

            Button(action: onCloseTapped) {
                Image(systemName: "xmark")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.calioTextSecondary)
                    .frame(width: 34, height: 34)
                    .contentShape(Circle())
                    .background(Circle().fill(Color.calioBackground))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("닫기")
            .accessibilityIdentifier("month_detail_close")
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
                            .overlay(Color.calioDivider)
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

            Image(systemName: "calendar")
                .font(.title3)
                .foregroundStyle(.calioTextSecondary)

            Text("일정이 없습니다")
                .font(.body.weight(.medium))
                .foregroundStyle(.calioTextSecondary)

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityLabel("\(dayTitle), 일정 없음. 일정 추가 가능")
        .accessibilityIdentifier("month_detail_empty")
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
                .fill(Color(hex: event.tag.colorCode))
                .frame(width: 5, height: 42)

            VStack(alignment: .leading, spacing: 5) {
                Text(eventTimeText)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.calioTextSecondary)

                Text(event.title)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.calioTextPrimary)
                    .lineLimit(2)

                if hasDescription {
                    Text(event.description)
                        .font(.subheadline)
                        .foregroundStyle(.calioTextSecondary)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(event.title), \(eventTimeText)\(hasDescription ? ", \(event.description)" : "")")
        .accessibilityIdentifier("month_detail_event_\(event.id)")
    }

    private var hasDescription: Bool {
        !event.description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var eventTimeText: String {
        CalendarEventDisplayText.compactDateTimeRange(
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

private extension DayKey {
    var idValue: String {
        "\(year)-\(month)-\(day)"
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
        onGoogleCalendarConnectTapped: {},
        onCreateTapped: {},
        onCreateInRangeTapped: { _ in },
        onCreateInDayTapped: { _ in }
    )
}
