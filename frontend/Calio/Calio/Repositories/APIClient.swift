import Foundation

enum HTTPMethod: String {
    case get = "GET"
    case post = "POST"
    case put = "PUT"
    case patch = "PATCH"
    case delete = "DELETE"
}

enum APIAuthorization: Equatable {
    case none
    case bearer
}

enum APIError: Error {
    case invalidRequest
    case invalidResponse
    case network(URLError)
    case encoding(Error)
    case decoding(Error)
    case backend(statusCode: Int, problem: ProblemDetailDTO?)
    case unexpected(Error)
}

struct APIClient {
    private let baseURL: URL
    private let session: URLSession
    private let jsonDecoder: JSONDecoder
    private let jsonEncoder: JSONEncoder
    private let authTokenProvider: AuthTokenProvider?

    init(
        baseURL: URL = CalioAPIConfig.baseURL,
        session: URLSession = .shared,
        jsonDecoder: JSONDecoder = APIJSONCoding.makeDecoder(),
        jsonEncoder: JSONEncoder = APIJSONCoding.makeEncoder(),
        authTokenProvider: AuthTokenProvider? = KeychainAuthTokenStore.shared
    ) {
        self.baseURL = baseURL
        self.session = session
        self.jsonDecoder = jsonDecoder
        self.jsonEncoder = jsonEncoder
        self.authTokenProvider = authTokenProvider
    }

    func send<Response: Decodable>(
        _ responseType: Response.Type,
        method: HTTPMethod,
        pathComponents: [String],
        queryItems: [URLQueryItem] = [],
        authorization: APIAuthorization
    ) async throws -> Response {
        let request = try makeRequest(
            method: method,
            pathComponents: pathComponents,
            queryItems: queryItems,
            authorization: authorization
        )
        return try await send(responseType, request: request)
    }

    func send<Response: Decodable, Body: Encodable>(
        _ responseType: Response.Type,
        method: HTTPMethod,
        pathComponents: [String],
        queryItems: [URLQueryItem] = [],
        authorization: APIAuthorization,
        body: Body
    ) async throws -> Response {
        let request = try makeRequest(
            method: method,
            pathComponents: pathComponents,
            queryItems: queryItems,
            authorization: authorization,
            body: body
        )
        return try await send(responseType, request: request)
    }

    func sendWithoutResponse(
        method: HTTPMethod,
        pathComponents: [String],
        queryItems: [URLQueryItem] = [],
        authorization: APIAuthorization
    ) async throws {
        let request = try makeRequest(
            method: method,
            pathComponents: pathComponents,
            queryItems: queryItems,
            authorization: authorization
        )
        _ = try await data(for: request)
    }

    private func makeRequest(
        method: HTTPMethod,
        pathComponents: [String],
        queryItems: [URLQueryItem],
        authorization: APIAuthorization
    ) throws -> URLRequest {
        var request = URLRequest(url: try url(pathComponents: pathComponents, queryItems: queryItems))
        request.httpMethod = method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        applyAuthorization(authorization, to: &request)
        return request
    }

    private func makeRequest<Body: Encodable>(
        method: HTTPMethod,
        pathComponents: [String],
        queryItems: [URLQueryItem],
        authorization: APIAuthorization,
        body: Body
    ) throws -> URLRequest {
        var request = try makeRequest(
            method: method,
            pathComponents: pathComponents,
            queryItems: queryItems,
            authorization: authorization
        )
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        do {
            request.httpBody = try jsonEncoder.encode(body)
        } catch {
            throw APIError.encoding(error)
        }
        return request
    }

    private func url(pathComponents: [String], queryItems: [URLQueryItem]) throws -> URL {
        let pathURL = pathComponents.reduce(baseURL) { partialURL, component in
            partialURL.appendingPathComponent(component)
        }
        var components = URLComponents(url: pathURL, resolvingAgainstBaseURL: false)
        components?.queryItems = queryItems.isEmpty ? nil : queryItems
        guard let url = components?.url else {
            throw APIError.invalidRequest
        }
        return url
    }

    private func applyAuthorization(_ authorization: APIAuthorization, to request: inout URLRequest) {
        guard authorization == .bearer,
              let token = authTokenProvider?.accessToken,
              !token.isEmpty else {
            return
        }
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
    }

    private func send<Response: Decodable>(_ responseType: Response.Type, request: URLRequest) async throws -> Response {
        let data = try await data(for: request)
        do {
            return try jsonDecoder.decode(responseType, from: data)
        } catch let error as DecodingError {
            throw APIError.decoding(error)
        } catch {
            throw APIError.unexpected(error)
        }
    }

    private func data(for request: URLRequest) async throws -> Data {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch is CancellationError {
            throw CancellationError()
        } catch let error as URLError where error.code == .cancelled || Task.isCancelled {
            throw CancellationError()
        } catch let error as URLError {
            throw APIError.network(error)
        } catch {
            throw APIError.unexpected(error)
        }

        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            throw APIError.backend(
                statusCode: httpResponse.statusCode,
                problem: try? jsonDecoder.decode(ProblemDetailDTO.self, from: data)
            )
        }
        return data
    }
}
