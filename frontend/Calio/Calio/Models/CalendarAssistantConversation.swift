import Foundation

enum CalendarAssistantResult {
  case events([Event])
  case freeTimes([CalendarAssistantFreeTime])
  case mutationPreviews([CalendarAssistantMutationPreview])
  case unsupported(String)
}
struct CalendarAssistantFreeTime: Identifiable, Equatable {
  let start: String
  let end: String
  let allDayNotices: [String]
  var id: String { "\(start)-\(end)" }
}
struct CalendarAssistantMutationPreview: Identifiable, Equatable {
  let type: String
  let scope: String
  let before: CalendarAssistantMutationEvent?
  let after: CalendarAssistantMutationEvent?
  let recurrenceBefore: [String]?
  let recurrenceAfter: [String]?
  var id: String { "\(type)-\(scope)-\(after?.title ?? before?.title ?? "event")" }
}
struct CalendarAssistantMutationEvent: Equatable {
  let title: String
  let startAt: Date
  let endAt: Date
  let allDay: Bool
  let tag: CalendarTag
}
struct CalendarAssistantMessage: Identifiable {
  enum Role: Equatable { case user, assistant }
  let id = UUID()
  let role: Role
  let text: String
  let results: [CalendarAssistantResult]
  let isPending: Bool
}
enum CalendarAssistantFailure: Error, Equatable {
  case connection, message, unexpected
  var message: String {
    switch self {
    case .connection: return "대화를 시작하지 못했어요. 다시 시도해 주세요."
    case .message: return "메시지를 보내지 못했어요. 다시 시도해 주세요."
    case .unexpected: return "요청을 처리하지 못했어요."
    }
  }
}
