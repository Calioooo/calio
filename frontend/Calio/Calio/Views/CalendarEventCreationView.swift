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
    
    @ViewBuilder
    private var timeSection: some View {
        if isRecurrenceEnabled {
            recurrenceDateSection
            recurrenceTimeSection
        } else {
            singleEventTimeSection
        }
    }

    private var recurrenceSection: some View {
        Section("반복") {
            recurrenceEnabledButton

            if isRecurrenceEnabled {
                VStack(alignment: .leading, spacing: 12) {
                    Text("반복 주기")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    HStack(spacing: 8) {
                        ForEach(RecurrenceFrequency.allCases, id: \.self) { frequency in
                            recurrenceFrequencyButton(frequency)
                        }
                    }
                }
                .padding(.vertical, 4)
            }
        }
    }

    private var singleEventTimeSection: some View {
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

    private var recurrenceDateSection: some View {
        Section("반복 기간") {
            DatePicker(
                "반복 시작일",
                selection: $recurrenceStartDate,
                displayedComponents: [.date]
            )

            DatePicker(
                "반복 종료일",
                selection: $recurrenceEndDate,
                displayedComponents: [.date]
            )
        }
    }

    private var recurrenceTimeSection: some View {
        Section("반복 시간") {
            DatePicker(
                "시작 시간",
                selection: $recurrenceStartTime,
                displayedComponents: [.hourAndMinute]
            )

            DatePicker(
                "종료 시간",
                selection: $recurrenceEndTime,
                displayedComponents: [.hourAndMinute]
            )
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

    private var recurrenceEnabledButton: some View {
        Button {
            isRecurrenceEnabled.toggle()

            if isRecurrenceEnabled {
                resetRecurrenceFieldsFromSingleEventTime()
            }
        } label: {
            HStack {
                Text("반복 일정")
                    .foregroundStyle(.primary)
                Spacer()
                Text(isRecurrenceEnabled ? "켜짐" : "꺼짐")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(isRecurrenceEnabled ? Color.accentColor : Color.secondary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func recurrenceFrequencyButton(_ frequency: RecurrenceFrequency) -> some View {
        Button {
            selectedRecurrenceFrequency = frequency
        } label: {
            Text(frequency.koreanLabel)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(selectedRecurrenceFrequency == frequency ? Color.white : Color.primary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(selectedRecurrenceFrequency == frequency ? Color.accentColor : Color(uiColor: .secondarySystemGroupedBackground))
                )
                .overlay {
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color.secondary.opacity(0.2), lineWidth: 1)
                }
                .contentShape(RoundedRectangle(cornerRadius: 8))
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
