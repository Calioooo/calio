//
//  CalendarEventCreationView.swift
//  Calio
//
//  Created by Codex on 6/19/26.
//

import SwiftUI

struct CalendarEventCreationView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var eventInput: EventInput
    @State private var recurrenceInput: RecurrenceInput
    
    let tags: [CalendarTag]
    let isSaving: Bool
    let failureMessage: String?
    let onSave: (CalendarEventCreationSubmitInput) async -> Bool
    
    init(
        referenceDay: DayKey,
        initialDateRange: CalendarDateRange? = nil,
        tags: [CalendarTag] = [],
        calendar: Calendar = .current,
        isSaving: Bool = false,
        failureMessage: String? = nil,
        onSave: @escaping (CalendarEventCreationSubmitInput) async -> Bool = { _ in true }
    ) {
        let timeRange = CalendarEventCreationView.defaultTimeRange(
            referenceDay: referenceDay,
            calendar: calendar
        )
        let initialTimeRange = CalendarEventCreationView.timeRange(
            from: initialDateRange,
            defaultTimeRange: timeRange,
            calendar: calendar
        )
        let startAt = initialTimeRange.startAt
        let endAt = initialTimeRange.endAt
        
        _eventInput = State(
            initialValue: EventInput(
                title: "",
                startAt: startAt,
                endAt: endAt,
                description: "",
                tag: CalendarEventCreationView.defaultTag(from: tags)
            )
        )
        _recurrenceInput = State(
            initialValue: RecurrenceInput(
                isEnabled: false,
                startDate: startAt,
                endDate: startAt,
                startTime: startAt,
                endTime: endAt,
                frequency: .daily
            )
        )
        self.tags = tags
        self.isSaving = isSaving
        self.failureMessage = failureMessage
        self.onSave = onSave
    }
    
    var body: some View {
        NavigationStack {
            Form {
                failureSection
                CalendarEventFormView(
                    eventInput: $eventInput,
                    recurrenceInput: $recurrenceInput,
                    tags: tags,
                    onRecurrenceEnabled: resetRecurrenceFieldsFromSingleEventTime
                )
            }
            .scrollContentBackground(.hidden)
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("새 일정")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("취소") {
                        dismiss()
                    }
                }
                
                ToolbarItem(placement: .confirmationAction) {
                    Button("저장") {
                        save()
                    }
                    .disabled(!canSave || isSaving)
                }
            }
        }
    }

    @ViewBuilder
    private var failureSection: some View {
        if let failureMessage {
            Section {
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "exclamationmark.circle.fill")
                        .foregroundStyle(.red)
                    Text(failureMessage)
                        .font(.subheadline)
                        .foregroundStyle(.primary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.vertical, 2)
                .accessibilityIdentifier("event_creation_failure_message")
            }
        }
    }
    
    private var canSave: Bool {
        CalendarEventFormView.canSave(
            title: eventInput.title,
            startAt: eventInput.startAt,
            endAt: eventInput.endAt,
            isRecurrenceEnabled: recurrenceInput.isEnabled,
            recurrenceStartDate: recurrenceInput.startDate,
            recurrenceEndDate: recurrenceInput.endDate,
            recurrenceStartTime: recurrenceInput.startTime,
            recurrenceEndTime: recurrenceInput.endTime
        )
    }
    
    private func save() {
        let eventCreateInput = EventCreateInput(
            title: eventInput.title.trimmingCharacters(in: .whitespacesAndNewlines),
            description: eventInput.description,
            startAt: eventInput.startAt,
            endAt: eventInput.endAt,
            tagId: eventInput.tag?.id
        )
        let submitInput: CalendarEventCreationSubmitInput

        if recurrenceInput.isEnabled {
            submitInput = .recurring(
                RecurrenceEventCreateInput(
                    title: eventCreateInput.title,
                    description: eventCreateInput.description,
                    recurrenceStartDate: recurrenceInput.startDate,
                    recurrenceEndDate: recurrenceInput.endDate,
                    recurrenceStartTime: recurrenceInput.startTime,
                    recurrenceEndTime: recurrenceInput.endTime,
                    recurrenceFrequency: recurrenceInput.frequency,
                    tagId: eventInput.tag?.id
                )
            )
        } else {
            submitInput = .single(eventCreateInput)
        }

        Task {
            let didSave = await onSave(submitInput)

            if didSave {
                dismiss()
            }
        }
    }

    nonisolated static func defaultTimeRange(
        referenceDay: DayKey,
        calendar: Calendar
    ) -> (startAt: Date, endAt: Date) {
        let date = referenceDay.toDate(calendar: calendar)
        let startAt = calendar.date(
            bySettingHour: 9,
            minute: 0,
            second: 0,
            of: date
        ) ?? date
        let endAt = calendar.date(
            byAdding: .hour,
            value: 1,
            to: startAt
        ) ?? startAt

        return (startAt, endAt)
    }

    nonisolated private static func timeRange(
        from dateRange: CalendarDateRange?,
        defaultTimeRange: (startAt: Date, endAt: Date),
        calendar: Calendar
    ) -> (startAt: Date, endAt: Date) {
        guard let dateRange else {
            return defaultTimeRange
        }

        return (
            startAt: date(
                for: dateRange.startDay,
                usingTimeFrom: defaultTimeRange.startAt,
                calendar: calendar
            ),
            endAt: date(
                for: dateRange.endDay,
                usingTimeFrom: defaultTimeRange.endAt,
                calendar: calendar
            )
        )
    }

    nonisolated private static func date(
        for day: DayKey,
        usingTimeFrom timeSource: Date,
        calendar: Calendar
    ) -> Date {
        let date = day.toDate(calendar: calendar)
        let timeComponents = calendar.dateComponents([.hour, .minute, .second], from: timeSource)

        return calendar.date(
            bySettingHour: timeComponents.hour ?? 0,
            minute: timeComponents.minute ?? 0,
            second: timeComponents.second ?? 0,
            of: date
        ) ?? date
    }

    private func resetRecurrenceFieldsFromSingleEventTime() {
        recurrenceInput.startDate = eventInput.startAt
        recurrenceInput.endDate = eventInput.startAt
        recurrenceInput.startTime = eventInput.startAt
        recurrenceInput.endTime = eventInput.endAt
        recurrenceInput.frequency = .daily
    }

    private static func defaultTag(from tags: [CalendarTag]) -> CalendarTag {
        tags.first { $0.title == "기타" } ?? tags.first ?? .fallback
    }
}

#Preview {
    CalendarEventCreationView(referenceDay: DayKey(date: Date()))
}
