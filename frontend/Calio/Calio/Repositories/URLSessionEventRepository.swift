//
//  URLSessionEventRepository.swift
//  Calio
//
//  Created by Codex on 6/19/26.
//

import Foundation

struct URLSessionEventRepository: EventRepository {
    private let baseURL: URL
    private let session: URLSession
    private let jsonDecoder: JSONDecoder
    private let jsonEncoder: JSONEncoder

    init(
        baseURL: URL = CalioAPIConfig.baseURL,
        session: URLSession = .shared,
        jsonDecoder: JSONDecoder = EventJSONCoding.makeDecoder(),
        jsonEncoder: JSONEncoder = EventJSONCoding.makeEncoder()
    ) {
        self.baseURL = baseURL
        self.session = session
        self.jsonDecoder = jsonDecoder
        self.jsonEncoder = jsonEncoder
    }

    func fetchEvents(from startDate: Date, to endDate: Date) async throws -> [EventResponseDTO] {
        var components = URLComponents(
            url: eventsURL,
            resolvingAgainstBaseURL: false
        )
        components?.queryItems = [
            URLQueryItem(name: "from", value: EventJSONCoding.string(from: startDate)),
            URLQueryItem(name: "to", value: EventJSONCoding.string(from: endDate))
        ]

        guard let url = components?.url else {
            throw EventRepositoryError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"

        let data = try await data(for: request)
        return try decode([EventResponseDTO].self, from: data)
    }

    func createEvent(_ requestDTO: CreateEventRequestDTO) async throws -> EventResponseDTO {
        var request = URLRequest(url: eventsURL)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        do {
            request.httpBody = try jsonEncoder.encode(requestDTO)
        } catch {
            throw EventRepositoryError.encoding(error)
        }

        let data = try await data(for: request)
        return try decode(EventResponseDTO.self, from: data)
    }

    private var eventsURL: URL {
        baseURL.appendingPathComponent("api/events")
    }

    private func data(for request: URLRequest) async throws -> Data {
        let response: URLResponse
        let data: Data

        do {
            (data, response) = try await session.data(for: request)
        } catch let error as URLError {
            throw EventRepositoryError.network(error)
        } catch {
            throw EventRepositoryError.unexpected(error)
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw EventRepositoryError.invalidResponse
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            throw EventRepositoryError.backend(
                statusCode: httpResponse.statusCode,
                response: try? jsonDecoder.decode(ErrorResponseDTO.self, from: data)
            )
        }

        return data
    }

    private func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        do {
            return try jsonDecoder.decode(type, from: data)
        } catch let error as DecodingError {
            throw EventRepositoryError.decoding(error)
        } catch {
            throw EventRepositoryError.unexpected(error)
        }
    }
}
