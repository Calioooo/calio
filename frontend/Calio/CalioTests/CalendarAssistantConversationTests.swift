import Foundation
import Testing

@testable import Calio

@Suite(.serialized)
struct CalendarAssistantConversationTests {
  @Test func assistantMarkdownRendersFormattingSyntaxAndFallsBackToPlainText() {
    let formatted = CalendarAssistantMarkdown.attributedText(from: "**중요** 일정")
    let malformed = CalendarAssistantMarkdown.attributedText(from: "[닫히지 않은 링크](")

    #expect(String(formatted.characters) == "중요 일정")
    #expect(
      formatted.runs.contains {
        $0.inlinePresentationIntent?.contains(.stronglyEmphasized) == true
      })
    #expect(String(malformed.characters) == "[닫히지 않은 링크](")
  }

  @Test func assistantBlockDecodingPreservesBackendOrderAndUnknownBlocks() throws {
    let data = Data(
      """
      [
        {"type":"FREE_TIMES","items":[{"start":"2026-08-18T13:00:00+09:00","end":"2026-08-18T15:00:00+09:00","allDayNotices":[]}]},
        {"type":"FUTURE_BLOCK","items":[]},
        {"type":"FREE_TIMES","items":[]}
      ]
      """.utf8)

    let blocks = try APIJSONCoding.makeDecoder().decode([CalendarAssistantBlockDTO].self, from: data)

    #expect(blocks.count == 3)
    guard case .freeTimes(let first) = blocks[0] else {
      Issue.record("Expected first FREE_TIMES block")
      return
    }
    #expect(first.first?.start != nil)
    guard case .unsupported(let type) = blocks[1] else {
      Issue.record("Unknown blocks must remain visible to the client")
      return
    }
    #expect(type == "FUTURE_BLOCK")
    guard case .freeTimes(let last) = blocks[2] else {
      Issue.record("Expected final FREE_TIMES block")
      return
    }
    #expect(last.isEmpty)
  }

  @Test func createMutationPreviewDecodesBackendPersonalDefaultTag() throws {
    let data = Data(
      """
      {
        "conversationId": "conversation-7",
        "assistantMessage": "팀 회의를 만들까요?",
        "blocks": [{
          "type": "MUTATION_PREVIEW",
          "items": [{
            "type": "CREATE",
            "scope": "EVENT",
            "before": null,
            "after": {
              "title": "팀 회의",
              "startAt": "2026-09-04T06:00:00Z",
              "endAt": "2026-09-04T07:00:00Z",
              "allDay": false,
              "tag": {
                "id": 1,
                "title": "기타",
                "colorCode": "#64748B",
                "tagType": "PERSONAL_DEFAULT"
              }
            },
            "recurrence": null
          }]
        }]
      }
      """.utf8)

    let response = try APIJSONCoding.makeDecoder().decode(
      SendCalendarConversationMessageResponseDTO.self, from: data)

    guard case .mutationPreviews(let previews) = response.blocks.first,
      let preview = previews.first,
      let after = preview.after
    else {
      Issue.record("Expected a create mutation preview with an after event")
      return
    }
    #expect(preview.type == "CREATE")
    #expect(preview.before == nil)
    #expect(after.title == "팀 회의")
    #expect(after.tag.tagType == .defaultTag)
    #expect(after.tag.title == "기타")
  }

  @Test func repositoryUsesAuthenticatedConversationPathsAndTimezonePayload() async throws {
    let response = Data(
      "{\"conversationId\":\"conversation-7\",\"assistantMessage\":\"확인했어요.\",\"blocks\":[]}".utf8)
    var requests: [URLRequest] = []
    MockURLProtocol.requestHandler = { request in
      requests.append(request)
      return (
        HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!,
        response
      )
    }
    let configuration = URLSessionConfiguration.ephemeral
    configuration.protocolClasses = [MockURLProtocol.self]
    let repository = URLSessionCalendarConversationRepository(
      baseURL: URL(string: "https://example.test")!,
      session: URLSession(configuration: configuration),
      authTokenProvider: StaticAuthTokenProvider(accessToken: "test-token")
    )

    _ = try await repository.createConversation()
    _ = try await repository.sendMessage(
      conversationId: "conversation-7", request: .init(message: "회의 옮겨줘", timeZone: "Asia/Seoul"))

    #expect(
      requests.map { $0.url?.path } == [
        "/api/ai/calendar/conversations", "/api/ai/calendar/conversations/conversation-7/messages",
      ])
    #expect(
      requests.allSatisfy { $0.value(forHTTPHeaderField: "Authorization") == "Bearer test-token" })
    let body = try #require(requestBodyData(from: requests[1]))
    let object = try #require(JSONSerialization.jsonObject(with: body) as? [String: String])
    #expect(object == ["message": "회의 옮겨줘", "timeZone": "Asia/Seoul"])
  }

  @Test func repositorySurfacesBackendFailureForConversationRequests() async throws {
    MockURLProtocol.requestHandler = { request in
      (
        HTTPURLResponse(url: request.url!, statusCode: 503, httpVersion: nil, headerFields: nil)!,
        Data(#"{"title":"Service Unavailable","status":503}"#.utf8)
      )
    }
    let configuration = URLSessionConfiguration.ephemeral
    configuration.protocolClasses = [MockURLProtocol.self]
    let repository = URLSessionCalendarConversationRepository(
      baseURL: URL(string: "https://example.test")!,
      session: URLSession(configuration: configuration),
      authTokenProvider: StaticAuthTokenProvider(accessToken: "test-token")
    )

    do {
      _ = try await repository.createConversation()
      Issue.record("Expected a backend failure")
    } catch let APIError.backend(statusCode, problem) {
      #expect(statusCode == 503)
      #expect(problem?.title == "Service Unavailable")
    }
  }

  @Test func servicePreservesNaturalLanguageApprovalAndMapsBlocksInOrder() async throws {
    let previewAfter = CalendarMutationEventResponseDTO(
      title: "팀 회의",
      startAt: Date(timeIntervalSince1970: 1_788_501_600),
      endAt: Date(timeIntervalSince1970: 1_788_505_200),
      allDay: false,
      tag: .init(id: 1, title: "기타", colorCode: "#64748B", tagType: .defaultTag))
    let repository = CalendarConversationRepositoryStub(
      conversationIds: ["conversation-7"],
      responses: [
        .init(
          conversationId: "conversation-7",
          assistantMessage: "적용을 준비했어요.",
          blocks: [
            .freeTimes([
              .init(
                start: Date(timeIntervalSince1970: 1_788_515_600),
                end: Date(timeIntervalSince1970: 1_788_519_200),
                allDayNotices: [])
            ]),
            .mutationPreviews([
              .init(
                type: "CREATE",
                scope: "EVENT",
                before: nil,
                after: previewAfter,
                recurrence: .init(before: [], after: ["RRULE:FREQ=WEEKLY"]))
            ]),
            .unsupported("FUTURE_BLOCK"),
          ]
        )
      ]
    )
    let service = CalendarConversationService(repository: repository)

    let response = try await service.send(
      message: "네, 이 제안을 적용해줘",
      conversationId: "conversation-7",
      timeZone: try #require(TimeZone(identifier: "Asia/Seoul"))
    )

    #expect(
      repository.sentMessages == [
        .init(conversationId: "conversation-7", message: "네, 이 제안을 적용해줘", timeZone: "Asia/Seoul")
      ])
    #expect(response.0 == "적용을 준비했어요.")
    guard case .freeTimes = response.1[0],
      case .mutationPreviews(let previews) = response.1[1],
      case .unsupported("FUTURE_BLOCK") = response.1[2],
      let preview = previews.first,
      let after = preview.after
    else {
      Issue.record("Expected service results to preserve canonical block order")
      return
    }
    #expect(preview.type == "CREATE")
    #expect(preview.scope == "EVENT")
    #expect(preview.before == nil)
    #expect(after.title == "팀 회의")
    #expect(after.tag.tagType == .defaultTag)
    #expect(preview.recurrenceBefore == [])
    #expect(preview.recurrenceAfter == ["RRULE:FREQ=WEEKLY"])
  }

  @MainActor @Test func viewModelCreatesFreshSessionAndOnlyRefreshesAfterSuccessfulMessage() async {
    let repository = CalendarConversationRepositoryStub(
      conversationIds: ["first", "second"],
      responses: [.init(conversationId: "first", assistantMessage: "처리했어요.", blocks: [])])
    var refreshCount = 0
    let first = CalendarAssistantConversationViewModel(service: .init(repository: repository)) {
      refreshCount += 1
    }
    await first.start()
    await first.send("일정을 추가해줘")

    #expect(first.state == .ready)
    #expect(first.messages.map(\.role) == [.user, .assistant])
    #expect(refreshCount == 1)

    let second = CalendarAssistantConversationViewModel(service: .init(repository: repository))
    await second.start()
    #expect(repository.createdConversationCount == 2)
    #expect(second.messages.isEmpty)
  }

  @MainActor @Test func viewModelRetainsMessagesUntilExplicitSessionClose() async {
    let repository = CalendarConversationRepositoryStub(
      conversationIds: ["first", "second"],
      responses: [.init(conversationId: "first", assistantMessage: "처리했어요.", blocks: [])]
    )
    let viewModel = CalendarAssistantConversationViewModel(service: .init(repository: repository))

    await viewModel.start()
    await viewModel.send("일정을 추가해줘")

    #expect(viewModel.messages.map(\.role) == [.user, .assistant])
    #expect(repository.createdConversationCount == 1)

    viewModel.endSession()

    #expect(viewModel.messages.isEmpty)
    #expect(viewModel.state == .connecting)

    await viewModel.start()

    #expect(repository.createdConversationCount == 2)
    #expect(viewModel.messages.isEmpty)
  }

  @MainActor @Test func viewModelGatesEmptyComposerAndPassesApprovalThroughUnchanged() async {
    let repository = CalendarConversationRepositoryStub(
      conversationIds: ["conversation-7"],
      responses: [.init(conversationId: "conversation-7", assistantMessage: "반영했어요.", blocks: [])]
    )
    let viewModel = CalendarAssistantConversationViewModel(service: .init(repository: repository))

    await viewModel.send("   ")
    #expect(repository.sentMessages.isEmpty)

    await viewModel.start()
    await viewModel.send("네, 이 제안을 적용해줘")

    #expect(
      repository.sentMessages == [
        .init(
          conversationId: "conversation-7", message: "네, 이 제안을 적용해줘",
          timeZone: TimeZone.current.identifier)
      ])
    #expect(viewModel.messages.map(\.role) == [.user, .assistant])
  }

  @MainActor @Test func viewModelKeepsTranscriptAndAllowsRetryAfterMessageFailure() async {
    let repository = CalendarConversationRepositoryStub(
      conversationIds: ["conversation-7"],
      responses: [
        .failure(CalendarAssistantFailure.message),
        .success(
          .init(conversationId: "conversation-7", assistantMessage: "다시 처리했어요.", blocks: [])),
      ]
    )
    let viewModel = CalendarAssistantConversationViewModel(service: .init(repository: repository))

    await viewModel.start()
    await viewModel.send("회의를 옮겨줘")

    #expect(viewModel.messageFailure == .message)
    #expect(viewModel.messages.map(\.role) == [.user])

    await viewModel.retryMessageSend()

    #expect(repository.sentMessages.map(\.message) == ["회의를 옮겨줘", "회의를 옮겨줘"])
    #expect(viewModel.messageFailure == nil)
    #expect(viewModel.messages.map(\.role) == [.user, .user, .assistant])
  }

  @MainActor @Test func viewModelRetriesInitialConnectionAndSerializesMessageSends() async {
    let repository = CalendarConversationRepositoryStub(
      conversationIds: ["conversation-7"],
      responses: [.init(conversationId: "conversation-7", assistantMessage: "첫 응답", blocks: [])],
      creationFailuresRemaining: 1,
      responseDelayNanoseconds: 100_000_000
    )
    let viewModel = CalendarAssistantConversationViewModel(service: .init(repository: repository))

    await viewModel.start()
    #expect(viewModel.state == .failed(.connection))

    await viewModel.retryConnection()
    #expect(viewModel.state == .ready)

    let firstSend = Task { await viewModel.send("첫 번째 요청") }
    #expect(await waitUntil { repository.sentMessages.count == 1 })
    await viewModel.send("두 번째 요청")

    #expect(repository.sentMessages.map(\.message) == ["첫 번째 요청"])
    await firstSend.value
    #expect(viewModel.messages.map(\.role) == [.user, .assistant])
  }

  @MainActor @Test func viewModelIgnoresLateMessageFromClosedSession() async {
    let repository = CalendarConversationRepositoryStub(
      conversationIds: ["first", "second"],
      responses: [
        .init(conversationId: "first", assistantMessage: "이전 대화 응답", blocks: []),
        .init(conversationId: "second", assistantMessage: "새 대화 응답", blocks: []),
      ],
      shouldSuspendResponses: true
    )
    var refreshCount = 0
    let viewModel = CalendarAssistantConversationViewModel(service: .init(repository: repository)) {
      refreshCount += 1
    }

    await viewModel.start()
    let firstSend = Task { await viewModel.send("첫 번째 요청") }
    #expect(await waitUntil { repository.sentMessages.count == 1 })
    #expect(await waitUntil { repository.suspendedResponseCount == 1 })

    viewModel.endSession()
    await viewModel.start()
    let secondSend = Task { await viewModel.send("두 번째 요청") }
    #expect(await waitUntil { repository.sentMessages.count == 2 })
    #expect(await waitUntil { repository.suspendedResponseCount == 2 })

    repository.finishNextSuspendedResponse()
    await Task.yield()

    #expect(viewModel.state == .ready)
    #expect(viewModel.messages.map(\.text) == ["두 번째 요청"])
    #expect(refreshCount == 0)

    repository.finishNextSuspendedResponse()
    await secondSend.value
    await firstSend.value

    #expect(viewModel.messages.map(\.text) == ["두 번째 요청", "새 대화 응답"])
    #expect(refreshCount == 1)
  }

  @MainActor @Test func viewModelCreatesOnlyOneConversationForConcurrentStarts() async {
    let repository = CalendarConversationRepositoryStub(
      conversationIds: ["conversation-7"],
      responses: [SendCalendarConversationMessageResponseDTO](),
      shouldSuspendCreation: true
    )
    let viewModel = CalendarAssistantConversationViewModel(service: .init(repository: repository))

    let firstStart = Task { await viewModel.start() }
    let secondStart = Task { await viewModel.start() }
    #expect(await waitUntil { repository.createdConversationCount == 1 })
    #expect(await waitUntil { repository.suspendedCreationCount == 1 })

    repository.finishNextSuspendedCreation()
    await firstStart.value
    await secondStart.value

    #expect(repository.createdConversationCount == 1)
    #expect(viewModel.state == .ready)
  }
}

private final class CalendarConversationRepositoryStub: CalendarConversationRepository {
  struct SentMessage: Equatable {
    let conversationId: String
    let message: String
    let timeZone: String
  }

  private var conversationIds: [String]
  private var responses: [Result<SendCalendarConversationMessageResponseDTO, Error>]
  private(set) var createdConversationCount = 0
  private(set) var sentMessages: [SentMessage] = []
  private var creationFailuresRemaining: Int
  private let responseDelayNanoseconds: UInt64
  private let shouldSuspendResponses: Bool
  private let shouldSuspendCreation: Bool
  private var suspendedCreationContinuations:
    [CheckedContinuation<CreateCalendarConversationResponseDTO, Error>] = []
  private var suspendedResponseContinuations:
    [CheckedContinuation<SendCalendarConversationMessageResponseDTO, Error>] = []

  var suspendedResponseCount: Int {
    suspendedResponseContinuations.count
  }
  var suspendedCreationCount: Int { suspendedCreationContinuations.count }

  init(
    conversationIds: [String],
    responses: [SendCalendarConversationMessageResponseDTO],
    creationFailuresRemaining: Int = 0,
    responseDelayNanoseconds: UInt64 = 0,
    shouldSuspendResponses: Bool = false,
    shouldSuspendCreation: Bool = false
  ) {
    self.conversationIds = conversationIds
    self.responses = responses.map(Result.success)
    self.creationFailuresRemaining = creationFailuresRemaining
    self.responseDelayNanoseconds = responseDelayNanoseconds
    self.shouldSuspendResponses = shouldSuspendResponses
    self.shouldSuspendCreation = shouldSuspendCreation
  }

  init(
    conversationIds: [String],
    responses: [Result<SendCalendarConversationMessageResponseDTO, Error>],
    creationFailuresRemaining: Int = 0,
    responseDelayNanoseconds: UInt64 = 0,
    shouldSuspendResponses: Bool = false,
    shouldSuspendCreation: Bool = false
  ) {
    self.conversationIds = conversationIds
    self.responses = responses
    self.creationFailuresRemaining = creationFailuresRemaining
    self.responseDelayNanoseconds = responseDelayNanoseconds
    self.shouldSuspendResponses = shouldSuspendResponses
    self.shouldSuspendCreation = shouldSuspendCreation
  }

  func createConversation() async throws -> CreateCalendarConversationResponseDTO {
    createdConversationCount += 1
    if creationFailuresRemaining > 0 {
      creationFailuresRemaining -= 1
      throw CalendarAssistantFailure.connection
    }
    if shouldSuspendCreation {
      return try await withCheckedThrowingContinuation { continuation in
        suspendedCreationContinuations.append(continuation)
      }
    }
    return .init(conversationId: conversationIds.removeFirst())
  }

  func sendMessage(conversationId: String, request: SendCalendarConversationMessageRequestDTO)
    async throws -> SendCalendarConversationMessageResponseDTO
  {
    sentMessages.append(
      .init(conversationId: conversationId, message: request.message, timeZone: request.timeZone))
    if responseDelayNanoseconds > 0 {
      try await Task.sleep(nanoseconds: responseDelayNanoseconds)
    }
    if shouldSuspendResponses {
      return try await withCheckedThrowingContinuation { continuation in
        suspendedResponseContinuations.append(continuation)
      }
    }
    return try responses.removeFirst().get()
  }

  func finishNextSuspendedResponse() {
    guard !suspendedResponseContinuations.isEmpty else { return }
    let continuation = suspendedResponseContinuations.removeFirst()
    continuation.resume(with: responses.removeFirst())
  }

  func finishNextSuspendedCreation() {
    guard !suspendedCreationContinuations.isEmpty else { return }
    let continuation = suspendedCreationContinuations.removeFirst()
    continuation.resume(returning: .init(conversationId: conversationIds.removeFirst()))
  }
}
