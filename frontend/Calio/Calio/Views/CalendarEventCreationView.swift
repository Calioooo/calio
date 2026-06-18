//
//  CalendarEventCreationView.swift
//  Calio
//
//  Created by Codex on 6/19/26.
//

import SwiftUI

struct CalendarEventCreationView: View {
    private let eventColors = [
        "#4F46E5",
        "#EF4444",
        "#F59E0B",
        "#22C55E",
        "#0EA5E9"
    ]
    
    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var startAt: Date
    @State private var endAt: Date
    @State private var description = ""
    @State private var selectedColorCode = "#4F46E5"
    
    let onSave: () -> Void
    
    init(
        focusedDay: DayKey,
        calendar: Calendar = .current,
        onSave: @escaping () -> Void = {}
    ) {
        let startAt = CalendarEventCreationView.defaultStartAt(
            focusedDay: focusedDay,
            calendar: calendar
        )
        let endAt = calendar.date(
            byAdding: .hour,
            value: 1,
            to: startAt
        ) ?? startAt
        
        _startAt = State(initialValue: startAt)
        _endAt = State(initialValue: endAt)
        self.onSave = onSave
    }
    
    var body: some View {
        NavigationStack {
            Form {
                titleSection
                timeSection
                descriptionSection
                colorSection
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
                    Button("저장") {
                        onSave()
                        dismiss()
                    }
                    .disabled(!canSave)
                }
            }
        }
    }
    
    private var titleSection: some View {
        Section {
            TextField("일정 제목", text: $title)
                .font(.system(size: 20, weight: .semibold))
                .submitLabel(.next)
        }
    }
    
    private var timeSection: some View {
        Section {
            DatePicker(
                "시작",
                selection: $startAt,
                displayedComponents: [.date, .hourAndMinute]
            )
            
            DatePicker(
                "종료",
                selection: $endAt,
                in: startAt...,
                displayedComponents: [.date, .hourAndMinute]
            )
        }
    }
    
    private var descriptionSection: some View {
        Section("설명") {
            TextEditor(text: $description)
                .frame(minHeight: 96)
                .overlay(alignment: .topLeading) {
                    if description.isEmpty {
                        Text("메모를 입력하세요")
                            .foregroundStyle(.secondary)
                            .padding(.top, 8)
                            .padding(.leading, 5)
                            .allowsHitTesting(false)
                    }
                }
        }
    }
    
    private var colorSection: some View {
        Section("색상") {
            HStack(spacing: 14) {
                ForEach(eventColors, id: \.self) { colorCode in
                    colorButton(colorCode)
                }
            }
            .padding(.vertical, 4)
        }
    }
    
    private func colorButton(_ colorCode: String) -> some View {
        Button {
            selectedColorCode = colorCode
        } label: {
            Circle()
                .fill(Color(hex: colorCode))
                .frame(width: 28, height: 28)
                .overlay {
                    if selectedColorCode == colorCode {
                        Image(systemName: "checkmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(.white)
                    }
                }
                .overlay {
                    Circle()
                        .stroke(Color.secondary.opacity(0.25), lineWidth: 1)
                }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("일정 색상 선택")
    }
    
    private var canSave: Bool {
        !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        endAt > startAt
    }
    
    private static func defaultStartAt(
        focusedDay: DayKey,
        calendar: Calendar
    ) -> Date {
        let date = focusedDay.toDate(calendar: calendar)
        return calendar.date(
            bySettingHour: 9,
            minute: 0,
            second: 0,
            of: date
        ) ?? date
    }
}

#Preview {
    CalendarEventCreationView(focusedDay: DayKey(date: Date()))
}
