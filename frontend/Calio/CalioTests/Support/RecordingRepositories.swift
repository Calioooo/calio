import Testing
import Foundation
import SwiftUI
@testable import Calio

final class RecordingNationalHolidayRepository: NationalHolidayRepository {
    private let lock = NSLock()
    private var storedRequests: [(startDay: DayKey, endDay: DayKey)] = []
    private var requestCountWaiters: [CountWaiter] = []
    private let fetchResponse: [NationalHolidayResponseDTO]
    private let responsesByMonth: [YearMonthKey: [NationalHolidayResponseDTO]]
    private let error: Error?

    init(
        fetchResponse: [NationalHolidayResponseDTO] = [],
        responsesByMonth: [YearMonthKey: [NationalHolidayResponseDTO]] = [:],
        error: Error? = nil
    ) {
        self.fetchResponse = fetchResponse
        self.responsesByMonth = responsesByMonth
        self.error = error
    }

    var requestCount: Int {
        locked {
            storedRequests.count
        }
    }

    var requestMonthKeys: [YearMonthKey] {
        locked {
            storedRequests.map { YearMonthKey(day: $0.startDay) }.sorted()
        }
    }

    func fetchNationalHolidays(
        from startDay: DayKey,
        to endDay: DayKey
    ) async throws -> [NationalHolidayResponseDTO] {
        recordRequest(startDay: startDay, endDay: endDay)

        if let error {
            throw error
        }

        return responsesByMonth[YearMonthKey(day: startDay)] ?? fetchResponse
    }

    func waitForRequestCount(
        _ count: Int,
        timeoutNanoseconds: UInt64 = 5_000_000_000
    ) async -> Bool {
        let waiterID = UUID()

        return await withCheckedContinuation { continuation in
            let shouldWait = locked {
                guard storedRequests.count < count else {
                    return false
                }

                requestCountWaiters.append(
                    CountWaiter(
                        id: waiterID,
                        count: count,
                        continuation: continuation
                    )
                )
                return true
            }

            guard shouldWait else {
                continuation.resume(returning: true)
                return
            }

            Task {
                try? await Task.sleep(nanoseconds: timeoutNanoseconds)
                completeWaiterIfNeeded(id: waiterID)
            }
        }
    }

    private func recordRequest(startDay: DayKey, endDay: DayKey) {
        let continuations = locked {
            storedRequests.append((startDay, endDay))
            return readyWaiters(currentCount: storedRequests.count)
        }
        continuations.forEach { $0.resume(returning: true) }
    }

    private func completeWaiterIfNeeded(id: UUID) {
        let continuation = locked {
            guard let index = requestCountWaiters.firstIndex(where: { $0.id == id }) else {
                return nil as CheckedContinuation<Bool, Never>?
            }

            return requestCountWaiters.remove(at: index).continuation
        }

        continuation?.resume(returning: false)
    }

    private func readyWaiters(currentCount: Int) -> [CheckedContinuation<Bool, Never>] {
        let readyWaiters = requestCountWaiters.filter { currentCount >= $0.count }
        requestCountWaiters.removeAll { currentCount >= $0.count }
        return readyWaiters.map(\.continuation)
    }

    private func locked<T>(_ work: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return work()
    }

    private struct CountWaiter {
        let id: UUID
        let count: Int
        let continuation: CheckedContinuation<Bool, Never>
    }
}

final class RecordingTagRepository: TagRepository {
    private let fetchResponse: [TagResponseDTO]
    private let createResponse: TagResponseDTO
    private let updateResponse: TagResponseDTO
    private let fetchError: Error?
    private let createError: Error?
    private let updateError: Error?
    private let deleteError: Error?

    init(
        fetchResponse: [TagResponseDTO] = [],
        createResponse: TagResponseDTO = TagResponseDTO(
            id: 1,
            title: "운동",
            colorCode: "#10B981",
            tagType: .custom
        ),
        updateResponse: TagResponseDTO = TagResponseDTO(
            id: 1,
            title: "운동",
            colorCode: "#10B981",
            tagType: .custom
        ),
        fetchError: Error? = nil,
        createError: Error? = nil,
        updateError: Error? = nil,
        deleteError: Error? = nil
    ) {
        self.fetchResponse = fetchResponse
        self.createResponse = createResponse
        self.updateResponse = updateResponse
        self.fetchError = fetchError
        self.createError = createError
        self.updateError = updateError
        self.deleteError = deleteError
    }

    func fetchTags() async throws -> [TagResponseDTO] {
        if let fetchError {
            throw fetchError
        }
        return fetchResponse
    }

    func createCustomTag(_ request: CustomTagRequestDTO) async throws -> TagResponseDTO {
        if let createError {
            throw createError
        }
        return createResponse
    }

    func updateCustomTag(tagId: Int64, request: CustomTagRequestDTO) async throws -> TagResponseDTO {
        if let updateError {
            throw updateError
        }
        return updateResponse
    }

    func deleteCustomTag(tagId: Int64) async throws {
        if let deleteError {
            throw deleteError
        }
    }
}

final class RecordingEventRepository: EventRepository {
    private let lock = NSLock()
    private var storedRequests: [(startDate: Date, endDate: Date)] = []
    private var storedCreateRequests: [CreateEventRequestDTO] = []
    private var storedRecurrenceCreateRequests: [CreateRecurrenceEventRequestDTO] = []
    private var storedFetchRecurrenceEventIDs: [Int64] = []
    private var storedUpdateRequests: [(eventId: Int64, request: UpdateEventRequestDTO)] = []
    private var storedUpdateImportantEventRequests: [(eventId: Int64, request: UpdateImportantEventRequestDTO)] = []
    private var storedUpdateRecurrenceEventRequests: [(recurrenceId: Int64, request: UpdateRecurrenceEventRequestDTO)] = []
    private var storedUpdateRecurrenceOccurrenceRequests: [(recurrenceId: Int64, request: UpdateRecurrenceOccurrenceRequestDTO)] = []
    private var storedDeleteEventIDs: [Int64] = []
    private var storedDeleteRecurrenceEventIDs: [Int64] = []
    private var storedDeleteRecurrenceOccurrenceRequests: [(recurrenceId: Int64, originStartAt: Date)] = []
    private var suspendedContinuations: [CheckedContinuation<[EventResponseDTO], Error>] = []
    private var suspendedCreateContinuations: [CheckedContinuation<EventResponseDTO, Error>] = []
    private var suspendedImportantEventContinuations: [CheckedContinuation<EventResponseDTO, Error>] = []
    private var requestCountWaiters: [CountWaiter] = []
    private var createRequestCountWaiters: [CountWaiter] = []
    private var importantEventRequestCountWaiters: [CountWaiter] = []
    private let shouldSuspend: Bool
    private let shouldSuspendCreate: Bool
    private let shouldSuspendImportantEvent: Bool
    private let error: Error?
    private var createError: Error?
    private let createResponse: EventResponseDTO
    private let updateResponse: EventResponseDTO
    private let updateError: Error?
    private let deleteError: Error?
    private let recurrenceDeleteError: Error?
    private let recurrenceOccurrenceDeleteError: Error?
    private let fetchResponse: [EventResponseDTO]
    private let recurrenceCreateResponse: RecurrenceEventResponseDTO
    private let recurrenceCreateError: Error?
    private let fetchRecurrenceResponse: RecurrenceEventResponseDTO
    private let fetchRecurrenceError: Error?
    private let updateRecurrenceResponse: RecurrenceEventResponseDTO
    private let updateRecurrenceError: Error?
    private let updateRecurrenceOccurrenceResponse: EventResponseDTO
    private let updateRecurrenceOccurrenceError: Error?

    init(
        shouldSuspend: Bool = false,
        error: Error? = nil,
        fetchResponse: [EventResponseDTO] = [],
        createResponse: EventResponseDTO = EventResponseDTO(
            id: 1,
            title: "생성된 일정",
            description: "",
            startAt: Date(timeIntervalSince1970: 0),
            endAt: Date(timeIntervalSince1970: 3600),
            createdAt: Date(timeIntervalSince1970: 0),
            updatedAt: Date(timeIntervalSince1970: 0)
        ),
        updateResponse: EventResponseDTO = EventResponseDTO(
            id: 1,
            title: "수정된 일정",
            description: "",
            startAt: Date(timeIntervalSince1970: 0),
            endAt: Date(timeIntervalSince1970: 3600),
            createdAt: Date(timeIntervalSince1970: 0),
            updatedAt: Date(timeIntervalSince1970: 3600)
        ),
        createError: Error? = nil,
        updateError: Error? = nil,
        deleteError: Error? = nil,
        shouldSuspendCreate: Bool = false,
        shouldSuspendImportantEvent: Bool = false,
        recurrenceCreateResponse: RecurrenceEventResponseDTO = RecurrenceEventResponseDTO(
            recurrenceId: 1,
            title: "반복 일정", description: "", allDay: false,
            firstOccurrenceStartAt: Date(timeIntervalSince1970: 0), firstOccurrenceEndAt: Date(timeIntervalSince1970: 3600),
            timeZone: "UTC", recurrence: ["RRULE:FREQ=DAILY;UNTIL=19700101T000000Z"],
            tag: .init(id: 0, title: "기타", colorCode: "#64748B", tagType: .defaultTag),
            createdAt: Date(timeIntervalSince1970: 0), updatedAt: Date(timeIntervalSince1970: 0), canUpdateSeries: true
        ),
        recurrenceCreateError: Error? = nil,
        fetchRecurrenceResponse: RecurrenceEventResponseDTO = RecurrenceEventResponseDTO(
            recurrenceId: 1,
            title: "반복 일정", description: "", allDay: false,
            firstOccurrenceStartAt: Date(timeIntervalSince1970: 0), firstOccurrenceEndAt: Date(timeIntervalSince1970: 3600),
            timeZone: "UTC", recurrence: ["RRULE:FREQ=DAILY;UNTIL=19700101T000000Z"],
            tag: .init(id: 0, title: "기타", colorCode: "#64748B", tagType: .defaultTag),
            createdAt: Date(timeIntervalSince1970: 0), updatedAt: Date(timeIntervalSince1970: 0), canUpdateSeries: true
        ),
        fetchRecurrenceError: Error? = nil,
        updateRecurrenceResponse: RecurrenceEventResponseDTO = RecurrenceEventResponseDTO(
            recurrenceId: 1,
            title: "수정된 반복 일정", description: "", allDay: false,
            firstOccurrenceStartAt: Date(timeIntervalSince1970: 0), firstOccurrenceEndAt: Date(timeIntervalSince1970: 3600),
            timeZone: "UTC", recurrence: ["RRULE:FREQ=DAILY;UNTIL=19700101T000000Z"],
            tag: .init(id: 0, title: "기타", colorCode: "#64748B", tagType: .defaultTag),
            createdAt: Date(timeIntervalSince1970: 0), updatedAt: Date(timeIntervalSince1970: 0), canUpdateSeries: true
        ),
        updateRecurrenceError: Error? = nil,
        updateRecurrenceOccurrenceResponse: EventResponseDTO = EventResponseDTO(
            id: 1,
            title: "수정된 반복 항목",
            description: "",
            startAt: Date(timeIntervalSince1970: 0),
            endAt: Date(timeIntervalSince1970: 3600),
            createdAt: Date(timeIntervalSince1970: 0),
            updatedAt: Date(timeIntervalSince1970: 3600)
        ),
        updateRecurrenceOccurrenceError: Error? = nil,
        recurrenceDeleteError: Error? = nil,
        recurrenceOccurrenceDeleteError: Error? = nil
    ) {
        self.shouldSuspend = shouldSuspend
        self.shouldSuspendCreate = shouldSuspendCreate
        self.shouldSuspendImportantEvent = shouldSuspendImportantEvent
        self.error = error
        self.fetchResponse = fetchResponse
        self.createError = createError
        self.createResponse = createResponse
        self.updateResponse = updateResponse
        self.updateError = updateError
        self.deleteError = deleteError
        self.recurrenceCreateResponse = recurrenceCreateResponse
        self.recurrenceCreateError = recurrenceCreateError
        self.fetchRecurrenceResponse = fetchRecurrenceResponse
        self.fetchRecurrenceError = fetchRecurrenceError
        self.updateRecurrenceResponse = updateRecurrenceResponse
        self.updateRecurrenceError = updateRecurrenceError
        self.updateRecurrenceOccurrenceResponse = updateRecurrenceOccurrenceResponse
        self.updateRecurrenceOccurrenceError = updateRecurrenceOccurrenceError
        self.recurrenceDeleteError = recurrenceDeleteError
        self.recurrenceOccurrenceDeleteError = recurrenceOccurrenceDeleteError
    }

    var requests: [(startDate: Date, endDate: Date)] {
        locked {
            storedRequests
        }
    }

    var createRequests: [CreateEventRequestDTO] {
        locked {
            storedCreateRequests
        }
    }

    var recurrenceCreateRequests: [CreateRecurrenceEventRequestDTO] {
        locked {
            storedRecurrenceCreateRequests
        }
    }

    var fetchRecurrenceEventIDs: [Int64] {
        locked {
            storedFetchRecurrenceEventIDs
        }
    }

    var updateRequests: [(eventId: Int64, request: UpdateEventRequestDTO)] {
        locked {
            storedUpdateRequests
        }
    }

    var updateImportantEventRequests: [(eventId: Int64, request: UpdateImportantEventRequestDTO)] {
        locked {
            storedUpdateImportantEventRequests
        }
    }

    var updateRecurrenceEventRequests: [(recurrenceId: Int64, request: UpdateRecurrenceEventRequestDTO)] {
        locked {
            storedUpdateRecurrenceEventRequests
        }
    }

    var updateRecurrenceOccurrenceRequests: [(recurrenceId: Int64, request: UpdateRecurrenceOccurrenceRequestDTO)] {
        locked {
            storedUpdateRecurrenceOccurrenceRequests
        }
    }

    var deleteEventIDs: [Int64] {
        locked {
            storedDeleteEventIDs
        }
    }

    var deleteRecurrenceEventIDs: [Int64] {
        locked {
            storedDeleteRecurrenceEventIDs
        }
    }

    var deleteRecurrenceOccurrenceRequests: [(recurrenceId: Int64, originStartAt: Date)] {
        locked {
            storedDeleteRecurrenceOccurrenceRequests
        }
    }

    var requestCount: Int {
        locked {
            storedRequests.count
        }
    }

    func fetchEvents(from startDate: Date, to endDate: Date) async throws -> [EventResponseDTO] {
        recordRequest(startDate: startDate, endDate: endDate)

        if let error {
            throw error
        }

        if shouldSuspend {
            return try await withCheckedThrowingContinuation { continuation in
                locked {
                    suspendedContinuations.append(continuation)
                }
            }
        }

        return fetchResponse
    }

    func createEvent(_ request: CreateEventRequestDTO) async throws -> EventResponseDTO {
        recordCreateRequest(request)

        if let createError = locked({ createError }) {
            throw createError
        }

        if shouldSuspendCreate {
            return try await withCheckedThrowingContinuation { continuation in
                locked {
                    suspendedCreateContinuations.append(continuation)
                }
            }
        }

        return createResponse
    }

    func createRecurrenceEvent(_ request: CreateRecurrenceEventRequestDTO) async throws -> RecurrenceEventResponseDTO {
        locked {
            storedRecurrenceCreateRequests.append(request)
        }

        if let recurrenceCreateError {
            throw recurrenceCreateError
        }

        return recurrenceCreateResponse
    }

    func fetchRecurrenceEvent(recurrenceId: Int64) async throws -> RecurrenceEventResponseDTO {
        locked {
            storedFetchRecurrenceEventIDs.append(recurrenceId)
        }

        if let fetchRecurrenceError {
            throw fetchRecurrenceError
        }

        return fetchRecurrenceResponse
    }

    func updateEvent(eventId: Int64, request: UpdateEventRequestDTO) async throws -> EventResponseDTO {
        locked {
            storedUpdateRequests.append((eventId, request))
        }

        if let updateError {
            throw updateError
        }

        return updateResponse
    }

    func updateImportantEvent(
        eventId: Int64,
        request: UpdateImportantEventRequestDTO
    ) async throws -> EventResponseDTO {
        let continuations = locked {
            storedUpdateImportantEventRequests.append((eventId, request))
            return readyWaiters(
                from: &importantEventRequestCountWaiters,
                currentCount: storedUpdateImportantEventRequests.count
            )
        }
        continuations.forEach { $0.resume(returning: true) }

        if let updateError {
            throw updateError
        }

        if shouldSuspendImportantEvent {
            return try await withCheckedThrowingContinuation { continuation in
                locked {
                    suspendedImportantEventContinuations.append(continuation)
                }
            }
        }

        return EventResponseDTO(
            id: eventId,
            title: updateResponse.title,
            description: updateResponse.description,
            startAt: updateResponse.startAt,
            endAt: updateResponse.endAt,
            allDay: updateResponse.allDay,
            timeZone: updateResponse.timeZone,
            importantEvent: request.importantEvent,
            recurrenceId: updateResponse.recurrenceId,
            isRecurrenceOccurrence: updateResponse.isRecurrenceOccurrence,
            originStartAt: updateResponse.originStartAt,
            tag: updateResponse.tag,
            createdAt: updateResponse.createdAt,
            updatedAt: updateResponse.updatedAt
        )
    }

    func updateRecurrenceEvent(
        recurrenceId: Int64,
        request: UpdateRecurrenceEventRequestDTO
    ) async throws -> RecurrenceEventResponseDTO {
        locked {
            storedUpdateRecurrenceEventRequests.append((recurrenceId, request))
        }

        if let updateRecurrenceError {
            throw updateRecurrenceError
        }

        return updateRecurrenceResponse
    }

    func updateRecurrenceOccurrence(
        recurrenceId: Int64,
        request: UpdateRecurrenceOccurrenceRequestDTO
    ) async throws -> EventResponseDTO {
        locked {
            storedUpdateRecurrenceOccurrenceRequests.append((recurrenceId, request))
        }

        if let updateRecurrenceOccurrenceError {
            throw updateRecurrenceOccurrenceError
        }

        return updateRecurrenceOccurrenceResponse
    }

    func deleteEvent(eventId: Int64) async throws {
        locked {
            storedDeleteEventIDs.append(eventId)
        }

        if let deleteError {
            throw deleteError
        }
    }

    func deleteRecurrenceEvent(recurrenceId: Int64) async throws {
        locked {
            storedDeleteRecurrenceEventIDs.append(recurrenceId)
        }

        if let recurrenceDeleteError {
            throw recurrenceDeleteError
        }
    }

    func deleteRecurrenceOccurrence(recurrenceId: Int64, originStartAt: Date) async throws {
        locked {
            storedDeleteRecurrenceOccurrenceRequests.append((recurrenceId, originStartAt))
        }

        if let recurrenceOccurrenceDeleteError {
            throw recurrenceOccurrenceDeleteError
        }
    }

    func setCreateError(_ error: Error?) {
        locked {
            createError = error
        }
    }

    func waitForRequestCount(
        _ count: Int,
        timeoutNanoseconds: UInt64 = 5_000_000_000
    ) async -> Bool {
        await waitForCount(
            count,
            timeoutNanoseconds: timeoutNanoseconds,
            kind: .fetch
        )
    }

    func finishSuspendedRequests() {
        let continuations = locked {
            let continuations = suspendedContinuations
            suspendedContinuations.removeAll()
            return continuations
        }
        continuations.forEach { continuation in
            continuation.resume(returning: [])
        }
    }

    func waitForCreateRequestCount(
        _ count: Int,
        timeoutNanoseconds: UInt64 = 5_000_000_000
    ) async -> Bool {
        await waitForCount(
            count,
            timeoutNanoseconds: timeoutNanoseconds,
            kind: .create
        )
    }

    func finishSuspendedCreateRequests() {
        let continuations = locked {
            let continuations = suspendedCreateContinuations
            suspendedCreateContinuations.removeAll()
            return continuations
        }
        continuations.forEach { continuation in
            continuation.resume(returning: createResponse)
        }
    }

    func waitForImportantEventRequestCount(
        _ count: Int,
        timeoutNanoseconds: UInt64 = 5_000_000_000
    ) async -> Bool {
        await waitForCount(
            count,
            timeoutNanoseconds: timeoutNanoseconds,
            kind: .importantEvent
        )
    }

    func finishSuspendedImportantEventRequests() {
        let continuations = locked {
            let continuations = suspendedImportantEventContinuations
            suspendedImportantEventContinuations.removeAll()
            return continuations
        }
        continuations.forEach { $0.resume(returning: updateResponse) }
    }

    func requestMonthKeys(calendar: Calendar) -> [YearMonthKey] {
        requests.map { request in
            YearMonthKey(date: request.startDate, calendar: calendar)
        }.sorted()
    }

    private func recordRequest(startDate: Date, endDate: Date) {
        let continuations = locked {
            storedRequests.append((startDate, endDate))
            return readyWaiters(from: &requestCountWaiters, currentCount: storedRequests.count)
        }
        continuations.forEach { $0.resume(returning: true) }
    }

    private func recordCreateRequest(_ request: CreateEventRequestDTO) {
        let continuations = locked {
            storedCreateRequests.append(request)
            return readyWaiters(
                from: &createRequestCountWaiters,
                currentCount: storedCreateRequests.count
            )
        }
        continuations.forEach { $0.resume(returning: true) }
    }

    private func waitForCount(
        _ count: Int,
        timeoutNanoseconds: UInt64,
        kind: CountWaiterKind
    ) async -> Bool {
        let waiterID = UUID()

        return await withCheckedContinuation { continuation in
            let shouldWait = locked {
                guard currentCount(for: kind) < count else {
                    return false
                }

                appendWaiter(
                    CountWaiter(
                        id: waiterID,
                        count: count,
                        continuation: continuation
                    ),
                    for: kind
                )
                return true
            }

            guard shouldWait else {
                continuation.resume(returning: true)
                return
            }

            Task {
                try? await Task.sleep(nanoseconds: timeoutNanoseconds)
                completeWaiterIfNeeded(id: waiterID, kind: kind)
            }
        }
    }

    private func completeWaiterIfNeeded(
        id: UUID,
        kind: CountWaiterKind
    ) {
        let continuation = locked {
            let waiters = waiters(for: kind)
            guard let index = waiters.firstIndex(where: { $0.id == id }) else {
                return nil as CheckedContinuation<Bool, Never>?
            }

            return removeWaiter(at: index, for: kind).continuation
        }

        continuation?.resume(returning: false)
    }

    private func currentCount(for kind: CountWaiterKind) -> Int {
        switch kind {
        case .fetch:
            return storedRequests.count
        case .create:
            return storedCreateRequests.count
        case .importantEvent:
            return storedUpdateImportantEventRequests.count
        }
    }

    private func waiters(for kind: CountWaiterKind) -> [CountWaiter] {
        switch kind {
        case .fetch:
            return requestCountWaiters
        case .create:
            return createRequestCountWaiters
        case .importantEvent:
            return importantEventRequestCountWaiters
        }
    }

    private func appendWaiter(_ waiter: CountWaiter, for kind: CountWaiterKind) {
        switch kind {
        case .fetch:
            requestCountWaiters.append(waiter)
        case .create:
            createRequestCountWaiters.append(waiter)
        case .importantEvent:
            importantEventRequestCountWaiters.append(waiter)
        }
    }

    private func removeWaiter(at index: Int, for kind: CountWaiterKind) -> CountWaiter {
        switch kind {
        case .fetch:
            return requestCountWaiters.remove(at: index)
        case .create:
            return createRequestCountWaiters.remove(at: index)
        case .importantEvent:
            return importantEventRequestCountWaiters.remove(at: index)
        }
    }

    private func readyWaiters(
        from waiters: inout [CountWaiter],
        currentCount: Int
    ) -> [CheckedContinuation<Bool, Never>] {
        let readyWaiters = waiters.filter { currentCount >= $0.count }
        waiters.removeAll { currentCount >= $0.count }
        return readyWaiters.map(\.continuation)
    }

    private func locked<T>(_ work: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return work()
    }

    private struct CountWaiter {
        let id: UUID
        let count: Int
        let continuation: CheckedContinuation<Bool, Never>
    }

    private enum CountWaiterKind {
        case fetch
        case create
        case importantEvent
    }
}
