import Foundation

struct URLSessionCalendarConversationRepository: CalendarConversationRepository {
  private let apiClient: APIClient
  init(
    baseURL: URL = CalioAPIConfig.baseURL, session: URLSession = .shared,
    jsonDecoder: JSONDecoder = APIJSONCoding.makeDecoder(),
    jsonEncoder: JSONEncoder = APIJSONCoding.makeEncoder(),
    authTokenProvider: AuthTokenProvider? = KeychainAuthTokenStore.shared
  ) {
    apiClient = APIClient(
      baseURL: baseURL, session: session, jsonDecoder: jsonDecoder, jsonEncoder: jsonEncoder,
      authTokenProvider: authTokenProvider)
  }
  func createConversation() async throws -> CreateCalendarConversationResponseDTO {
    try await apiClient.send(
      CreateCalendarConversationResponseDTO.self, method: .post,
      pathComponents: ["api", "ai", "calendar", "conversations"], authorization: .bearer)
  }
  func sendMessage(conversationId: String, request: SendCalendarConversationMessageRequestDTO)
    async throws -> SendCalendarConversationMessageResponseDTO
  {
    try await apiClient.send(
      SendCalendarConversationMessageResponseDTO.self, method: .post,
      pathComponents: ["api", "ai", "calendar", "conversations", conversationId, "messages"],
      authorization: .bearer, body: request)
  }
}
