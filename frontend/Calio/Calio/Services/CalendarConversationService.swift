import Foundation

struct CalendarConversationService {
  private let repository: CalendarConversationRepository
  init(repository: CalendarConversationRepository = URLSessionCalendarConversationRepository()) {
    self.repository = repository
  }
  func createConversation() async throws -> String {
    do {
      return try await repository.createConversation().conversationId
    } catch let failure as CalendarAssistantFailure {
      throw failure
    } catch let error as APIError {
      throw mapCreateFailure(error)
    } catch {
      throw CalendarAssistantFailure.unexpected
    }
  }
  func send(message: String, conversationId: String, timeZone: TimeZone = .current) async throws
    -> (String, [CalendarAssistantResult])
  {
    do {
      let response = try await repository.sendMessage(
        conversationId: conversationId,
        request: .init(message: message, timeZone: timeZone.identifier))
      return (response.assistantMessage, try response.blocks.map(mapBlock))
    } catch let failure as CalendarAssistantFailure {
      throw failure
    } catch let error as APIError {
      throw mapMessageFailure(error)
    } catch {
      throw CalendarAssistantFailure.unexpected
    }
  }
  private func mapBlock(_ block: CalendarAssistantBlockDTO) throws -> CalendarAssistantResult {
    switch block {
    case .events(let values): return .events(try values.map(mapEvent))
    case .freeTimes(let values):
      return .freeTimes(
        values.map { .init(start: $0.start, end: $0.end, allDayNotices: $0.allDayNotices) })
    case .mutationPreviews(let values): return .mutationPreviews(try values.map(mapPreview))
    case .unsupported(let type): return .unsupported(type)
    }
  }
  private func mapCreateFailure(_ error: APIError) -> CalendarAssistantFailure {
    if case .network = error { return .connection }
    return .unexpected
  }
  private func mapMessageFailure(_ error: APIError) -> CalendarAssistantFailure {
    if case .network = error { return .message }
    return .unexpected
  }
  private func mapEvent(_ dto: EventResponseDTO) throws -> Event {
    Event(
      id: dto.id, title: dto.title, description: dto.description ?? "", startAt: dto.startAt,
      endAt: dto.endAt, isAllDay: dto.allDay, timeZone: dto.timeZone,
      tag: .init(
        id: dto.tag.id, title: dto.tag.title, colorCode: dto.tag.colorCode,
        tagType: dto.tag.tagType == .custom ? .custom : .defaultTag),
      importantEvent: dto.importantEvent, recurrenceId: dto.recurrenceId,
      isRecurrenceOccurrence: dto.isRecurrenceOccurrence, originStartAt: dto.originStartAt)
  }
  private func mapPreview(_ dto: CalendarMutationPreviewResponseDTO) throws
    -> CalendarAssistantMutationPreview
  {
    .init(
      type: dto.type, scope: dto.scope, before: mapPreviewEvent(dto.before),
      after: mapPreviewEvent(dto.after), recurrenceBefore: dto.recurrence?.before,
      recurrenceAfter: dto.recurrence?.after)
  }
  private func mapPreviewEvent(_ dto: CalendarMutationEventResponseDTO?)
    -> CalendarAssistantMutationEvent?
  {
    dto.map {
      .init(
        title: $0.title, startAt: $0.startAt, endAt: $0.endAt, allDay: $0.allDay,
        tag: .init(
          id: $0.tag.id, title: $0.tag.title, colorCode: $0.tag.colorCode,
          tagType: $0.tag.tagType == .custom ? .custom : .defaultTag))
    }
  }
}
