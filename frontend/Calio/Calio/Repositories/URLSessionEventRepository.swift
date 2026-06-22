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
        let request = makeRequest(
            method: "GET",
            url: try eventsURL(
                queryItems: [
                    URLQueryItem(name: "from", value: EventJSONCoding.string(from: startDate)),
                    URLQueryItem(name: "to", value: EventJSONCoding.string(from: endDate))
                ]
            )
        )

        return try await response([EventResponseDTO].self, for: request)
    }

    func createEvent(_ requestDTO: CreateEventRequestDTO) async throws -> EventResponseDTO {
        let request = try makeRequest(
            method: "POST",
            url: eventsURL(),
            body: requestDTO
        )

        return try await response(EventResponseDTO.self, for: request)
    }

    private func eventsURL(queryItems: [URLQueryItem] = []) throws -> URL {
        var components = URLComponents(
            url: baseURL.appendingPathComponent("api/events"),
            resolvingAgainstBaseURL: false
        )
        components?.queryItems = queryItems.isEmpty ? nil : queryItems

        guard let url = components?.url else {
            throw EventRepositoryError.invalidURL
        }

        return url
    }

    private func makeRequest(method: String, url: URL) -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        return request
    }

    private func makeRequest<Body: Encodable>(
        method: String,
        url: URL,
        body: Body
    ) throws -> URLRequest {
        var request = makeRequest(method: method, url: url)
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        do {
            request.httpBody = try jsonEncoder.encode(body)
        } catch {
            throw EventRepositoryError.encoding(error)
        }

        return request
    }

    private func response<T: Decodable>(_ type: T.Type, for request: URLRequest) async throws -> T {
        let data = try await data(for: request)
        return try decode(type, from: data)
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
