//
//  CalendarEventFormView.swift
//  Calio
//
//  Created by Codex on 6/28/26.
//

import SwiftUI

struct EventInput: Equatable {
    var title: String
    var startAt: Date
    var endAt: Date
    var description: String
    var colorCode: String
}

struct RecurrenceInput: Equatable {
    var isEnabled: Bool
    var startDate: Date
    var endDate: Date
    var startTime: Date
    var endTime: Date
    var frequency: RecurrenceFrequency
}

struct CalendarEventFormView: View {
    private let eventColors = [
        "#4F46E5",
        "#EF4444",
        "#F59E0B",
        "#22C55E",
        "#0EA5E9"
    ]

    @Binding var eventInput: EventInput
    @Binding var recurrenceInput: RecurrenceInput

    let mode: CalendarEventFormMode
    let onRecurrenceEnabled: () -> Void

    init(
        eventInput: Binding<EventInput>,
        recurrenceInput: Binding<RecurrenceInput>,
        mode: CalendarEventFormMode = .create,
        onRecurrenceEnabled: @escaping () -> Void
    ) {
        _eventInput = eventInput
        _recurrenceInput = recurrenceInput
        self.mode = mode
        self.onRecurrenceEnabled = onRecurrenceEnabled
    }

    var title: String {
        eventInput.title
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

    var body: some View {
        titleSection
        timeSection
        if mode.showsRecurrenceFields {
            recurrenceSection
        }
        descriptionSection
        if mode.showsColorFields {
            colorSection
        }
    }

    private var titleSection: some View {
        Section {
            TextField("일정 제목", text: $eventInput.title)
                .font(.system(size: 20, weight: .semibold))
                .submitLabel(.next)
        }
    }

    @ViewBuilder
    private var timeSection: some View {
        if mode.usesRecurrenceDateAndTime(isRecurrenceEnabled: recurrenceInput.isEnabled) {
            recurrenceDateSection
            recurrenceTimeSection
        } else {
            singleEventTimeSection
        }
    }

    private var recurrenceSection: some View {
        Section("반복") {
            if mode.allowsRecurrenceToggle {
                recurrenceEnabledButton
            } else {
                fixedRecurrenceEnabledRow
            }

            if mode.showsRecurrenceFrequency(isRecurrenceEnabled: recurrenceInput.isEnabled) {
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
                selection: $eventInput.startAt,
                displayedComponents: [.date, .hourAndMinute]
            )

            DatePicker(
                "종료",
                selection: $eventInput.endAt,
                in: eventInput.startAt...,
                displayedComponents: [.date, .hourAndMinute]
            )
        }
    }

    private var recurrenceDateSection: some View {
        Section("반복 기간") {
            DatePicker(
                "반복 시작일",
                selection: $recurrenceInput.startDate,
                displayedComponents: [.date]
            )

            DatePicker(
                "반복 종료일",
                selection: $recurrenceInput.endDate,
                displayedComponents: [.date]
            )
        }
    }

    private var recurrenceTimeSection: some View {
        Section("반복 시간") {
            DatePicker(
                "시작 시간",
                selection: $recurrenceInput.startTime,
                displayedComponents: [.hourAndMinute]
            )

            DatePicker(
                "종료 시간",
                selection: $recurrenceInput.endTime,
                displayedComponents: [.hourAndMinute]
            )
        }
    }

    private var descriptionSection: some View {
        Section("설명") {
            TextEditor(text: $eventInput.description)
                .frame(minHeight: 96)
                .overlay(alignment: .topLeading) {
                    if eventInput.description.isEmpty {
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

    private var recurrenceEnabledButton: some View {
        Button {
            recurrenceInput.isEnabled.toggle()

            if recurrenceInput.isEnabled {
                onRecurrenceEnabled()
            }
        } label: {
            HStack {
                Text("반복 일정")
                    .foregroundStyle(.primary)
                Spacer()
                Text(recurrenceInput.isEnabled ? "켜짐" : "꺼짐")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(recurrenceInput.isEnabled ? Color.accentColor : Color.secondary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var fixedRecurrenceEnabledRow: some View {
        HStack {
            Text("반복 일정")
                .foregroundStyle(.primary)
            Spacer()
            Text("켜짐")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Color.accentColor)
        }
    }

    private func recurrenceFrequencyButton(_ frequency: RecurrenceFrequency) -> some View {
        Button {
            recurrenceInput.frequency = frequency
        } label: {
            Text(frequency.koreanLabel)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(recurrenceInput.frequency == frequency ? Color.white : Color.primary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(recurrenceInput.frequency == frequency ? Color.accentColor : Color(uiColor: .secondarySystemGroupedBackground))
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

    private func colorButton(_ colorCode: String) -> some View {
        Button {
            eventInput.colorCode = colorCode
        } label: {
            Circle()
                .fill(Color(hex: colorCode))
                .frame(width: 28, height: 28)
                .overlay {
                    if eventInput.colorCode == colorCode {
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

    private nonisolated static var utcCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }
}

enum CalendarEventFormMode: Equatable {
    case create
    case editSingleEvent
    case editRecurrenceOccurrence
    case editRecurrenceSeries

    var showsRecurrenceFields: Bool {
        switch self {
        case .create, .editRecurrenceSeries:
            return true
        case .editSingleEvent, .editRecurrenceOccurrence:
            return false
        }
    }

    var allowsRecurrenceToggle: Bool {
        self == .create
    }

    var showsColorFields: Bool {
        switch self {
        case .create, .editSingleEvent:
            return true
        case .editRecurrenceOccurrence, .editRecurrenceSeries:
            return false
        }
    }

    func usesRecurrenceDateAndTime(isRecurrenceEnabled: Bool) -> Bool {
        switch self {
        case .create:
            return isRecurrenceEnabled
        case .editRecurrenceSeries:
            return true
        case .editSingleEvent, .editRecurrenceOccurrence:
            return false
        }
    }

    func showsRecurrenceFrequency(isRecurrenceEnabled: Bool) -> Bool {
        switch self {
        case .create:
            return isRecurrenceEnabled
        case .editRecurrenceSeries:
            return true
        case .editSingleEvent, .editRecurrenceOccurrence:
            return false
        }
    }
}
