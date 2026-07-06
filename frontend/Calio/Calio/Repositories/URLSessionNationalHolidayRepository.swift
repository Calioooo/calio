//
//  URLSessionNationalHolidayRepository.swift
//  Calio
//
//  Created by Codex on 7/6/26.
//

import Foundation

struct URLSessionNationalHolidayRepository: NationalHolidayRepository {
    private let baseURL: URL
    private let session: URLSession
    private let jsonDecoder: JSONDecoder

    init(
        baseURL: URL = CalioAPIConfig.baseURL,
        session: URLSession = .shared,
        jsonDecoder: JSONDecoder = JSONDecoder()
    ) {
        self.baseURL = baseURL
        self.session = session
        self.jsonDecoder = jsonDecoder
    }

    func fetchNationalHolidays(from startDay: DayKey, to endDay: DayKey) async throws -> [NationalHolidayResponseDTO] {
        let request = makeRequest(
            method: "GET",
            url: try nationalHolidaysURL(
                queryItems: [
                    URLQueryItem(name: "from", value: Self.dateString(from: startDay)),
                    URLQueryItem(name: "to", value: Self.dateString(from: endDay))
                ]
            )
        )

        return try await response([NationalHolidayResponseDTO].self, for: request)
    }

    private func nationalHolidaysURL(queryItems: [URLQueryItem]) throws -> URL {
        var components = URLComponents(
            url: baseURL.appendingPathComponent("api/national-holidays"),
            resolvingAgainstBaseURL: false
        )
        components?.queryItems = queryItems

        guard let url = components?.url else {
            throw NationalHolidayRepositoryError.invalidURL
        }

        return url
    }

    private func makeRequest(method: String, url: URL) -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
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
            throw NationalHolidayRepositoryError.network(error)
        } catch {
            throw NationalHolidayRepositoryError.unexpected(error)
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw NationalHolidayRepositoryError.invalidResponse
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            throw NationalHolidayRepositoryError.backend(
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
            throw NationalHolidayRepositoryError.decoding(error)
        } catch {
            throw NationalHolidayRepositoryError.unexpected(error)
        }
    }

    private static func dateString(from day: DayKey) -> String {
        String(format: "%04d-%02d-%02d", day.year, day.month, day.day)
    }
}
