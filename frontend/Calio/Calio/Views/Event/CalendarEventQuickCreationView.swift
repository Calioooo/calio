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
            .background(Color(uiColor: .systemGroupedBackground))
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
                    .disabled(draft?.canSave != true || isSaving)
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
                        .foregroundStyle(.red)
                    Text(failureMessage)
                        .font(.subheadline)
                        .foregroundStyle(.primary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.vertical, 2)
                .accessibilityIdentifier("event_quick_creation_failure_message")
            }
        }
    }

    private var inputSection: some View {
        Section {
            TextField(
                "예: 내일 오후 3시 팀 회의",
                text: $text,
                axis: .vertical
            )
            .font(.system(size: 20, weight: .semibold))
            .lineLimit(1...3)
            .submitLabel(.done)
            .focused($isInputFocused)
            .accessibilityIdentifier("event_quick_creation_input")

            Button(action: onShowDetailedInput) {
                Label("상세 입력", systemImage: "slider.horizontal.3")
                    .font(.subheadline.weight(.medium))
            }
            .accessibilityIdentifier("event_quick_creation_detail_button")
        }
    }

    @ViewBuilder
    private var previewSections: some View {
        if let draft {
            Section {
                HStack(alignment: .top, spacing: 10) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(Color(hex: draft.eventInput.tag?.colorCode ?? CalendarTag.fallback.colorCode))
                        .frame(width: 6, height: 34)

                    Text(draft.eventInput.title)
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.primary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.vertical, 2)
            }

            if draft.eventInput.isAllDay {
                Section("기간") {
                    Label(dateOnlyRangeText(draft), systemImage: "calendar")
                }
            } else {
                Section("시간") {
                    Label(
                        CalendarEventDisplayText.dateRange(
                            startAt: draft.eventInput.startAt,
                            endAt: draft.eventInput.endAt
                        ),
                        systemImage: "calendar"
                    )

                    Label(
                        CalendarEventDisplayText.timeRange(
                            startAt: draft.eventInput.startAt,
                            endAt: draft.eventInput.endAt
                        ),
                        systemImage: "clock"
                    )
                }
            }

            if draft.recurrenceInput.isEnabled {
                Section("반복") {
                    Label(
                        draft.recurrenceInput.frequency.koreanLabel,
                        systemImage: "repeat"
                    )
                }
            }
        }
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
