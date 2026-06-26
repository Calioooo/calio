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

    func createRecurrenceEvent(_ input: RecurrenceEventCreateInput) async throws {
        let request = CreateRecurrenceEventRequestDTO(
            recurrenceTitle: input.title,
            recurrenceDescription: input.description,
            recurrenceStartDate: Self.utcDateString(from: input.startAt),
            recurrenceEndDate: Self.utcDateString(from: input.recurrenceEndAt),
            recurrenceStartTime: Self.utcTimeString(from: input.startAt),
            recurrenceEndTime: Self.utcTimeString(from: input.endAt),
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

    private nonisolated static func makeUTCCalendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    private func mapToServiceError(_ error: EventRepositoryError) -> EventServiceError {
        switch error {
        case .backend(_, let response):
            switch response?.errorCode {
            case "VALIDATION_FAILED":
                return .validationFailed
            case "INVALID_TIME_RANGE":
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
    case validationFailed
    case invalidTimeRange
    case network
    case decoding
    case unexpected
}
