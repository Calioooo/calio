//
//  CalendarEventFormView.swift
//  Calio
//
//  Created by Codex on 6/28/26.
//

import SwiftUI

struct CalendarEventFormView: View {
    private let eventColors = [
        "#4F46E5",
        "#EF4444",
        "#F59E0B",
        "#22C55E",
        "#0EA5E9"
    ]

    @Binding var title: String
    @Binding var startAt: Date
    @Binding var endAt: Date
    @Binding var description: String
    @Binding var selectedColorCode: String
    @Binding var isRecurrenceEnabled: Bool
    @Binding var recurrenceStartDate: Date
    @Binding var recurrenceEndDate: Date
    @Binding var recurrenceStartTime: Date
    @Binding var recurrenceEndTime: Date
    @Binding var selectedRecurrenceFrequency: RecurrenceFrequency

    let mode: CalendarEventFormMode
    let onRecurrenceEnabled: () -> Void

    init(
        title: Binding<String>,
        startAt: Binding<Date>,
        endAt: Binding<Date>,
        description: Binding<String>,
        selectedColorCode: Binding<String>,
        isRecurrenceEnabled: Binding<Bool>,
        recurrenceStartDate: Binding<Date>,
        recurrenceEndDate: Binding<Date>,
        recurrenceStartTime: Binding<Date>,
        recurrenceEndTime: Binding<Date>,
        selectedRecurrenceFrequency: Binding<RecurrenceFrequency>,
        mode: CalendarEventFormMode = .create,
        onRecurrenceEnabled: @escaping () -> Void
    ) {
        _title = title
        _startAt = startAt
        _endAt = endAt
        _description = description
        _selectedColorCode = selectedColorCode
        _isRecurrenceEnabled = isRecurrenceEnabled
        _recurrenceStartDate = recurrenceStartDate
        _recurrenceEndDate = recurrenceEndDate
        _recurrenceStartTime = recurrenceStartTime
        _recurrenceEndTime = recurrenceEndTime
        _selectedRecurrenceFrequency = selectedRecurrenceFrequency
        self.mode = mode
        self.onRecurrenceEnabled = onRecurrenceEnabled
    }

    var body: some View {
        titleSection
        timeSection
        if mode.showsRecurrenceFields {
            recurrenceSection
        }
        descriptionSection
        colorSection
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
        if mode.showsRecurrenceFields && isRecurrenceEnabled {
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

    private var recurrenceEnabledButton: some View {
        Button {
            isRecurrenceEnabled.toggle()

            if isRecurrenceEnabled {
                onRecurrenceEnabled()
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
}

enum CalendarEventFormMode: Equatable {
    case create
    case editSingleEvent

    var showsRecurrenceFields: Bool {
        switch self {
        case .create:
            return true
        case .editSingleEvent:
            return false
        }
    }
}
