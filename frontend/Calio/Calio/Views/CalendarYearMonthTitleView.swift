//
//  CalendarYearMonthTitleView.swift
//  Calio
//
//  Created by Codex on 6/18/26.
//

import SwiftUI

struct CalendarYearMonthTitleView: View {
    let focusedDay: DayKey
    let onTap: (() -> Void)?

    init(
        focusedDay: DayKey,
        onTap: (() -> Void)? = nil
    ) {
        self.focusedDay = focusedDay
        self.onTap = onTap
    }
    
    var body: some View {
        if let onTap {
            Button(action: onTap) {
                titleText
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("년월 선택")
        } else {
            titleText
        }
    }
    
    private var title: String {
        "\(focusedDay.year)년 \(focusedDay.month)월"
    }

    private var titleText: some View {
        Text(title)
            .font(.system(size: 24, weight: .semibold))
            .foregroundStyle(.primary)
    }
}

struct CalendarYearMonthPickerView: View {
    let focusedDay: DayKey
    let calendar: Calendar
    let onCancel: () -> Void
    let onConfirm: (Int, Int) -> Void

    @State private var selectedYear: Int
    @State private var selectedMonth: Int

    init(
        focusedDay: DayKey,
        calendar: Calendar = .current,
        onCancel: @escaping () -> Void,
        onConfirm: @escaping (Int, Int) -> Void
    ) {
        self.focusedDay = focusedDay
        self.calendar = calendar
        self.onCancel = onCancel
        self.onConfirm = onConfirm
        _selectedYear = State(initialValue: focusedDay.year)
        _selectedMonth = State(initialValue: focusedDay.month)
    }

    var body: some View {
        VStack(spacing: 14) {
            HStack {
                Button("취소", action: onCancel)
                    .buttonStyle(.plain)

                Spacer()

                Button("이동") {
                    onConfirm(selectedYear, selectedMonth)
                }
                .fontWeight(.semibold)
            }
            .padding(.horizontal, 18)
            .padding(.top, 16)

            HStack(spacing: 0) {
                Picker("연도", selection: $selectedYear) {
                    ForEach(years, id: \.self) { year in
                        Text("\(year)년").tag(year)
                    }
                }
                .pickerStyle(.wheel)

                Picker("월", selection: $selectedMonth) {
                    ForEach(1...12, id: \.self) { month in
                        Text("\(month)월").tag(month)
                    }
                }
                .pickerStyle(.wheel)
            }
            .frame(width: 280, height: 190)
            .clipped()
            .padding(.horizontal, 10)
            .padding(.bottom, 12)
        }
        .frame(width: 320)
    }

    private var years: [Int] {
        let currentYear = calendar.component(.year, from: Date())
        return Array((currentYear - 20)...(currentYear + 20))
    }
}

#Preview {
    CalendarYearMonthTitleView(focusedDay: DayKey(date: Date()))
}
