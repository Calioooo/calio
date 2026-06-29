//
//  EventService.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation

struct EventService {
    private let repository: EventRepository
    private let defaultColorCode = "#4F46E5"
    
    init(repository: EventRepository = URLSessionEventRepository()) {
        self.repository = repository
    }
    
    func fetchEvents(from startDate: Date, to endDate: Date) async throws -> [Event] {
        do {
            let response = try await repository.fetchEvents(from: startDate, to: endDate)
            return response.map(mapToEvent(_:))
        } catch let error as EventRepositoryError {
            throw mapToServiceError(error)
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func createEvent(_ input: EventCreateInput) async throws -> Event {
        let request = CreateEventRequestDTO(
            title: input.title,
            description: input.description,
            startAt: input.startAt,
            endAt: input.endAt
        )

        do {
            let response = try await repository.createEvent(request)
            return mapToEvent(response)
        } catch let error as EventRepositoryError {
            throw mapToServiceError(error)
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func updateEvent(eventId: Int64, input: EventUpdateInput) async throws -> Event {
        let request = UpdateEventRequestDTO(
            title: input.title,
            description: input.description,
            startAt: input.startAt,
            endAt: input.endAt
        )

        do {
            let response = try await repository.updateEvent(eventId: eventId, request: request)
            return mapToEvent(response)
        } catch let error as EventRepositoryError {
            throw mapToServiceError(error)
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func fetchRecurrenceEvent(recurrenceId: Int64) async throws -> RecurrenceEventDetails {
        do {
            let response = try await repository.fetchRecurrenceEvent(recurrenceId: recurrenceId)
            return try mapToRecurrenceEventDetails(response)
        } catch let error as EventRepositoryError {
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
            description: input.description,
            startAt: input.startAt,
            endAt: input.endAt,
            recurrenceFrequency: input.recurrenceFrequency
        )

        do {
            let response = try await repository.updateRecurrenceEvent(
                recurrenceId: recurrenceId,
                request: request
            )
            return try mapToRecurrenceEventDetails(response)
        } catch let error as EventRepositoryError {
            throw mapToServiceError(error)
        } catch let error as EventServiceError {
            throw error
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func updateRecurrenceOccurrence(
        recurrenceId: Int64,
        eventId: Int64,
        input: RecurrenceOccurrenceUpdateInput
    ) async throws -> Event {
        let request = UpdateRecurrenceOccurrenceRequestDTO(
            title: input.title,
            description: input.description,
            startAt: input.startAt,
            endAt: input.endAt,
            isImportant: input.isImportant
        )

        do {
            let response = try await repository.updateRecurrenceOccurrence(
                recurrenceId: recurrenceId,
                eventId: eventId,
                request: request
            )
            return mapToEvent(response)
        } catch let error as EventRepositoryError {
            throw mapToServiceError(error)
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func deleteEvent(eventId: Int64) async throws {
        do {
            try await repository.deleteEvent(eventId: eventId)
        } catch let error as EventRepositoryError {
            throw mapToServiceError(error)
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func deleteRecurrenceEvent(recurrenceId: Int64) async throws {
        do {
            try await repository.deleteRecurrenceEvent(recurrenceId: recurrenceId)
        } catch let error as EventRepositoryError {
            throw mapToServiceError(error)
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func deleteRecurrenceOccurrence(recurrenceId: Int64, eventId: Int64) async throws {
        do {
            try await repository.deleteRecurrenceOccurrence(
                recurrenceId: recurrenceId,
                eventId: eventId
            )
        } catch let error as EventRepositoryError {
            throw mapToServiceError(error)
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func createRecurrenceEvent(_ input: RecurrenceEventCreateInput) async throws {
        let request = CreateRecurrenceEventRequestDTO(
            recurrenceTitle: input.title,
            recurrenceDescription: input.description,
            recurrenceStartDate: Self.utcDateString(from: input.recurrenceStartDate),
            recurrenceEndDate: Self.utcDateString(from: input.recurrenceEndDate),
            recurrenceStartTime: Self.utcTimeString(from: input.recurrenceStartTime),
            recurrenceEndTime: Self.utcTimeString(from: input.recurrenceEndTime),
            recurrenceFrequency: input.recurrenceFrequency
        )

        do {
            _ = try await repository.createRecurrenceEvent(request)
        } catch let error as EventRepositoryError {
            throw mapToServiceError(error)
        } catch {
            throw EventServiceError.unexpected
        }
    }

    private func mapToEvent(_ dto: EventResponseDTO) -> Event {
        Event(
            id: dto.id,
            title: dto.title,
            description: dto.description ?? "",
            startAt: dto.startAt,
            endAt: dto.endAt,
            colorCode: defaultColorCode,
            importantEvent: dto.importantEvent,
            recurrenceId: dto.recurrenceId,
            isRecurrenceOccurrence: dto.isRecurrenceOccurrence
        )
    }

    private func mapToRecurrenceEventDetails(_ dto: RecurrenceEventResponseDTO) throws -> RecurrenceEventDetails {
        do {
            return RecurrenceEventDetails(
                recurrenceId: dto.recurrenceId,
                title: dto.recurrenceTitle,
                description: dto.recurrenceDescription ?? "",
                recurrenceStartDate: try Self.utcDate(from: dto.recurrenceStartDate),
                recurrenceEndDate: try Self.utcDate(from: dto.recurrenceEndDate),
                recurrenceStartTime: try Self.utcTime(from: dto.recurrenceStartTime),
                recurrenceEndTime: try Self.utcTime(from: dto.recurrenceEndTime),
                recurrenceFrequency: dto.recurrenceFrequency
            )
        } catch {
            throw EventServiceError.decoding
        }
    }

    nonisolated static func utcDateString(from date: Date) -> String {
        let utcCalendar = makeUTCCalendar()
        let components = utcCalendar.dateComponents([.year, .month, .day], from: date)
        return String(
            format: "%04d-%02d-%02d",
            components.year ?? 0,
            components.month ?? 0,
            components.day ?? 0
        )
    }

    nonisolated static func utcTimeString(from date: Date) -> String {
        let utcCalendar = makeUTCCalendar()
        let components = utcCalendar.dateComponents([.hour, .minute, .second], from: date)
        return String(
            format: "%02d:%02d:%02d",
            components.hour ?? 0,
            components.minute ?? 0,
            components.second ?? 0
        )
    }

    nonisolated static func utcDate(from string: String) throws -> Date {
        let components = string.split(separator: "-").compactMap { Int($0) }

        guard components.count == 3 else {
            throw EventServiceError.decoding
        }

        return try utcDate(year: components[0], month: components[1], day: components[2])
    }

    nonisolated static func utcTime(from string: String) throws -> Date {
        let components = string.split(separator: ":").compactMap { Int($0) }

        guard components.count >= 2 else {
            throw EventServiceError.decoding
        }

        var dateComponents = DateComponents()
        dateComponents.calendar = makeUTCCalendar()
        dateComponents.timeZone = TimeZone(secondsFromGMT: 0)
        dateComponents.year = 1970
        dateComponents.month = 1
        dateComponents.day = 1
        dateComponents.hour = components[0]
        dateComponents.minute = components[1]
        dateComponents.second = components.count > 2 ? components[2] : 0

        guard let date = dateComponents.date else {
            throw EventServiceError.decoding
        }

        return date
    }

    nonisolated static func composeUTCDateTime(date: Date, time: Date) throws -> Date {
        let calendar = makeUTCCalendar()
        let dateComponents = calendar.dateComponents([.year, .month, .day], from: date)
        let timeComponents = calendar.dateComponents([.hour, .minute, .second], from: time)

        return try utcDate(
            year: dateComponents.year,
            month: dateComponents.month,
            day: dateComponents.day,
            hour: timeComponents.hour,
            minute: timeComponents.minute,
            second: timeComponents.second
        )
    }

    private nonisolated static func utcDate(
        year: Int?,
        month: Int?,
        day: Int?,
        hour: Int? = 0,
        minute: Int? = 0,
        second: Int? = 0
    ) throws -> Date {
        guard let year, let month, let day else {
            throw EventServiceError.decoding
        }

        var components = DateComponents()
        components.calendar = makeUTCCalendar()
        components.timeZone = TimeZone(secondsFromGMT: 0)
        components.year = year
        components.month = month
        components.day = day
        components.hour = hour
        components.minute = minute
        components.second = second

        guard let date = components.date else {
            throw EventServiceError.decoding
        }

        return date
    }

    private nonisolated static func makeUTCCalendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    private func mapToServiceError(_ error: EventRepositoryError) -> EventServiceError {
        switch error {
        case .backend(_, let response):
            switch response?.errorCode {
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

        case .invalidURL, .invalidResponse, .encoding, .unexpected:
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
