//
//  CalendarYearMonthTitleView.swift
//  Calio
//
//  Created by Codex on 6/18/26.
//

import SwiftUI

struct CalendarYearMonthTitleView: View {
    let focusedDay: DayKey
    
    var body: some View {
        Text(title)
            .font(.system(size: 24, weight: .semibold))
            .foregroundStyle(.primary)
    }
    
    private var title: String {
        "\(focusedDay.year)년 \(focusedDay.month)월"
    }
}

#Preview {
    CalendarYearMonthTitleView(focusedDay: DayKey(date: Date()))
}
