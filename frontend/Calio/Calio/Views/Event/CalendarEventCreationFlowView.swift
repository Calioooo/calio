//
//  CalendarEventCreationFlowView.swift
//  Calio
//
//  Created by Codex on 7/14/26.
//

import SwiftUI

struct CalendarEventCreationFlowView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var detailedDraft: CalendarEventCreationDraft?
    @State private var selectedDetent: PresentationDetent = .medium

    private let baseDraft: CalendarEventCreationDraft
    private let parser: LocalEventTextParser
    private let calendar: Calendar
    private let entryMode: CalendarEventCreationEntryMode

    let tags: [CalendarTag]
    let isSaving: Bool
    let isTagMutating: Bool
    let failureMessage: String?
    let tagMutationFailureMessage: String?
    let onSave: (CalendarEventCreationSubmitInput) async -> Bool
    let onResetTagMutation: () -> Void
    let onCreateCustomTag: (CustomTagInput) async -> Bool
    let onUpdateCustomTag: (CalendarTag, CustomTagInput) async -> Bool
    let onDeleteCustomTag: (CalendarTag) async -> Bool

    init(
        referenceDay: DayKey,
        initialDateRange: CalendarDateRange? = nil,
        tags: [CalendarTag] = [],
        calendar: Calendar = .current,
        isSaving: Bool = false,
        isTagMutating: Bool = false,
        failureMessage: String? = nil,
        tagMutationFailureMessage: String? = nil,
        onSave: @escaping (CalendarEventCreationSubmitInput) async -> Bool = { _ in true },
        onResetTagMutation: @escaping () -> Void = {},
        onCreateCustomTag: @escaping (CustomTagInput) async -> Bool = { _ in false },
        onUpdateCustomTag: @escaping (CalendarTag, CustomTagInput) async -> Bool = { _, _ in false },
        onDeleteCustomTag: @escaping (CalendarTag) async -> Bool = { _ in false }
    ) {
        let baseDraft = CalendarEventCreationDraft(
            referenceDay: referenceDay,
            initialDateRange: initialDateRange,
            tags: tags,
            calendar: calendar
        )
        let entryMode = CalendarEventCreationEntryMode(initialDateRange: initialDateRange)

        self.baseDraft = baseDraft
        self.parser = LocalEventTextParser(calendar: calendar)
        self.calendar = calendar
        self.entryMode = entryMode
        _detailedDraft = State(initialValue: entryMode == .detailed ? baseDraft : nil)
        _selectedDetent = State(initialValue: entryMode == .detailed ? .large : .medium)
        self.tags = tags
        self.isSaving = isSaving
        self.isTagMutating = isTagMutating
        self.failureMessage = failureMessage
        self.tagMutationFailureMessage = tagMutationFailureMessage
        self.onSave = onSave
        self.onResetTagMutation = onResetTagMutation
        self.onCreateCustomTag = onCreateCustomTag
        self.onUpdateCustomTag = onUpdateCustomTag
        self.onDeleteCustomTag = onDeleteCustomTag
    }

    var body: some View {
        Group {
            if let detailedDraft {
                CalendarEventCreationView(
                    initialDraft: detailedDraft,
                    tags: tags,
                    isSaving: isSaving,
                    isTagMutating: isTagMutating,
                    failureMessage: failureMessage,
                    tagMutationFailureMessage: tagMutationFailureMessage,
                    onBack: entryMode == .quick ? closeDetailedInput : nil,
                    onSave: onSave,
                    onResetTagMutation: onResetTagMutation,
                    onCreateCustomTag: onCreateCustomTag,
                    onUpdateCustomTag: onUpdateCustomTag,
                    onDeleteCustomTag: onDeleteCustomTag
                )
            } else {
                CalendarEventQuickCreationView(
                    text: $text,
                    draft: parsedDraft,
                    isSaving: isSaving,
                    failureMessage: failureMessage,
                    onShowDetailedInput: showDetailedInput,
                    onSave: saveQuickDraft
                )
            }
        }
        .presentationDetents(availableDetents, selection: $selectedDetent)
        .presentationDragIndicator(.visible)
    }

    private var parsedDraft: CalendarEventCreationDraft? {
        guard let parseResult = parser.parse(
            text,
            referenceDate: baseDraft.eventInput.startAt
        ), !parseResult.title.isEmpty else {
            return nil
        }

        return baseDraft.applying(parseResult, calendar: calendar)
    }

    private func showDetailedInput() {
        detailedDraft = parsedDraft ?? baseDraft.replacingTitle(with: text)
        selectedDetent = .large
    }

    private func closeDetailedInput() {
        detailedDraft = nil
        selectedDetent = .medium
    }

    private func saveQuickDraft() {
        guard let parsedDraft, parsedDraft.canSave else {
            return
        }

        Task {
            let didSave = await onSave(parsedDraft.submitInput)

            if didSave {
                dismiss()
            }
        }
    }

    private var availableDetents: Set<PresentationDetent> {
        entryMode == .detailed ? [.large] : [.medium, .large]
    }
}

enum CalendarEventCreationEntryMode: Equatable {
    case quick
    case detailed

    init(initialDateRange: CalendarDateRange?) {
        self = initialDateRange == nil ? .quick : .detailed
    }
}

#Preview {
    CalendarEventCreationFlowView(referenceDay: DayKey(date: Date()))
}
