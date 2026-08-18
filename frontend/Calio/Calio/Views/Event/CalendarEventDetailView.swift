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
    let tags: [CalendarTag]
    let isMutating: Bool
    let isTagMutating: Bool
    let mutationFailureMessage: String?
    let tagMutationFailureMessage: String?
    let onFetchRecurrenceEvent: (Int64) async -> RecurrenceEventDetails?
    let onUpdateSingleEvent: (Event, EventUpdateInput) async -> Bool
    let onUpdateRecurrenceOccurrence: (Event, EventUpdateInput) async -> Bool
    let onUpdateRecurrenceSeries: (Int64, RecurrenceEventSeriesEditInput) async -> Bool
    let onDeleteSingleEvent: (Event) async -> Bool
    let onDeleteRecurrenceOccurrence: (Event) async -> Bool
    let onDeleteRecurrenceSeries: (Event) async -> Bool
    let onResetTagMutation: () -> Void
    let onCreateCustomTag: (CustomTagInput) async -> Bool
    let onUpdateCustomTag: (CalendarTag, CustomTagInput) async -> Bool
    let onDeleteCustomTag: (CalendarTag) async -> Bool

    @State private var formMode: CalendarEventFormMode?
    @State private var isFetchingRecurrenceEvent = false
    @State private var isShowingSingleDeleteConfirmation = false
    @State private var isShowingRecurrenceEditScope = false
    @State private var isShowingRecurrenceDeleteScope = false
    @State private var editInput: EventInput
    @State private var recurrenceInput: RecurrenceInput
    @State private var seriesTimeZone: String?
    @State private var seriesMutationMessage: String?
    @State private var recurrenceDetails: RecurrenceEventDetails?

    init(
        event: Event,
        tags: [CalendarTag] = [],
        isMutating: Bool = false,
        isTagMutating: Bool = false,
        mutationFailureMessage: String? = nil,
        tagMutationFailureMessage: String? = nil,
        onFetchRecurrenceEvent: @escaping (Int64) async -> RecurrenceEventDetails? = { _ in nil },
        onUpdateSingleEvent: @escaping (Event, EventUpdateInput) async -> Bool = { _, _ in true },
        onUpdateRecurrenceOccurrence: @escaping (Event, EventUpdateInput) async -> Bool = { _, _ in true },
        onUpdateRecurrenceSeries: @escaping (Int64, RecurrenceEventSeriesEditInput) async -> Bool = { _, _ in true },
        onDeleteSingleEvent: @escaping (Event) async -> Bool = { _ in true },
        onDeleteRecurrenceOccurrence: @escaping (Event) async -> Bool = { _ in true },
        onDeleteRecurrenceSeries: @escaping (Event) async -> Bool = { _ in true },
        onResetTagMutation: @escaping () -> Void = {},
        onCreateCustomTag: @escaping (CustomTagInput) async -> Bool = { _ in false },
        onUpdateCustomTag: @escaping (CalendarTag, CustomTagInput) async -> Bool = { _, _ in false },
        onDeleteCustomTag: @escaping (CalendarTag) async -> Bool = { _ in false }
    ) {
        self.event = event
        self.tags = tags
        self.isMutating = isMutating
        self.isTagMutating = isTagMutating
        self.mutationFailureMessage = mutationFailureMessage
        self.tagMutationFailureMessage = tagMutationFailureMessage
        self.onFetchRecurrenceEvent = onFetchRecurrenceEvent
        self.onUpdateSingleEvent = onUpdateSingleEvent
        self.onUpdateRecurrenceOccurrence = onUpdateRecurrenceOccurrence
        self.onUpdateRecurrenceSeries = onUpdateRecurrenceSeries
        self.onDeleteSingleEvent = onDeleteSingleEvent
        self.onDeleteRecurrenceOccurrence = onDeleteRecurrenceOccurrence
        self.onDeleteRecurrenceSeries = onDeleteRecurrenceSeries
        self.onResetTagMutation = onResetTagMutation
        self.onCreateCustomTag = onCreateCustomTag
        self.onUpdateCustomTag = onUpdateCustomTag
        self.onDeleteCustomTag = onDeleteCustomTag
        _editInput = State(
            initialValue: EventInput(
                title: event.title,
                startAt: event.startAt,
                endAt: event.endAt,
                isAllDay: event.isAllDay,
                description: event.description,
                tag: event.tag
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
        _seriesTimeZone = State(initialValue: event.timeZone)
    }

    var body: some View {
        NavigationStack {
            content
            .navigationTitle(isEditing ? "일정 수정" : "일정 상세")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                toolbarContent
            }
            .task(id: event.recurrenceId) {
                await loadRecurrenceDetails()
            }
            .confirmationDialog(
                "반복 일정 수정",
                isPresented: $isShowingRecurrenceEditScope,
                titleVisibility: .visible
            ) {
                Button("이 일정만 수정") {
                    startEditingRecurrenceOccurrence()
                }
                if canUpdateSeries {
                    Button("전체 반복 일정 수정") {
                        startEditingRecurrenceSeries()
                    }
                }
                Button("취소", role: .cancel) {}
            } message: {
                Text(recurrenceEditScopeGuidance)
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
                if canUpdateSeries {
                    Button("전체 반복 일정 삭제", role: .destructive) {
                        deleteRecurrenceSeries()
                    }
                }
                Button("취소", role: .cancel) {}
            } message: {
                Text(recurrenceDeleteScopeGuidance)
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
        .scrollContentBackground(.hidden)
        .background(Color.calioBackground)
        .tint(.calioBrand)
        .environment(\.locale, Locale(identifier: "ko_KR"))
    }

    private var editForm: some View {
        Form {
            mutationFailureSection
            CalendarEventFormView(
                eventInput: $editInput,
                recurrenceInput: recurrenceInputForEditForm,
                tags: tags,
                isTagMutating: isTagMutating,
                tagMutationFailureMessage: tagMutationFailureMessage,
                mode: formMode ?? .editSingleEvent,
                onRecurrenceEnabled: {},
                onResetTagMutation: onResetTagMutation,
                onCreateCustomTag: onCreateCustomTag,
                onUpdateCustomTag: onUpdateCustomTag,
                onDeleteCustomTag: onDeleteCustomTag
            )
        }
        .scrollContentBackground(.hidden)
        .background(Color.calioBackground)
        .tint(.calioBrand)
        .environment(\.locale, Locale(identifier: "ko_KR"))
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
                .fontWeight(.semibold)
                .disabled(!canSaveEdit || isEventActionInProgress)
                .accessibilityIdentifier("event_detail_save_button")
            }
        } else {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if canUpdateRecurringEvent {
                    Button("수정") {
                        fetchRecurrenceEventForAction(.edit)
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
                        fetchRecurrenceEventForAction(.delete)
                    }
                    .disabled(isEventActionInProgress)
                }
            }
        }
    }

    @ViewBuilder
    private var mutationFailureSection: some View {
        if let mutationFailureMessage = mutationFailureMessage ?? seriesMutationMessage {
            Section {
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "exclamationmark.circle.fill")
                        .foregroundStyle(Color.calendarHoliday)
                    Text(mutationFailureMessage)
                        .font(.subheadline)
                        .foregroundStyle(.calioTextPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.vertical, 2)
                .listRowBackground(Color.calioSelection)
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
                        .foregroundStyle(.calioTextSecondary)
                }
                .padding(.vertical, 2)
            }
        }
    }

    private var titleSection: some View {
        Section {
            HStack(alignment: .top, spacing: 10) {
                RoundedRectangle(cornerRadius: 3)
                    .fill(Color(hex: event.tag.colorCode))
                    .frame(width: 6, height: 34)

                VStack(alignment: .leading, spacing: 6) {
                    Text(event.title)
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.calioTextPrimary)
                        .fixedSize(horizontal: false, vertical: true)

                    Text(event.tag.title)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.calioTextSecondary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Capsule().fill(Color.calioSelection))
                }
            }
            .padding(.vertical, 2)
            .accessibilityIdentifier("event_detail_title")
        } header: {
            Text("일정")
                .foregroundStyle(.calioTextSecondary)
        }
    }

    @ViewBuilder
    private var timeSection: some View {
        Section("시간") {
            Label(
                CalendarEventDisplayText.dateRange(
                    startAt: event.startAt,
                    endAt: event.isAllDay ? inclusiveAllDayEndAt : event.endAt
                ),
                systemImage: "calendar"
            )

            if event.isAllDay {
                Label("하루 종일", systemImage: "sun.max")
            } else {
                Label(
                    CalendarEventDisplayText.timeRange(
                        startAt: event.startAt,
                        endAt: event.endAt
                    ),
                    systemImage: "clock"
                )
            }
        }
        .foregroundStyle(.calioTextPrimary)
    }

    private var inclusiveAllDayEndAt: Date {
        Calendar.current.date(byAdding: .day, value: -1, to: event.endAt) ?? event.endAt
    }

    private var statusSection: some View {
        Section("상태") {
            Label(importantStatusText, systemImage: importantStatusIconName)
            Label(recurrenceStatusText, systemImage: "repeat")
        }
        .foregroundStyle(.calioTextPrimary)
    }

    @ViewBuilder
    private var recurrenceDetailSection: some View {
        if isRepeatedEvent {
            Section("반복 정보") {
                LabeledContent("종료 조건", value: recurrenceEndConditionText)
                LabeledContent("반복 주기", value: recurrenceFrequencyText)
            }
            .foregroundStyle(.calioTextPrimary)
        }
    }

    @ViewBuilder
    private var descriptionSection: some View {
        if hasDescription {
            Section("설명") {
                Text(event.description)
                    .foregroundStyle(.calioTextPrimary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    private var hasDescription: Bool {
        !event.description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var recurrenceEndConditionText: String {
        guard let details = recurrenceDetails else { return "제공된 정보 없음" }
        guard let endDate = details.recurrenceEndDate else { return "종료일 없음" }
        return CalendarEventDisplayText.dateRange(startAt: endDate, endAt: endDate)
    }

    private var recurrenceFrequencyText: String {
        guard let details = recurrenceDetails else { return "제공된 정보 없음" }
        switch details.recurrenceFrequency {
        case .daily: return "매일"
        case .weekly: return "매주"
        case .monthly: return "매월"
        case .yearly: return "매년"
        }
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
        CalendarEventFormRules.canSave(
            title: editInput.title,
            startAt: editInput.startAt,
            endAt: editInput.endAt,
            isRecurrenceEnabled: formMode == .editRecurrenceSeries,
            recurrenceStartDate: recurrenceInput.startDate,
            recurrenceEndDate: recurrenceInput.endDate,
            recurrenceStartTime: recurrenceInput.startTime,
            recurrenceEndTime: recurrenceInput.endTime,
            isAllDay: editInput.isAllDay
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

    private var canUpdateSeries: Bool {
        recurrenceDetails?.canUpdateSeries == true && recurrenceDetails?.isRuleEditable == true
    }

    private var recurrenceEditScopeGuidance: String {
        Self.recurrenceEditScopeGuidance(canUpdateSeries: canUpdateSeries)
    }

    private var recurrenceDeleteScopeGuidance: String {
        Self.recurrenceDeleteScopeGuidance(canUpdateSeries: canUpdateSeries)
    }

    private enum RecurrenceAction {
        case edit
        case delete
    }

    private func fetchRecurrenceEventForAction(_ action: RecurrenceAction) {
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

            recurrenceDetails = details
            switch action {
            case .edit:
                isShowingRecurrenceEditScope = true
            case .delete:
                isShowingRecurrenceDeleteScope = true
            }
        }
    }

    private func loadRecurrenceDetails() async {
        guard isRepeatedEvent,
              let recurrenceId = event.recurrenceId,
              recurrenceDetails == nil else {
            return
        }

        recurrenceDetails = await onFetchRecurrenceEvent(recurrenceId)
    }

    private func startEditingRecurrenceSeries() {
        guard let details = recurrenceDetails,
              details.canUpdateSeries,
              details.isRuleEditable else {
            seriesMutationMessage = "이 반복 일정은 전체 수정할 수 없습니다."
            return
        }

        editInput.title = details.title
        editInput.description = details.description
        editInput.startAt = details.recurrenceStartDate
        editInput.endAt = details.recurrenceEndDate ?? details.recurrenceStartDate
        editInput.isAllDay = details.isAllDay
        seriesTimeZone = details.timeZone
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

    private func resetEditInputFromEvent() {
        editInput.title = event.title
        editInput.description = event.description
        editInput.startAt = event.startAt
        editInput.endAt = event.endAt
        editInput.isAllDay = event.isAllDay
        editInput.timeZone = event.timeZone
        editInput.tag = event.tag
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
            endAt: editInput.endAt,
            isAllDay: editInput.isAllDay,
            timeZone: editInput.timeZone,
            tagId: editInput.tag?.id
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

            if didUpdate || shouldDismissStaleOccurrence {
                dismiss()
            }
        }
    }

    private func updateRecurrenceSeries() {
        guard let recurrenceId = event.recurrenceId else {
            return
        }

        Task {
            let didUpdate = await onUpdateRecurrenceSeries(
                recurrenceId,
                RecurrenceEventSeriesEditInput(
                    title: editInput.title.trimmingCharacters(in: .whitespacesAndNewlines),
                    description: editInput.description,
                    recurrenceStartDate: recurrenceInput.startDate,
                    recurrenceEndDate: recurrenceInput.endDate,
                    recurrenceStartTime: recurrenceInput.startTime,
                    recurrenceEndTime: recurrenceInput.endTime,
                    recurrenceFrequency: recurrenceInput.frequency,
                    isAllDay: editInput.isAllDay,
                    timeZone: seriesTimeZone,
                    tagId: editInput.tag?.id
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

            if didDelete || shouldDismissStaleOccurrence {
                dismiss()
            }
        }
    }

    private var shouldDismissStaleOccurrence: Bool {
        mutationFailureMessage == "반복 일정을 찾을 수 없습니다."
            || mutationFailureMessage == "반복 일정 항목을 찾을 수 없습니다."
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

    nonisolated static func recurrenceEditScopeGuidance(canUpdateSeries: Bool) -> String {
        canUpdateSeries
            ? "이 일정만 수정은 선택한 날짜에, 전체 반복 일정 수정은 시리즈 전체에 적용됩니다."
            : "이 반복 일정은 전체 수정이 불가능해 선택한 날짜만 수정할 수 있습니다."
    }

    nonisolated static func recurrenceDeleteScopeGuidance(canUpdateSeries: Bool) -> String {
        canUpdateSeries
            ? "이 일정만 삭제는 선택한 날짜에, 전체 반복 일정 삭제는 시리즈 전체에 적용됩니다."
            : "이 반복 일정은 전체 삭제가 불가능해 선택한 날짜만 삭제할 수 있습니다."
    }
}

enum CalendarEventDisplayText {
    static func dateRange(startAt: Date, endAt: Date) -> String {
        let startText = dateText(for: startAt, includesYear: true)
        let endText = dateText(for: endAt, includesYear: true)

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

    private static func dateText(for date: Date, includesYear: Bool) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.dateFormat = includesYear ? "yyyy년 M월 d일" : "M월 d일"

        return formatter.string(from: date)
    }
}
