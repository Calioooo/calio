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
                    .foregroundStyle(.calioTextSecondary)
                    .lineLimit(4)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("event_summary_description")
            }

            if onShowDetail != nil {
                Divider()
                    .overlay(Color.calioDivider)

                Button {
                    onShowDetail?(event)
                } label: {
                    HStack(spacing: 8) {
                        Text("자세히 보기")
                            .font(.system(size: 14, weight: .semibold))
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.caption.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
                .foregroundStyle(.calioBrand)
                .accessibilityLabel("\(event.title) 상세 보기")
                .accessibilityIdentifier("event_summary_show_detail_button")
            }
        }
        .padding(14)
        .frame(width: 260, alignment: .leading)
        .background(Color.calioSurface)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("event_summary_panel")
    }

    private var summaryHeader: some View {
        HStack(alignment: .top, spacing: 8) {
            RoundedRectangle(cornerRadius: 3)
                .fill(Color(hex: event.tag.colorCode))
                .frame(width: 6, height: 32)

            VStack(alignment: .leading, spacing: 4) {
                Text(event.title)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.calioTextPrimary)
                    .lineLimit(2)

                Text(eventTimeText)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.calioTextSecondary)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(event.title), \(eventTimeText)")
        .accessibilityIdentifier("event_summary_header")
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
