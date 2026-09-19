import Foundation
import Testing

@testable import Calio

@Suite(.serialized)
struct VoteRepositoryTests {
  private let publicId = UUID(uuidString: "9F17BFC0-D2ED-48EA-9253-7A98EBCA4C2F")!

  @Test func repositoryUsesVoteEndpointAuthorizationAndPayloadContracts() async throws {
    let repository = URLSessionVoteRepository(
      baseURL: try #require(URL(string: "https://example.test")),
      session: makeVoteSession(),
      authTokenProvider: StaticAuthTokenProvider(accessToken: "guest-token")
    )

    MockURLProtocol.requestHandler = { request in
      #expect(request.url?.path == "/api/vote-rooms")
      #expect(request.httpMethod == "POST")
      #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer guest-token")
      let body = try #require(requestBodyData(from: request))
      let payload = try #require(JSONSerialization.jsonObject(with: body) as? [String: String])
      #expect(payload == ["name": "가을 여행 일정", "candidateEndDate": "2026-10-18"])
      return voteResponse(
        for: request,
        statusCode: 201,
        body:
          #"{"publicId":"9F17BFC0-D2ED-48EA-9253-7A98EBCA4C2F","name":"가을 여행 일정","candidateStartDate":"2026-09-18","candidateEndDate":"2026-10-18"}"#
      )
    }
    _ = try await repository.createVoteRoom(
      CreateVoteRoomRequestDTO(name: "가을 여행 일정", candidateEndDate: "2026-10-18")
    )

    MockURLProtocol.requestHandler = { request in
      #expect(request.url?.path == "/api/vote-rooms/9F17BFC0-D2ED-48EA-9253-7A98EBCA4C2F")
      #expect(request.httpMethod == "GET")
      #expect(request.value(forHTTPHeaderField: "Authorization") == nil)
      return voteResponse(
        for: request,
        statusCode: 200,
        body:
          #"{"publicId":"9F17BFC0-D2ED-48EA-9253-7A98EBCA4C2F","name":"가을 여행 일정","candidateStartDate":"2026-09-18","candidateEndDate":"2026-10-18","dates":[],"submittedNicknames":[]}"#
      )
    }
    _ = try await repository.fetchVoteResult(publicId: publicId)

    MockURLProtocol.requestHandler = { request in
      #expect(
        request.url?.path == "/api/vote-rooms/9F17BFC0-D2ED-48EA-9253-7A98EBCA4C2F/participants")
      #expect(request.httpMethod == "POST")
      #expect(request.value(forHTTPHeaderField: "Authorization") == nil)
      let body = try #require(requestBodyData(from: request))
      let payload = try #require(JSONSerialization.jsonObject(with: body) as? [String: String])
      #expect(payload == ["nickname": "민지", "password": "secret"])
      return voteResponse(
        for: request, statusCode: 201, body: #"{"nickname":"민지","status":"REGISTERED"}"#)
    }
    _ = try await repository.createVoteParticipant(
      publicId: publicId,
      request: CreateVoteParticipantRequestDTO(nickname: "민지", password: "secret")
    )

    MockURLProtocol.requestHandler = { request in
      #expect(
        request.url?.path == "/api/vote-rooms/9F17BFC0-D2ED-48EA-9253-7A98EBCA4C2F/votes/lookup")
      #expect(request.httpMethod == "POST")
      #expect(request.value(forHTTPHeaderField: "Authorization") == nil)
      let body = try #require(requestBodyData(from: request))
      let payload = try #require(JSONSerialization.jsonObject(with: body) as? [String: String])
      #expect(payload == ["nickname": "민지", "password": "secret"])
      return voteResponse(
        for: request, statusCode: 200,
        body: #"{"nickname":"민지","status":"SUBMITTED","unavailableDates":["2026-09-19"]}"#)
    }
    _ = try await repository.lookupVoteParticipantSelection(
      publicId: publicId,
      request: LookupVoteParticipantSelectionRequestDTO(nickname: "민지", password: "secret")
    )

    MockURLProtocol.requestHandler = { request in
      #expect(request.url?.path == "/api/vote-rooms/9F17BFC0-D2ED-48EA-9253-7A98EBCA4C2F/votes")
      #expect(request.httpMethod == "PUT")
      #expect(request.value(forHTTPHeaderField: "Authorization") == nil)
      let body = try #require(requestBodyData(from: request))
      let payload = try #require(JSONSerialization.jsonObject(with: body) as? [String: Any])
      #expect(payload["nickname"] as? String == "민지")
      #expect(payload["password"] as? String == "secret")
      #expect(payload["unavailableDates"] as? [String] == ["2026-09-19"])
      return voteResponse(
        for: request, statusCode: 200,
        body: #"{"nickname":"민지","status":"SUBMITTED","unavailableDates":["2026-09-19"]}"#)
    }
    _ = try await repository.submitVotes(
      publicId: publicId,
      request: SubmitVoteRequestDTO(
        nickname: "민지",
        password: "secret",
        unavailableDates: ["2026-09-19"]
      )
    )
  }
}

private func makeVoteSession() -> URLSession {
  let configuration = URLSessionConfiguration.ephemeral
  configuration.protocolClasses = [MockURLProtocol.self]
  return URLSession(configuration: configuration)
}

private func voteResponse(
  for request: URLRequest,
  statusCode: Int,
  body: String
) -> (HTTPURLResponse, Data) {
  guard let url = request.url else {
    fatalError("Expected request URL")
  }
  let response = HTTPURLResponse(
    url: url,
    statusCode: statusCode,
    httpVersion: nil,
    headerFields: ["Content-Type": "application/json"]
  )!
  return (response, Data(body.utf8))
}
