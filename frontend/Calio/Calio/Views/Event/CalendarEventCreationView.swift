//
//  CalendarEventCreationView.swift
//  Calio
//
//  Created by Codex on 6/19/26.
//

import SwiftUI

struct CalendarEventCreationView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var eventInput: EventInput
    @State private var recurrenceInput: RecurrenceInput
    
    let tags: [CalendarTag]
    let isSaving: Bool
    let isTagMutating: Bool
    let failureMessage: String?
    let tagMutationFailureMessage: String?
    let onBack: (() -> Void)?
    let onSave: (CalendarEventCreationSubmitInput) async -> Bool
    let onResetTagMutation: () -> Void
    let onCreateCustomTag: (CustomTagInput) async -> Bool
    let onUpdateCustomTag: (CalendarTag, CustomTagInput) async -> Bool
    let onDeleteCustomTag: (CalendarTag) async -> Bool
    
    init(
        referenceDay: DayKey = DayKey(date: Date()),
        initialDateRange: CalendarDateRange? = nil,
        initialDraft: CalendarEventCreationDraft? = nil,
        tags: [CalendarTag] = [],
        calendar: Calendar = .current,
        isSaving: Bool = false,
        isTagMutating: Bool = false,
        failureMessage: String? = nil,
        tagMutationFailureMessage: String? = nil,
        onBack: (() -> Void)? = nil,
        onSave: @escaping (CalendarEventCreationSubmitInput) async -> Bool = { _ in true },
        onResetTagMutation: @escaping () -> Void = {},
        onCreateCustomTag: @escaping (CustomTagInput) async -> Bool = { _ in false },
        onUpdateCustomTag: @escaping (CalendarTag, CustomTagInput) async -> Bool = { _, _ in false },
        onDeleteCustomTag: @escaping (CalendarTag) async -> Bool = { _ in false }
    ) {
        let draft = initialDraft ?? CalendarEventCreationDraft(
            referenceDay: referenceDay,
            initialDateRange: initialDateRange,
            tags: tags,
            calendar: calendar
        )
        
        _eventInput = State(initialValue: draft.eventInput)
        _recurrenceInput = State(initialValue: draft.recurrenceInput)
        self.tags = tags
        self.isSaving = isSaving
        self.isTagMutating = isTagMutating
        self.failureMessage = failureMessage
        self.tagMutationFailureMessage = tagMutationFailureMessage
        self.onBack = onBack
        self.onSave = onSave
        self.onResetTagMutation = onResetTagMutation
        self.onCreateCustomTag = onCreateCustomTag
        self.onUpdateCustomTag = onUpdateCustomTag
        self.onDeleteCustomTag = onDeleteCustomTag
    }
    
    var body: some View {
        NavigationStack {
            Form {
                failureSection
                CalendarEventFormView(
                    eventInput: $eventInput,
                    recurrenceInput: $recurrenceInput,
                    tags: tags,
                    isTagMutating: isTagMutating,
                    tagMutationFailureMessage: tagMutationFailureMessage,
                    onRecurrenceEnabled: resetRecurrenceFieldsFromSingleEventTime,
                    onResetTagMutation: onResetTagMutation,
                    onCreateCustomTag: onCreateCustomTag,
                    onUpdateCustomTag: onUpdateCustomTag,
                    onDeleteCustomTag: onDeleteCustomTag
                )
            }
            .scrollContentBackground(.hidden)
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("새 일정")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        if let onBack {
                            onBack()
                        } else {
                            dismiss()
                        }
                    } label: {
                        if onBack != nil {
                            Label("빠른 입력", systemImage: "chevron.left")
                        } else {
                            Text("취소")
                        }
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
        currentDraft.canSave
    }
    
    private func save() {
        Task {
            let didSave = await onSave(currentDraft.submitInput)

            if didSave {
                dismiss()
            }
        }
    }

    private var currentDraft: CalendarEventCreationDraft {
        CalendarEventCreationDraft(
            eventInput: eventInput,
            recurrenceInput: recurrenceInput
        )
    }

    nonisolated static func defaultTimeRange(
        referenceDay: DayKey,
        calendar: Calendar
    ) -> (startAt: Date, endAt: Date) {
        CalendarEventCreationDraft.defaultTimeRange(
            referenceDay: referenceDay,
            calendar: calendar
        )
    }

    private func resetRecurrenceFieldsFromSingleEventTime() {
        recurrenceInput.startDate = eventInput.startAt
        recurrenceInput.endDate = eventInput.endAt
        recurrenceInput.startTime = eventInput.startAt
        recurrenceInput.endTime = eventInput.endAt
        recurrenceInput.frequency = .daily
    }
}

#Preview {
    CalendarEventCreationView(referenceDay: DayKey(date: Date()))
}
