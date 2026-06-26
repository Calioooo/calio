//
//  CalendarEventCreationView.swift
//  Calio
//
//  Created by Codex on 6/19/26.
//

import SwiftUI

struct CalendarEventCreationView: View {
    private let eventColors = [
        "#4F46E5",
        "#EF4444",
        "#F59E0B",
        "#22C55E",
        "#0EA5E9"
    ]
    
    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var startAt: Date
    @State private var endAt: Date
    @State private var description = ""
    @State private var selectedColorCode = "#4F46E5"
    @State private var isRecurrenceEnabled = false
    @State private var recurrenceEndAt: Date
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
        _recurrenceEndAt = State(initialValue: startAt)
        self.isSaving = isSaving
        self.failureMessage = failureMessage
        self.onSave = onSave
    }
    
    var body: some View {
        NavigationStack {
            Form {
                failureSection
                titleSection
                timeSection
                recurrenceSection
                descriptionSection
                colorSection
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
    
    private var titleSection: some View {
        Section {
            TextField("일정 제목", text: $title)
                .font(.system(size: 20, weight: .semibold))
                .submitLabel(.next)
        }
    }
    
    private var timeSection: some View {
        Section {
            DatePicker(
                "시작",
                selection: $startAt,
                displayedComponents: [.date, .hourAndMinute]
            )
            
            DatePicker(
                "종료",
                selection: $endAt,
                in: startAt...,
                displayedComponents: [.date, .hourAndMinute]
            )
        }
    }

    private var recurrenceSection: some View {
        Section("반복") {
            Toggle("반복 일정", isOn: recurrenceEnabledBinding)

            if isRecurrenceEnabled {
                VStack(alignment: .leading, spacing: 12) {
                    Text("반복 주기")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    ForEach(RecurrenceFrequency.allCases, id: \.self) { frequency in
                        recurrenceFrequencyButton(frequency)
                    }
                }
                .padding(.vertical, 4)

                DatePicker(
                    "반복 종료일",
                    selection: $recurrenceEndAt,
                    displayedComponents: [.date]
                )
            }
        }
    }
    
    private var descriptionSection: some View {
        Section("설명") {
            TextEditor(text: $description)
                .frame(minHeight: 96)
                .overlay(alignment: .topLeading) {
                    if description.isEmpty {
                        Text("메모를 입력하세요")
                            .foregroundStyle(.secondary)
                            .padding(.top, 8)
                            .padding(.leading, 5)
                            .allowsHitTesting(false)
                    }
                }
        }
    }
    
    private var colorSection: some View {
        Section("색상") {
            HStack(spacing: 14) {
                ForEach(eventColors, id: \.self) { colorCode in
                    colorButton(colorCode)
                }
            }
            .padding(.vertical, 4)
        }
    }
    
    private func colorButton(_ colorCode: String) -> some View {
        Button {
            selectedColorCode = colorCode
        } label: {
            Circle()
                .fill(Color(hex: colorCode))
                .frame(width: 28, height: 28)
                .overlay {
                    if selectedColorCode == colorCode {
                        Image(systemName: "checkmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(.white)
                    }
                }
                .overlay {
                    Circle()
                        .stroke(Color.secondary.opacity(0.25), lineWidth: 1)
                }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("일정 색상 선택")
    }

    private var recurrenceEnabledBinding: Binding<Bool> {
        Binding(
            get: { isRecurrenceEnabled },
            set: { isEnabled in
                isRecurrenceEnabled = isEnabled

                if isEnabled {
                    recurrenceEndAt = startAt
                    selectedRecurrenceFrequency = .daily
                }
            }
        )
    }

    private func recurrenceFrequencyButton(_ frequency: RecurrenceFrequency) -> some View {
        Button {
            selectedRecurrenceFrequency = frequency
        } label: {
            HStack(spacing: 10) {
                Image(systemName: selectedRecurrenceFrequency == frequency ? "largecircle.fill.circle" : "circle")
                    .foregroundStyle(selectedRecurrenceFrequency == frequency ? Color.accentColor : Color.secondary)
                Text(frequency.koreanLabel)
                    .foregroundStyle(.primary)
                Spacer()
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("recurrence_frequency_\(frequency.rawValue)")
    }
    
    private var canSave: Bool {
        CalendarEventCreationView.canSave(
            title: title,
            startAt: startAt,
            endAt: endAt,
            isRecurrenceEnabled: isRecurrenceEnabled,
            recurrenceEndAt: recurrenceEndAt
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
                    startAt: eventInput.startAt,
                    endAt: eventInput.endAt,
                    recurrenceEndAt: recurrenceEndAt,
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
            recurrenceEndAt: startAt
        )
    }

    nonisolated static func canSave(
        title: String,
        startAt: Date,
        endAt: Date,
        isRecurrenceEnabled: Bool,
        recurrenceEndAt: Date
    ) -> Bool {
        let hasValidSingleEventFields =
            !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            endAt > startAt

        guard hasValidSingleEventFields else {
            return false
        }

        guard isRecurrenceEnabled else {
            return true
        }

        return !isUTCDate(recurrenceEndAt, before: startAt)
    }

    private nonisolated static func isUTCDate(_ candidate: Date, before startAt: Date) -> Bool {
        DayKey(date: candidate, calendar: utcCalendar) < DayKey(date: startAt, calendar: utcCalendar)
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
