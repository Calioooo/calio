import Testing
import Foundation
import SwiftUI
@testable import Calio

@Suite(.serialized)
struct CalendarHomeViewModelTests {

    @MainActor
    @Test func calendarHomeViewModelTagMutationKeepsValidationFailurePresentation() async throws {
        let calendar = fixedCalendar
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: RecordingEventRepository()),
            tagService: TagService(
                repository: RecordingTagRepository(
                    createError: APIError.backend(
                        statusCode: 400,
                        problem: ProblemDetailDTO(
                            type: nil,
                            title: "Validation failed",
                            status: 400,
                            detail: nil,
                            errorCode: "VALIDATION_FAILED"
                        )
                    )
                )
            ),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar)
        )

        let didCreate = await viewModel.createCustomTag(
            CustomTagInput(title: "", colorCode: "#10B981")
        )

        #expect(!didCreate)
        #expect(viewModel.tagMutationState == .failed(.validationFailed))
        #expect(viewModel.tagMutationState.failureMessage == "태그 이름과 색상을 확인해 주세요.")
    }

    @MainActor
    @Test func calendarHomeViewModelSetReferenceDayUpdatesCanonicalReferenceDay() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let referenceDay = makeDayKey(dayOffset: 3, from: baseDate, calendar: calendar)
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: RecordingEventRepository()),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(dayOffsets: 0...10, from: baseDate, calendar: calendar)
        )

        viewModel.setReferenceDay(referenceDay)

        #expect(viewModel.referenceDay == referenceDay)
    }

    @MainActor
    @Test func calendarHomeViewModelSetReferenceDayInsideLoadedRangePreservesDateCells() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let event = makeEvent(id: 42, on: baseDate)
        let items = (0...10).map { offset in
            makeDateCellItem(
                dayOffset: offset,
                from: baseDate,
                calendar: calendar,
                events: offset == 0 ? [event] : []
            )
        }
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: RecordingEventRepository()),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: CalendarState(
                startDate: baseDate,
                endDate: try #require(calendar.date(byAdding: .day, value: 10, to: baseDate)),
                daysByKey: Dictionary(uniqueKeysWithValues: items.map { ($0.id, $0) })
            )
        )

        viewModel.setReferenceDay(makeDayKey(dayOffset: 3, from: baseDate, calendar: calendar))

        #expect(viewModel.state.daysByKey[DayKey(date: baseDate, calendar: calendar)]?.events.map(\.backendId) == [42])
    }

    @MainActor
    @Test func calendarHomeViewModelInitialLoadRequestsReferenceAndAdjacentMonthsOnly() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository()
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialReferenceDate: baseDate
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
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(dayOffsets: 0...10, from: baseDate, calendar: calendar)
        )

        viewModel.selectYearMonth(year: 2036, month: 6)
        let didRequestTargetMonths = await repository.waitForRequestCount(3)

        #expect(viewModel.referenceDay == DayKey(year: 2036, month: 6, day: 8))
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
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(dayOffsets: 0...10, from: baseDate, calendar: calendar)
        )

        viewModel.selectYearMonth(year: 2026, month: 2)

        #expect(viewModel.referenceDay == DayKey(year: 2026, month: 2, day: 28))
    }

    @MainActor
    @Test func calendarHomeViewModelSuppressesDuplicateLoadingAndLoadedMonthRequests() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository(shouldSuspend: true)
        let initialState = makeLoadedState(
            dayOffsets: 0...10,
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
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: initialState
        )

        viewModel.retryEventLoading()
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
    @Test func calendarHomeViewModelDiscardsEventResponseStartedBeforeGlobalCacheInvalidation() async throws {
        let calendar = fixedCalendar
        let june = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let staleEvent = makeEvent(id: 1, title: "이전 응답", on: june)
        let freshEvent = makeEvent(id: 2, title: "새 응답", on: june)
        let repository = RecordingEventRepository(shouldSuspend: true)
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialReferenceDate: june
        )

        viewModel.loadInitialIfNeeded()
        #expect(await repository.waitForRequestCount(3))

        viewModel.refreshAfterAssistantResponse()
        #expect(await repository.waitForRequestCount(6))

        repository.finishNextSuspendedRequest(returning: [])
        repository.finishNextSuspendedRequest(returning: [makeEventResponse(from: staleEvent)])
        await Task.yield()

        let juneKey = YearMonthKey(date: june, calendar: calendar)
        #expect(viewModel.state.monthEventCache[juneKey]?.isLoading == true)
        #expect(viewModel.state.monthEventCache[juneKey]?.loadedEvents.isEmpty == true)

        repository.finishNextSuspendedRequest(returning: [])
        repository.finishNextSuspendedRequest(returning: [])
        repository.finishNextSuspendedRequest(returning: [makeEventResponse(from: freshEvent)])
        repository.finishNextSuspendedRequest(returning: [])
        #expect(await waitUntil {
            viewModel.state.monthEventCache[juneKey]?.loadedEvents.map(\.backendId) == [freshEvent.backendId]
        })
    }

    @MainActor
    @Test func calendarHomeViewModelRefreshesVisibleMonthPrefetchRangeAfterAssistantResponse() async throws {
        let calendar = fixedCalendar
        let december = try #require(calendar.date(from: DateComponents(year: 2026, month: 12, day: 8)))
        let june = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository(shouldSuspend: true)
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(dayOffsets: 0...10, from: december, calendar: calendar)
        )

        viewModel.loadAdditionalEventsIfNeeded(
            visibleRange: CalendarVisibleIndexRange(startIndex: 0, endIndex: 10)
        )
        #expect(await repository.waitForRequestCount(3))
        repository.finishSuspendedRequests()

        viewModel.setReferenceDay(DayKey(date: june, calendar: calendar))
        viewModel.refreshAfterAssistantResponse()
        #expect(await repository.waitForRequestCount(9))

        let requestedMonths = Set(repository.requestMonthKeys(calendar: calendar))
        #expect(requestedMonths.isSuperset(of: [
            YearMonthKey(year: 2026, month: 5),
            YearMonthKey(year: 2026, month: 6),
            YearMonthKey(year: 2026, month: 7),
            YearMonthKey(year: 2026, month: 11),
            YearMonthKey(year: 2026, month: 12),
            YearMonthKey(year: 2027, month: 1),
        ]))
        #expect(viewModel.referenceDay == DayKey(date: june, calendar: calendar))
        repository.finishSuspendedRequests()
    }

    @MainActor
    @Test func calendarHomeViewModelKeepsReferenceAndExposesRetryStateWhenTargetMonthFails() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository(error: APIError.network(URLError(.notConnectedToInternet)))
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(dayOffsets: 0...10, from: baseDate, calendar: calendar)
        )

        viewModel.selectYearMonth(year: 2036, month: 6)
        let didRequestTargetMonths = await repository.waitForRequestCount(3)
        let didExposeFailure = await waitUntil {
            viewModel.eventLoadState == .failed("일정을 불러오지 못했습니다. 네트워크 연결을 확인해 주세요.")
        }

        #expect(didRequestTargetMonths)
        #expect(viewModel.referenceDay == DayKey(year: 2036, month: 6, day: 8))
        #expect(didExposeFailure)
    }
    @MainActor
    @Test func calendarHomeViewModelLoadsHolidaysIntoSeparateDateCellItems() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let holidayRepository = RecordingNationalHolidayRepository(
            responsesByMonth: [
                YearMonthKey(year: 2026, month: 6): [
                    NationalHolidayResponseDTO(
                        nationalHolidayId: 1,
                        holidayDate: "2026-06-06",
                        holidayTitle: "현충일"
                    ),
                    NationalHolidayResponseDTO(
                        nationalHolidayId: 2,
                        holidayDate: "2026-06-06",
                        holidayTitle: "대체 공휴일"
                    )
                ]
            ]
        )
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: RecordingEventRepository()),
            nationalHolidayService: NationalHolidayService(
                repository: holidayRepository,
                calendar: calendar
            ),
            initialReferenceDate: baseDate
        )

        viewModel.loadInitialIfNeeded()
        let didRequestHolidayMonths = await holidayRepository.waitForRequestCount(3)
        let didLoadHolidays = await waitUntil {
            viewModel.state.daysByKey[DayKey(year: 2026, month: 6, day: 6)]?.holidays.count == 2
        }
        let holidayDayItem = viewModel.state.daysByKey[DayKey(year: 2026, month: 6, day: 6)]

        #expect(didRequestHolidayMonths)
        #expect(holidayRepository.requestMonthKeys == [
            YearMonthKey(year: 2026, month: 5),
            YearMonthKey(year: 2026, month: 6),
            YearMonthKey(year: 2026, month: 7)
        ])
        #expect(didLoadHolidays)
        #expect(holidayDayItem?.events.isEmpty == true)
        #expect(holidayDayItem?.holidays.map(\.title) == ["현충일", "대체 공휴일"])
    }

    @MainActor
    @Test func calendarHomeViewModelHolidayFailureKeepsEventAreaUsable() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let event = makeEvent(id: 44, on: baseDate)
        let eventRepository = RecordingEventRepository(fetchResponse: [makeEventResponse(from: event)])
        let holidayRepository = RecordingNationalHolidayRepository(
            error: APIError.network(URLError(.notConnectedToInternet))
        )
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: eventRepository),
            nationalHolidayService: NationalHolidayService(
                repository: holidayRepository,
                calendar: calendar
            ),
            initialReferenceDate: baseDate
        )

        viewModel.loadInitialIfNeeded()
        let didLoadEvents = await waitUntil {
            viewModel.state.daysByKey[DayKey(date: baseDate, calendar: calendar)]?.events.map(\.backendId) == [44]
        }
        let didStoreHolidayFailure = await waitUntil {
            if case .failed = viewModel.state.monthHolidayCache[YearMonthKey(year: 2026, month: 6)] {
                return true
            }

            return false
        }

        #expect(didLoadEvents)
        #expect(didStoreHolidayFailure)
        #expect(viewModel.eventLoadState == .idle)
        #expect(viewModel.state.daysByKey[DayKey(date: baseDate, calendar: calendar)]?.holidays.isEmpty == true)
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
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(dayOffsets: 0...2, from: baseDate, calendar: calendar)
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
        #expect(viewModel.state.daysByKey[eventDay]?.events.map(\.backendId) == [99])
        #expect(viewModel.createState == .idle)
    }

    @MainActor
    @Test func calendarHomeViewModelDoesNotTreatCreatedEventInIdleMonthAsLoadedCache() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let startAt = try #require(calendar.date(byAdding: DateComponents(day: 5, hour: 9), to: baseDate))
        let createdEvent = makeEvent(id: 100, title: "범위 밖 일정", on: startAt)
        let repository = RecordingEventRepository(createResponse: makeEventResponse(from: createdEvent))
        let initialState = makeLoadedState(dayOffsets: 0...2, from: baseDate, calendar: calendar)
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
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
        let monthKey = YearMonthKey(date: createdEvent.startAt, calendar: calendar)

        #expect(didCreate)
        #expect(repository.requests.isEmpty)
        #expect(viewModel.state.startDate == initialState.startDate)
        #expect(viewModel.state.endDate == initialState.endDate)
        #expect(viewModel.state.daysByKey.count == initialState.daysByKey.count)
        #expect(!viewModel.state.monthEventCache.keys.contains(monthKey))
    }

    @MainActor
    @Test func calendarHomeViewModelDoesNotReplaceLoadingMonthCacheWhenCreatedEventArrives() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let startAt = try #require(calendar.date(byAdding: DateComponents(day: 1, hour: 9), to: baseDate))
        let createdEvent = makeEvent(id: 101, title: "로딩 중 생성된 일정", on: startAt)
        let monthKey = YearMonthKey(date: createdEvent.startAt, calendar: calendar)
        let repository = RecordingEventRepository(createResponse: makeEventResponse(from: createdEvent))
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(
                dayOffsets: 0...2,
                from: baseDate,
                calendar: calendar,
                monthEventCache: [monthKey: .loading]
            )
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
        #expect(viewModel.state.monthEventCache[monthKey]?.isLoading == true)
        #expect(viewModel.state.monthEventCache[monthKey]?.loadedEvents.isEmpty == true)
        #expect(viewModel.state.daysByKey[DayKey(date: createdEvent.startAt, calendar: calendar)]?.events.map(\.backendId) == [101])
    }

    @MainActor
    @Test func calendarHomeViewModelKeepsFailureStateAndMapsBackendCreateError() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository(
            createError: APIError.backend(
                statusCode: 400,
                problem: ProblemDetailDTO(
                    type: "about:blank",
                    title: "INVALID_TIME_RANGE",
                    status: 400,
                    detail: "invalid",
                    errorCode: "INVALID_TIME_RANGE",
                )
            )
        )
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(dayOffsets: 0...2, from: baseDate, calendar: calendar)
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
    @Test func calendarHomeViewModelMapsCanonicalScheduleValidationFailures() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))

        for errorCode in ["INVALID_ALL_DAY_SCHEDULE", "INVALID_TIME_ZONE"] {
            let repository = RecordingEventRepository(
                createError: APIError.backend(
                    statusCode: 400,
                    problem: ProblemDetailDTO(
                        type: "about:blank",
                        title: errorCode,
                        status: 400,
                        detail: "invalid",
                        errorCode: errorCode
                    )
                )
            )
            let viewModel = CalendarHomeViewModel(
                calendar: calendar,
                dateService: CalendarDateService(calendar: calendar),
                eventService: EventService(repository: repository),
                nationalHolidayService: makeNationalHolidayService(calendar: calendar),
                initialState: makeLoadedState(dayOffsets: 0...2, from: baseDate, calendar: calendar)
            )

            let didCreate = await viewModel.createEvent(
                EventCreateInput(
                    title: "일정",
                    description: "",
                    startAt: baseDate,
                    endAt: baseDate.addingTimeInterval(3600)
                )
            )

            #expect(!didCreate)
            #expect(viewModel.createState.failureMessage == "입력값을 확인해 주세요.")
        }
    }

    @MainActor
    @Test func calendarHomeViewModelClearsFailureOnRetryAndBlocksDuplicateCreateWhileSaving() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository(
            createResponse: makeEventResponse(from: makeEvent(on: baseDate)),
            createError: APIError.network(URLError(.notConnectedToInternet)),
            shouldSuspendCreate: true
        )
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(dayOffsets: 0...2, from: baseDate, calendar: calendar)
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

    @MainActor
    @Test func calendarHomeViewModelRefetchesDefaultRangeAfterRecurrenceCreateWithoutSynthesizingEvents() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let cachedEvent = makeEvent(id: 10, title: "기존 일정", on: baseDate)
        let repository = RecordingEventRepository()
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(
                dayOffsets: 0...2,
                from: baseDate,
                calendar: calendar,
                monthEventCache: [
                    YearMonthKey(year: 2026, month: 5): .loaded([]),
                    YearMonthKey(year: 2026, month: 6): .loaded([cachedEvent]),
                    YearMonthKey(year: 2026, month: 7): .loaded([])
                ]
            )
        )

        let didCreate = await viewModel.createEvent(
            .recurring(
                RecurrenceEventCreateInput(
                    title: "반복 회의",
                    description: "설명",
                    recurrenceStartDate: baseDate,
                    recurrenceEndDate: baseDate.addingTimeInterval(86400),
                    recurrenceStartTime: baseDate,
                    recurrenceEndTime: baseDate.addingTimeInterval(3600),
                    recurrenceFrequency: .daily
                )
            )
        )
        let didRequestDefaultRange = await repository.waitForRequestCount(3)
        await Task.yield()

        #expect(didCreate)
        #expect(didRequestDefaultRange)
        #expect(repository.createRequests.isEmpty)
        #expect(repository.recurrenceCreateRequests.count == 1)
        #expect(repository.requestMonthKeys(calendar: calendar) == [
            YearMonthKey(year: 2026, month: 5),
            YearMonthKey(year: 2026, month: 6),
            YearMonthKey(year: 2026, month: 7)
        ])
        #expect(viewModel.state.daysByKey[DayKey(date: baseDate, calendar: calendar)]?.events.isEmpty == true)
    }

    @MainActor
    @Test func calendarHomeViewModelRefetchesOriginalAndUpdatedMonthsAfterSingleEventUpdate() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let updatedStartAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 7, day: 2, hour: 9)))
        let originalEvent = makeEvent(id: 501, title: "월 이동 일정", on: baseDate)
        let updatedEvent = Event(
            id: originalEvent.backendId,
            title: "수정된 일정",
            description: "수정",
            startAt: updatedStartAt,
            endAt: updatedStartAt.addingTimeInterval(3600),
            tag: .sample(colorCode: "#4F46E5")
        )
        let repository = RecordingEventRepository(
            updateResponse: makeEventResponse(from: updatedEvent)
        )
        let initialState = makeLoadedState(
            dayOffsets: 0...40,
            from: baseDate,
            calendar: calendar,
            monthEventCache: [
                YearMonthKey(year: 2026, month: 6): .loaded([originalEvent]),
                YearMonthKey(year: 2026, month: 7): .loaded([])
            ]
        )
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: initialState
        )

        let didUpdate = await viewModel.updateSingleEvent(
            originalEvent,
            input: EventUpdateInput(
                title: updatedEvent.title,
                description: updatedEvent.description,
                startAt: updatedEvent.startAt,
                endAt: updatedEvent.endAt
            )
        )
        let didRequestAffectedMonths = await repository.waitForRequestCount(2)

        #expect(didUpdate)
        #expect(didRequestAffectedMonths)
        #expect(repository.updateRequests.count == 1)
        #expect(repository.requestMonthKeys(calendar: calendar) == [
            YearMonthKey(year: 2026, month: 6),
            YearMonthKey(year: 2026, month: 7)
        ])
        #expect(viewModel.mutationState == .idle)
    }

    @MainActor
    @Test func calendarHomeViewModelUpdatesOnlyIndependentEventImportanceAndRefetchesItsMonth() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8, hour: 9)))
        let event = makeEvent(id: 503, title: "중요 표시 대상", on: baseDate)
        let response = EventResponseDTO(
            id: 503,
            title: event.title,
            description: event.description,
            startAt: event.startAt,
            endAt: event.endAt,
            allDay: false,
            timeZone: "Asia/Seoul",
            importantEvent: true,
            tag: TagResponseDTO(id: event.tag.id, title: event.tag.title, colorCode: event.tag.colorCode, tagType: event.tag.tagType),
            createdAt: event.startAt,
            updatedAt: event.startAt
        )
        let repository = RecordingEventRepository(updateResponse: response)
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(
                dayOffsets: 0...2,
                from: baseDate,
                calendar: calendar,
                monthEventCache: [YearMonthKey(date: baseDate, calendar: calendar): .loaded([event])]
            )
        )

        let updatedEvent = await viewModel.updateImportantEvent(event, importantEvent: true)
        let didRequestMonth = await repository.waitForRequestCount(1)

        #expect(updatedEvent?.importantEvent == true)
        #expect(repository.updateImportantEventRequests.first?.eventId == 503)
        #expect(repository.updateImportantEventRequests.first?.request.importantEvent == true)
        #expect(didRequestMonth)
        #expect(repository.requestMonthKeys(calendar: calendar) == [
            YearMonthKey(date: baseDate, calendar: calendar)
        ])
        #expect(viewModel.mutationState == .idle)
    }

    @MainActor
    @Test func calendarHomeViewModelDoesNotRequestImportanceMutationForRecurrenceOccurrence() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8, hour: 9)))
        let occurrence = Event(
            id: 506,
            title: "반복 회차",
            description: "",
            startAt: baseDate,
            endAt: baseDate.addingTimeInterval(3600),
            tag: .sample(colorCode: "#4F46E5"),
            recurrenceId: 700,
            isRecurrenceOccurrence: true,
            originStartAt: baseDate
        )
        let repository = RecordingEventRepository()
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar)
        )

        let result = await viewModel.updateImportantEvent(occurrence, importantEvent: true)

        #expect(result == nil)
        #expect(repository.updateImportantEventRequests.isEmpty)
        #expect(viewModel.mutationState == .idle)
    }

    @MainActor
    @Test func calendarHomeViewModelPreventsDuplicateImportanceMutationWhileRequestIsInFlight() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8, hour: 9)))
        let event = makeEvent(id: 505, title: "중복 요청 방지", on: baseDate)
        let repository = RecordingEventRepository(shouldSuspendImportantEvent: true)
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar)
        )

        let firstRequest = Task { await viewModel.updateImportantEvent(event, importantEvent: true) }
        let didStartRequest = await repository.waitForImportantEventRequestCount(1)
        #expect(didStartRequest)

        let duplicateRequest = await viewModel.updateImportantEvent(event, importantEvent: true)
        #expect(duplicateRequest == nil)
        #expect(repository.updateImportantEventRequests.count == 1)

        repository.finishSuspendedImportantEventRequests()
        _ = await firstRequest.value
    }

    @MainActor
    @Test func calendarHomeViewModelKeepsImportanceStateUnchangedWhenMutationFails() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8, hour: 9)))
        let event = Event(
            id: 504,
            title: "실패 대상",
            description: "",
            startAt: baseDate,
            endAt: baseDate.addingTimeInterval(3600),
            tag: .sample(colorCode: "#4F46E5"),
            importantEvent: false
        )
        let repository = RecordingEventRepository(
            updateError: APIError.network(URLError(.notConnectedToInternet))
        )
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(
                dayOffsets: 0...2,
                from: baseDate,
                calendar: calendar,
                monthEventCache: [YearMonthKey(date: baseDate, calendar: calendar): .loaded([event])]
            )
        )

        let result = await viewModel.updateImportantEvent(event, importantEvent: true)

        #expect(result == nil)
        #expect(repository.updateImportantEventRequests.count == 1)
        #expect(viewModel.state.monthEventCache[YearMonthKey(date: baseDate, calendar: calendar)]?.loadedEvents
            .first?.importantEvent == false)
        #expect(viewModel.mutationState == .failed(.network))
        #expect(viewModel.mutationState.failureMessage == "서버에 연결할 수 없습니다.")
    }

    @MainActor
    @Test func calendarHomeViewModelEventMutationDoesNotInvalidateLoadedHolidayCache() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let event = makeEvent(id: 601, title: "수정 대상", on: baseDate)
        let holiday = NationalHoliday(
            id: 1,
            day: DayKey(date: baseDate, calendar: calendar),
            title: "공휴일"
        )
        let eventRepository = RecordingEventRepository(
            updateResponse: makeEventResponse(from: event)
        )
        let holidayRepository = RecordingNationalHolidayRepository()
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: eventRepository),
            nationalHolidayService: NationalHolidayService(
                repository: holidayRepository,
                calendar: calendar
            ),
            initialState: makeLoadedState(
                dayOffsets: 0...2,
                from: baseDate,
                calendar: calendar,
                monthEventCache: [YearMonthKey(year: 2026, month: 6): .loaded([event])],
                monthHolidayCache: [YearMonthKey(year: 2026, month: 6): .loaded([holiday])]
            )
        )

        let didUpdate = await viewModel.updateSingleEvent(
            event,
            input: EventUpdateInput(
                title: "수정 대상",
                description: "",
                startAt: event.startAt,
                endAt: event.endAt
            )
        )
        let didRequestEventMonth = await eventRepository.waitForRequestCount(1)

        #expect(didUpdate)
        #expect(didRequestEventMonth)
        #expect(holidayRepository.requestCount == 0)
        #expect(viewModel.state.monthHolidayCache[YearMonthKey(year: 2026, month: 6)]?.loadedHolidays.map(\.id) == [1])
        #expect(viewModel.state.daysByKey[holiday.day]?.holidays.map(\.id) == [1])
    }

    @MainActor
    @Test func calendarHomeViewModelRefetchesOriginalMonthAfterSingleEventDelete() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let event = makeEvent(id: 502, title: "삭제 일정", on: baseDate)
        let repository = RecordingEventRepository()
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(
                dayOffsets: 0...2,
                from: baseDate,
                calendar: calendar,
                monthEventCache: [YearMonthKey(year: 2026, month: 6): .loaded([event])]
            )
        )

        let didDelete = await viewModel.deleteSingleEvent(event)
        let didRequestOriginalMonth = await repository.waitForRequestCount(1)

        #expect(didDelete)
        #expect(didRequestOriginalMonth)
        #expect(repository.deleteEventIDs == [502])
        #expect(repository.requestMonthKeys(calendar: calendar) == [
            YearMonthKey(year: 2026, month: 6)
        ])
    }

    @MainActor
    @Test func calendarHomeViewModelRefetchesDefaultRangeAfterRecurrenceDelete() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let recurrenceEvent = Event(
            id: 503,
            title: "반복 일정",
            description: "",
            startAt: baseDate,
            endAt: baseDate.addingTimeInterval(3600),
            tag: .sample(colorCode: "#4F46E5"),
            recurrenceId: 700,
            isRecurrenceOccurrence: true,
            originStartAt: baseDate
        )
        let repository = RecordingEventRepository()
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(
                dayOffsets: 0...2,
                from: baseDate,
                calendar: calendar,
                monthEventCache: [
                    YearMonthKey(year: 2026, month: 5): .loaded([]),
                    YearMonthKey(year: 2026, month: 6): .loaded([recurrenceEvent]),
                    YearMonthKey(year: 2026, month: 7): .loaded([])
                ]
            )
        )

        let didDeleteOccurrence = await viewModel.deleteRecurrenceOccurrence(recurrenceEvent)
        let didRequestDefaultRange = await repository.waitForRequestCount(3)

        #expect(didDeleteOccurrence)
        #expect(didRequestDefaultRange)
        #expect(repository.deleteRecurrenceOccurrenceRequests.count == 1)
        #expect(repository.deleteRecurrenceOccurrenceRequests.first?.originStartAt == baseDate)
        #expect(repository.requestMonthKeys(calendar: calendar) == [
            YearMonthKey(year: 2026, month: 5),
            YearMonthKey(year: 2026, month: 6),
            YearMonthKey(year: 2026, month: 7)
        ])
        #expect(viewModel.state.daysByKey[DayKey(date: baseDate, calendar: calendar)]?.events.isEmpty == true)
    }

    @MainActor
    @Test func calendarHomeViewModelFetchesRecurrenceRuleBeforeSeriesEdit() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository(
            fetchRecurrenceResponse: RecurrenceEventResponseDTO(
                recurrenceId: 700,
                recurrenceTitle: "반복 회의",
                recurrenceDescription: "설명",
                recurrenceStartDate: "2026-08-01",
                recurrenceEndDate: "2026-08-31",
                recurrenceStartTime: "00:00:00",
                recurrenceEndTime: "01:00:00",
                recurrenceFrequency: .weekly
            )
        )
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(dayOffsets: 0...2, from: baseDate, calendar: calendar)
        )

        let details = await viewModel.fetchRecurrenceEvent(recurrenceId: 700)

        #expect(repository.fetchRecurrenceEventIDs == [700])
        #expect(details?.title == "반복 회의")
        #expect(details?.recurrenceFrequency == .weekly)
        #expect(viewModel.mutationState == .idle)
    }

    @MainActor
    @Test func calendarHomeViewModelRecurrenceOccurrenceUpdateSendsOriginStartAtAndRefetchesDefaultRange() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let event = Event(
            id: 701,
            title: "반복 항목",
            description: "",
            startAt: baseDate,
            endAt: baseDate.addingTimeInterval(3600),
            tag: .sample(colorCode: "#4F46E5"),
            importantEvent: true,
            recurrenceId: 700,
            isRecurrenceOccurrence: true,
            originStartAt: baseDate
        )
        let repository = RecordingEventRepository()
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(
                dayOffsets: 0...2,
                from: baseDate,
                calendar: calendar,
                monthEventCache: [
                    YearMonthKey(year: 2026, month: 5): .loaded([]),
                    YearMonthKey(year: 2026, month: 6): .loaded([event]),
                    YearMonthKey(year: 2026, month: 7): .loaded([])
                ]
            )
        )

        let didUpdate = await viewModel.updateRecurrenceOccurrence(
            event,
            input: EventUpdateInput(
                title: "수정 반복 항목",
                description: "설명",
                startAt: baseDate,
                endAt: baseDate.addingTimeInterval(7200)
            )
        )
        let didRequestDefaultRange = await repository.waitForRequestCount(3)
        let request = try #require(repository.updateRecurrenceOccurrenceRequests.first)

        #expect(didUpdate)
        #expect(didRequestDefaultRange)
        #expect(request.recurrenceId == 700)
        #expect(request.request.originStartAt == baseDate)
        #expect(request.request.endAt == baseDate.addingTimeInterval(7200))
        #expect(repository.requestMonthKeys(calendar: calendar) == [
            YearMonthKey(year: 2026, month: 5),
            YearMonthKey(year: 2026, month: 6),
            YearMonthKey(year: 2026, month: 7)
        ])
    }

    @MainActor
    @Test func calendarHomeViewModelRecurrenceSeriesUpdateComposesUTCDateTimeAndRefetchesDefaultRange() async throws {
        let calendar = fixedCalendar
        let baseDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 8)))
        let repository = RecordingEventRepository()
        let viewModel = CalendarHomeViewModel(
            calendar: calendar,
            dateService: CalendarDateService(calendar: calendar),
            eventService: EventService(repository: repository),
            nationalHolidayService: makeNationalHolidayService(calendar: calendar),
            initialState: makeLoadedState(
                dayOffsets: 0...2,
                from: baseDate,
                calendar: calendar,
                monthEventCache: [
                    YearMonthKey(year: 2026, month: 5): .loaded([]),
                    YearMonthKey(year: 2026, month: 6): .loaded([]),
                    YearMonthKey(year: 2026, month: 7): .loaded([])
                ]
            )
        )
        let startDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 8, day: 1)))
        let endDate = try #require(calendar.date(from: DateComponents(year: 2026, month: 8, day: 31)))
        let startTime = try #require(calendar.date(from: DateComponents(year: 1970, month: 1, day: 1, hour: 9)))
        let endTime = try #require(calendar.date(from: DateComponents(year: 1970, month: 1, day: 1, hour: 10, minute: 30)))

        let didUpdate = await viewModel.updateRecurrenceSeries(
            recurrenceId: 700,
            input: RecurrenceEventSeriesEditInput(
                title: "수정 반복 일정",
                description: "설명",
                recurrenceStartDate: startDate,
                recurrenceEndDate: endDate,
                recurrenceStartTime: startTime,
                recurrenceEndTime: endTime,
                recurrenceFrequency: .monthly
            )
        )
        let didRequestDefaultRange = await repository.waitForRequestCount(3)
        let request = try #require(repository.updateRecurrenceEventRequests.first)

        #expect(didUpdate)
        #expect(didRequestDefaultRange)
        #expect(request.recurrenceId == 700)
        #expect(request.request.title == "수정 반복 일정")
        #expect(request.request.recurrenceFrequency == .monthly)
        #expect(request.request.startDate == "2026-08-01")
        #expect(request.request.endDate == "2026-08-31")
        #expect(request.request.startTime == "09:00:00")
        #expect(request.request.endTime == "10:30:00")
    }
}
