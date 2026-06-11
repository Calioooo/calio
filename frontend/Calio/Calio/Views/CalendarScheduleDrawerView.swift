//
//  CalendarScheduleDrawerView.swift
//  Calio
//
//  Created by Codex on 6/8/26.
//

import SwiftUI

struct CalendarScheduleDrawerView: View {
    let items: [CalendarDateCellItem]
    let focusedDay: DayKey
    let displayMode: CalendarDisplayMode
    let onFocusedDayChanged: (DayKey) -> Void
    let onVisibleRangeChanged: (CalendarVisibleIndexRange) -> Void
    let onDragEnded: (CGSize) -> Void

    var body: some View {
        VStack(spacing: 0) {
            dragHandle

            CalendarDateEventView(
                items: items,
                focusedDay: focusedDay,
                onFocusedDayChanged: onFocusedDayChanged,
                onVisibleRangeChanged: onVisibleRangeChanged
            )
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(uiColor: .systemBackground))
        .overlay(alignment: .top) {
            Divider()
        }
        .accessibilityIdentifier("calendar_schedule_drawer")
    }

    private var dragHandle: some View {
        VStack(spacing: 6) {
            Capsule()
                .fill(Color.secondary.opacity(0.35))
                .frame(width: 42, height: 5)

            Image(systemName: handleIconName)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 8)
                .onEnded { value in
                    onDragEnded(value.translation)
                }
        )
        .accessibilityLabel(handleAccessibilityLabel)
    }

    private var handleIconName: String {
        switch displayMode {
        case .week:
            return "chevron.down"
        case .month:
            return "chevron.up"
        }
    }

    private var handleAccessibilityLabel: String {
        switch displayMode {
        case .week:
            return "일정 패널 펼치기"
        case .month:
            return "일정 패널 접기"
        }
    }
}

#Preview {
    CalendarScheduleDrawerView(
        items: [],
        focusedDay: DayKey(date: Date()),
        displayMode: .week,
        onFocusedDayChanged: { _ in },
        onVisibleRangeChanged: { _ in },
        onDragEnded: { _ in }
    )
}
