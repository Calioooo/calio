//
//  CalendarEventQuickCreationView.swift
//  Calio
//
//  Created by Codex on 7/14/26.
//

import SwiftUI

struct CalendarEventQuickCreationView: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var text: String
    @FocusState private var isInputFocused: Bool

    let draft: CalendarEventCreationDraft?
    let isSaving: Bool
    let failureMessage: String?
    let onShowDetailedInput: () -> Void
    let onSave: () -> Void

    var body: some View {
        NavigationStack {
            Form {
                failureSection
                inputSection
                previewSections
            }
            .scrollContentBackground(.hidden)
            .background(Color.calioBackground)
            .tint(.calioBrand)
            .navigationTitle("새 일정")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("취소") {
                        dismiss()
                    }
                }

                ToolbarItem(placement: .confirmationAction) {
                    Button("생성") {
                        onSave()
                    }
                    .fontWeight(.semibold)
                    .disabled(draft?.canSave != true || isSaving)
                    .accessibilityLabel(isSaving ? "일정 저장 중" : "일정 생성")
                    .accessibilityValue(saveAccessibilityValue)
                    .accessibilityIdentifier("event_quick_creation_save_button")
                }
            }
        }
        .task {
            isInputFocused = true
        }
    }

    @ViewBuilder
    private var failureSection: some View {
        if let failureMessage {
            Section {
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "exclamationmark.circle.fill")
                        .foregroundStyle(.calioCalendarSunday)

                    VStack(alignment: .leading, spacing: 3) {
                        Text("저장하지 못했어요")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.calioTextPrimary)

                        Text(failureMessage)
                            .font(.subheadline)
                            .foregroundStyle(.calioTextSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .padding(.vertical, 2)
                .listRowBackground(Color.calioSelection)
                .accessibilityElement(children: .combine)
                .accessibilityLabel("일정 저장 실패: \(failureMessage)")
                .accessibilityIdentifier("event_quick_creation_failure_message")
            }
        }
    }

    private var inputSection: some View {
        Section("빠른 입력") {
            TextField(
                "예: 내일 오후 3시 팀 회의",
                text: $text,
                axis: .vertical
            )
            .font(.system(size: 20, weight: .semibold))
            .foregroundStyle(.calioTextPrimary)
            .lineLimit(1...3)
            .submitLabel(.done)
            .focused($isInputFocused)
            .accessibilityIdentifier("event_quick_creation_input")

            Text("날짜와 시간을 함께 입력하면 일정을 미리 확인할 수 있어요.")
                .font(.footnote)
                .foregroundStyle(.calioTextSecondary)

            Button(action: onShowDetailedInput) {
                HStack(spacing: 8) {
                    Label("상세 입력으로 전환", systemImage: "slider.horizontal.3")
                        .font(.subheadline.weight(.medium))

                    Spacer()

                    Image(systemName: "chevron.right")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.calioTextSecondary)
                }
            }
            .accessibilityIdentifier("event_quick_creation_detail_button")
        }
    }

    private var previewSections: some View {
        Section("미리보기") {
            if let draft {
                previewContent(draft)
            } else {
                Label("일정 내용을 입력하면 미리보기가 표시됩니다.", systemImage: "text.cursor")
                    .font(.subheadline)
                    .foregroundStyle(.calioTextSecondary)
                    .accessibilityLabel("일정 미리보기 없음. 일정 내용을 입력하면 미리보기가 표시됩니다.")
                    .accessibilityIdentifier("event_quick_creation_empty_preview")
            }
        }
    }

    @ViewBuilder
    private func previewContent(_ draft: CalendarEventCreationDraft) -> some View {
        HStack(alignment: .top, spacing: 10) {
            RoundedRectangle(cornerRadius: 3)
                .fill(Color(hex: draft.eventInput.tag?.colorCode ?? CalendarTag.fallback.colorCode))
                .frame(width: 6, height: 38)

            VStack(alignment: .leading, spacing: 4) {
                Text(draft.eventInput.title)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.calioTextPrimary)
                    .fixedSize(horizontal: false, vertical: true)

                Text(scheduleSummary(for: draft))
                    .font(.subheadline)
                    .foregroundStyle(.calioTextSecondary)
            }
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("일정 미리보기: \(draft.eventInput.title), \(scheduleSummary(for: draft))")
        .accessibilityIdentifier("event_quick_creation_preview")

        if draft.recurrenceInput.isEnabled {
            Label(draft.recurrenceInput.frequency.koreanLabel, systemImage: "repeat")
                .font(.subheadline)
                .foregroundStyle(.calioTextSecondary)
                .accessibilityIdentifier("event_quick_creation_recurrence_preview")
        }
    }

    private func scheduleSummary(for draft: CalendarEventCreationDraft) -> String {
        if draft.eventInput.isAllDay {
            return dateOnlyRangeText(draft)
        }

        return CalendarEventDisplayText.compactDateTimeRange(
            startAt: draft.eventInput.startAt,
            endAt: draft.eventInput.endAt
        )
    }

    private var saveAccessibilityValue: String {
        if isSaving {
            return "저장 중"
        }

        return draft?.canSave == true ? "저장 가능" : "일정 내용을 더 입력해 주세요"
    }

    private func dateOnlyRangeText(_ draft: CalendarEventCreationDraft) -> String {
        let inclusiveEndAt = Calendar.current.date(
            byAdding: .day,
            value: -1,
            to: draft.eventInput.endAt
        ) ?? draft.eventInput.endAt

        return CalendarEventDisplayText.dateRange(
            startAt: draft.eventInput.startAt,
            endAt: inclusiveEndAt
        )
    }
}
