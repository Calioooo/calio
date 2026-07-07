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
            endAt: input.endAt,
            tagId: input.tagId
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
            endAt: input.endAt,
            tagId: input.tagId
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
            recurrenceFrequency: input.recurrenceFrequency,
            tagId: input.tagId
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
            recurrenceStartDate: CalendarDateService.utcDateString(from: input.recurrenceStartDate),
            recurrenceEndDate: CalendarDateService.utcDateString(from: input.recurrenceEndDate),
            recurrenceStartTime: CalendarDateService.utcTimeString(from: input.recurrenceStartTime),
            recurrenceEndTime: CalendarDateService.utcTimeString(from: input.recurrenceEndTime),
            recurrenceFrequency: input.recurrenceFrequency,
            tagId: input.tagId
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
            tag: mapToCalendarTag(dto.tag),
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
                recurrenceStartDate: try CalendarDateService.utcDate(from: dto.recurrenceStartDate),
                recurrenceEndDate: try CalendarDateService.utcDate(from: dto.recurrenceEndDate),
                recurrenceStartTime: try CalendarDateService.utcTime(from: dto.recurrenceStartTime),
                recurrenceEndTime: try CalendarDateService.utcTime(from: dto.recurrenceEndTime),
                recurrenceFrequency: dto.recurrenceFrequency,
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

struct TagService {
    private let repository: TagRepository

    init(repository: TagRepository = URLSessionTagRepository()) {
        self.repository = repository
    }

    func fetchTags() async throws -> [CalendarTag] {
        do {
            return try await repository.fetchTags().map(mapToCalendarTag(_:))
        } catch let error as EventRepositoryError {
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
        } catch let error as EventRepositoryError {
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
        } catch let error as EventRepositoryError {
            throw mapToServiceError(error)
        } catch {
            throw EventServiceError.unexpected
        }
    }

    func deleteCustomTag(tagId: Int64) async throws {
        do {
            try await repository.deleteCustomTag(tagId: tagId)
        } catch let error as EventRepositoryError {
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

    private func mapToServiceError(_ error: EventRepositoryError) -> EventServiceError {
        switch error {
        case .network:
            return .network
        case .decoding:
            return .decoding
        case .backend(_, let response) where response?.errorCode == "VALIDATION_FAILED":
            return .validationFailed
        case .invalidURL, .invalidResponse, .backend, .encoding, .unexpected:
            return .unexpected
        }
    }
}
