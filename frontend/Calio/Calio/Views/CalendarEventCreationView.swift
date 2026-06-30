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
    
    let isSaving: Bool
    let failureMessage: String?
    let onSave: (CalendarEventCreationSubmitInput) async -> Bool
    
    init(
        referenceDay: DayKey,
        calendar: Calendar = .current,
        isSaving: Bool = false,
        failureMessage: String? = nil,
        onSave: @escaping (CalendarEventCreationSubmitInput) async -> Bool = { _ in true }
    ) {
        let timeRange = CalendarEventCreationView.defaultTimeRange(
            referenceDay: referenceDay,
            calendar: calendar
        )
        let startAt = timeRange.startAt
        let endAt = timeRange.endAt
        
        _eventInput = State(
            initialValue: EventInput(
                title: "",
                startAt: startAt,
                endAt: endAt,
                description: "",
                colorCode: "#4F46E5"
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
            endAt: eventInput.endAt
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
                    recurrenceFrequency: recurrenceInput.frequency
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

    private func resetRecurrenceFieldsFromSingleEventTime() {
        recurrenceInput.startDate = eventInput.startAt
        recurrenceInput.endDate = eventInput.startAt
        recurrenceInput.startTime = eventInput.startAt
        recurrenceInput.endTime = eventInput.endAt
        recurrenceInput.frequency = .daily
    }
}

#Preview {
    CalendarEventCreationView(referenceDay: DayKey(date: Date()))
}
