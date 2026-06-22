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
        let response = try await repository.fetchEvents(from: startDate, to: endDate)
        
        return response.map(mapToEvent(_:))
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

    private func mapToEvent(_ dto: EventResponseDTO) -> Event {
        Event(
            id: dto.id,
            title: dto.title,
            description: dto.description ?? "",
            startAt: dto.startAt,
            endAt: dto.endAt,
            colorCode: defaultColorCode
        )
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
