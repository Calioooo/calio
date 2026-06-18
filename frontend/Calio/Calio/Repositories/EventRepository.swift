//
//  EventRepository.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation

protocol EventRepository {
    func fetchEvents(from startDate: Date, to endDate: Date) async throws -> [EventResponseDTO]
    func createEvent(_ request: CreateEventRequestDTO) async throws -> EventResponseDTO
}

enum EventRepositoryError: Error {
    case invalidURL
    case invalidResponse
    case network(URLError)
    case backend(statusCode: Int, response: ErrorResponseDTO?)
    case decoding(Error)
    case encoding(Error)
    case unexpected(Error)
}
