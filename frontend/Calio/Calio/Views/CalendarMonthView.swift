//
//  CalendarMonthView.swift
//  Calio
//
//  Created by Codex on 6/8/26.
//

import SwiftUI

struct CalendarMonthView: View {
    let items: [CalendarDateCellItem]

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text(monthTitle)
                .font(.system(size: 24, weight: .semibold))
                .foregroundStyle(.primary)

            HStack(spacing: 8) {
                ForEach(CalendarWeekday.allCases) { weekday in
                    Text(weekday.shortKoreanText)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity)
                }
            }

            placeholder
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 18)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .accessibilityIdentifier("calendar_header_month")
    }

    private var monthTitle: String {
        guard let monthText = items.first?.monthText else {
            return "월간 캘린더"
        }

        return "\(monthText)월"
    }

    private var placeholder: some View {
        Text("월간 캘린더 영역")
            .font(.system(size: 16, weight: .medium))
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, minHeight: 110)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color.secondary.opacity(0.25), style: StrokeStyle(lineWidth: 1, dash: [6]))
            )
    }
}

#Preview {
    CalendarMonthView(items: [])
}
