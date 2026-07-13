//
//  CalendarScheduleDrawerView.swift
//  Calio
//
//  Created by Codex on 6/8/26.
//

import SwiftUI

struct CalendarScheduleDrawerView: View {
    let items: [CalendarDayItem]
    let tags: [CalendarTag]
    let referenceDay: DayKey
    let displayMode: CalendarDisplayMode
    let eventLoadState: CalendarEventLoadState
    let onReferenceDayChanged: (DayKey) -> Void
    let onVisibleRangeChanged: (CalendarVisibleIndexRange) -> Void
    let onRetryEventLoading: () -> Void
    let onDragEnded: (CGSize) -> Void
    let isEventMutating: Bool
    let isTagMutating: Bool
    let eventMutationFailureMessage: String?
    let tagMutationFailureMessage: String?
    let onResetEventMutation: () -> Void
    let onResetTagMutation: () -> Void
    let onFetchRecurrenceEvent: (Int64) async -> RecurrenceEventDetails?
    let onUpdateSingleEvent: (Event, EventUpdateInput) async -> Bool
    let onUpdateRecurrenceOccurrence: (Event, EventUpdateInput) async -> Bool
    let onUpdateRecurrenceSeries: (Int64, RecurrenceEventSeriesEditInput) async -> Bool
    let onDeleteSingleEvent: (Event) async -> Bool
    let onDeleteRecurrenceOccurrence: (Event) async -> Bool
    let onDeleteRecurrenceSeries: (Event) async -> Bool
    let onCreateCustomTag: (CustomTagInput) async -> Bool
    let onUpdateCustomTag: (CalendarTag, CustomTagInput) async -> Bool
    let onDeleteCustomTag: (CalendarTag) async -> Bool

    init(
        items: [CalendarDayItem],
        tags: [CalendarTag] = [],
        referenceDay: DayKey,
        displayMode: CalendarDisplayMode,
        eventLoadState: CalendarEventLoadState,
        onReferenceDayChanged: @escaping (DayKey) -> Void,
        onVisibleRangeChanged: @escaping (CalendarVisibleIndexRange) -> Void,
        onRetryEventLoading: @escaping () -> Void,
        onDragEnded: @escaping (CGSize) -> Void,
        isEventMutating: Bool = false,
        isTagMutating: Bool = false,
        eventMutationFailureMessage: String? = nil,
        tagMutationFailureMessage: String? = nil,
        onResetEventMutation: @escaping () -> Void = {},
        onResetTagMutation: @escaping () -> Void = {},
        onFetchRecurrenceEvent: @escaping (Int64) async -> RecurrenceEventDetails? = { _ in nil },
        onUpdateSingleEvent: @escaping (Event, EventUpdateInput) async -> Bool = { _, _ in true },
        onUpdateRecurrenceOccurrence: @escaping (Event, EventUpdateInput) async -> Bool = { _, _ in true },
        onUpdateRecurrenceSeries: @escaping (Int64, RecurrenceEventSeriesEditInput) async -> Bool = { _, _ in true },
        onDeleteSingleEvent: @escaping (Event) async -> Bool = { _ in true },
        onDeleteRecurrenceOccurrence: @escaping (Event) async -> Bool = { _ in true },
        onDeleteRecurrenceSeries: @escaping (Event) async -> Bool = { _ in true },
        onCreateCustomTag: @escaping (CustomTagInput) async -> Bool = { _ in false },
        onUpdateCustomTag: @escaping (CalendarTag, CustomTagInput) async -> Bool = { _, _ in false },
        onDeleteCustomTag: @escaping (CalendarTag) async -> Bool = { _ in false }
    ) {
        self.items = items
        self.tags = tags
        self.referenceDay = referenceDay
        self.displayMode = displayMode
        self.eventLoadState = eventLoadState
        self.onReferenceDayChanged = onReferenceDayChanged
        self.onVisibleRangeChanged = onVisibleRangeChanged
        self.onRetryEventLoading = onRetryEventLoading
        self.onDragEnded = onDragEnded
        self.isEventMutating = isEventMutating
        self.isTagMutating = isTagMutating
        self.eventMutationFailureMessage = eventMutationFailureMessage
        self.tagMutationFailureMessage = tagMutationFailureMessage
        self.onResetEventMutation = onResetEventMutation
        self.onResetTagMutation = onResetTagMutation
        self.onFetchRecurrenceEvent = onFetchRecurrenceEvent
        self.onUpdateSingleEvent = onUpdateSingleEvent
        self.onUpdateRecurrenceOccurrence = onUpdateRecurrenceOccurrence
        self.onUpdateRecurrenceSeries = onUpdateRecurrenceSeries
        self.onDeleteSingleEvent = onDeleteSingleEvent
        self.onDeleteRecurrenceOccurrence = onDeleteRecurrenceOccurrence
        self.onDeleteRecurrenceSeries = onDeleteRecurrenceSeries
        self.onCreateCustomTag = onCreateCustomTag
        self.onUpdateCustomTag = onUpdateCustomTag
        self.onDeleteCustomTag = onDeleteCustomTag
    }

    var body: some View {
        VStack(spacing: 0) {
            dragHandle

            CalendarDateEventView(
                items: items,
                tags: tags,
                referenceDay: referenceDay,
                eventLoadState: eventLoadState,
                onReferenceDayChanged: onReferenceDayChanged,
                onVisibleRangeChanged: onVisibleRangeChanged,
                onRetryEventLoading: onRetryEventLoading,
                isEventMutating: isEventMutating,
                isTagMutating: isTagMutating,
                eventMutationFailureMessage: eventMutationFailureMessage,
                tagMutationFailureMessage: tagMutationFailureMessage,
                onResetEventMutation: onResetEventMutation,
                onResetTagMutation: onResetTagMutation,
                onFetchRecurrenceEvent: onFetchRecurrenceEvent,
                onUpdateSingleEvent: onUpdateSingleEvent,
                onUpdateRecurrenceOccurrence: onUpdateRecurrenceOccurrence,
                onUpdateRecurrenceSeries: onUpdateRecurrenceSeries,
                onDeleteSingleEvent: onDeleteSingleEvent,
                onDeleteRecurrenceOccurrence: onDeleteRecurrenceOccurrence,
                onDeleteRecurrenceSeries: onDeleteRecurrenceSeries,
                onCreateCustomTag: onCreateCustomTag,
                onUpdateCustomTag: onUpdateCustomTag,
                onDeleteCustomTag: onDeleteCustomTag
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
        eventLoadState: .idle,
        onReferenceDayChanged: { _ in },
        onVisibleRangeChanged: { _ in },
        onRetryEventLoading: {},
        onDragEnded: { _ in }
    )
}
