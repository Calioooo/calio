//
//  CalendarEventSummaryPopoverView.swift
//  Calio
//
//  Created by Codex on 6/28/26.
//

import SwiftUI

struct CalendarEventSummaryPopoverView: View {
    let event: Event
    let onShowDetail: ((Event) -> Void)?

    init(
        event: Event,
        onShowDetail: ((Event) -> Void)? = nil
    ) {
        self.event = event
        self.onShowDetail = onShowDetail
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            summaryHeader

            if hasDescription {
                Text(event.description)
                    .font(.system(size: 14, weight: .regular))
                    .foregroundStyle(.primary)
                    .lineLimit(4)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if onShowDetail != nil {
                Divider()

                Button {
                    onShowDetail?(event)
                } label: {
                    Text("자세히 보기")
                        .font(.system(size: 14, weight: .semibold))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
                .foregroundStyle(Color.accentColor)
                .accessibilityIdentifier("event_summary_show_detail_button")
            }
        }
        .padding(14)
        .frame(width: 260, alignment: .leading)
    }

    private var summaryHeader: some View {
        HStack(alignment: .top, spacing: 8) {
            RoundedRectangle(cornerRadius: 3)
                .fill(Color(hex: event.tag.colorCode))
                .frame(width: 6, height: 32)

            VStack(alignment: .leading, spacing: 4) {
                Text(event.title)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(2)

                Text(eventTimeText)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var hasDescription: Bool {
        !event.description.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var eventTimeText: String {
        CalendarEventDisplayText.compactDateTimeRange(
            startAt: event.startAt,
            endAt: event.endAt
        )
    }
}
