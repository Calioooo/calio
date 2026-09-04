import Foundation

struct CreateCalendarConversationResponseDTO: Decodable { let conversationId: String }
struct SendCalendarConversationMessageRequestDTO: Encodable {
  let message: String
  let timeZone: String
}
struct SendCalendarConversationMessageResponseDTO: Decodable {
  let conversationId: String
  let assistantMessage: String
  let blocks: [CalendarAssistantBlockDTO]
}

enum CalendarAssistantBlockTypeDTO: String, Decodable {
  case events = "EVENTS"
  case freeTimes = "FREE_TIMES"
  case mutationPreview = "MUTATION_PREVIEW"
}

enum CalendarAssistantBlockDTO: Decodable {
  case events([EventResponseDTO])
  case freeTimes([FreeTimeResponseDTO])
  case mutationPreviews([CalendarMutationPreviewResponseDTO])
  case unsupported(String)

  private enum Keys: String, CodingKey { case type, items }
  init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: Keys.self)
    let rawType = try container.decode(String.self, forKey: .type)
    switch CalendarAssistantBlockTypeDTO(rawValue: rawType) {
    case .events: self = .events(try container.decode([EventResponseDTO].self, forKey: .items))
    case .freeTimes:
      self = .freeTimes(try container.decode([FreeTimeResponseDTO].self, forKey: .items))
    case .mutationPreview:
      self = .mutationPreviews(
        try container.decode([CalendarMutationPreviewResponseDTO].self, forKey: .items))
    case nil: self = .unsupported(rawType)
    }
  }
}

struct FreeTimeResponseDTO: Decodable {
  let start: String
  let end: String
  let allDayNotices: [String]
}
struct CalendarMutationPreviewResponseDTO: Decodable {
  let type: String
  let scope: String
  let before: CalendarMutationEventResponseDTO?
  let after: CalendarMutationEventResponseDTO?
  let recurrence: CalendarMutationRecurrencePreviewResponseDTO?
}
struct CalendarMutationEventResponseDTO: Decodable {
  let title: String
  let startAt: Date
  let endAt: Date
  let allDay: Bool
  let tag: TagResponseDTO
}
struct CalendarMutationRecurrencePreviewResponseDTO: Decodable {
  let before: [String]
  let after: [String]
}
