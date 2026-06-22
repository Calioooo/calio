//
//  CalendarYearMonthTitleView.swift
//  Calio
//
//  Created by Codex on 6/18/26.
//

import SwiftUI

struct CalendarYearMonthTitleView: View {
    let focusedDay: DayKey
    let onSelectedYearMonth: ((Int, Int) -> Void)?

    @State private var isShowingYearPicker = false
    @State private var isShowingMonthPicker = false

    init(
        focusedDay: DayKey,
        onSelectedYearMonth: ((Int, Int) -> Void)? = nil
    ) {
        self.focusedDay = focusedDay
        self.onSelectedYearMonth = onSelectedYearMonth
    }
    
    var body: some View {
        if let onSelectedYearMonth {
            HStack(spacing: 4) {
                titleButton(text: "\(focusedDay.year)년") {
                    isShowingYearPicker = true
                }
                .popover(
                    isPresented: $isShowingYearPicker,
                    attachmentAnchor: .rect(.bounds),
                    arrowEdge: .top
                ) {
                    CalendarYearMonthComponentPickerView(
                        title: "연도",
                        values: years,
                        selectedValue: focusedDay.year,
                        displayText: { "\($0)년" },
                        onCancel: {
                            isShowingYearPicker = false
                        },
                        onConfirm: { year in
                            isShowingYearPicker = false
                            onSelectedYearMonth(year, focusedDay.month)
                        }
                    )
                    .presentationCompactAdaptation(.popover)
                }

                titleButton(text: "\(focusedDay.month)월") {
                    isShowingMonthPicker = true
                }
                .popover(
                    isPresented: $isShowingMonthPicker,
                    attachmentAnchor: .rect(.bounds),
                    arrowEdge: .top
                ) {
                    CalendarYearMonthComponentPickerView(
                        title: "월",
                        values: Array(1...12),
                        selectedValue: focusedDay.month,
                        displayText: { "\($0)월" },
                        onCancel: {
                            isShowingMonthPicker = false
                        },
                        onConfirm: { month in
                            isShowingMonthPicker = false
                            onSelectedYearMonth(focusedDay.year, month)
                        }
                    )
                    .presentationCompactAdaptation(.popover)
                }
            }
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

    private var years: [Int] {
        let currentYear = Calendar.current.component(.year, from: Date())
        return Array((currentYear - 20)...(currentYear + 20))
    }

    private func titleButton(
        text: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(text)
                .font(.system(size: 24, weight: .semibold))
                .foregroundStyle(.primary)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(text) 선택")
    }
}

private struct CalendarYearMonthComponentPickerView: View {
    let title: String
    let values: [Int]
    let selectedValue: Int
    let displayText: (Int) -> String
    let onCancel: () -> Void
    let onConfirm: (Int) -> Void

    @State private var selection: Int

    init(
        title: String,
        values: [Int],
        selectedValue: Int,
        displayText: @escaping (Int) -> String,
        onCancel: @escaping () -> Void,
        onConfirm: @escaping (Int) -> Void
    ) {
        self.title = title
        self.values = values
        self.selectedValue = selectedValue
        self.displayText = displayText
        self.onCancel = onCancel
        self.onConfirm = onConfirm
        _selection = State(initialValue: selectedValue)
    }

    var body: some View {
        VStack(spacing: 14) {
            HStack {
                Button("취소", action: onCancel)
                    .buttonStyle(.plain)

                Spacer()

                Button("이동") {
                    onConfirm(selection)
                }
                .fontWeight(.semibold)
            }
            .padding(.horizontal, 18)
            .padding(.top, 16)

            Picker(title, selection: $selection) {
                ForEach(values, id: \.self) { value in
                    Text(displayText(value)).tag(value)
                }
            }
            .pickerStyle(.wheel)
            .frame(width: 180, height: 190)
            .clipped()
            .padding(.horizontal, 10)
            .padding(.bottom, 12)
        }
        .frame(width: 220)
        .onChange(of: selectedValue) { _, newValue in
            selection = newValue
        }
    }
}

#Preview {
    CalendarYearMonthTitleView(focusedDay: DayKey(date: Date()))
}
