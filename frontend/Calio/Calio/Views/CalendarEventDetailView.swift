//
//  CalendarEventDetailView.swift
//  Calio
//
//  Created by Codex on 6/28/26.
//

import SwiftUI

struct CalendarEventDetailView: View {
    let event: Event

    var body: some View {
        NavigationStack {
            List {
                titleSection
                timeSection
                statusSection
                descriptionSection
            }
            .navigationTitle("일정 상세")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    EmptyView()
                }
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

    nonisolated static func importantStatusText(for event: Event) -> String {
        event.importantEvent ? "중요 일정" : "일반 일정"
    }

    nonisolated static func recurrenceStatusText(for event: Event) -> String {
        isRepeatedEvent(event) ? "반복 일정" : "반복 없음"
    }

    private nonisolated static func isRepeatedEvent(_ event: Event) -> Bool {
        event.isRecurrenceOccurrence || event.recurrenceId != nil
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
