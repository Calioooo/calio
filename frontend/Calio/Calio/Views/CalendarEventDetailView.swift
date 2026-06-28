//
//  CalendarEventDetailView.swift
//  Calio
//
//  Created by Codex on 6/28/26.
//

import SwiftUI

struct CalendarEventDetailView: View {
    @Environment(\.dismiss) private var dismiss

    let event: Event
    let isMutating: Bool
    let mutationFailureMessage: String?
    let onUpdateSingleEvent: (Event, EventUpdateInput) async -> Bool
    let onDeleteSingleEvent: (Event) async -> Bool
    let onDeleteRecurrenceOccurrence: (Event) async -> Bool
    let onDeleteRecurrenceSeries: (Event) async -> Bool

    @State private var isEditing = false
    @State private var isShowingSingleDeleteConfirmation = false
    @State private var isShowingRecurrenceDeleteScope = false
    @State private var editInput: EventInput
    @State private var recurrenceInput: RecurrenceInput

    init(
        event: Event,
        isMutating: Bool = false,
        mutationFailureMessage: String? = nil,
        onUpdateSingleEvent: @escaping (Event, EventUpdateInput) async -> Bool = { _, _ in true },
        onDeleteSingleEvent: @escaping (Event) async -> Bool = { _ in true },
        onDeleteRecurrenceOccurrence: @escaping (Event) async -> Bool = { _ in true },
        onDeleteRecurrenceSeries: @escaping (Event) async -> Bool = { _ in true }
    ) {
        self.event = event
        self.isMutating = isMutating
        self.mutationFailureMessage = mutationFailureMessage
        self.onUpdateSingleEvent = onUpdateSingleEvent
        self.onDeleteSingleEvent = onDeleteSingleEvent
        self.onDeleteRecurrenceOccurrence = onDeleteRecurrenceOccurrence
        self.onDeleteRecurrenceSeries = onDeleteRecurrenceSeries
        _editInput = State(
            initialValue: EventInput(
                title: event.title,
                startAt: event.startAt,
                endAt: event.endAt,
                description: event.description,
                colorCode: event.colorCode
            )
        )
        _recurrenceInput = State(
            initialValue: RecurrenceInput(
                isEnabled: false,
                startDate: event.startAt,
                endDate: event.startAt,
                startTime: event.startAt,
                endTime: event.endAt,
                frequency: .daily
            )
        )
    }

    var body: some View {
        NavigationStack {
            content
            .navigationTitle(isEditing ? "일정 수정" : "일정 상세")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                toolbarContent
            }
            .confirmationDialog(
                "삭제하시겠습니까?",
                isPresented: $isShowingSingleDeleteConfirmation,
                titleVisibility: .visible
            ) {
                Button("삭제", role: .destructive) {
                    deleteSingleEvent()
                }
                Button("취소", role: .cancel) {}
            }
            .confirmationDialog(
                "반복 일정 삭제",
                isPresented: $isShowingRecurrenceDeleteScope,
                titleVisibility: .visible
            ) {
                Button("이 일정만 삭제", role: .destructive) {
                    deleteRecurrenceOccurrence()
                }
                Button("전체 반복 일정 삭제", role: .destructive) {
                    deleteRecurrenceSeries()
                }
                Button("취소", role: .cancel) {}
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if isEditing {
            editForm
        } else {
            detailList
        }
    }

    private var detailList: some View {
        List {
            mutationFailureSection
            titleSection
            timeSection
            statusSection
            recurrenceDetailSection
            descriptionSection
        }
    }

    private var editForm: some View {
        Form {
            mutationFailureSection
            CalendarEventFormView(
                eventInput: $editInput,
                recurrenceInput: $recurrenceInput,
                mode: .editSingleEvent,
                onRecurrenceEnabled: {}
            )
        }
        .scrollContentBackground(.hidden)
        .background(Color(uiColor: .systemGroupedBackground))
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        if isEditing {
            ToolbarItem(placement: .cancellationAction) {
                Button("취소") {
                    isEditing = false
                }
                .disabled(isMutating)
            }

            ToolbarItem(placement: .confirmationAction) {
                Button("저장") {
                    updateSingleEvent()
                }
                .disabled(!canSaveEdit || isMutating)
            }
        } else {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if canUpdateSingleEvent {
                    Button("수정") {
                        isEditing = true
                    }
                    .disabled(isMutating)
                }

                if canDeleteSingleEvent {
                    Button("삭제", role: .destructive) {
                        isShowingSingleDeleteConfirmation = true
                    }
                    .disabled(isMutating)
                }

                if canDeleteRecurringEvent {
                    Button("삭제", role: .destructive) {
                        isShowingRecurrenceDeleteScope = true
                    }
                    .disabled(isMutating)
                }
            }
        }
    }

    @ViewBuilder
    private var mutationFailureSection: some View {
        if let mutationFailureMessage {
            Section {
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "exclamationmark.circle.fill")
                        .foregroundStyle(.red)
                    Text(mutationFailureMessage)
                        .font(.subheadline)
                        .foregroundStyle(.primary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.vertical, 2)
                .accessibilityIdentifier("event_mutation_failure_message")
            }
        }
    }

    private var titleSection: some View {
        Section {
            HStack(alignment: .top, spacing: 10) {
                RoundedRectangle(cornerRadius: 3)
                    .fill(Color(hex: event.colorCode))
                    .frame(width: 6, height: 34)

                Text(event.title)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.primary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.vertical, 2)
        }
    }

    private var timeSection: some View {
        Section("시간") {
            Label(
                CalendarEventDisplayText.dateRange(
                    startAt: event.startAt,
                    endAt: event.endAt
                ),
                systemImage: "calendar"
            )

            Label(
                CalendarEventDisplayText.timeRange(
                    startAt: event.startAt,
                    endAt: event.endAt
                ),
                systemImage: "clock"
            )
        }
    }

    private var statusSection: some View {
        Section("상태") {
            Label(importantStatusText, systemImage: importantStatusIconName)
            Label(recurrenceStatusText, systemImage: "repeat")
        }
    }

    @ViewBuilder
    private var recurrenceDetailSection: some View {
        if isRepeatedEvent {
            Section("반복 정보") {
                LabeledContent("반복 기간", value: "제공된 정보 없음")
                LabeledContent("반복 주기", value: "제공된 정보 없음")
            }
        }
    }

    @ViewBuilder
    private var descriptionSection: some View {
        if hasDescription {
            Section("설명") {
                Text(event.description)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var hasDescription: Bool {
        !event.description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var importantStatusText: String {
        Self.importantStatusText(for: event)
    }

    private var importantStatusIconName: String {
        event.importantEvent ? "exclamationmark.circle.fill" : "circle"
    }

    private var recurrenceStatusText: String {
        Self.recurrenceStatusText(for: event)
    }

    private var isRepeatedEvent: Bool {
        Self.isRepeatedEvent(event)
    }

    private var canUpdateSingleEvent: Bool {
        Self.canUpdateSingleEvent(event)
    }

    private var canDeleteSingleEvent: Bool {
        Self.canDeleteSingleEvent(event)
    }

    private var canDeleteRecurringEvent: Bool {
        Self.canDeleteRecurringEvent(event)
    }

    private var canSaveEdit: Bool {
        CalendarEventCreationView.canSave(
            title: editInput.title,
            startAt: editInput.startAt,
            endAt: editInput.endAt
        )
    }

    private func updateSingleEvent() {
        let input = EventUpdateInput(
            title: editInput.title.trimmingCharacters(in: .whitespacesAndNewlines),
            description: editInput.description,
            startAt: editInput.startAt,
            endAt: editInput.endAt
        )

        Task {
            let didUpdate = await onUpdateSingleEvent(event, input)

            if didUpdate {
                dismiss()
            }
        }
    }

    private func deleteSingleEvent() {
        Task {
            let didDelete = await onDeleteSingleEvent(event)

            if didDelete {
                dismiss()
            }
        }
    }

    private func deleteRecurrenceOccurrence() {
        Task {
            let didDelete = await onDeleteRecurrenceOccurrence(event)

            if didDelete {
                dismiss()
            }
        }
    }

    private func deleteRecurrenceSeries() {
        Task {
            let didDelete = await onDeleteRecurrenceSeries(event)

            if didDelete {
                dismiss()
            }
        }
    }

    nonisolated static func importantStatusText(for event: Event) -> String {
        event.importantEvent ? "중요 일정" : "일반 일정"
    }

    nonisolated static func recurrenceStatusText(for event: Event) -> String {
        isRepeatedEvent(event) ? "반복 일정" : "반복 없음"
    }

    nonisolated static func isRepeatedEvent(_ event: Event) -> Bool {
        event.isRecurrenceOccurrence || event.recurrenceId != nil
    }

    nonisolated static func canUpdateSingleEvent(_ event: Event) -> Bool {
        !isRepeatedEvent(event)
    }

    nonisolated static func canDeleteSingleEvent(_ event: Event) -> Bool {
        !isRepeatedEvent(event)
    }

    nonisolated static func canDeleteRecurringEvent(_ event: Event) -> Bool {
        isRepeatedEvent(event) && event.recurrenceId != nil
    }
}

enum CalendarEventDisplayText {
    static func dateRange(startAt: Date, endAt: Date) -> String {
        let startText = startAt.formatted(date: .abbreviated, time: .omitted)
        let endText = endAt.formatted(date: .abbreviated, time: .omitted)

        guard !Calendar.current.isDate(startAt, inSameDayAs: endAt) else {
            return startText
        }

        return "\(startText) - \(endText)"
    }

    static func timeRange(startAt: Date, endAt: Date) -> String {
        let startText = startAt.formatted(date: .omitted, time: .shortened)
        let endText = endAt.formatted(date: .omitted, time: .shortened)

        return "\(startText) - \(endText)"
    }
}
