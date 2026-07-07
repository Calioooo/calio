//
//  CalendarDateEventCellView.swift
//  Calio
//
//  Created by 김준하 on 6/8/26.
//

import SwiftUI
import UIKit

struct CalendarDateEventCellView: View {
    private let maxVisibleEventRowCount = 3
    private let eventChipHorizontalPadding: CGFloat = 20
    private let eventChipSpacing: CGFloat = 8
    private let eventChipFont = UIFont.systemFont(ofSize: 13, weight: .medium)
    private let lowerScreenPopoverThreshold: CGFloat = 0.62
    
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

    @State private var eventChipFrames: [Int64: CGRect] = [:]

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
        GeometryReader { geometry in
            let chipLayout = eventChipLayout(maxWidth: geometry.size.width)

            ZStack(alignment: .topLeading) {
                Color.clear
                    .contentShape(Rectangle())
                    .onTapGesture(perform: onTap)

                VStack(spacing: 8) {
                    Text("\(monthText) / \(dayText)")
                        .font(.system(size: 18, weight: .medium))
                        .frame(maxWidth: .infinity, alignment: .leading)

                    FlowLayout(spacing: eventChipSpacing) {
                        ForEach(chipLayout.visibleChips) { chip in
                            switch chip.kind {
                            case .holiday(let holiday):
                                holidayChip(holiday)
                            case .event(let event):
                                eventChipButton(event)
                            }
                        }

                        if chipLayout.hiddenChipCount > 0 {
                            Text("+\(chipLayout.hiddenChipCount) more")
                                .font(.system(size: 13, weight: .medium))
                                .lineLimit(1)
                                .foregroundStyle(.secondary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(
                                    RoundedRectangle(cornerRadius: 6)
                                        .fill(Color.secondary.opacity(0.12))
                                )
                        }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
    }

    private func holidayChip(_ holiday: NationalHoliday) -> some View {
        Text(holiday.title)
            .font(.system(size: 13, weight: .medium))
            .lineLimit(1)
            .foregroundStyle(.white)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(
                RoundedRectangle(cornerRadius: 6)
                    .fill(Color.calendarHoliday)
            )
            .accessibilityLabel("\(holiday.title) 공휴일")
    }

    private func eventChipButton(_ event: Event) -> some View {
        Button {
            selectedEvent = CalendarDateEventSelection(day: day, event: event)
            onEventSelected(event)
        } label: {
            Text(event.title)
                .font(.system(size: 13, weight: .medium))
                .lineLimit(1)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Color(hex: event.tag.colorCode))
                )
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
        guard let frame = eventChipFrames[event.id] else {
            return .top
        }

        let screenHeight = UIScreen.main.bounds.height
        let isLowerScreenChip = frame.midY > screenHeight * lowerScreenPopoverThreshold

        return isLowerScreenChip ? .bottom : .top
    }
    
    private func eventChipLayout(maxWidth: CGFloat) -> EventChipLayout {
        let chips = calendarChips

        guard maxWidth > 0 else {
            return EventChipLayout(visibleChips: [], hiddenChipCount: chips.count)
        }
        
        var visibleChips: [CalendarDateEventChip] = []
        
        for chip in chips {
            let candidateChips = visibleChips + [chip]
            let hiddenChipCount = chips.count - candidateChips.count
            
            guard calendarChipsFit(
                visibleChips: candidateChips,
                hiddenChipCount: hiddenChipCount,
                maxWidth: maxWidth
            ) else {
                break
            }
            
            visibleChips = candidateChips
        }
        
        return EventChipLayout(
            visibleChips: visibleChips,
            hiddenChipCount: chips.count - visibleChips.count
        )
    }
    
    private func calendarChipsFit(
        visibleChips: [CalendarDateEventChip],
        hiddenChipCount: Int,
        maxWidth: CGFloat
    ) -> Bool {
        let chipWidths = visibleChips.map { chipWidth(text: $0.title, maxWidth: maxWidth) }
        let hiddenWidth = hiddenChipCount > 0
            ? [chipWidth(text: "+\(hiddenChipCount) more", maxWidth: maxWidth)]
            : []
        
        return rowCount(for: chipWidths + hiddenWidth, maxWidth: maxWidth) <= maxVisibleEventRowCount
    }

    var calendarChips: [CalendarDateEventChip] {
        holidays.map { CalendarDateEventChip(kind: .holiday($0)) }
            + events.map { CalendarDateEventChip(kind: .event($0)) }
    }
    
    private func chipWidth(text: String, maxWidth: CGFloat) -> CGFloat {
        let textWidth = (text as NSString).size(withAttributes: [.font: eventChipFont]).width
        
        return min(textWidth + eventChipHorizontalPadding, maxWidth)
    }
    
    private func rowCount(for widths: [CGFloat], maxWidth: CGFloat) -> Int {
        widths.reduce(into: EventChipRows()) { rows, width in
            rows.append(width: width, maxWidth: maxWidth, spacing: eventChipSpacing)
        }
        .count
    }
}

private struct EventChipLayout {
    let visibleChips: [CalendarDateEventChip]
    let hiddenChipCount: Int
}

struct CalendarDateEventChip: Identifiable {
    let kind: CalendarDateEventChipKind

    var id: String {
        switch kind {
        case .holiday(let holiday):
            return "holiday-\(holiday.id)"
        case .event(let event):
            return "event-\(event.id)"
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

private struct EventChipRows {
    private(set) var count = 0
    private var currentRowWidth: CGFloat = 0
    
    mutating func append(width: CGFloat, maxWidth: CGFloat, spacing: CGFloat) {
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
