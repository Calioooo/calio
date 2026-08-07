//
//  EventService.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation

struct EventService {
    private let repository: EventRepository
    
    init(repository: EventRepository = URLSessionEventRepository()) {
        self.repository = repository
    }
    
    func fetchEvents(from startDate: Date, to endDate: Date) async throws -> [Event] {
        do {
            let response = try await repository.fetchEvents(from: startDate, to: endDate)
            return try response.map(mapToEvent(_:))
        } catch let error as APIError {
            throw mapToServiceError(error)
        } catch let error as EventServiceError {
            throw error
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func createEvent(_ input: EventCreateInput) async throws -> Event {
        let range = try backendEventRange(
            startAt: input.startAt,
            endAt: input.endAt,
            isAllDay: input.isAllDay
        )
        let request = CreateEventRequestDTO(
            title: input.title,
            description: backendDescription(from: input.description),
            startAt: range.startAt,
            endAt: range.endAt,
            tagId: input.tagId
        )

        do {
            let response = try await repository.createEvent(request)
            return try mapToEvent(response)
        } catch let error as APIError {
            throw mapToServiceError(error)
        } catch let error as EventServiceError {
            throw error
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func updateEvent(eventId: Int64, input: EventUpdateInput) async throws -> Event {
        let range = try backendEventRange(
            startAt: input.startAt,
            endAt: input.endAt,
            isAllDay: input.isAllDay
        )
        let request = UpdateEventRequestDTO(
            title: input.title,
            description: backendDescription(from: input.description),
            startAt: range.startAt,
            endAt: range.endAt,
            tagId: input.tagId
        )

        do {
            let response = try await repository.updateEvent(eventId: eventId, request: request)
            return try mapToEvent(response)
        } catch let error as APIError {
            throw mapToServiceError(error)
        } catch let error as EventServiceError {
            throw error
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func fetchRecurrenceEvent(recurrenceId: Int64) async throws -> RecurrenceEventDetails {
        do {
            let response = try await repository.fetchRecurrenceEvent(recurrenceId: recurrenceId)
            return try mapToRecurrenceEventDetails(response)
        } catch let error as APIError {
            throw mapToServiceError(error)
        } catch let error as EventServiceError {
            throw error
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func updateRecurrenceEvent(
        recurrenceId: Int64,
        input: RecurrenceEventUpdateInput
    ) async throws -> RecurrenceEventDetails {
        let request = UpdateRecurrenceEventRequestDTO(
            title: input.title,
            description: backendDescription(from: input.description),
            startDate: recurrenceDateString(from: input.recurrenceStartDate, isAllDay: input.isAllDay),
            endDate: recurrenceDateString(from: input.recurrenceEndDate, isAllDay: input.isAllDay),
            startTime: input.isAllDay ? Self.allDayRecurrenceStartTime : CalendarDateService.utcTimeString(from: input.recurrenceStartTime),
            endTime: input.isAllDay ? Self.allDayRecurrenceEndTime : CalendarDateService.utcTimeString(from: input.recurrenceEndTime),
            recurrenceFrequency: input.recurrenceFrequency,
            tagId: input.tagId
        )

        do {
            let response = try await repository.updateRecurrenceEvent(
                recurrenceId: recurrenceId,
                request: request
            )
            return try mapToRecurrenceEventDetails(response)
        } catch let error as APIError {
            throw mapToServiceError(error)
        } catch let error as EventServiceError {
            throw error
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func updateRecurrenceOccurrence(
        recurrenceId: Int64,
        originStartAt: Date,
        input: RecurrenceOccurrenceUpdateInput
    ) async throws -> Event {
        let range = try backendEventRange(
            startAt: input.startAt,
            endAt: input.endAt,
            isAllDay: input.isAllDay
        )
        let request = UpdateRecurrenceOccurrenceRequestDTO(
            originStartAt: originStartAt,
            startAt: range.startAt,
            endAt: range.endAt
        )

        do {
            let response = try await repository.updateRecurrenceOccurrence(
                recurrenceId: recurrenceId,
                request: request
            )
            return try mapToEvent(response)
        } catch let error as APIError {
            throw mapToServiceError(error)
        } catch let error as EventServiceError {
            throw error
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func deleteEvent(eventId: Int64) async throws {
        do {
            try await repository.deleteEvent(eventId: eventId)
        } catch let error as APIError {
            throw mapToServiceError(error)
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func deleteRecurrenceEvent(recurrenceId: Int64) async throws {
        do {
            try await repository.deleteRecurrenceEvent(recurrenceId: recurrenceId)
        } catch let error as APIError {
            throw mapToServiceError(error)
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func deleteRecurrenceOccurrence(recurrenceId: Int64, originStartAt: Date) async throws {
        do {
            try await repository.deleteRecurrenceOccurrence(
                recurrenceId: recurrenceId,
                originStartAt: originStartAt
            )
        } catch let error as APIError {
            throw mapToServiceError(error)
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func createRecurrenceEvent(_ input: RecurrenceEventCreateInput) async throws {
        let request = CreateRecurrenceEventRequestDTO(
            recurrenceTitle: input.title,
            recurrenceDescription: backendDescription(from: input.description),
            recurrenceStartDate: recurrenceDateString(from: input.recurrenceStartDate, isAllDay: input.isAllDay),
            recurrenceEndDate: recurrenceDateString(from: input.recurrenceEndDate, isAllDay: input.isAllDay),
            recurrenceStartTime: input.isAllDay ? Self.allDayRecurrenceStartTime : CalendarDateService.utcTimeString(from: input.recurrenceStartTime),
            recurrenceEndTime: input.isAllDay ? Self.allDayRecurrenceEndTime : CalendarDateService.utcTimeString(from: input.recurrenceEndTime),
            recurrenceFrequency: input.recurrenceFrequency,
            tagId: input.tagId
        )

        do {
            _ = try await repository.createRecurrenceEvent(request)
        } catch let error as APIError {
            throw mapToServiceError(error)
        } catch {
            throw EventServiceError.unexpected
        }
    }

    private func mapToEvent(_ dto: EventResponseDTO) throws -> Event {
        let allDayRange = try CalendarDateService.localAllDayRange(
            utcStartAt: dto.startAt,
            utcEndAt: dto.endAt
        )
        let startAt = allDayRange?.startAt ?? dto.startAt
        let endAt = allDayRange?.endAt ?? dto.endAt

        return Event(
            id: dto.id,
            title: dto.title,
            description: dto.description ?? "",
            startAt: startAt,
            endAt: endAt,
            isAllDay: allDayRange != nil,
            tag: mapToCalendarTag(dto.tag),
            importantEvent: dto.importantEvent,
            recurrenceId: dto.recurrenceId,
            isRecurrenceOccurrence: dto.isRecurrenceOccurrence,
            originStartAt: dto.originStartAt
        )
    }

    private func mapToRecurrenceEventDetails(_ dto: RecurrenceEventResponseDTO) throws -> RecurrenceEventDetails {
        do {
            let isAllDay = dto.recurrenceStartTime == Self.allDayRecurrenceStartTime
                && dto.recurrenceEndTime == Self.allDayRecurrenceEndTime
            let startDate = try recurrenceDate(from: dto.recurrenceStartDate, isAllDay: isAllDay)
            let endDate = try recurrenceDate(from: dto.recurrenceEndDate, isAllDay: isAllDay)
            let startTime = try CalendarDateService.utcTime(from: dto.recurrenceStartTime)
            let endTime = try CalendarDateService.utcTime(from: dto.recurrenceEndTime)

            return RecurrenceEventDetails(
                recurrenceId: dto.recurrenceId,
                title: dto.recurrenceTitle,
                description: dto.recurrenceDescription ?? "",
                recurrenceStartDate: startDate,
                recurrenceEndDate: endDate,
                recurrenceStartTime: startTime,
                recurrenceEndTime: endTime,
                recurrenceFrequency: dto.recurrenceFrequency,
                isAllDay: isAllDay,
                tagId: dto.tag.id
            )
        } catch {
            throw EventServiceError.decoding
        }
    }

    private func mapToCalendarTag(_ dto: TagResponseDTO) -> CalendarTag {
        CalendarTag(
            id: dto.id,
            title: dto.title,
            colorCode: dto.colorCode,
            tagType: dto.tagType
        )
    }

    private func backendDescription(from description: String) -> String? {
        description.isEmpty ? nil : description
    }

    private func backendEventRange(
        startAt: Date,
        endAt: Date,
        isAllDay: Bool
    ) throws -> (startAt: Date, endAt: Date) {
        guard isAllDay else {
            return (startAt, endAt)
        }
        return try CalendarDateService.utcAllDayRange(startAt: startAt, endAt: endAt)
    }

    private func recurrenceDateString(from date: Date, isAllDay: Bool) -> String {
        isAllDay
            ? CalendarDateService.localDateString(from: date)
            : CalendarDateService.utcDateString(from: date)
    }

    private func recurrenceDate(from string: String, isAllDay: Bool) throws -> Date {
        if isAllDay {
            return try CalendarDateService.localDate(from: string)
        }
        return try CalendarDateService.utcDate(from: string)
    }

    private static let allDayRecurrenceStartTime = "00:00:00"
    private static let allDayRecurrenceEndTime = "23:59:59"

    private func mapToServiceError(_ error: APIError) -> EventServiceError {
        switch error {
        case .backend(_, let problem):
            switch problem?.errorCode {
            case "EVENT_NOT_FOUND":
                return .eventNotFound
            case "RECURRENCE_EVENT_NOT_FOUND":
                return .recurrenceEventNotFound
            case "RECURRENCE_OCCURRENCE_NOT_FOUND":
                return .recurrenceOccurrenceNotFound
            case "VALIDATION_FAILED":
                return .validationFailed
            case "INVALID_TIME_RANGE", "RECURRENCE_UPDATE_TIME_RANGE_INVALID":
                return .invalidTimeRange
            default:
                return .unexpected
            }

        case .network:
            return .network

        case .decoding:
            return .decoding

        case .invalidRequest, .invalidResponse, .encoding, .unexpected:
            return .unexpected
        }
    }
}

enum EventServiceError: Error, Equatable {
    case eventNotFound
    case recurrenceEventNotFound
    case recurrenceOccurrenceNotFound
    case validationFailed
    case invalidTimeRange
    case network
    case decoding
    case unexpected
}

struct TagService {
    private let repository: TagRepository

    init(repository: TagRepository = URLSessionTagRepository()) {
        self.repository = repository
    }

    func fetchTags() async throws -> [CalendarTag] {
        do {
            return try await repository.fetchTags().map(mapToCalendarTag(_:))
        } catch let error as APIError {
            throw mapToServiceError(error)
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func createCustomTag(_ input: CustomTagInput) async throws -> CalendarTag {
        let request = CustomTagRequestDTO(
            title: input.title,
            colorCode: input.colorCode
        )

        do {
            let dto = try await repository.createCustomTag(request)
            return mapToCalendarTag(dto)
        } catch let error as APIError {
            throw mapToServiceError(error)
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func updateCustomTag(tagId: Int64, input: CustomTagInput) async throws -> CalendarTag {
        let request = CustomTagRequestDTO(
            title: input.title,
            colorCode: input.colorCode
        )

        do {
            let dto = try await repository.updateCustomTag(tagId: tagId, request: request)
            return mapToCalendarTag(dto)
        } catch let error as APIError {
            throw mapToServiceError(error)
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func deleteCustomTag(tagId: Int64) async throws {
        do {
            try await repository.deleteCustomTag(tagId: tagId)
        } catch let error as APIError {
            throw mapToServiceError(error)
        } catch {
            throw EventServiceError.unexpected
        }
    }

    private func mapToCalendarTag(_ dto: TagResponseDTO) -> CalendarTag {
        CalendarTag(
            id: dto.id,
            title: dto.title,
            colorCode: dto.colorCode,
            tagType: dto.tagType
        )
    }

    private func mapToServiceError(_ error: APIError) -> EventServiceError {
        switch error {
        case .network:
            return .network
        case .decoding:
            return .decoding
        case .backend(_, let problem) where problem?.errorCode == "VALIDATION_FAILED":
            return .validationFailed
        case .invalidRequest, .invalidResponse, .backend, .encoding, .unexpected:
            return .unexpected
        }
    }
}
