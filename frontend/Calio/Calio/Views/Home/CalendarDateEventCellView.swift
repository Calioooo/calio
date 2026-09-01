//
//  CalendarDateEventCellView.swift
//  Calio
//
//  Created by 김준하 on 6/8/26.
//

import SwiftUI
import UIKit

struct CalendarDateEventCellView: View {
    @Environment(\.sizeCategory) private var sizeCategory
    private let popoverEdgeResolver = CalendarDateEventPopoverEdgeResolver()
    
    let day: DayKey
    let weekday: CalendarWeekday
    let monthText: String
    let dayText: String
    let isToday: Bool
    let onTap: () -> Void
    @Binding var selectedEvent: CalendarDateEventSelection?
    let onEventSelected: (Event) -> Void
    let onShowEventDetail: (Event) -> Void
    let events: [Event]
    let holidays: [NationalHoliday]

    @State private var eventChipFrames: [String: CGRect] = [:]

    init(
        day: DayKey,
        weekday: CalendarWeekday,
        monthText: String,
        dayText: String,
        isToday: Bool,
        onTap: @escaping () -> Void,
        selectedEvent: Binding<CalendarDateEventSelection?>,
        onEventSelected: @escaping (Event) -> Void,
        onShowEventDetail: @escaping (Event) -> Void,
        events: [Event],
        holidays: [NationalHoliday] = []
    ) {
        self.day = day
        self.weekday = weekday
        self.monthText = monthText
        self.dayText = dayText
        self.isToday = isToday
        self.onTap = onTap
        self._selectedEvent = selectedEvent
        self.onEventSelected = onEventSelected
        self.onShowEventDetail = onShowEventDetail
        self.events = events
        self.holidays = holidays
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            agendaHeader

            ForEach(holidays) { holiday in
                holidayChip(holiday)
            }

            ForEach(events) { event in
                eventChipButton(event)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
    }

    private func holidayChip(_ holiday: NationalHoliday) -> some View {
        HStack(spacing: 6) {
            Text(holiday.title)
                .font(.caption.weight(.semibold))
                .lineLimit(1)

            Text("종일")
                .font(.caption2.weight(.medium))
        }
        .foregroundStyle(.white)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(
                Capsule()
                    .fill(Color.calendarHoliday)
            )
            .accessibilityLabel("\(holiday.title) 공휴일, 종일")
    }

    @ViewBuilder
    private var agendaHeader: some View {
        if sizeCategory.isAccessibilityCategory {
            VStack(alignment: .leading, spacing: 4) {
                dateTitle
                eventCount
            }
        } else {
            HStack(alignment: .firstTextBaseline) {
                dateTitle
                Spacer()
                eventCount
            }
        }
    }

    private var dateTitle: some View {
        HStack(alignment: .firstTextBaseline, spacing: 7) {
            Text("\(monthText)월 \(dayText)일")
                .font(.title3.weight(.bold))
                .foregroundStyle(.calioTextPrimary)

            Text(weekday.shortEnglishText)
                .font(.caption2.weight(.semibold))
                .tracking(0.4)
                .foregroundStyle(.calioTextSecondary)
        }
        .accessibilityLabel("\(monthText)월 \(dayText)일 \(weekday.fullKoreanText)")
    }

    @ViewBuilder
    private var eventCount: some View {
        if !events.isEmpty {
            Text("일정 \(events.count)개")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.calioTextSecondary)
        }
    }

    private func eventChipButton(_ event: Event) -> some View {
        Button {
            selectedEvent = CalendarDateEventSelection(day: day, event: event)
            onEventSelected(event)
        } label: {
            HStack(alignment: .top, spacing: 12) {
                Text(eventScheduleText(event))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.calioTextSecondary)
                    .frame(width: 72, alignment: .leading)

                Circle()
                    .fill(Color(hex: event.tag.colorCode))
                    .frame(width: 8, height: 8)
                    .padding(.top, 6)

                HStack(alignment: .firstTextBaseline, spacing: 5) {
                    Text(event.title)
                        .font(.subheadline.weight(.semibold))
                        .fixedSize(horizontal: false, vertical: true)

                    if event.importantEvent {
                        Image(systemName: "star.fill")
                            .font(.caption)
                            .foregroundStyle(Color.calioImportantStar)
                            .accessibilityLabel("중요 일정")
                    }
                }

                Spacer(minLength: 0)
            }
            .foregroundStyle(.calioTextPrimary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.calioSurface)
            )
            .overlay {
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.calioDivider, lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
        .background {
            GeometryReader { geometry in
                Color.clear
                    .onAppear {
                        updateChipFrame(for: event, frame: geometry.frame(in: .global))
                    }
                    .onChange(of: geometry.frame(in: .global)) { _, newFrame in
                        updateChipFrame(for: event, frame: newFrame)
                    }
            }
        }
        .popover(
            isPresented: isShowingEventPopover(for: event),
            attachmentAnchor: .rect(.bounds),
            arrowEdge: popoverArrowEdge(for: event)
        ) {
            CalendarEventSummaryPopoverView(
                event: event,
                onShowDetail: onShowEventDetail
            )
            .presentationCompactAdaptation(.popover)
        }
    }

    private func isShowingEventPopover(for event: Event) -> Binding<Bool> {
        Binding(
            get: {
                selectedEvent?.day == day && selectedEvent?.event.id == event.id
            },
            set: { isPresented in
                if !isPresented {
                    selectedEvent = nil
                }
            }
        )
    }

    private func updateChipFrame(for event: Event, frame: CGRect) {
        guard eventChipFrames[event.id] != frame else {
            return
        }

        eventChipFrames[event.id] = frame
    }

    private func popoverArrowEdge(for event: Event) -> Edge {
        popoverEdgeResolver.arrowEdge(
            for: eventChipFrames[event.id],
            screenHeight: UIScreen.main.bounds.height
        )
    }

    private func eventScheduleText(_ event: Event) -> String {
        if event.isAllDay {
            return "종일"
        }

        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "a h:mm"
        let time = formatter.string(from: event.startAt)
        return time
    }

    var calendarChips: [CalendarDateEventChip] {
        holidays.map { CalendarDateEventChip(kind: .holiday($0)) }
            + events.map { CalendarDateEventChip(kind: .event($0)) }
    }
}

struct CalendarDateEventChip: Identifiable {
    let kind: CalendarDateEventChipKind

    var id: String {
        switch kind {
        case .holiday(let holiday):
            return "holiday-\(holiday.id)"
        case .event(let event):
            return event.id
        }
    }

    var title: String {
        switch kind {
        case .holiday(let holiday):
            return holiday.title
        case .event(let event):
            return event.title
        }
    }
}

enum CalendarDateEventChipKind {
    case holiday(NationalHoliday)
    case event(Event)
}

struct CalendarDateEventSelection: Equatable {
    let day: DayKey
    let event: Event
    
    static func == (
        lhs: CalendarDateEventSelection,
        rhs: CalendarDateEventSelection
    ) -> Bool {
        lhs.day == rhs.day && lhs.event.id == rhs.event.id
    }
}

#Preview {
    CalendarDateEventCellView(
        day: DayKey(date: Date()),
        weekday: .monday,
        monthText: "6",
        dayText: "27",
        isToday: false,
        onTap: {
            return
        },
        selectedEvent: .constant(nil),
        onEventSelected: { _ in },
        onShowEventDetail: { _ in },
        events: [
            Event(id: 1, title: "제목1212312313212313123123123123", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#FDDDDD")),
            Event(id: 2, title: "제목2312", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#5DDDDD")),
            Event(id: 3, title: "제목341231231231231321312312321356", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#BDDDDD")),
            Event(id: 4, title: "제목457", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#ADDDDD")),
            Event(id: 5, title: "제목82352323522352352538", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#AD9DDD")),
            Event(id: 6, title: "제목22", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#B3DD6D")),
            Event(id: 7, title: "제목asd", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#BDDDDD")),
            Event(id: 8, title: "제목313", description: "설명", startAt: Date(), endAt: Date().addingTimeInterval(3000), tag: .sample(colorCode: "#ADDDDD"))
        ]
    )
}
