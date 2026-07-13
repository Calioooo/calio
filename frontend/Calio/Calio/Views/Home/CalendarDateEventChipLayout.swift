//
//  CalendarDateEventChipLayout.swift
//  Calio
//
//  Created by Codex on 7/7/26.
//

import SwiftUI
import UIKit

struct CalendarDateEventChipLayout {
    let visibleChips: [CalendarDateEventChip]
    let hiddenChipCount: Int
}

struct CalendarDateEventChipLayoutBuilder {
    let maxVisibleRowCount: Int
    let horizontalPadding: CGFloat
    let spacing: CGFloat
    let font: UIFont

    init(
        maxVisibleRowCount: Int = 3,
        horizontalPadding: CGFloat = 20,
        spacing: CGFloat = 8,
        font: UIFont = .systemFont(ofSize: 13, weight: .medium)
    ) {
        self.maxVisibleRowCount = maxVisibleRowCount
        self.horizontalPadding = horizontalPadding
        self.spacing = spacing
        self.font = font
    }

    func make(
        chips: [CalendarDateEventChip],
        maxWidth: CGFloat
    ) -> CalendarDateEventChipLayout {
        guard maxWidth > 0 else {
            return CalendarDateEventChipLayout(
                visibleChips: [],
                hiddenChipCount: chips.count
            )
        }

        var visibleChips: [CalendarDateEventChip] = []

        for chip in chips {
            let candidateChips = visibleChips + [chip]
            let hiddenChipCount = chips.count - candidateChips.count

            guard chipsFit(
                visibleChips: candidateChips,
                hiddenChipCount: hiddenChipCount,
                maxWidth: maxWidth
            ) else {
                break
            }

            visibleChips = candidateChips
        }

        return CalendarDateEventChipLayout(
            visibleChips: visibleChips,
            hiddenChipCount: chips.count - visibleChips.count
        )
    }

    private func chipsFit(
        visibleChips: [CalendarDateEventChip],
        hiddenChipCount: Int,
        maxWidth: CGFloat
    ) -> Bool {
        let chipWidths = visibleChips.map { chipWidth(text: $0.title, maxWidth: maxWidth) }
        let hiddenWidth = hiddenChipCount > 0
            ? [chipWidth(text: "+\(hiddenChipCount) more", maxWidth: maxWidth)]
            : []

        return rowCount(for: chipWidths + hiddenWidth, maxWidth: maxWidth) <= maxVisibleRowCount
    }

    private func chipWidth(text: String, maxWidth: CGFloat) -> CGFloat {
        let textWidth = (text as NSString).size(withAttributes: [.font: font]).width

        return min(textWidth + horizontalPadding, maxWidth)
    }

    private func rowCount(for widths: [CGFloat], maxWidth: CGFloat) -> Int {
        widths.reduce(into: CalendarDateEventChipRows()) { rows, width in
            rows.append(width: width, maxWidth: maxWidth, spacing: spacing)
        }
        .count
    }
}

struct CalendarDateEventPopoverEdgeResolver {
    let lowerScreenThreshold: CGFloat

    init(lowerScreenThreshold: CGFloat = 0.62) {
        self.lowerScreenThreshold = lowerScreenThreshold
    }

    func arrowEdge(
        for frame: CGRect?,
        screenHeight: CGFloat
    ) -> Edge {
        guard let frame else {
            return .top
        }

        let isLowerScreenChip = frame.midY > screenHeight * lowerScreenThreshold

        return isLowerScreenChip ? .bottom : .top
    }
}

private struct CalendarDateEventChipRows {
    private(set) var count = 0
    private var currentRowWidth: CGFloat = 0

    mutating func append(
        width: CGFloat,
        maxWidth: CGFloat,
        spacing: CGFloat
    ) {
        guard count > 0 else {
            count = 1
            currentRowWidth = width
            return
        }

        let nextRowWidth = currentRowWidth + spacing + width
        guard nextRowWidth <= maxWidth else {
            count += 1
            currentRowWidth = width
            return
        }

        currentRowWidth = nextRowWidth
    }
}
