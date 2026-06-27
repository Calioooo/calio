//
//  CalendarEventCreationView.swift
//  Calio
//
//  Created by Codex on 6/19/26.
//

import SwiftUI

struct CalendarEventCreationView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var startAt: Date
    @State private var endAt: Date
    @State private var description = ""
    @State private var selectedColorCode = "#4F46E5"
    @State private var isRecurrenceEnabled = false
    @State private var recurrenceStartDate: Date
    @State private var recurrenceEndDate: Date
    @State private var recurrenceStartTime: Date
    @State private var recurrenceEndTime: Date
    @State private var selectedRecurrenceFrequency = RecurrenceFrequency.daily
    
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
        
        _startAt = State(initialValue: startAt)
        _endAt = State(initialValue: endAt)
        _recurrenceStartDate = State(initialValue: startAt)
        _recurrenceEndDate = State(initialValue: startAt)
        _recurrenceStartTime = State(initialValue: startAt)
        _recurrenceEndTime = State(initialValue: endAt)
        self.isSaving = isSaving
        self.failureMessage = failureMessage
        self.onSave = onSave
    }
    
    var body: some View {
        NavigationStack {
            Form {
                failureSection
                CalendarEventFormView(
                    title: $title,
                    startAt: $startAt,
                    endAt: $endAt,
                    description: $description,
                    selectedColorCode: $selectedColorCode,
                    isRecurrenceEnabled: $isRecurrenceEnabled,
                    recurrenceStartDate: $recurrenceStartDate,
                    recurrenceEndDate: $recurrenceEndDate,
                    recurrenceStartTime: $recurrenceStartTime,
                    recurrenceEndTime: $recurrenceEndTime,
                    selectedRecurrenceFrequency: $selectedRecurrenceFrequency,
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
        CalendarEventCreationView.canSave(
            title: title,
            startAt: startAt,
            endAt: endAt,
            isRecurrenceEnabled: isRecurrenceEnabled,
            recurrenceStartDate: recurrenceStartDate,
            recurrenceEndDate: recurrenceEndDate,
            recurrenceStartTime: recurrenceStartTime,
            recurrenceEndTime: recurrenceEndTime
        )
    }
    
    private func save() {
        let eventInput = EventCreateInput(
            title: title.trimmingCharacters(in: .whitespacesAndNewlines),
            description: description,
            startAt: startAt,
            endAt: endAt
        )
        let submitInput: CalendarEventCreationSubmitInput

        if isRecurrenceEnabled {
            submitInput = .recurring(
                RecurrenceEventCreateInput(
                    title: eventInput.title,
                    description: eventInput.description,
                    recurrenceStartDate: recurrenceStartDate,
                    recurrenceEndDate: recurrenceEndDate,
                    recurrenceStartTime: recurrenceStartTime,
                    recurrenceEndTime: recurrenceEndTime,
                    recurrenceFrequency: selectedRecurrenceFrequency
                )
            )
        } else {
            submitInput = .single(eventInput)
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

    nonisolated static func canSave(title: String, startAt: Date, endAt: Date) -> Bool {
        canSave(
            title: title,
            startAt: startAt,
            endAt: endAt,
            isRecurrenceEnabled: false,
            recurrenceStartDate: startAt,
            recurrenceEndDate: startAt,
            recurrenceStartTime: startAt,
            recurrenceEndTime: endAt
        )
    }

    nonisolated static func canSave(
        title: String,
        startAt: Date,
        endAt: Date,
        isRecurrenceEnabled: Bool,
        recurrenceStartDate: Date,
        recurrenceEndDate: Date,
        recurrenceStartTime: Date,
        recurrenceEndTime: Date
    ) -> Bool {
        guard !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return false
        }

        guard isRecurrenceEnabled else {
            return endAt > startAt
        }

        return !isUTCDate(recurrenceEndDate, before: recurrenceStartDate) &&
        isUTCTime(recurrenceStartTime, before: recurrenceEndTime)
    }

    private nonisolated static func isUTCDate(_ candidate: Date, before startAt: Date) -> Bool {
        DayKey(date: candidate, calendar: utcCalendar) < DayKey(date: startAt, calendar: utcCalendar)
    }

    private nonisolated static func isUTCTime(_ startTime: Date, before endTime: Date) -> Bool {
        let calendar = utcCalendar
        let startComponents = calendar.dateComponents([.hour, .minute, .second], from: startTime)
        let endComponents = calendar.dateComponents([.hour, .minute, .second], from: endTime)
        let startSecond = totalSeconds(from: startComponents)
        let endSecond = totalSeconds(from: endComponents)

        return startSecond < endSecond
    }

    private nonisolated static func totalSeconds(from components: DateComponents) -> Int {
        ((components.hour ?? 0) * 3600) +
        ((components.minute ?? 0) * 60) +
        (components.second ?? 0)
    }

    private func resetRecurrenceFieldsFromSingleEventTime() {
        recurrenceStartDate = startAt
        recurrenceEndDate = startAt
        recurrenceStartTime = startAt
        recurrenceEndTime = endAt
        selectedRecurrenceFrequency = .daily
    }

    private nonisolated static var utcCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }
}

#Preview {
    CalendarEventCreationView(referenceDay: DayKey(date: Date()))
}
