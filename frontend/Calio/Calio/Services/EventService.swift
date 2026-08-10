//
//  EventService.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation

struct EventService {
    private let repository: EventRepository
    private let deviceTimeZone: TimeZone
    
    init(
        repository: EventRepository = URLSessionEventRepository(),
        deviceTimeZone: TimeZone = .current
    ) {
        self.repository = repository
        self.deviceTimeZone = deviceTimeZone
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
            allDay: input.isAllDay,
            timeZone: input.isAllDay ? nil : deviceTimeZone.identifier,
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
            allDay: input.isAllDay,
            timeZone: input.isAllDay ? nil : input.timeZone ?? deviceTimeZone.identifier,
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
        let schedule = try recurrenceSchedule(
            startDate: input.recurrenceStartDate,
            endDate: input.recurrenceEndDate,
            startTime: input.recurrenceStartTime,
            endTime: input.recurrenceEndTime,
            frequency: input.recurrenceFrequency,
            isAllDay: input.isAllDay,
            timeZone: input.timeZone
        )
        let request = UpdateRecurrenceEventRequestDTO(
            title: input.title,
            description: backendDescription(from: input.description),
            allDay: input.isAllDay,
            firstOccurrenceStartAt: schedule.firstOccurrenceStartAt,
            firstOccurrenceEndAt: schedule.firstOccurrenceEndAt,
            timeZone: schedule.timeZone,
            recurrence: schedule.recurrence,
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
            title: input.title,
            description: backendDescription(from: input.description),
            startAt: range.startAt,
            endAt: range.endAt,
            allDay: input.isAllDay,
            timeZone: input.isAllDay ? nil : input.timeZone ?? deviceTimeZone.identifier
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
        let schedule = try recurrenceSchedule(
            startDate: input.recurrenceStartDate,
            endDate: input.recurrenceEndDate,
            startTime: input.recurrenceStartTime,
            endTime: input.recurrenceEndTime,
            frequency: input.recurrenceFrequency,
            isAllDay: input.isAllDay,
            timeZone: nil
        )
        let request = CreateRecurrenceEventRequestDTO(
            title: input.title,
            description: backendDescription(from: input.description),
            allDay: input.isAllDay,
            firstOccurrenceStartAt: schedule.firstOccurrenceStartAt,
            firstOccurrenceEndAt: schedule.firstOccurrenceEndAt,
            timeZone: schedule.timeZone,
            recurrence: schedule.recurrence,
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
        let allDayRange = dto.allDay
            ? try CalendarDateService.localAllDayDisplayRange(
                utcStartAt: dto.startAt,
                utcEndAt: dto.endAt
            )
            : nil
        let startAt = allDayRange?.startAt ?? dto.startAt
        let endAt = allDayRange?.endAt ?? dto.endAt

        return Event(
            id: dto.id,
            title: dto.title,
            description: dto.description ?? "",
            startAt: startAt,
            endAt: endAt,
            isAllDay: dto.allDay,
            timeZone: dto.timeZone,
            tag: mapToCalendarTag(dto.tag),
            importantEvent: dto.importantEvent,
            recurrenceId: dto.recurrenceId,
            isRecurrenceOccurrence: dto.isRecurrenceOccurrence,
            originStartAt: dto.originStartAt
        )
    }

    private func mapToRecurrenceEventDetails(_ dto: RecurrenceEventResponseDTO) throws -> RecurrenceEventDetails {
        let editableRule = RecurrenceRule.editableRule(from: dto.recurrence, allDay: dto.allDay)
        let allDayRange = dto.allDay
            ? try CalendarDateService.localAllDayDisplayRange(
                utcStartAt: dto.firstOccurrenceStartAt,
                utcEndAt: dto.firstOccurrenceEndAt
            )
            : nil
        return RecurrenceEventDetails(
            recurrenceId: dto.recurrenceId,
            title: dto.title,
            description: dto.description ?? "",
            recurrenceStartDate: allDayRange?.startAt ?? dto.firstOccurrenceStartAt,
            recurrenceEndDate: editableRule?.until ?? allDayRange?.startAt ?? dto.firstOccurrenceStartAt,
            recurrenceStartTime: dto.firstOccurrenceStartAt,
            recurrenceEndTime: dto.firstOccurrenceEndAt,
            recurrenceFrequency: editableRule?.frequency ?? .daily,
            isAllDay: dto.allDay,
            timeZone: dto.timeZone,
            canUpdateSeries: dto.canUpdateSeries == true,
            isRuleEditable: editableRule != nil,
            tagId: dto.tag.id
        )
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

    private func recurrenceSchedule(
        startDate: Date,
        endDate: Date,
        startTime: Date,
        endTime: Date,
        frequency: RecurrenceFrequency,
        isAllDay: Bool,
        timeZone: String?
    ) throws -> RecurrenceScheduleRequest {
        let zone = timeZone.flatMap(TimeZone.init(identifier:)) ?? deviceTimeZone
        return try RecurrenceScheduleBuilder.make(
            startDate: startDate,
            endDate: endDate,
            startTime: startTime,
            endTime: endTime,
            frequency: frequency,
            allDay: isAllDay,
            timeZone: zone,
            formTimeZone: deviceTimeZone
        )
    }

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
            case "EXTERNAL_EVENT_MUTATION_NOT_SUPPORTED":
                return .seriesMutationNotAllowed
            case "VALIDATION_FAILED", "INVALID_ALL_DAY_SCHEDULE", "INVALID_TIME_ZONE":
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
    case seriesMutationNotAllowed
    case validationFailed
    case invalidTimeRange
    case network
    case decoding
    case unexpected
}
