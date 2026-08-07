import Testing
import Foundation
import SwiftUI
@testable import Calio

@Suite(.serialized)
struct NetworkRepositoryTests {

    @Test func urlSessionEventRepositoryCreatesEventWithInjectedBaseURLAndContractBody() async throws {
        let calendar = fixedCalendar
        let startAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 10, hour: 9)))
        let endAt = startAt.addingTimeInterval(3600)
        let responseJSON = """
        {
          "id": 88,
          "title": "새 일정",
          "description": "메모",
          "startAt": "2026-06-10T09:00:00Z",
          "endAt": "2026-06-10T10:00:00Z",
          "tag": {
            "id": 1,
            "title": "업무",
            "colorCode": "#3B82F6",
            "tagType": "DEFAULT"
          },
          "createdAt": "2026-06-10T09:00:00Z",
          "updatedAt": "2026-06-10T09:00:00Z"
        }
        """.data(using: .utf8)!
        var capturedRequest: URLRequest?
        MockURLProtocol.requestHandler = { request in
            capturedRequest = request
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 201,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, responseJSON)
        }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let repository = URLSessionEventRepository(
            baseURL: URL(string: "https://example.test")!,
            session: session
        )

        let event = try await repository.createEvent(
            CreateEventRequestDTO(
                title: "새 일정",
                description: "메모",
                startAt: startAt,
                endAt: endAt
            )
        )
        let request = try #require(capturedRequest)
        let body = try #require(requestBodyData(from: request))
        let object = try #require(JSONSerialization.jsonObject(with: body) as? [String: Any])

        #expect(event.id == 88)
        #expect(request.url?.absoluteString == "https://example.test/api/events")
        #expect(request.httpMethod == "POST")
        #expect(request.value(forHTTPHeaderField: "Content-Type") == "application/json")
        #expect(Set(object.keys) == ["title", "description", "startAt", "endAt"])
        #expect(object["title"] as? String == "새 일정")
        #expect(object["selectedColorCode"] == nil)
    }

    @Test func urlSessionEventRepositoryCreatesRecurrenceEventWithInjectedBaseURLAndContractBody() async throws {
        let responseJSON = """
        {
          "recurrenceId": 123,
          "recurrenceTitle": "반복 일정",
          "recurrenceDescription": "설명",
          "recurrenceStartDate": "2026-08-01",
          "recurrenceEndDate": "2026-08-31",
          "recurrenceStartTime": "00:00:00",
          "recurrenceEndTime": "01:00:00",
          "recurrenceFrequency": "MONTHLY",
          "tag": {
            "id": 1,
            "title": "업무",
            "colorCode": "#3B82F6",
            "tagType": "DEFAULT"
          }
        }
        """.data(using: .utf8)!
        var capturedRequest: URLRequest?
        MockURLProtocol.requestHandler = { request in
            capturedRequest = request
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 201,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, responseJSON)
        }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let repository = URLSessionEventRepository(
            baseURL: URL(string: "https://example.test")!,
            session: session
        )

        let response = try await repository.createRecurrenceEvent(
            CreateRecurrenceEventRequestDTO(
                recurrenceTitle: "반복 일정",
                recurrenceDescription: "설명",
                recurrenceStartDate: "2026-08-01",
                recurrenceEndDate: "2026-08-31",
                recurrenceStartTime: "00:00:00",
                recurrenceEndTime: "01:00:00",
                recurrenceFrequency: .monthly
            )
        )
        let request = try #require(capturedRequest)
        let body = try #require(requestBodyData(from: request))
        let object = try #require(JSONSerialization.jsonObject(with: body) as? [String: Any])

        #expect(response.recurrenceId == 123)
        #expect(request.url?.absoluteString == "https://example.test/api/recurrence-events")
        #expect(request.httpMethod == "POST")
        #expect(Set(object.keys) == [
            "recurrenceTitle",
            "recurrenceDescription",
            "recurrenceStartDate",
            "recurrenceEndDate",
            "recurrenceStartTime",
            "recurrenceEndTime",
            "recurrenceFrequency"
        ])
        #expect(object["recurrenceFrequency"] as? String == "MONTHLY")
        #expect(object["colorCode"] == nil)
    }

    @Test func urlSessionEventRepositoryUpdatesEventWithInjectedBaseURLAndContractBody() async throws {
        let calendar = fixedCalendar
        let startAt = try #require(calendar.date(from: DateComponents(year: 2026, month: 6, day: 10, hour: 9)))
        let endAt = startAt.addingTimeInterval(3600)
        let responseJSON = """
        {
          "id": 88,
          "title": "수정 일정",
          "description": "수정 메모",
          "startAt": "2026-06-10T09:00:00Z",
          "endAt": "2026-06-10T10:00:00Z",
          "tag": {
            "id": 1,
            "title": "업무",
            "colorCode": "#3B82F6",
            "tagType": "DEFAULT"
          },
          "createdAt": "2026-06-10T09:00:00Z",
          "updatedAt": "2026-06-10T10:00:00Z"
        }
        """.data(using: .utf8)!
        var capturedRequest: URLRequest?
        MockURLProtocol.requestHandler = { request in
            capturedRequest = request
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, responseJSON)
        }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let repository = URLSessionEventRepository(
            baseURL: URL(string: "https://example.test")!,
            session: session
        )

        let event = try await repository.updateEvent(
            eventId: 88,
            request: UpdateEventRequestDTO(
                title: "수정 일정",
                description: "수정 메모",
                startAt: startAt,
                endAt: endAt
            )
        )
        let request = try #require(capturedRequest)
        let body = try #require(requestBodyData(from: request))
        let object = try #require(JSONSerialization.jsonObject(with: body) as? [String: Any])

        #expect(event.id == 88)
        #expect(request.url?.absoluteString == "https://example.test/api/events/88")
        #expect(request.httpMethod == "PUT")
        #expect(Set(object.keys) == ["title", "description", "startAt", "endAt"])
        #expect(object["colorCode"] == nil)
    }

    @Test func urlSessionEventRepositoryUpdatesRecurrenceEndpointsWithBackendContractBodies() async throws {
        let recurrenceResponseJSON = """
        {
          "recurrenceId": 700,
          "recurrenceTitle": "수정 반복 일정",
          "recurrenceDescription": "설명",
          "recurrenceStartDate": "2026-08-01",
          "recurrenceEndDate": "2026-08-31",
          "recurrenceStartTime": "09:00:00",
          "recurrenceEndTime": "10:00:00",
          "recurrenceFrequency": "WEEKLY",
          "tag": {
            "id": 1,
            "title": "업무",
            "colorCode": "#3B82F6",
            "tagType": "DEFAULT"
          }
        }
        """.data(using: .utf8)!
        let occurrenceResponseJSON = """
        {
          "id": 701,
          "title": "수정 반복 항목",
          "description": "설명",
          "startAt": "2026-08-01T09:00:00Z",
          "endAt": "2026-08-01T10:00:00Z",
          "importantEvent": true,
          "recurrenceId": 700,
          "isRecurrenceOccurrence": true,
          "tag": {
            "id": 1,
            "title": "업무",
            "colorCode": "#3B82F6",
            "tagType": "DEFAULT"
          },
          "createdAt": "2026-08-01T09:00:00Z",
          "updatedAt": "2026-08-01T10:00:00Z"
        }
        """.data(using: .utf8)!
        var capturedRequests: [URLRequest] = []
        MockURLProtocol.requestHandler = { request in
            capturedRequests.append(request)
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: nil
            )!
            let isOccurrenceUpdate = request.url?.path.hasSuffix("/occurrences") == true
            let data = isOccurrenceUpdate ? occurrenceResponseJSON : recurrenceResponseJSON
            return (response, data)
        }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let repository = URLSessionEventRepository(
            baseURL: URL(string: "https://example.test")!,
            session: session
        )

        _ = try await repository.updateRecurrenceEvent(
            recurrenceId: 700,
            request: UpdateRecurrenceEventRequestDTO(
                title: "수정 반복 일정",
                description: "설명",
                startDate: "1970-01-01",
                endDate: "1970-01-01",
                startTime: "00:00:00",
                endTime: "01:00:00",
                recurrenceFrequency: .weekly
            )
        )
        _ = try await repository.updateRecurrenceOccurrence(
            recurrenceId: 700,
            request: UpdateRecurrenceOccurrenceRequestDTO(
                originStartAt: Date(timeIntervalSince1970: 0),
                startAt: Date(timeIntervalSince1970: 0),
                endAt: Date(timeIntervalSince1970: 3600)
            )
        )

        #expect(capturedRequests.map { $0.url?.absoluteString } == [
            "https://example.test/api/recurrence-events/700",
            "https://example.test/api/recurrence-events/700/occurrences"
        ])
        #expect(capturedRequests.map(\.httpMethod) == ["PUT", "PATCH"])
    }

    @Test func urlSessionEventRepositoryDeletesWithoutRequestBodiesAndAcceptsNoContent() async throws {
        var capturedRequests: [URLRequest] = []
        MockURLProtocol.requestHandler = { request in
            capturedRequests.append(request)
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 204,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, Data())
        }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let repository = URLSessionEventRepository(
            baseURL: URL(string: "https://example.test")!,
            session: session
        )

        try await repository.deleteEvent(eventId: 1)
        try await repository.deleteRecurrenceEvent(recurrenceId: 2)
        try await repository.deleteRecurrenceOccurrence(
            recurrenceId: 2,
            originStartAt: Date(timeIntervalSince1970: 0)
        )

        #expect(capturedRequests.map { $0.url?.absoluteString } == [
            "https://example.test/api/events/1",
            "https://example.test/api/recurrence-events/2",
            "https://example.test/api/recurrence-events/2/occurrences?originStartAt=1970-01-01T00:00:00Z"
        ])
        #expect(capturedRequests.allSatisfy { $0.httpMethod == "DELETE" })
        #expect(capturedRequests.allSatisfy { requestBodyData(from: $0) == nil })
    }

    @Test func urlSessionTagRepositoryManagesCustomTagsWithInjectedBaseURLAndContractBody() async throws {
        let tagResponseJSON = """
        {
          "id": 9,
          "title": "운동",
          "colorCode": "#10B981",
          "tagType": "CUSTOM"
        }
        """.data(using: .utf8)!
        var capturedRequests: [URLRequest] = []
        MockURLProtocol.requestHandler = { request in
            capturedRequests.append(request)
            let statusCode = request.httpMethod == "POST" ? 201 : 200
            let data = request.httpMethod == "DELETE" ? Data() : tagResponseJSON
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: request.httpMethod == "DELETE" ? 204 : statusCode,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, data)
        }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let repository = URLSessionTagRepository(
            baseURL: URL(string: "https://example.test")!,
            session: session
        )

        let createdTag = try await repository.createCustomTag(
            CustomTagRequestDTO(title: "운동", colorCode: "#10B981")
        )
        let updatedTag = try await repository.updateCustomTag(
            tagId: 9,
            request: CustomTagRequestDTO(title: "운동", colorCode: "#10B981")
        )
        try await repository.deleteCustomTag(tagId: 9)

        #expect(createdTag.tagType == .custom)
        #expect(updatedTag.id == 9)
        #expect(capturedRequests.map { $0.url?.absoluteString } == [
            "https://example.test/api/custom-tags",
            "https://example.test/api/custom-tags/9",
            "https://example.test/api/custom-tags/9"
        ])
        #expect(capturedRequests.map { $0.httpMethod ?? "" } == ["POST", "PUT", "DELETE"])

        let createBody = try #require(requestBodyData(from: capturedRequests[0]))
        let updateBody = try #require(requestBodyData(from: capturedRequests[1]))
        let createObject = try #require(JSONSerialization.jsonObject(with: createBody) as? [String: Any])
        let updateObject = try #require(JSONSerialization.jsonObject(with: updateBody) as? [String: Any])

        #expect(createObject["title"] as? String == "운동")
        #expect(createObject["colorCode"] as? String == "#10B981")
        #expect(Set(createObject.keys) == ["title", "colorCode"])
        #expect(updateObject["title"] as? String == "운동")
        #expect(updateObject["colorCode"] as? String == "#10B981")
        #expect(requestBodyData(from: capturedRequests[2]) == nil)
    }

    @Test func urlSessionAuthRepositoryIssuesGuestTokenWithBackendContract() async throws {
        let responseJSON = """
        {
          "accessToken": "guest-token",
          "tokenType": "Bearer"
        }
        """.data(using: .utf8)!
        var capturedRequest: URLRequest?
        MockURLProtocol.requestHandler = { request in
            capturedRequest = request
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 201,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, responseJSON)
        }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let repository = URLSessionAuthRepository(
            baseURL: URL(string: "https://example.test")!,
            session: session
        )

        let response = try await repository.issueGuestToken()
        let request = try #require(capturedRequest)

        #expect(response == GuestAuthResponseDTO(accessToken: "guest-token", tokenType: "Bearer"))
        #expect(request.url?.absoluteString == "https://example.test/api/auth/guest")
        #expect(request.httpMethod == "POST")
        #expect(request.value(forHTTPHeaderField: "Accept") == "application/json")
        #expect(request.value(forHTTPHeaderField: "Authorization") == nil)
        #expect(requestBodyData(from: request) == nil)
    }

    @Test func authServiceIssuesGuestTokenOnceAndStoresRawToken() async throws {
        let repository = RecordingAuthRepository(
            response: GuestAuthResponseDTO(accessToken: "guest-token", tokenType: "Bearer")
        )
        let tokenStore = InMemoryAuthTokenStore()
        let service = AuthService(repository: repository, tokenStore: tokenStore)

        let token = try await service.ensureGuestAuthentication()
        let reusedToken = try await service.ensureGuestAuthentication()

        #expect(token == "guest-token")
        #expect(reusedToken == "guest-token")
        #expect(tokenStore.accessToken == "guest-token")
        #expect(repository.issueGuestTokenCallCount == 1)
    }

    @Test func urlSessionProtectedRepositoriesAttachStoredBearerToken() async throws {
        let tagResponseJSON = """
        [
          {
            "id": 1,
            "title": "업무",
            "colorCode": "#3B82F6",
            "tagType": "DEFAULT"
          }
        ]
        """.data(using: .utf8)!
        var capturedRequests: [URLRequest] = []
        MockURLProtocol.requestHandler = { request in
            capturedRequests.append(request)
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: nil
            )!
            let data = request.url?.path == "/api/events" ? Data("[]".utf8) : tagResponseJSON
            return (response, data)
        }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let tokenProvider = StaticAuthTokenProvider(accessToken: "guest-token")
        let eventRepository = URLSessionEventRepository(
            baseURL: URL(string: "https://example.test")!,
            session: session,
            authTokenProvider: tokenProvider
        )
        let tagRepository = URLSessionTagRepository(
            baseURL: URL(string: "https://example.test")!,
            session: session,
            authTokenProvider: tokenProvider
        )

        _ = try await eventRepository.fetchEvents(
            from: Date(timeIntervalSince1970: 0),
            to: Date(timeIntervalSince1970: 3600)
        )
        _ = try await tagRepository.fetchTags()

        #expect(capturedRequests.map { $0.value(forHTTPHeaderField: "Authorization") } == [
            "Bearer guest-token",
            "Bearer guest-token"
        ])
    }

    @Test func urlSessionNationalHolidayRepositoryFetchesWithLocalDateQuery() async throws {
        let responseJSON = """
        [
          {
            "nationalHolidayId": 1,
            "holidayDate": "2026-06-06",
            "holidayTitle": "현충일"
          }
        ]
        """.data(using: .utf8)!
        var capturedRequest: URLRequest?
        MockURLProtocol.requestHandler = { request in
            capturedRequest = request
            let response = HTTPURLResponse(
                url: request.url!,
                statusCode: 200,
                httpVersion: nil,
                headerFields: nil
            )!
            return (response, responseJSON)
        }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        let session = URLSession(configuration: configuration)
        let repository = URLSessionNationalHolidayRepository(
            baseURL: URL(string: "https://example.test")!,
            session: session
        )

        let response = try await repository.fetchNationalHolidays(
            from: DayKey(year: 2026, month: 6, day: 1),
            to: DayKey(year: 2026, month: 6, day: 30)
        )
        let request = try #require(capturedRequest)

        #expect(response.map(\.holidayTitle) == ["현충일"])
        #expect(request.url?.absoluteString == "https://example.test/api/national-holidays?from=2026-06-01&to=2026-06-30")
        #expect(request.httpMethod == "GET")
        #expect(request.value(forHTTPHeaderField: "Accept") == "application/json")
    }
}
