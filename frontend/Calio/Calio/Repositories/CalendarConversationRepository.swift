import Foundation

protocol CalendarConversationRepository {
  func createConversation() async throws -> CreateCalendarConversationResponseDTO
  func sendMessage(conversationId: String, request: SendCalendarConversationMessageRequestDTO)
    async throws -> SendCalendarConversationMessageResponseDTO
}
