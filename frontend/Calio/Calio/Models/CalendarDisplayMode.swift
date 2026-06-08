//
//  CalendarDisplayMode.swift
//  Calio
//
//  Created by Codex on 6/8/26.
//

import CoreGraphics

enum CalendarDisplayMode: Equatable {
    case week
    case month

    private static let drawerDragThreshold: CGFloat = 40

    func resolved(afterDragTranslationHeight translationHeight: CGFloat) -> CalendarDisplayMode {
        if translationHeight > Self.drawerDragThreshold {
            return .month
        }

        if translationHeight < -Self.drawerDragThreshold {
            return .week
        }

        return self
    }
}
