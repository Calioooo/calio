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
    let onFetchRecurrenceEvent: (Int64) async -> RecurrenceEventDetails?
    let onUpdateSingleEvent: (Event, EventUpdateInput) async -> Bool
    let onUpdateRecurrenceOccurrence: (Event, EventUpdateInput) async -> Bool
    let onUpdateRecurrenceSeries: (Int64, RecurrenceEventSeriesEditInput) async -> Bool
    let onDeleteSingleEvent: (Event) async -> Bool
    let onDeleteRecurrenceOccurrence: (Event) async -> Bool
    let onDeleteRecurrenceSeries: (Event) async -> Bool

    @State private var formMode: CalendarEventFormMode?
    @State private var isFetchingRecurrenceEvent = false
    @State private var isShowingSingleDeleteConfirmation = false
    @State private var isShowingRecurrenceEditScope = false
    @State private var isShowingRecurrenceDeleteScope = false
    @State private var editInput: EventInput
    @State private var recurrenceInput: RecurrenceInput

    init(
        event: Event,
        isMutating: Bool = false,
        mutationFailureMessage: String? = nil,
        onFetchRecurrenceEvent: @escaping (Int64) async -> RecurrenceEventDetails? = { _ in nil },
        onUpdateSingleEvent: @escaping (Event, EventUpdateInput) async -> Bool = { _, _ in true },
        onUpdateRecurrenceOccurrence: @escaping (Event, EventUpdateInput) async -> Bool = { _, _ in true },
        onUpdateRecurrenceSeries: @escaping (Int64, RecurrenceEventSeriesEditInput) async -> Bool = { _, _ in true },
        onDeleteSingleEvent: @escaping (Event) async -> Bool = { _ in true },
        onDeleteRecurrenceOccurrence: @escaping (Event) async -> Bool = { _ in true },
        onDeleteRecurrenceSeries: @escaping (Event) async -> Bool = { _ in true }
    ) {
        self.event = event
        self.isMutating = isMutating
        self.mutationFailureMessage = mutationFailureMessage
        self.onFetchRecurrenceEvent = onFetchRecurrenceEvent
        self.onUpdateSingleEvent = onUpdateSingleEvent
        self.onUpdateRecurrenceOccurrence = onUpdateRecurrenceOccurrence
        self.onUpdateRecurrenceSeries = onUpdateRecurrenceSeries
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
                "반복 일정 수정",
                isPresented: $isShowingRecurrenceEditScope,
                titleVisibility: .visible
            ) {
                Button("이 일정만 수정") {
                    startEditingRecurrenceOccurrence()
                }
                Button("전체 반복 일정 수정") {
                    fetchRecurrenceEventForSeriesEdit()
                }
                Button("취소", role: .cancel) {}
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
            recurrenceFetchSection
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
                recurrenceInput: recurrenceInputForEditForm,
                mode: formMode ?? .editSingleEvent,
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
                    formMode = nil
                }
                .disabled(isEventActionInProgress)
            }

            ToolbarItem(placement: .confirmationAction) {
                Button("저장") {
                    saveEdit()
                }
                .disabled(!canSaveEdit || isEventActionInProgress)
            }
        } else {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if canUpdateRecurringEvent {
                    Button("수정") {
                        isShowingRecurrenceEditScope = true
                    }
                    .disabled(isEventActionInProgress)
                }

                if canUpdateSingleEvent {
                    Button("수정") {
                        startEditingSingleEvent()
                    }
                    .disabled(isEventActionInProgress)
                }

                if canDeleteSingleEvent {
                    Button("삭제", role: .destructive) {
                        isShowingSingleDeleteConfirmation = true
                    }
                    .disabled(isEventActionInProgress)
                }

                if canDeleteRecurringEvent {
                    Button("삭제", role: .destructive) {
                        isShowingRecurrenceDeleteScope = true
                    }
                    .disabled(isEventActionInProgress)
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

    @ViewBuilder
    private var recurrenceFetchSection: some View {
        if isFetchingRecurrenceEvent {
            Section {
                HStack(spacing: 10) {
                    ProgressView()
                    Text("반복 일정 정보를 불러오는 중입니다.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 2)
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

    private var canUpdateRecurringEvent: Bool {
        Self.canUpdateRecurringEvent(event)
    }

    private var canSaveEdit: Bool {
        CalendarEventFormView.canSave(
            title: editInput.title,
            startAt: editInput.startAt,
            endAt: editInput.endAt,
            isRecurrenceEnabled: formMode == .editRecurrenceSeries,
            recurrenceStartDate: recurrenceInput.startDate,
            recurrenceEndDate: recurrenceInput.endDate,
            recurrenceStartTime: recurrenceInput.startTime,
            recurrenceEndTime: recurrenceInput.endTime
        )
    }

    private var isEditing: Bool {
        formMode != nil
    }

    private var isEventActionInProgress: Bool {
        isMutating || isFetchingRecurrenceEvent
    }

    private var recurrenceInputForEditForm: Binding<RecurrenceInput>? {
        formMode == .editRecurrenceSeries ? $recurrenceInput : nil
    }

    private func startEditingSingleEvent() {
        resetEditInputFromEvent()
        recurrenceInput.isEnabled = false
        formMode = .editSingleEvent
    }

    private func startEditingRecurrenceOccurrence() {
        guard event.recurrenceId != nil else {
            return
        }

        resetEditInputFromEvent()
        recurrenceInput.isEnabled = false
        formMode = .editRecurrenceOccurrence
    }

    private func fetchRecurrenceEventForSeriesEdit() {
        guard let recurrenceId = event.recurrenceId else {
            return
        }

        isFetchingRecurrenceEvent = true

        Task {
            let details = await onFetchRecurrenceEvent(recurrenceId)
            isFetchingRecurrenceEvent = false

            guard let details else {
                return
            }

            editInput.title = details.title
            editInput.description = details.description
            editInput.startAt = details.recurrenceStartDate
            editInput.endAt = details.recurrenceEndDate
            recurrenceInput = RecurrenceInput(
                isEnabled: true,
                startDate: details.recurrenceStartDate,
                endDate: details.recurrenceEndDate,
                startTime: details.recurrenceStartTime,
                endTime: details.recurrenceEndTime,
                frequency: details.recurrenceFrequency
            )
            formMode = .editRecurrenceSeries
        }
    }

    private func resetEditInputFromEvent() {
        editInput.title = event.title
        editInput.description = event.description
        editInput.startAt = event.startAt
        editInput.endAt = event.endAt
        editInput.colorCode = event.colorCode
    }

    private func saveEdit() {
        switch formMode {
        case .editSingleEvent:
            updateSingleEvent()
        case .editRecurrenceOccurrence:
            updateRecurrenceOccurrence()
        case .editRecurrenceSeries:
            updateRecurrenceSeries()
        case .create, nil:
            return
        }
    }

    private func makeEventUpdateInput() -> EventUpdateInput {
        EventUpdateInput(
            title: editInput.title.trimmingCharacters(in: .whitespacesAndNewlines),
            description: editInput.description,
            startAt: editInput.startAt,
            endAt: editInput.endAt
        )
    }

    private func updateSingleEvent() {
        let input = makeEventUpdateInput()

        Task {
            let didUpdate = await onUpdateSingleEvent(event, input)

            if didUpdate {
                dismiss()
            }
        }
    }

    private func updateRecurrenceOccurrence() {
        let input = makeEventUpdateInput()

        Task {
            let didUpdate = await onUpdateRecurrenceOccurrence(event, input)

            if didUpdate {
                dismiss()
            }
        }
    }

    private func updateRecurrenceSeries() {
        guard let recurrenceId = event.recurrenceId else {
            return
        }

        let input = EventUpdateInput(
            title: editInput.title.trimmingCharacters(in: .whitespacesAndNewlines),
            description: editInput.description,
            startAt: recurrenceInput.startDate,
            endAt: recurrenceInput.endDate
        )

        Task {
            let didUpdate = await onUpdateRecurrenceSeries(
                recurrenceId,
                RecurrenceEventSeriesEditInput(
                    title: input.title,
                    description: input.description,
                    recurrenceStartDate: recurrenceInput.startDate,
                    recurrenceEndDate: recurrenceInput.endDate,
                    recurrenceStartTime: recurrenceInput.startTime,
                    recurrenceEndTime: recurrenceInput.endTime,
                    recurrenceFrequency: recurrenceInput.frequency
                )
            )

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

    nonisolated static func canUpdateRecurringEvent(_ event: Event) -> Bool {
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
    
    static func compactDateTimeRange(startAt: Date, endAt: Date) -> String {
        guard !Calendar.current.isDate(startAt, inSameDayAs: endAt) else {
            return timeRange(startAt: startAt, endAt: endAt)
        }
        
        let includesYear = !Calendar.current.isDate(startAt, equalTo: endAt, toGranularity: .year)
        let startText = dateTimeText(for: startAt, includesYear: includesYear)
        let endText = dateTimeText(for: endAt, includesYear: includesYear)
        
        return "\(startText) - \(endText)"
    }
    
    private static func dateTimeText(for date: Date, includesYear: Bool) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.dateFormat = includesYear ? "yyyy년 M월 d일 a h:mm" : "M월 d일 a h:mm"
        
        return formatter.string(from: date)
    }
}
