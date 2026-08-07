import Foundation
import Testing
@testable import Calio

@Suite(.serialized)
struct APIClientTests {
    @Test func typedResponseBuildsEncodedRequestAndBearerAuthorization() async throws {
        let session = makeSession()
        let tokenProvider = TestAuthTokenProvider(accessToken: "guest-token")
        let client = APIClient(
            baseURL: try #require(URL(string: "https://example.test")),
            session: session,
            authTokenProvider: tokenProvider
        )
        let startAt = Date(timeIntervalSince1970: 1_780_000_000)

        TestURLProtocol.handler = { request in
            #expect(request.url?.path == "/api/events/with space")
            #expect(request.url?.query?.contains("from=2026-05") == true)
            #expect(request.httpMethod == "POST")
            #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer guest-token")
            #expect(request.value(forHTTPHeaderField: "Accept") == "application/json")
            #expect(request.value(forHTTPHeaderField: "Content-Type") == "application/json")
            let body = try #require(request.httpBody)
            let payload = try #require(JSONSerialization.jsonObject(with: body) as? [String: Any])
            #expect(payload["title"] as? String == "회의")
            return response(for: request, statusCode: 201, body: #"{"value":"created"}"#)
        }

        let result = try await client.send(
            APIClientTestResponse.self,
            method: .post,
            pathComponents: ["api", "events", "with space"],
            queryItems: [URLQueryItem(name: "from", value: "2026-05-01")],
            authorization: .bearer,
            body: APIClientTestRequest(title: "회의", startAt: startAt)
        )

        #expect(result == APIClientTestResponse(value: "created"))
    }

    @Test func noneAuthorizationNeverSendsStoredToken() async throws {
        let client = APIClient(
            baseURL: try #require(URL(string: "https://example.test")),
            session: makeSession(),
            authTokenProvider: TestAuthTokenProvider(accessToken: "stored-token")
        )
        TestURLProtocol.handler = { request in
            #expect(request.value(forHTTPHeaderField: "Authorization") == nil)
            return response(for: request, statusCode: 200, body: #"{"value":"ok"}"#)
        }

        _ = try await client.send(
            APIClientTestResponse.self,
            method: .get,
            pathComponents: ["api", "national-holidays"],
            authorization: .none
        )
    }

    @Test func emptySuccessDoesNotDecodeBody() async throws {
        let client = APIClient(baseURL: try #require(URL(string: "https://example.test")), session: makeSession())
        TestURLProtocol.handler = { request in
            response(for: request, statusCode: 204, body: "")
        }

        try await client.sendWithoutResponse(
            method: .delete,
            pathComponents: ["api", "events", "10"],
            authorization: .bearer
        )
    }

    @Test func backendProblemDetailRetainsStatusAndErrorCode() async throws {
        let client = APIClient(baseURL: try #require(URL(string: "https://example.test")), session: makeSession())
        TestURLProtocol.handler = { request in
            response(for: request, statusCode: 400, body: #"{"type":"about:blank","title":"VALIDATION_FAILED","status":400,"detail":"Validation failed.","errorCode":"VALIDATION_FAILED"}"#)
        }

        do {
            _ = try await client.send(APIClientTestResponse.self, method: .get, pathComponents: ["api", "events"], authorization: .bearer)
            Issue.record("Expected backend failure")
        } catch let APIError.backend(statusCode, problem) {
            #expect(statusCode == 400)
            #expect(problem?.errorCode == "VALIDATION_FAILED")
            #expect(problem?.detail == "Validation failed.")
        }
    }

    @Test func redactedOrMalformedBackendBodyRemainsBackendFailure() async throws {
        let client = APIClient(baseURL: try #require(URL(string: "https://example.test")), session: makeSession())
        let bodies = [#"{"type":"about:blank","title":"Internal Server Error","status":500}"#, "<html>bad gateway</html>"]

        for body in bodies {
            TestURLProtocol.handler = { request in response(for: request, statusCode: 500, body: body) }
            do {
                _ = try await client.send(APIClientTestResponse.self, method: .get, pathComponents: ["api", "events"], authorization: .bearer)
                Issue.record("Expected backend failure")
            } catch let APIError.backend(statusCode, problem) {
                #expect(statusCode == 500)
                #expect(problem?.title == (body.hasPrefix("{") ? "Internal Server Error" : nil))
                #expect(problem?.errorCode == nil)
            }
        }
    }

    @Test func networkAndSuccessDecodingFailuresRemainDistinct() async throws {
        let client = APIClient(baseURL: try #require(URL(string: "https://example.test")), session: makeSession())
        TestURLProtocol.handler = { _ in throw URLError(.notConnectedToInternet) }

        do {
            _ = try await client.send(APIClientTestResponse.self, method: .get, pathComponents: ["api", "events"], authorization: .bearer)
            Issue.record("Expected network failure")
        } catch let APIError.network(error) {
            #expect(error.code == .notConnectedToInternet)
        }

        TestURLProtocol.handler = { request in response(for: request, statusCode: 200, body: #"{"other":"field"}"#) }
        do {
            _ = try await client.send(APIClientTestResponse.self, method: .get, pathComponents: ["api", "events"], authorization: .bearer)
            Issue.record("Expected decoding failure")
        } catch let APIError.decoding(error) {
            #expect(error is DecodingError)
        }
    }

    @Test func commonJSONCodingDecodesFractionalAndWholeSecondInstants() throws {
        let decoder = APIJSONCoding.makeDecoder()
        let fractional = try decoder.decode(APIClientTestDateResponse.self, from: #"{"date":"2026-08-05T01:02:03.456Z"}"#.data(using: .utf8)!)
        let wholeSecond = try decoder.decode(APIClientTestDateResponse.self, from: #"{"date":"2026-08-05T01:02:03Z"}"#.data(using: .utf8)!)

        #expect(fractional.date.timeIntervalSince1970 != wholeSecond.date.timeIntervalSince1970)
    }

    @Test func cancelledURLSessionRequestRemainsCancellation() async throws {
        let client = APIClient(baseURL: try #require(URL(string: "https://example.test")), session: makeSession())
        TestURLProtocol.handler = { _ in throw URLError(.cancelled) }

        do {
            _ = try await client.send(APIClientTestResponse.self, method: .get, pathComponents: ["api", "events"], authorization: .bearer)
            Issue.record("Expected cancellation")
        } catch is CancellationError {
        }
    }

    @Test func repositoriesUseExpectedEndpointAndAuthorizationPolicies() async throws {
        let baseURL = try #require(URL(string: "https://example.test"))
        let session = makeSession()
        let tokenProvider = TestAuthTokenProvider(accessToken: "guest-token")
        let authRepository = URLSessionAuthRepository(baseURL: baseURL, session: session)
        let eventRepository = URLSessionEventRepository(baseURL: baseURL, session: session, authTokenProvider: tokenProvider)
        let holidayRepository = URLSessionNationalHolidayRepository(baseURL: baseURL, session: session)
        let tagRepository = URLSessionTagRepository(baseURL: baseURL, session: session, authTokenProvider: tokenProvider)
        let date = Date(timeIntervalSince1970: 1_780_000_000)

        TestURLProtocol.handler = { request in
            #expect(request.url?.path == "/api/auth/guest")
            #expect(request.httpMethod == "POST")
            #expect(request.value(forHTTPHeaderField: "Authorization") == nil)
            return response(for: request, statusCode: 201, body: #"{"accessToken":"token","tokenType":"Bearer"}"#)
        }
        _ = try await authRepository.issueGuestToken()

        TestURLProtocol.handler = { request in
            #expect(request.url?.path == "/api/events")
            #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer guest-token")
            return response(for: request, statusCode: 200, body: "[]")
        }
        _ = try await eventRepository.fetchEvents(from: date, to: date.addingTimeInterval(3600))

        TestURLProtocol.handler = { request in
            #expect(request.url?.path == "/api/national-holidays")
            #expect(request.url?.query?.contains("from=2026-08-01") == true)
            #expect(request.value(forHTTPHeaderField: "Authorization") == nil)
            return response(for: request, statusCode: 200, body: "[]")
        }
        _ = try await holidayRepository.fetchNationalHolidays(
            from: DayKey(year: 2026, month: 8, day: 1),
            to: DayKey(year: 2026, month: 8, day: 31)
        )

        TestURLProtocol.handler = { request in
            #expect(request.url?.path == "/api/tags")
            #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer guest-token")
            return response(for: request, statusCode: 200, body: "[]")
        }
        _ = try await tagRepository.fetchTags()
    }
}

private struct APIClientTestRequest: Encodable {
    let title: String
    let startAt: Date
}

private struct APIClientTestResponse: Decodable, Equatable {
    let value: String
}

private struct APIClientTestDateResponse: Decodable {
    let date: Date
}

private final class TestAuthTokenProvider: AuthTokenProvider {
    let accessToken: String?

    init(accessToken: String?) {
        self.accessToken = accessToken
    }
}

private final class TestURLProtocol: URLProtocol {
    nonisolated(unsafe) static var handler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        guard let handler = Self.handler else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }
        do {
            let (response, data) = try handler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {
    }
}

private func makeSession() -> URLSession {
    let configuration = URLSessionConfiguration.ephemeral
    configuration.protocolClasses = [TestURLProtocol.self]
    return URLSession(configuration: configuration)
}

private func response(for request: URLRequest, statusCode: Int, body: String) -> (HTTPURLResponse, Data) {
    let response = HTTPURLResponse(
        url: request.url!,
        statusCode: statusCode,
        httpVersion: nil,
        headerFields: ["Content-Type": "application/json"]
    )!
    return (response, Data(body.utf8))
}
