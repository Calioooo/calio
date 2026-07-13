//
//  URLSessionAuthRepository.swift
//  Calio
//
//  Created by Codex on 7/13/26.
//

import Foundation

struct URLSessionAuthRepository: AuthRepository {
    private let baseURL: URL
    private let session: URLSession
    private let jsonDecoder: JSONDecoder

    init(
        baseURL: URL = CalioAPIConfig.baseURL,
        session: URLSession = .shared,
        jsonDecoder: JSONDecoder = EventJSONCoding.makeDecoder()
    ) {
        self.baseURL = baseURL
        self.session = session
        self.jsonDecoder = jsonDecoder
    }

    func issueGuestToken() async throws -> GuestAuthResponseDTO {
        let request = makeRequest(
            method: "POST",
            url: baseURL.appendingPathComponent("api/auth/guest")
        )

        return try await response(GuestAuthResponseDTO.self, for: request)
    }

    private func makeRequest(method: String, url: URL) -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        return request
    }

    private func response<T: Decodable>(_ type: T.Type, for request: URLRequest) async throws -> T {
        let data = try await data(for: request)

        do {
            return try jsonDecoder.decode(type, from: data)
        } catch let error as DecodingError {
            throw AuthRepositoryError.decoding(error)
        } catch {
            throw AuthRepositoryError.unexpected(error)
        }
    }

    private func data(for request: URLRequest) async throws -> Data {
        let data: Data
        let response: URLResponse

        do {
            (data, response) = try await session.data(for: request)
        } catch let error as URLError {
            throw AuthRepositoryError.network(error)
        } catch {
            throw AuthRepositoryError.unexpected(error)
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw AuthRepositoryError.invalidResponse
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            throw AuthRepositoryError.backend(
                statusCode: httpResponse.statusCode,
                response: try? jsonDecoder.decode(ErrorResponseDTO.self, from: data)
            )
        }

        return data
    }
}
