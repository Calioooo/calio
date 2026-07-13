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
                        .stroke(Color.secondary.opacity(0.25), lineWidth: 1)
                }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("태그 색상 선택")
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
