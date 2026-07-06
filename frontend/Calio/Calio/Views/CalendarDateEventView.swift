//
//  CalendarDateEventView.swift
//  Calio
//
//  Created by 김준하 on 6/8/26.
//

import SwiftUI

struct CalendarDateEventView: View {
    private let dateRowHeight: CGFloat = 96
    private let rowSpacing: CGFloat = 15
    private let contentTopPadding: CGFloat = 12
    private let contentBottomPadding: CGFloat = 24
    
    let items: [CalendarDateCellItem]
    let referenceDay: DayKey
    let eventAreaState: CalendarEventAreaState
    let onReferenceDayChanged: (DayKey) -> Void
    let onVisibleRangeChanged: (CalendarVisibleIndexRange) -> Void
    let onRetryEvents: () -> Void
    let isEventMutating: Bool
    let eventMutationFailureMessage: String?
    let onResetEventMutation: () -> Void
    let onFetchRecurrenceEvent: (Int64) async -> RecurrenceEventDetails?
    let onUpdateSingleEvent: (Event, EventUpdateInput) async -> Bool
    let onUpdateRecurrenceOccurrence: (Event, EventUpdateInput) async -> Bool
    let onUpdateRecurrenceSeries: (Int64, RecurrenceEventSeriesEditInput) async -> Bool
    let onDeleteSingleEvent: (Event) async -> Bool
    let onDeleteRecurrenceOccurrence: (Event) async -> Bool
    let onDeleteRecurrenceSeries: (Event) async -> Bool

    init(
        items: [CalendarDateCellItem],
        referenceDay: DayKey,
        eventAreaState: CalendarEventAreaState,
        onReferenceDayChanged: @escaping (DayKey) -> Void,
        onVisibleRangeChanged: @escaping (CalendarVisibleIndexRange) -> Void,
        onRetryEvents: @escaping () -> Void,
        isEventMutating: Bool = false,
        eventMutationFailureMessage: String? = nil,
        onResetEventMutation: @escaping () -> Void = {},
        onFetchRecurrenceEvent: @escaping (Int64) async -> RecurrenceEventDetails? = { _ in nil },
        onUpdateSingleEvent: @escaping (Event, EventUpdateInput) async -> Bool = { _, _ in true },
        onUpdateRecurrenceOccurrence: @escaping (Event, EventUpdateInput) async -> Bool = { _, _ in true },
        onUpdateRecurrenceSeries: @escaping (Int64, RecurrenceEventSeriesEditInput) async -> Bool = { _, _ in true },
        onDeleteSingleEvent: @escaping (Event) async -> Bool = { _ in true },
        onDeleteRecurrenceOccurrence: @escaping (Event) async -> Bool = { _ in true },
        onDeleteRecurrenceSeries: @escaping (Event) async -> Bool = { _ in true }
    ) {
        self.items = items
        self.referenceDay = referenceDay
        self.eventAreaState = eventAreaState
        self.onReferenceDayChanged = onReferenceDayChanged
        self.onVisibleRangeChanged = onVisibleRangeChanged
        self.onRetryEvents = onRetryEvents
        self.isEventMutating = isEventMutating
        self.eventMutationFailureMessage = eventMutationFailureMessage
        self.onResetEventMutation = onResetEventMutation
        self.onFetchRecurrenceEvent = onFetchRecurrenceEvent
        self.onUpdateSingleEvent = onUpdateSingleEvent
        self.onUpdateRecurrenceOccurrence = onUpdateRecurrenceOccurrence
        self.onUpdateRecurrenceSeries = onUpdateRecurrenceSeries
        self.onDeleteSingleEvent = onDeleteSingleEvent
        self.onDeleteRecurrenceOccurrence = onDeleteRecurrenceOccurrence
        self.onDeleteRecurrenceSeries = onDeleteRecurrenceSeries
    }
    
    @StateObject private var focusCoordinator = CalendarScrollFocusCoordinator()
    @State private var selectedEvent: CalendarDateEventSelection?
    @State private var detailEvent: Event?
    
    var body: some View {
        VStack(spacing: 0) {
            CalendarEventStatusBannerView(
                state: eventAreaState,
                onRetry: onRetryEvents
            )

            GeometryReader { _ in
                let itemIDs = items.map(\.id)

                if focusCoordinator.canRenderContent(referenceDay: referenceDay, itemIDs: itemIDs) {
                    scrollContent(itemIDs: itemIDs)
                } else {
                    initialAlignmentPlaceholder(itemIDs: itemIDs)
                }
            }
        }
        .sheet(item: $detailEvent) { event in
            CalendarEventDetailView(
                event: event,
                isMutating: isEventMutating,
                mutationFailureMessage: eventMutationFailureMessage,
                onFetchRecurrenceEvent: onFetchRecurrenceEvent,
                onUpdateSingleEvent: onUpdateSingleEvent,
                onUpdateRecurrenceOccurrence: onUpdateRecurrenceOccurrence,
                onUpdateRecurrenceSeries: onUpdateRecurrenceSeries,
                onDeleteSingleEvent: onDeleteSingleEvent,
                onDeleteRecurrenceOccurrence: onDeleteRecurrenceOccurrence,
                onDeleteRecurrenceSeries: onDeleteRecurrenceSeries
            )
        }
    }

    private func scrollContent(
        itemIDs: [DayKey]
    ) -> some View {
        return ScrollView(.vertical) {
            LazyVStack(spacing: rowSpacing) {
                ForEach(items) { item in
                    CalendarDateEventCellView(
                        day: item.id,
                        weekday: item.weekday,
                        monthText: item.monthText,
                        dayText: item.dayText,
                        isToday: item.isToday,
                        onTap: {
                            focusCoordinator.notifyUserSelectedReferenceDay(
                                item.id,
                                onReferenceDayChanged: onReferenceDayChanged
                            )
                        },
                        selectedEvent: $selectedEvent,
                        onEventSelected: { _ in },
                        onShowEventDetail: showEventDetail,
                        events: item.events,
                        holidays: item.holidays
                    )
                    .frame(height: dateRowHeight)
                    .clipped()
                    .id(item.id)
                }
            }
            .scrollTargetLayout()
            .padding(.horizontal, 16)
            .padding(.top, contentTopPadding)
            .padding(.bottom, contentBottomPadding)
        }
        .scrollTargetBehavior(.viewAligned)
        .scrollIndicators(.hidden)
        .scrollPosition(id: $focusCoordinator.scrollPosition, anchor: .top)
        .onChange(of: focusCoordinator.scrollPosition) { _, newDay in
            focusCoordinator.notifyScrollReferenceDayIfNeeded(
                newDay,
                currentReferenceDay: referenceDay,
                onReferenceDayChanged: onReferenceDayChanged
            )
            notifyVisibleRangeChanged(day: newDay, itemIDs: itemIDs)
        }
        .onChange(of: referenceDay) { _, newDay in
            focusCoordinator.alignAfterReferenceDayChanged(
                to: newDay,
                itemIDs: itemIDs
            )
        }
        .onChange(of: itemIDs) { _, newItemIDs in
            focusCoordinator.alignAfterItemsChanged(
                referenceDay: referenceDay,
                itemIDs: newItemIDs
            )
        }
        .onDisappear {
            focusCoordinator.cancel()
        }
    }

    private func initialAlignmentPlaceholder(itemIDs: [DayKey]) -> some View {
        Color.clear
            .onAppear {
                focusCoordinator.prepareContentPosition(
                    referenceDay: referenceDay,
                    itemIDs: itemIDs
                )
            }
            .onChange(of: itemIDs) { _, newItemIDs in
                focusCoordinator.prepareContentPosition(
                    referenceDay: referenceDay,
                    itemIDs: newItemIDs
                )
            }
            .onChange(of: referenceDay) { _, newDay in
                focusCoordinator.prepareContentPosition(
                    referenceDay: newDay,
                    itemIDs: itemIDs
                )
            }
    }
    
    private func notifyVisibleRangeChanged(
        day: DayKey?,
        itemIDs: [DayKey]
    ) {
        guard let day,
              let index = itemIDs.firstIndex(of: day)
        else {
            return
        }

        onVisibleRangeChanged(
            CalendarVisibleIndexRange(
                startIndex: index,
                endIndex: index
            )
        )
    }

    private func showEventDetail(_ event: Event) {
        selectedEvent = nil
        onResetEventMutation()
        detailEvent = event
    }
}

#Preview {
    let calendar = Calendar.current
    let dateService = CalendarDateService(calendar: calendar)
    let today = Date()
    
    let makeEvent: (Int64, String, Date, Int, String) -> Event = { id, title, date, hour, colorCode in
        let startOfDay = calendar.startOfDay(for: date)
        
        let startAt = calendar.date(
            bySettingHour: hour,
            minute: 0,
            second: 0,
            of: startOfDay
        ) ?? startOfDay
        
        let endAt = calendar.date(
            byAdding: .hour,
            value: 1,
            to: startAt
        ) ?? startAt
        
        return Event(
            id: id,
            title: title,
            description: "",
            startAt: startAt,
            endAt: endAt,
            colorCode: colorCode
        )
    }
    
    let items: [CalendarDateCellItem] = (0..<7).map { offset in
        let date = calendar.date(
            byAdding: .day,
            value: offset,
            to: today
        ) ?? today
        
        let day = DayKey(date: date, calendar: calendar)
        
        let events: [Event] = switch offset {
        case 0:
            [
                makeEvent(1, "팀 미팅", date, 9, "#4F46E5"),
                makeEvent(2, "제품 리뷰", date, 13, "#059669")
            ]
            
        case 1:
            [
                makeEvent(3, "1:1", date, 10, "#DC2626")
            ]
            
        case 2:
            [
                makeEvent(4, "운동", date, 8, "#D97706"),
                makeEvent(5, "API 정리", date, 15, "#0891B2"),
                makeEvent(6, "저녁 약속", date, 19, "#4F46E5")
            ]
            
        case 4:
            [
                makeEvent(7, "디자인 리뷰", date, 14, "#059669"),
                makeEvent(8, "회고", date, 16, "#DC2626"),
                makeEvent(9, "문서 정리", date, 17, "#D97706"),
                makeEvent(10, "개인 일정", date, 20, "#0891B2"),
                makeEvent(11, "추가 일정", date, 21, "#4F46E5")
            ]
            
        default:
            []
        }
        
        return CalendarDateCellItem(
            id: day,
            weekday: dateService.getWeekday(from: date),
            monthText: dateService.monthText(from: date),
            dayText: dateService.dayText(from: date),
            isToday: calendar.isDateInToday(date),
            events: events
        )
    }
    
    CalendarDateEventView(
        items: items,
        referenceDay: items[0].id,
        eventAreaState: .idle,
        onReferenceDayChanged: { _ in },
        onVisibleRangeChanged: { _ in },
        onRetryEvents: {}
    )
    .frame(height: 110)
}
