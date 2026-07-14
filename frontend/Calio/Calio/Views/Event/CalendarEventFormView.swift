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
    var tag: CalendarTag?
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
    private let tagTitleMaxLength = 12

    @Binding var eventInput: EventInput
    let recurrenceInput: Binding<RecurrenceInput>?
    let tags: [CalendarTag]
    let isTagMutating: Bool
    let tagMutationFailureMessage: String?

    let mode: CalendarEventFormMode
    let onRecurrenceEnabled: () -> Void
    let onTitleChanged: (String) -> Void
    let onResetTagMutation: () -> Void
    let onCreateCustomTag: (CustomTagInput) async -> Bool
    let onUpdateCustomTag: (CalendarTag, CustomTagInput) async -> Bool
    let onDeleteCustomTag: (CalendarTag) async -> Bool

    @State private var isShowingTagManagement = false

    init(
        eventInput: Binding<EventInput>,
        recurrenceInput: Binding<RecurrenceInput>? = nil,
        tags: [CalendarTag] = [],
        isTagMutating: Bool = false,
        tagMutationFailureMessage: String? = nil,
        mode: CalendarEventFormMode = .create,
        onRecurrenceEnabled: @escaping () -> Void,
        onTitleChanged: @escaping (String) -> Void = { _ in },
        onResetTagMutation: @escaping () -> Void = {},
        onCreateCustomTag: @escaping (CustomTagInput) async -> Bool = { _ in false },
        onUpdateCustomTag: @escaping (CalendarTag, CustomTagInput) async -> Bool = { _, _ in false },
        onDeleteCustomTag: @escaping (CalendarTag) async -> Bool = { _ in false }
    ) {
        _eventInput = eventInput
        self.recurrenceInput = recurrenceInput
        self.tags = tags
        self.isTagMutating = isTagMutating
        self.tagMutationFailureMessage = tagMutationFailureMessage
        self.mode = mode
        self.onRecurrenceEnabled = onRecurrenceEnabled
        self.onTitleChanged = onTitleChanged
        self.onResetTagMutation = onResetTagMutation
        self.onCreateCustomTag = onCreateCustomTag
        self.onUpdateCustomTag = onUpdateCustomTag
        self.onDeleteCustomTag = onDeleteCustomTag
    }

    var title: String {
        eventInput.title
    }

    var body: some View {
        Group {
            if mode.showsTitleField {
                titleSection
            }
            timeSection
            if mode.showsRecurrenceFields {
                recurrenceSection
            }
            if mode.showsDescriptionField {
                descriptionSection
            }
            if mode.showsTagField {
                tagSection
            }
        }
    }

    private var titleSection: some View {
        Section {
            TextField("일정 제목", text: $eventInput.title)
                .font(.system(size: 20, weight: .semibold))
                .submitLabel(.next)
                .onChange(of: eventInput.title) { _, title in
                    onTitleChanged(title)
                }
        }
    }

    @ViewBuilder
    private var timeSection: some View {
        if let recurrenceInput,
           mode.usesRecurrenceDateAndTime(isRecurrenceEnabled: recurrenceInput.wrappedValue.isEnabled) {
            recurrenceDateSection(recurrenceInput)
            recurrenceTimeSection(recurrenceInput)
        } else {
            singleEventTimeSection
        }
    }

    @ViewBuilder
    private var recurrenceSection: some View {
        if let recurrenceInput {
            Section("반복") {
                if mode.allowsRecurrenceToggle {
                    recurrenceEnabledButton(recurrenceInput)
                } else {
                    fixedRecurrenceEnabledRow
                }

                if mode.showsRecurrenceFrequency(isRecurrenceEnabled: recurrenceInput.wrappedValue.isEnabled) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("반복 주기")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)

                        HStack(spacing: 8) {
                            ForEach(RecurrenceFrequency.allCases, id: \.self) { frequency in
                                recurrenceFrequencyButton(frequency, recurrenceInput: recurrenceInput)
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }
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

    private func recurrenceDateSection(_ recurrenceInput: Binding<RecurrenceInput>) -> some View {
        Section("반복 기간") {
            DatePicker(
                "반복 시작일",
                selection: recurrenceInput.startDate,
                displayedComponents: [.date]
            )
            .environment(\.locale, Locale(identifier: "ko_KR"))

            DatePicker(
                "반복 종료일",
                selection: recurrenceInput.endDate,
                displayedComponents: [.date]
            )
            .environment(\.locale, Locale(identifier: "ko_KR"))
        }
    }

    private func recurrenceTimeSection(_ recurrenceInput: Binding<RecurrenceInput>) -> some View {
        Section("반복 시간") {
            DatePicker(
                "시작 시간",
                selection: recurrenceInput.startTime,
                displayedComponents: [.hourAndMinute]
            )

            DatePicker(
                "종료 시간",
                selection: recurrenceInput.endTime,
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

    @ViewBuilder
    private var tagSection: some View {
        Section("태그") {
            Button {
                isShowingTagManagement = true
            } label: {
                Label("태그 관리", systemImage: "tag")
            }
            .sheet(isPresented: $isShowingTagManagement) {
                CalendarTagManagementView(
                    tags: tags,
                    selectedTag: $eventInput.tag,
                    isMutating: isTagMutating,
                    failureMessage: tagMutationFailureMessage,
                    maxTitleLength: tagTitleMaxLength,
                    onResetFailure: onResetTagMutation,
                    onCreateCustomTag: onCreateCustomTag,
                    onUpdateCustomTag: onUpdateCustomTag,
                    onDeleteCustomTag: onDeleteCustomTag
                )
            }

            if availableTags.isEmpty {
                Text("사용 가능한 태그가 없습니다.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                FlowLayout(spacing: 8) {
                    ForEach(availableTags) { tag in
                        tagButton(tag)
                    }
                }
                .padding(.vertical, 4)
            }
        }
    }

    private func recurrenceEnabledButton(_ recurrenceInput: Binding<RecurrenceInput>) -> some View {
        Button {
            recurrenceInput.wrappedValue.isEnabled.toggle()

            if recurrenceInput.wrappedValue.isEnabled {
                onRecurrenceEnabled()
            }
        } label: {
            HStack {
                Text("반복 일정")
                    .foregroundStyle(.primary)
                Spacer()
                Text(recurrenceInput.wrappedValue.isEnabled ? "켜짐" : "꺼짐")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(recurrenceInput.wrappedValue.isEnabled ? Color.accentColor : Color.secondary)
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

    private func recurrenceFrequencyButton(
        _ frequency: RecurrenceFrequency,
        recurrenceInput: Binding<RecurrenceInput>
    ) -> some View {
        Button {
            recurrenceInput.wrappedValue.frequency = frequency
        } label: {
            Text(frequency.koreanLabel)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(recurrenceInput.wrappedValue.frequency == frequency ? Color.white : Color.primary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(recurrenceInput.wrappedValue.frequency == frequency ? Color.accentColor : Color(uiColor: .secondarySystemGroupedBackground))
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

    private func tagButton(_ tag: CalendarTag) -> some View {
        Button {
            eventInput.tag = tag
        } label: {
            HStack(spacing: 6) {
                Circle()
                    .fill(Color(hex: tag.colorCode))
                    .frame(width: 10, height: 10)

                Text(tag.title)
                    .font(.subheadline.weight(.semibold))

                if eventInput.tag?.id == tag.id {
                    Image(systemName: "checkmark")
                        .font(.system(size: 11, weight: .bold))
                }
            }
            .foregroundStyle(eventInput.tag?.id == tag.id ? Color.white : Color.primary)
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(eventInput.tag?.id == tag.id ? Color(hex: tag.colorCode) : Color(uiColor: .secondarySystemGroupedBackground))
            )
            .overlay {
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color.secondary.opacity(0.2), lineWidth: 1)
            }
            .contentShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(tag.title) 태그 선택")
    }

    private var availableTags: [CalendarTag] {
        tags.isEmpty ? eventInput.tag.map { [$0] } ?? [] : tags
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

    var showsTitleField: Bool {
        self != .editRecurrenceOccurrence
    }

    var showsDescriptionField: Bool {
        self != .editRecurrenceOccurrence
    }

    var allowsRecurrenceToggle: Bool {
        self == .create
    }

    var showsTagField: Bool {
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
