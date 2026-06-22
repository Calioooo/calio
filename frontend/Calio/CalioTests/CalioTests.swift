//
//  CalioTests.swift
//  CalioTests
//
//  Created by 김준하 on 6/6/26.
//

import Testing
import Foundation
@testable import Calio

@Suite(.serialized)
struct CalioTests {

    @Test func calendarDisplayModeStartsFromWeek() async throws {
        let displayMode = CalendarDisplayMode.week

        #expect(displayMode == .week)
    }

    @Test func drawerDragDownPastThresholdResolvesToMonth() async throws {
        let displayMode = CalendarDisplayMode.week

        #expect(displayMode.resolved(afterDragTranslationHeight: 41) == .month)
    }

    @Test func drawerDragUpPastThresholdResolvesToWeek() async throws {
        let displayMode = CalendarDisplayMode.month

        #expect(displayMode.resolved(afterDragTranslationHeight: -41) == .week)
    }

    @Test func drawerDragInsideThresholdKeepsCurrentDisplayMode() async throws {
        #expect(CalendarDisplayMode.week.resolved(afterDragTranslationHeight: 40) == .week)
        #expect(CalendarDisplayMode.month.resolved(afterDragTranslationHeight: -40) == .month)
    }

    @MainActor
    @Test func scheduleDrawerUsesItemsAndCallbacksWithoutViewModel() async throws {
        let drawer = CalendarScheduleDrawerView(
            items: [],
            focusedDay: DayKey(date: Date()),
            displayMode: .week,
            eventAreaState: .idle,
            onFocusedDayChanged: { _ in },
            onVisibleRangeChanged: { _ in },
            onRetryEvents: {},
            onDragEnded: { _ in }
        )

        #expect(drawer.items.isEmpty)
        #expect(drawer.displayMode == .week)
    }
    
    @Test func calendarStateReturnsLoadedDateItemsInDateOrder() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let items = [
            makeDateCellItem(dayOffset: 2, from: baseDate, calendar: calendar),
            makeDateCellItem(dayOffset: 0, from: baseDate, calendar: calendar),
            makeDateCellItem(dayOffset: 1, from: baseDate, calendar: calendar)
        ]
        let state = CalendarState(
            startDate: baseDate,
            endDate: try #require(calendar.date(byAdding: .day, value: 2, to: baseDate)),
            daysByKey: Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) }),
            focusedDay: items[1].id
        )
        
        let loadedDays = state.loadedDateCellItems(calendar: calendar).map(\.id)
        
        #expect(loadedDays == [
            makeDayKey(dayOffset: 0, from: baseDate, calendar: calendar),
            makeDayKey(dayOffset: 1, from: baseDate, calendar: calendar),
            makeDayKey(dayOffset: 2, from: baseDate, calendar: calendar)
        ])
    }
    
    @Test func calendarStatePreservesEmptyDaysInsideLoadedRange() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let emptyDayItem = makeDateCellItem(dayOffset: 1, from: baseDate, calendar: calendar)
        let state = CalendarState(
            startDate: baseDate,
            endDate: try #require(calendar.date(byAdding: .day, value: 2, to: baseDate)),
            daysByKey: [
                makeDayKey(dayOffset: 0, from: baseDate, calendar: calendar): makeDateCellItem(dayOffset: 0, from: baseDate, calendar: calendar, events: [makeEvent(on: baseDate)]),
                emptyDayItem.id: emptyDayItem,
                makeDayKey(dayOffset: 2, from: baseDate, calendar: calendar): makeDateCellItem(dayOffset: 2, from: baseDate, calendar: calendar)
            ],
            focusedDay: emptyDayItem.id
        )
        
        let loadedItems = state.loadedDateCellItems(calendar: calendar)
        
        #expect(loadedItems.count == 3)
        #expect(loadedItems[1].id == emptyDayItem.id)
        #expect(loadedItems[1].events.isEmpty)
    }
    
    @Test func calendarStateFocusesOnlyCanonicalFocusedDay() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let firstDay = makeDayKey(dayOffset: 0, from: baseDate, calendar: calendar)
        let secondDay = makeDayKey(dayOffset: 1, from: baseDate, calendar: calendar)
        let state = CalendarState(
            startDate: baseDate,
            endDate: try #require(calendar.date(byAdding: .day, value: 1, to: baseDate)),
            daysByKey: [
                firstDay: makeDateCellItem(dayOffset: 0, from: baseDate, calendar: calendar),
                secondDay: makeDateCellItem(dayOffset: 1, from: baseDate, calendar: calendar)
            ],
            focusedDay: firstDay
        )
        
        #expect(state.focused(on: secondDay).focusedDay == secondDay)
    }
    
    @Test func calendarStateDetectsLoadedRangeEdgesByThreshold() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let endDate = try #require(calendar.date(byAdding: .day, value: 10, to: baseDate))
        let state = CalendarState(
            startDate: baseDate,
            endDate: endDate,
            daysByKey: [:],
            focusedDay: makeDayKey(dayOffset: 5, from: baseDate, calendar: calendar)
        )
        
        #expect(state.nearLoadedEdge(around: makeDayKey(dayOffset: 1, from: baseDate, calendar: calendar), thresholdDays: 3, calendar: calendar) == .start)
        #expect(state.nearLoadedEdge(around: makeDayKey(dayOffset: 9, from: baseDate, calendar: calendar), thresholdDays: 3, calendar: calendar) == .end)
        #expect(state.nearLoadedEdge(around: makeDayKey(dayOffset: 5, from: baseDate, calendar: calendar), thresholdDays: 3, calendar: calendar) == nil)
    }
    
    @MainActor
    @Test func scrollingDateViewsReceiveItemsFocusedDayAndCallbacksWithoutViewModel() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let items = [
            makeDateCellItem(dayOffset: 0, from: baseDate, calendar: calendar),
            makeDateCellItem(dayOffset: 1, from: baseDate, calendar: calendar)
        ]
        
        let strip = CalendarDateStripView(
            items: items,
            focusedDay: items[0].id,
            onFocusedDayChanged: { _ in }
        )
        let eventList = CalendarDateEventView(
            items: items,
            focusedDay: items[0].id,
            eventAreaState: .idle,
            onFocusedDayChanged: { _ in },
            onVisibleRangeChanged: { _ in },
            onRetryEvents: {}
        )
        
        #expect(strip.items.count == 2)
        #expect(strip.focusedDay == items[0].id)
        #expect(eventList.items.count == 2)
        #expect(eventList.focusedDay == items[0].id)
    }

    @MainActor
    @Test func calendarHomeViewModelFocusDayUpdatesCanonicalFocusedDay() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let focusedDay = makeDayKey(dayOffset: 3, from: baseDate, calendar: calendar)
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: RecordingEventRepository()),
            initialState: makeLoadedState(dayOffsets: 0...10, focusedOffset: 0, from: baseDate, calendar: calendar)
        )

        viewModel.focusDay(focusedDay)

        #expect(viewModel.state.focusedDay == focusedDay)
    }

    @MainActor
    @Test func calendarHomeViewModelInitialLoadRequestsFocusedAndAdjacentMonthsOnly() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository()
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            initialFocusedDate: baseDate
        )

        viewModel.loadInitialIfNeeded()
        let didRequestInitialMonths = await repository.waitForRequestCount(3)

        #expect(didRequestInitialMonths)
        #expect(repository.requestMonthKeys(calendar: calendar) == [
            YearMonthKey(year: 2026, month: 5),
            YearMonthKey(year: 2026, month: 6),
            YearMonthKey(year: 2026, month: 7)
        ])
    }

    @MainActor
    @Test func calendarHomeViewModelSelectYearMonthJumpsImmediatelyWithoutIntermediateFetches() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository(shouldSuspend: true)
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            initialState: makeLoadedState(dayOffsets: 0...10, focusedOffset: 0, from: baseDate, calendar: calendar)
        )

        viewModel.selectYearMonth(year: 2036, month: 6)
        let didRequestTargetMonths = await repository.waitForRequestCount(3)

        #expect(viewModel.state.focusedDay == DayKey(year: 2036, month: 6, day: 8))
        #expect(didRequestTargetMonths)
        #expect(repository.requestMonthKeys(calendar: calendar) == [
            YearMonthKey(year: 2036, month: 5),
            YearMonthKey(year: 2036, month: 6),
            YearMonthKey(year: 2036, month: 7)
        ])
        repository.finishSuspendedRequests()
    }

    @MainActor
    @Test func calendarHomeViewModelSelectYearMonthClampsMissingDayToLastDay() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 1, day: 31)))
        let repository = RecordingEventRepository()
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            initialState: makeLoadedState(dayOffsets: 0...10, focusedOffset: 0, from: baseDate, calendar: calendar)
        )

        viewModel.selectYearMonth(year: 2026, month: 2)

        #expect(viewModel.state.focusedDay == DayKey(year: 2026, month: 2, day: 28))
    }

    @MainActor
    @Test func calendarHomeViewModelSuppressesDuplicateLoadingAndLoadedMonthRequests() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository(shouldSuspend: true)
        let initialState = makeLoadedState(
            dayOffsets: 0...10,
            focusedOffset: 0,
            from: baseDate,
            calendar: calendar,
            monthEventCache: [
                YearMonthKey(year: 2026, month: 6): .loaded([]),
                YearMonthKey(year: 2026, month: 7): .loading
            ]
        )
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            initialState: initialState
        )

        viewModel.retryFocusedMonthEvents()
        viewModel.loadAdditionalEventsIfNeeded(
            visibleRange: CalendarVisibleIndexRange(startIndex: 0, endIndex: 6)
        )
        let didRequestMissingAdjacentMonth = await repository.waitForRequestCount(1)

        #expect(didRequestMissingAdjacentMonth)
        #expect(repository.requestMonthKeys(calendar: calendar) == [
            YearMonthKey(year: 2026, month: 5)
        ])
        repository.finishSuspendedRequests()
    }

    @MainActor
    @Test func calendarHomeViewModelKeepsFocusAndExposesRetryStateWhenTargetMonthFails() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository(error: EventRepositoryError.network(URLError(.notConnectedToInternet)))
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            initialState: makeLoadedState(dayOffsets: 0...10, focusedOffset: 0, from: baseDate, calendar: calendar)
        )

        viewModel.selectYearMonth(year: 2036, month: 6)
        let didRequestTargetMonths = await repository.waitForRequestCount(3)
        await Task.yield()

        #expect(didRequestTargetMonths)
        #expect(viewModel.state.focusedDay == DayKey(year: 2036, month: 6, day: 8))
        #expect(viewModel.focusedEventAreaState == .failed("일정을 불러오지 못했습니다. 네트워크 연결을 확인해 주세요."))
    }

    @Test func eventCreationDefaultTimesUseFocusedDayMorningRange() async throws {
        let calendar = fixedCalendar
        let focusedDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 10, hour: 15)))
        let focusedDay = DayKey(date: focusedDate, calendar: calendar)

        let range = CalendarEventCreationView.defaultTimeRange(focusedDay: focusedDay, calendar: calendar)
        let startComponents = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: range.startAt)
        let endComponents = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: range.endAt)

        #expect(startComponents.year == 2026)
        #expect(startComponents.month == 6)
        #expect(startComponents.day == 10)
        #expect(startComponents.hour == 9)
        #expect(startComponents.minute == 0)
        #expect(endComponents.hour == 10)
        #expect(endComponents.minute == 0)
    }

    @Test func eventCreationSaveValidationRequiresTitleAndPositiveTimeRange() async throws {
        let startAt = Date()
        let endAt = startAt.addingTimeInterval(3600)

        #expect(CalendarEventCreationView.canSave(title: "회의", startAt: startAt, endAt: endAt))
        #expect(!CalendarEventCreationView.canSave(title: "   ", startAt: startAt, endAt: endAt))
        #expect(!CalendarEventCreationView.canSave(title: "회의", startAt: startAt, endAt: startAt))
        #expect(!CalendarEventCreationView.canSave(title: "회의", startAt: startAt, endAt: startAt.addingTimeInterval(-1)))
    }

    @Test func createEventRequestDTOEncodesOnlyBackendContractFields() async throws {
        let startAt = Date(timeIntervalSince1970: 1_780_000_000)
        let endAt = startAt.addingTimeInterval(3600)
        let request = CreateEventRequestDTO(
            title: "저녁 약속",
            description: "식당 예약",
            startAt: startAt,
            endAt: endAt
        )

        let data = try EventJSONCoding.makeEncoder().encode(request)
        let object = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])

        #expect(Set(object.keys) == ["title", "description", "startAt", "endAt"])
        #expect(object["title"] as? String == "저녁 약속")
        #expect(object["description"] as? String == "식당 예약")
        #expect(object["selectedColorCode"] == nil)
        #expect(object["colorCode"] == nil)
        #expect((object["startAt"] as? String)?.hasSuffix("Z") == true)
    }

    @Test func eventServiceCreateEventMapsRepositoryResponseToAppEvent() async throws {
        let calendar = fixedCalendar
        let startAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 10, hour: 9)))
        let endAt = startAt.addingTimeInterval(3600)
        let repository = RecordingEventRepository(
            createResponse: EventResponseDTO(
                id: 77,
                title: "제품 리뷰",
                description: nil,
                startAt: startAt,
                endAt: endAt,
                createdAt: startAt,
                updatedAt: startAt
            )
        )
        let service = EventService(repository: repository)

        let event = try await service.createEvent(
            EventCreateInput(
                title: "제품 리뷰",
                description: "",
                startAt: startAt,
                endAt: endAt
            )
        )

        #expect(event.id == 77)
        #expect(event.title == "제품 리뷰")
        #expect(event.description == "")
        #expect(event.startAt == startAt)
        #expect(event.endAt == endAt)
        #expect(event.colorCode == "#4F46E5")
        #expect(repository.createRequests.count == 1)
    }

    @Test func urlSessionEventRepositoryCreatesEventWithInjectedBaseURLAndContractBody() async throws {
        let calendar = fixedCalendar
        let startAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 10, hour: 9)))
        let endAt = startAt.addingTimeInterval(3600)
        let responseJSON = """
        {
          "id": 88,
          "title": "새 일정",
          "description": "메모",
          "startAt": "2026-06-10T09:00:00Z",
          "endAt": "2026-06-10T10:00:00Z",
          "createdAt": "2026-06-10T09:00:00Z",
          "updatedAt": "2026-06-10T09:00:00Z"
        }
        """.data(using: .utf8)!
        var capturedRequest: URLRequest?
        MockURLProtocol.requestHandler = { request in
            capturedRequest = request
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 201,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, responseJSON)
        }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let repository = URLSessionEventRepository(
            baseURL: URL(string: "https://example.test")!,
            session: session
        )

        let event = try await repository.createEvent(
            CreateEventRequestDTO(
                title: "새 일정",
                description: "메모",
                startAt: startAt,
                endAt: endAt
            )
        )
        let request = try #require(capturedRequest)
        let body = try #require(requestBodyData(from: request))
        let object = try #require(JSONSerialization.jsonObject(with: body) as? [String: Any])

        #expect(event.id == 88)
        #expect(request.url?.absoluteString == "https://example.test/api/events")
        #expect(request.httpMethod == "POST")
        #expect(request.value(forHTTPHeaderField: "Content-Type") == "application/json")
        #expect(Set(object.keys) == ["title", "description", "startAt", "endAt"])
        #expect(object["title"] as? String == "새 일정")
        #expect(object["selectedColorCode"] == nil)
    }

    @MainActor
    @Test func calendarHomeViewModelInsertsCreatedEventIntoStartAtMonthCache() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let startAt = try #require(calendar.date(byAdding: DateComponents(day: 1, hour: 9), to: baseDate))
        let createdEvent = makeEvent(id: 99, title: "생성된 일정", on: startAt)
        let repository = RecordingEventRepository(createResponse: makeEventResponse(from: createdEvent))
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            initialState: makeLoadedState(dayOffsets: 0...2, focusedOffset: 0, from: baseDate, calendar: calendar)
        )

        let didCreate = await viewModel.createEvent(
            EventCreateInput(
                title: createdEvent.title,
                description: createdEvent.description,
                startAt: createdEvent.startAt,
                endAt: createdEvent.endAt
            )
        )
        let eventDay = DayKey(date: createdEvent.startAt, calendar: calendar)

        #expect(didCreate)
        #expect(viewModel.state.daysByKey[eventDay]?.events.map(\.id) == [99])
        #expect(viewModel.createState == .idle)
    }

    @MainActor
    @Test func calendarHomeViewModelCreatesMonthCacheEntryForCreatedEventOutsideGeneratedRange() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let startAt = try #require(calendar.date(byAdding: DateComponents(day: 5, hour: 9), to: baseDate))
        let createdEvent = makeEvent(id: 100, title: "범위 밖 일정", on: startAt)
        let repository = RecordingEventRepository(createResponse: makeEventResponse(from: createdEvent))
        let initialState = makeLoadedState(dayOffsets: 0...2, focusedOffset: 0, from: baseDate, calendar: calendar)
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            initialState: initialState
        )

        let didCreate = await viewModel.createEvent(
            EventCreateInput(
                title: createdEvent.title,
                description: createdEvent.description,
                startAt: createdEvent.startAt,
                endAt: createdEvent.endAt
            )
        )

        #expect(didCreate)
        #expect(viewModel.state.startDate == initialState.startDate)
        #expect(viewModel.state.endDate == initialState.endDate)
        #expect(viewModel.state.daysByKey.count == initialState.daysByKey.count)
        #expect(viewModel.state.monthEventCache[YearMonthKey(date: createdEvent.startAt, calendar: calendar)]?.loadedEvents.map(\.id) == [100])
    }

    @MainActor
    @Test func calendarHomeViewModelKeepsFailureStateAndMapsBackendCreateError() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository(
            createError: EventRepositoryError.backend(
                statusCode: 400,
                response: ErrorResponseDTO(
                    errorCode: "INVALID_TIME_RANGE",
                    message: "invalid"
                )
            )
        )
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            initialState: makeLoadedState(dayOffsets: 0...2, focusedOffset: 0, from: baseDate, calendar: calendar)
        )

        let didCreate = await viewModel.createEvent(
            EventCreateInput(
                title: "실패 일정",
                description: "입력은 보존되어야 함",
                startAt: baseDate,
                endAt: baseDate
            )
        )

        #expect(!didCreate)
        #expect(viewModel.createState.failureMessage == "종료 시각은 시작 시각보다 늦어야 합니다.")
    }

    @MainActor
    @Test func calendarHomeViewModelClearsFailureOnRetryAndBlocksDuplicateCreateWhileSaving() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository(
            createResponse: makeEventResponse(from: makeEvent(on: baseDate)),
            createError: EventRepositoryError.network(URLError(.notConnectedToInternet)),
            shouldSuspendCreate: true
        )
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            initialState: makeLoadedState(dayOffsets: 0...2, focusedOffset: 0, from: baseDate, calendar: calendar)
        )
        let input = EventCreateInput(
            title: "재시도 일정",
            description: "",
            startAt: baseDate,
            endAt: baseDate.addingTimeInterval(3600)
        )

        let didFailCreate = await viewModel.createEvent(input)
        repository.setCreateError(nil)

        let createTask = Task {
            await viewModel.createEvent(input)
        }
        let didStartCreate = await repository.waitForCreateRequestCount(2)
        let didDuplicateCreate = await viewModel.createEvent(input)

        #expect(!didFailCreate)
        #expect(didStartCreate)
        #expect(viewModel.createState == .saving)
        #expect(!didDuplicateCreate)
        #expect(repository.createRequests.count == 2)

        repository.finishSuspendedCreateRequests()
        _ = await createTask.value
    }
    
    private var fixedCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }
    
    private func makeDayKey(dayOffset: Int, from baseDate: Date, calendar: Calendar) -> DayKey {
        let date = calendar.date(byAdding: .day, value: dayOffset, to: baseDate) ?? baseDate
        return DayKey(date: date, calendar: calendar)
    }
    
    private func makeDateCellItem(
        dayOffset: Int,
        from baseDate: Date,
        calendar: Calendar,
        events: [Event] = []
    ) -> CalendarDateCellItem {
        let date = calendar.date(byAdding: .day, value: dayOffset, to: baseDate) ?? baseDate
        let dateService = CalendarDateService(calendar: calendar)
        
        return CalendarDateCellItem(
            id: DayKey(date: date, calendar: calendar),
            weekday: dateService.getWeekday(from: date),
            monthText: dateService.monthText(from: date),
            dayText: dateService.dayText(from: date),
            isToday: false,
            isSelected: false,
            events: events
        )
    }

    private func makeLoadedState(
        dayOffsets: ClosedRange<Int>,
        focusedOffset: Int,
        from baseDate: Date,
        calendar: Calendar,
        monthEventCache: [YearMonthKey: CalendarMonthEventCacheEntry] = [:]
    ) -> CalendarState {
        let items = dayOffsets.map { offset in
            makeDateCellItem(dayOffset: offset, from: baseDate, calendar: calendar)
        }
        let startDate = calendar.date(byAdding: .day, value: dayOffsets.lowerBound, to: baseDate) ?? baseDate
        let endDate = calendar.date(byAdding: .day, value: dayOffsets.upperBound, to: baseDate) ?? baseDate

        return CalendarState(
            startDate: startDate,
            endDate: endDate,
            daysByKey: Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) }),
            focusedDay: makeDayKey(dayOffset: focusedOffset, from: baseDate, calendar: calendar),
            monthEventCache: monthEventCache
        )
    }
    
    private func makeEvent(
        id: Int64 = 1,
        title: String = "테스트 일정",
        on date: Date
    ) -> Event {
        Event(
            id: id,
            title: title,
            description: "",
            startAt: date,
            endAt: date.addingTimeInterval(3600),
            colorCode: "#4F46E5"
        )
    }

    private func makeEventResponse(from event: Event) -> EventResponseDTO {
        EventResponseDTO(
            id: event.id,
            title: event.title,
            description: event.description,
            startAt: event.startAt,
            endAt: event.endAt,
            createdAt: event.startAt,
            updatedAt: event.startAt
        )
    }

    private func requestBodyData(from request: URLRequest) -> Data? {
        if let httpBody = request.httpBody {
            return httpBody
        }

        guard let stream = request.httpBodyStream else {
            return nil
        }

        stream.open()
        defer {
            stream.close()
        }

        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 1024)

        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)

            guard count > 0 else {
                break
            }

            data.append(buffer, count: count)
        }

        return data
    }

}

private final class RecordingEventRepository: EventRepository {
    private(set) var requests: [(startDate: Date, endDate: Date)] = []
    private(set) var createRequests: [CreateEventRequestDTO] = []
    private var suspendedContinuations: [CheckedContinuation<[EventResponseDTO], Error>] = []
    private var suspendedCreateContinuations: [CheckedContinuation<EventResponseDTO, Error>] = []
    private let shouldSuspend: Bool
    private let shouldSuspendCreate: Bool
    private let error: Error?
    private var createError: Error?
    private let createResponse: EventResponseDTO

    init(
        shouldSuspend: Bool = false,
        error: Error? = nil,
        createResponse: EventResponseDTO = EventResponseDTO(
            id: 1,
            title: "생성된 일정",
            description: "",
            startAt: Date(timeIntervalSince1970: 0),
            endAt: Date(timeIntervalSince1970: 3600),
            createdAt: Date(timeIntervalSince1970: 0),
            updatedAt: Date(timeIntervalSince1970: 0)
        ),
        createError: Error? = nil,
        shouldSuspendCreate: Bool = false
    ) {
        self.shouldSuspend = shouldSuspend
        self.shouldSuspendCreate = shouldSuspendCreate
        self.error = error
        self.createError = createError
        self.createResponse = createResponse
    }

    var requestCount: Int {
        requests.count
    }

    func fetchEvents(from startDate: Date, to endDate: Date) async throws -> [EventResponseDTO] {
        requests.append((startDate, endDate))

        if let error {
            throw error
        }

        if shouldSuspend {
            return try await withCheckedThrowingContinuation { continuation in
                suspendedContinuations.append(continuation)
            }
        }

        return []
    }

    func createEvent(_ request: CreateEventRequestDTO) async throws -> EventResponseDTO {
        createRequests.append(request)

        if let createError {
            throw createError
        }

        if shouldSuspendCreate {
            return try await withCheckedThrowingContinuation { continuation in
                suspendedCreateContinuations.append(continuation)
            }
        }

        return createResponse
    }

    func setCreateError(_ error: Error?) {
        createError = error
    }

    func waitForRequestCount(
        _ count: Int,
        timeoutNanoseconds: UInt64 = 5_000_000_000
    ) async -> Bool {
        let retryIntervalNanoseconds: UInt64 = 10_000_000
        let maxAttemptCount = Int(timeoutNanoseconds / retryIntervalNanoseconds)
        
        for _ in 0...maxAttemptCount {
            if requests.count >= count {
                return true
            }
            
            try? await Task.sleep(nanoseconds: retryIntervalNanoseconds)
        }
        
        return requests.count >= count
    }

    func finishSuspendedRequests() {
        let continuations = suspendedContinuations
        suspendedContinuations.removeAll()
        continuations.forEach { continuation in
            continuation.resume(returning: [])
        }
    }

    func waitForCreateRequestCount(
        _ count: Int,
        timeoutNanoseconds: UInt64 = 5_000_000_000
    ) async -> Bool {
        let retryIntervalNanoseconds: UInt64 = 10_000_000
        let maxAttemptCount = Int(timeoutNanoseconds / retryIntervalNanoseconds)

        for _ in 0...maxAttemptCount {
            if createRequests.count >= count {
                return true
            }

            try? await Task.sleep(nanoseconds: retryIntervalNanoseconds)
        }

        return createRequests.count >= count
    }

    func finishSuspendedCreateRequests() {
        let continuations = suspendedCreateContinuations
        suspendedCreateContinuations.removeAll()
        continuations.forEach { continuation in
            continuation.resume(returning: createResponse)
        }
    }

    func requestMonthKeys(calendar: Calendar) -> [YearMonthKey] {
        requests.map { request in
            YearMonthKey(date: request.startDate, calendar: calendar)
        }.sorted()
    }
}

private final class MockURLProtocol: URLProtocol {
    static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        guard let handler = MockURLProtocol.requestHandler else {
            return
        }

        do {
            let (response, data) = try handler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}
