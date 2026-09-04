import Foundation

@MainActor
final class CalendarAssistantConversationViewModel: ObservableObject {
  enum State: Equatable {
    case connecting, ready
    case failed(CalendarAssistantFailure)
  }
  @Published private(set) var state: State = .connecting
  @Published private(set) var messages: [CalendarAssistantMessage] = []
  @Published private(set) var isSending = false
  @Published private(set) var messageFailure: CalendarAssistantFailure?
  private let service: CalendarConversationService
  private let onCalendarRefreshNeeded: () -> Void
  private var conversationId: String?
  private var retryMessage: String?
  private var sessionGeneration = 0

  init(
    service: CalendarConversationService = CalendarConversationService(),
    onCalendarRefreshNeeded: @escaping () -> Void = {}
  ) {
    self.service = service
    self.onCalendarRefreshNeeded = onCalendarRefreshNeeded
  }
  func start() async {
    guard case .connecting = state else { return }
    await createConversation()
  }
  func retryConnection() async {
    state = .connecting
    await createConversation()
  }
  func endSession() {
    sessionGeneration &+= 1
    conversationId = nil
    messages = []
    isSending = false
    messageFailure = nil
    retryMessage = nil
    state = .connecting
  }
  func send(_ text: String) async {
    let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty, !isSending, let conversationId else { return }
    let generation = sessionGeneration
    isSending = true
    messageFailure = nil
    retryMessage = trimmed
    messages.append(.init(role: .user, text: trimmed, results: [], isPending: true))
    do {
      let response = try await service.send(message: trimmed, conversationId: conversationId)
      guard generation == sessionGeneration else { return }
      if let index = messages.lastIndex(where: { $0.role == .user && $0.isPending }) {
        let pending = messages[index]
        messages[index] = .init(role: .user, text: pending.text, results: [], isPending: false)
      }
      messages.append(
        .init(role: .assistant, text: response.0, results: response.1, isPending: false))
      retryMessage = nil
      onCalendarRefreshNeeded()
    } catch let failure as CalendarAssistantFailure {
      guard generation == sessionGeneration else { return }
      messageFailure = failure
      markLastPendingFailed()
    } catch {
      guard generation == sessionGeneration else { return }
      messageFailure = .unexpected
      markLastPendingFailed()
    }
    guard generation == sessionGeneration else { return }
    isSending = false
  }
  func retryMessageSend() async {
    guard let retryMessage else { return }
    await send(retryMessage)
  }
  private func createConversation() async {
    let generation = sessionGeneration
    do {
      let identifier = try await service.createConversation()
      guard generation == sessionGeneration else { return }
      conversationId = identifier
      state = .ready
    } catch let failure as CalendarAssistantFailure {
      guard generation == sessionGeneration else { return }
      state = .failed(failure)
    } catch {
      guard generation == sessionGeneration else { return }
      state = .failed(.unexpected)
    }
  }
  private func markLastPendingFailed() {
    if let index = messages.lastIndex(where: { $0.role == .user && $0.isPending }) {
      let pending = messages[index]
      messages[index] = .init(role: .user, text: pending.text, results: [], isPending: false)
    }
  }
}
