//
//  CalendarScheduleDrawerView.swift
//  Calio
//
//  Created by Codex on 6/8/26.
//

import SwiftUI

struct CalendarScheduleDrawerView: View {
    let items: [CalendarDateCellItem]
    let tags: [CalendarTag]
    let referenceDay: DayKey
    let displayMode: CalendarDisplayMode
    let eventAreaState: CalendarEventAreaState
    let onReferenceDayChanged: (DayKey) -> Void
    let onVisibleRangeChanged: (CalendarVisibleIndexRange) -> Void
    let onRetryEvents: () -> Void
    let onDragEnded: (CGSize) -> Void
    let isEventMutating: Bool
    let eventMutationFailureMessage: String?
    let onResetEventMutation: () -> Void
    let onFetchRecurrenceEvent: (Int64) async -> RecurrenceEventDetails?
    let onUpdateSingleEvent: (Event, EventUpdateInput) async -> Bool
    let onUpdateRecurrenceOccurrence: (Event, EventUpdateInput) async -> Bool
    let onUpdateRecurrenceSeries: (Int64, RecurrenceEventSeriesEditInput) async -> Bool
    let onDeleteSingleEvent: (Event) async -> Bool
    let onDeleteRecurrenceOccurrence: (Event) async -> Bool
    let onDeleteRecurrenceSeries: (Event) async -> Bool

    init(
        items: [CalendarDateCellItem],
        tags: [CalendarTag] = [],
        referenceDay: DayKey,
        displayMode: CalendarDisplayMode,
        eventAreaState: CalendarEventAreaState,
        onReferenceDayChanged: @escaping (DayKey) -> Void,
        onVisibleRangeChanged: @escaping (CalendarVisibleIndexRange) -> Void,
        onRetryEvents: @escaping () -> Void,
        onDragEnded: @escaping (CGSize) -> Void,
        isEventMutating: Bool = false,
        eventMutationFailureMessage: String? = nil,
        onResetEventMutation: @escaping () -> Void = {},
        onFetchRecurrenceEvent: @escaping (Int64) async -> RecurrenceEventDetails? = { _ in nil },
        onUpdateSingleEvent: @escaping (Event, EventUpdateInput) async -> Bool = { _, _ in true },
        onUpdateRecurrenceOccurrence: @escaping (Event, EventUpdateInput) async -> Bool = { _, _ in true },
        onUpdateRecurrenceSeries: @escaping (Int64, RecurrenceEventSeriesEditInput) async -> Bool = { _, _ in true },
        onDeleteSingleEvent: @escaping (Event) async -> Bool = { _ in true },
        onDeleteRecurrenceOccurrence: @escaping (Event) async -> Bool = { _ in true },
        onDeleteRecurrenceSeries: @escaping (Event) async -> Bool = { _ in true }
    ) {
        self.items = items
        self.tags = tags
        self.referenceDay = referenceDay
        self.displayMode = displayMode
        self.eventAreaState = eventAreaState
        self.onReferenceDayChanged = onReferenceDayChanged
        self.onVisibleRangeChanged = onVisibleRangeChanged
        self.onRetryEvents = onRetryEvents
        self.onDragEnded = onDragEnded
        self.isEventMutating = isEventMutating
        self.eventMutationFailureMessage = eventMutationFailureMessage
        self.onResetEventMutation = onResetEventMutation
        self.onFetchRecurrenceEvent = onFetchRecurrenceEvent
        self.onUpdateSingleEvent = onUpdateSingleEvent
        self.onUpdateRecurrenceOccurrence = onUpdateRecurrenceOccurrence
        self.onUpdateRecurrenceSeries = onUpdateRecurrenceSeries
        self.onDeleteSingleEvent = onDeleteSingleEvent
        self.onDeleteRecurrenceOccurrence = onDeleteRecurrenceOccurrence
        self.onDeleteRecurrenceSeries = onDeleteRecurrenceSeries
    }

    var body: some View {
        VStack(spacing: 0) {
            dragHandle

            CalendarDateEventView(
                items: items,
                tags: tags,
                referenceDay: referenceDay,
                eventAreaState: eventAreaState,
                onReferenceDayChanged: onReferenceDayChanged,
                onVisibleRangeChanged: onVisibleRangeChanged,
                onRetryEvents: onRetryEvents,
                isEventMutating: isEventMutating,
                eventMutationFailureMessage: eventMutationFailureMessage,
                onResetEventMutation: onResetEventMutation,
                onFetchRecurrenceEvent: onFetchRecurrenceEvent,
                onUpdateSingleEvent: onUpdateSingleEvent,
                onUpdateRecurrenceOccurrence: onUpdateRecurrenceOccurrence,
                onUpdateRecurrenceSeries: onUpdateRecurrenceSeries,
                onDeleteSingleEvent: onDeleteSingleEvent,
                onDeleteRecurrenceOccurrence: onDeleteRecurrenceOccurrence,
                onDeleteRecurrenceSeries: onDeleteRecurrenceSeries
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
        referenceDay: DayKey(date: Date()),
        displayMode: .week,
        eventAreaState: .idle,
        onReferenceDayChanged: { _ in },
        onVisibleRangeChanged: { _ in },
        onRetryEvents: {},
        onDragEnded: { _ in }
    )
}
