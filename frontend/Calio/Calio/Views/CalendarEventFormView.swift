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
        self.onResetTagMutation = onResetTagMutation
        self.onCreateCustomTag = onCreateCustomTag
        self.onUpdateCustomTag = onUpdateCustomTag
        self.onDeleteCustomTag = onDeleteCustomTag
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
        Group {
            titleSection
            timeSection
            if mode.showsRecurrenceFields {
                recurrenceSection
            }
            descriptionSection
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

            DatePicker(
                "반복 종료일",
                selection: recurrenceInput.endDate,
                displayedComponents: [.date]
            )
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

private struct CalendarTagManagementView: View {
    @Environment(\.dismiss) private var dismiss

    let tags: [CalendarTag]
    @Binding var selectedTag: CalendarTag?
    let isMutating: Bool
    let failureMessage: String?
    let maxTitleLength: Int
    let onResetFailure: () -> Void
    let onCreateCustomTag: (CustomTagInput) async -> Bool
    let onUpdateCustomTag: (CalendarTag, CustomTagInput) async -> Bool
    let onDeleteCustomTag: (CalendarTag) async -> Bool

    @State private var editingTag: CalendarTag?
    @State private var isCreatingTag = false
    @State private var deletingTag: CalendarTag?

    var body: some View {
        NavigationStack {
            List {
                failureSection
                tagSection(title: "기본 태그", tags: defaultTags)
                tagSection(title: "커스텀 태그", tags: customTags)
            }
            .navigationTitle("태그 관리")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("닫기") {
                        dismiss()
                    }
                }

                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        onResetFailure()
                        isCreatingTag = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .disabled(isMutating)
                    .accessibilityLabel("커스텀 태그 추가")
                }
            }
            .sheet(isPresented: $isCreatingTag) {
                CalendarTagEditView(
                    title: "태그 추가",
                    maxTitleLength: maxTitleLength,
                    isSaving: isMutating,
                    onSave: createTag(_:)
                )
            }
            .sheet(item: $editingTag) { tag in
                CalendarTagEditView(
                    title: "태그 수정",
                    initialInput: CustomTagInput(
                        title: tag.title,
                        colorCode: tag.colorCode
                    ),
                    maxTitleLength: maxTitleLength,
                    isSaving: isMutating,
                    onSave: { input in
                        await updateTag(tag, input: input)
                    }
                )
            }
            .confirmationDialog(
                "태그를 삭제하시겠습니까?",
                isPresented: Binding(
                    get: { deletingTag != nil },
                    set: { isPresented in
                        if !isPresented {
                            deletingTag = nil
                        }
                    }
                ),
                titleVisibility: .visible
            ) {
                Button("삭제", role: .destructive) {
                    guard let deletingTag else {
                        return
                    }

                    deleteTag(deletingTag)
                }
                Button("취소", role: .cancel) {
                    deletingTag = nil
                }
            } message: {
                Text("이 태그를 사용하는 일정은 기본 태그로 변경됩니다.")
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
                }
            }
        }
    }

    @ViewBuilder
    private func tagSection(title: String, tags: [CalendarTag]) -> some View {
        Section(title) {
            if tags.isEmpty {
                Text(title == "커스텀 태그" ? "추가한 커스텀 태그가 없습니다." : "태그가 없습니다.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(tags) { tag in
                    tagRow(tag)
                }
            }
        }
    }

    private func tagRow(_ tag: CalendarTag) -> some View {
        HStack(spacing: 10) {
            Circle()
                .fill(Color(hex: tag.colorCode))
                .frame(width: 12, height: 12)

            Text(tag.title)
                .font(.body)

            Spacer()

            if tag.tagType == .custom {
                Button {
                    onResetFailure()
                    editingTag = tag
                } label: {
                    Image(systemName: "pencil")
                }
                .buttonStyle(.borderless)
                .disabled(isMutating)
                .accessibilityLabel("\(tag.title) 태그 수정")

                Button(role: .destructive) {
                    onResetFailure()
                    deletingTag = tag
                } label: {
                    Image(systemName: "trash")
                }
                .buttonStyle(.borderless)
                .disabled(isMutating)
                .accessibilityLabel("\(tag.title) 태그 삭제")
            } else {
                Text("기본")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
        }
        .contentShape(Rectangle())
    }

    private var defaultTags: [CalendarTag] {
        tags.filter { $0.tagType == .defaultTag }
    }

    private var customTags: [CalendarTag] {
        tags.filter { $0.tagType == .custom }
    }

    private func createTag(_ input: CustomTagInput) async -> Bool {
        let didCreate = await onCreateCustomTag(input)

        if didCreate {
            isCreatingTag = false
        }

        return didCreate
    }

    private func updateTag(_ tag: CalendarTag, input: CustomTagInput) async -> Bool {
        let didUpdate = await onUpdateCustomTag(tag, input)

        if didUpdate {
            editingTag = nil
            if selectedTag?.id == tag.id {
                selectedTag = CalendarTag(
                    id: tag.id,
                    title: input.title,
                    colorCode: input.colorCode,
                    tagType: tag.tagType
                )
            }
        }

        return didUpdate
    }

    private func deleteTag(_ tag: CalendarTag) {
        Task {
            let didDelete = await onDeleteCustomTag(tag)

            if didDelete {
                if selectedTag?.id == tag.id {
                    selectedTag = fallbackTag
                }
                deletingTag = nil
            }
        }
    }

    private var fallbackTag: CalendarTag {
        tags.first { $0.title == "기타" } ?? tags.first ?? .fallback
    }
}

private struct CalendarTagEditView: View {
    @Environment(\.dismiss) private var dismiss

    private let colorCodes = [
        "#3B82F6",
        "#A855F7",
        "#F97316",
        "#10B981",
        "#64748B",
        "#EF4444",
        "#0EA5E9",
        "#EAB308"
    ]

    let title: String
    let maxTitleLength: Int
    let isSaving: Bool
    let onSave: (CustomTagInput) async -> Bool

    @State private var input: CustomTagInput

    init(
        title: String,
        initialInput: CustomTagInput = CustomTagInput(title: "", colorCode: "#3B82F6"),
        maxTitleLength: Int,
        isSaving: Bool,
        onSave: @escaping (CustomTagInput) async -> Bool
    ) {
        self.title = title
        self.maxTitleLength = maxTitleLength
        self.isSaving = isSaving
        self.onSave = onSave
        _input = State(initialValue: initialInput)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("이름") {
                    TextField("태그 이름", text: titleBinding)
                    Text("\(input.title.count)/\(maxTitleLength)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Section("색상") {
                    FlowLayout(spacing: 10) {
                        ForEach(colorCodes, id: \.self) { colorCode in
                            colorButton(colorCode)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("취소") {
                        dismiss()
                    }
                    .disabled(isSaving)
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

    private var titleBinding: Binding<String> {
        Binding(
            get: { input.title },
            set: { newValue in
                input = CustomTagInput(
                    title: String(newValue.prefix(maxTitleLength)),
                    colorCode: input.colorCode
                )
            }
        )
    }

    private var canSave: Bool {
        !trimmedTitle.isEmpty && input.title.count <= maxTitleLength
    }

    private var trimmedTitle: String {
        input.title.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func colorButton(_ colorCode: String) -> some View {
        Button {
            input = CustomTagInput(
                title: input.title,
                colorCode: colorCode
            )
        } label: {
            Circle()
                .fill(Color(hex: colorCode))
                .frame(width: 30, height: 30)
                .overlay {
                    if input.colorCode == colorCode {
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
        .accessibilityLabel("태그 색상 선택")
    }

    private func save() {
        let saveInput = CustomTagInput(
            title: trimmedTitle,
            colorCode: input.colorCode
        )

        Task {
            let didSave = await onSave(saveInput)

            if didSave {
                dismiss()
            }
        }
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
