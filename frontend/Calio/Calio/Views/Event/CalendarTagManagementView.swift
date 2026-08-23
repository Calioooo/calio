//
//  CalendarTagManagementView.swift
//  Calio
//
//  Created by Codex on 7/7/26.
//

import SwiftUI

struct CalendarTagManagementRules {
    let tags: [CalendarTag]

    var defaultTags: [CalendarTag] {
        tags.filter { $0.tagType == .defaultTag }
    }

    var customTags: [CalendarTag] {
        tags.filter { $0.tagType == .custom }
    }

    var fallbackTag: CalendarTag {
        tags.first { $0.title == "기타" } ?? tags.first ?? .fallback
    }
}

struct CalendarTagEditInputRules {
    let maxTitleLength: Int

    func limitedTitle(_ title: String) -> String {
        String(title.prefix(maxTitleLength))
    }

    func trimmedTitle(_ title: String) -> String {
        title.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func canSave(_ input: CustomTagInput) -> Bool {
        !trimmedTitle(input.title).isEmpty && input.title.count <= maxTitleLength
    }

    func saveInput(from input: CustomTagInput) -> CustomTagInput {
        CustomTagInput(
            title: trimmedTitle(input.title),
            colorCode: input.colorCode
        )
    }
}

struct CalendarTagManagementView: View {
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
                tagSection(title: "기본 태그", tags: rules.defaultTags)
                tagSection(title: "커스텀 태그", tags: rules.customTags)
            }
            .scrollContentBackground(.hidden)
            .background(Color.calioBackground)
            .tint(.calioBrand)
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
                        Label("태그 추가", systemImage: "plus")
                            .font(.subheadline.weight(.semibold))
                    }
                    .disabled(isMutating)
                    .accessibilityLabel("커스텀 태그 추가")
                    .accessibilityIdentifier("tag_management_add_button")
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
                        .foregroundStyle(Color.calendarHoliday)
                    Text(failureMessage)
                        .font(.subheadline)
                        .foregroundStyle(.calioTextPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.vertical, 2)
                .listRowBackground(Color.calioSelection)
                .accessibilityIdentifier("tag_management_failure_message")
            }
        }
    }

    @ViewBuilder
    private func tagSection(title: String, tags: [CalendarTag]) -> some View {
        Section(title) {
            if tags.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Label(title == "커스텀 태그" ? "추가한 커스텀 태그가 없습니다." : "태그가 없습니다.", systemImage: "tag")
                        .font(.subheadline)
                        .foregroundStyle(.calioTextPrimary)

                    if title == "커스텀 태그" {
                        Text("오른쪽 위의 태그 추가로 필요한 분류를 만들어 보세요.")
                            .font(.caption)
                            .foregroundStyle(.calioTextSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .padding(.vertical, 4)
                .listRowBackground(Color.calioSurface)
                    .accessibilityIdentifier("tag_management_empty_\(title == "커스텀 태그" ? "custom" : "default")")
            } else {
                ForEach(tags) { tag in
                    tagRow(tag)
                }
            }
        }
    }

    private func tagRow(_ tag: CalendarTag) -> some View {
        let isSelected = selectedTag?.id == tag.id

        return HStack(spacing: 10) {
            Circle()
                .fill(Color(hex: tag.colorCode))
                .frame(width: 14, height: 14)
                .overlay(Circle().stroke(Color.calioDivider, lineWidth: 1))

            Text(tag.title)
                .font(.body.weight(isSelected ? .semibold : .regular))
                .foregroundStyle(.calioTextPrimary)
                .lineLimit(1)

            Spacer()

            if isSelected {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(.calioBrand)
                    .accessibilityLabel("선택됨")
            }

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
                .accessibilityIdentifier("tag_management_edit_\(tag.id)")

                Button(role: .destructive) {
                    onResetFailure()
                    deletingTag = tag
                } label: {
                    Image(systemName: "trash")
                }
                .buttonStyle(.borderless)
                .disabled(isMutating)
                .accessibilityLabel("\(tag.title) 태그 삭제")
                .accessibilityIdentifier("tag_management_delete_\(tag.id)")
            } else {
                Text("기본")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.calioTextSecondary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Capsule().fill(Color.calioSelection))
            }
        }
        .padding(.vertical, 5)
        .listRowBackground(isSelected ? Color.calioSelection : Color.calioSurface)
        .contentShape(Rectangle())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("tag_management_row_\(tag.id)")
    }

    private var rules: CalendarTagManagementRules {
        CalendarTagManagementRules(tags: tags)
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
                    selectedTag = rules.fallbackTag
                }
                deletingTag = nil
            }
        }
    }
}

struct CalendarTagEditView: View {
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
                        .foregroundStyle(.calioTextPrimary)
                        .accessibilityIdentifier("tag_edit_title_input")
                    Text("\(input.title.count)/\(maxTitleLength)")
                        .font(.caption)
                        .foregroundStyle(.calioTextSecondary)
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
            .scrollContentBackground(.hidden)
            .background(Color.calioBackground)
            .tint(.calioBrand)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("취소") {
                        dismiss()
                    }
                    .disabled(isSaving)
                    .accessibilityIdentifier("tag_edit_cancel_button")
                }

                ToolbarItem(placement: .confirmationAction) {
                    Button("저장") {
                        save()
                    }
                    .disabled(!canSave || isSaving)
                    .accessibilityIdentifier("tag_edit_save_button")
                }
            }
        }
    }

    private var titleBinding: Binding<String> {
        Binding(
            get: { input.title },
            set: { newValue in
                input = CustomTagInput(
                    title: inputRules.limitedTitle(newValue),
                    colorCode: input.colorCode
                )
            }
        )
    }

    private var canSave: Bool {
        inputRules.canSave(input)
    }

    private var inputRules: CalendarTagEditInputRules {
        CalendarTagEditInputRules(maxTitleLength: maxTitleLength)
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
                        .stroke(input.colorCode == colorCode ? Color.calioBrand : Color.calioDivider, lineWidth: input.colorCode == colorCode ? 2 : 1)
                }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(colorName(for: colorCode)) 태그 색상")
        .accessibilityValue(input.colorCode == colorCode ? "선택됨" : "선택 안 됨")
        .accessibilityIdentifier("tag_edit_color_\(colorCode)")
    }

    private func colorName(for colorCode: String) -> String {
        switch colorCode {
        case "#3B82F6": "파란색"
        case "#A855F7": "보라색"
        case "#F97316": "주황색"
        case "#10B981": "초록색"
        case "#64748B": "회색"
        case "#EF4444": "빨간색"
        case "#0EA5E9": "하늘색"
        case "#EAB308": "노란색"
        default: "태그"
        }
    }

    private func save() {
        let saveInput = inputRules.saveInput(from: input)

        Task {
            let didSave = await onSave(saveInput)

            if didSave {
                dismiss()
            }
        }
    }
}
