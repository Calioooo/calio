import Foundation

@MainActor
final class VoteCreationViewModel: ObservableObject {
  @Published private(set) var name = ""
  @Published private(set) var selectedCandidateEndDay: VoteDay
  @Published private(set) var displayedMonth: VoteMonth
  @Published private(set) var state: VoteCreationState = .editing

  let candidatePeriod: VoteCandidatePeriod

  private let voteService: VoteService
  private let calendar: Calendar

  init(
    voteService: VoteService = VoteService(),
    currentDate: Date = Date(),
    calendar: Calendar = Self.koreaCalendar
  ) {
    self.voteService = voteService
    self.calendar = calendar

    let startDay = VoteDay(date: currentDate, calendar: calendar)
    guard let lastSelectableDate = calendar.date(byAdding: .day, value: 30, to: currentDate) else {
      preconditionFailure("Failed to calculate VoteRoom candidate period")
    }
    let lastSelectableEndDay = VoteDay(date: lastSelectableDate, calendar: calendar)
    candidatePeriod = VoteCandidatePeriod(
      startDay: startDay,
      lastSelectableEndDay: lastSelectableEndDay
    )
    selectedCandidateEndDay = startDay
    displayedMonth = VoteMonth(day: startDay)
  }

  var canCreate: Bool {
    !trimmedName.isEmpty && candidatePeriod.contains(selectedCandidateEndDay) && !state.isCreating
  }

  var selectedDayCount: Int {
    guard let startDate = calendar.date(from: dateComponents(for: candidatePeriod.startDay)),
      let endDate = calendar.date(from: dateComponents(for: selectedCandidateEndDay))
    else {
      return 0
    }
    return calendar.dateComponents([.day], from: startDate, to: endDate).day.map { $0 + 1 } ?? 0
  }

  func updateName(_ name: String) {
    self.name = name
    clearFailure()
  }

  func selectCandidateEndDay(_ day: VoteDay) {
    guard candidatePeriod.contains(day) else {
      return
    }
    selectedCandidateEndDay = day
    clearFailure()
  }

  func moveMonth(by value: Int) {
    guard
      let currentDate = calendar.date(
        from: DateComponents(year: displayedMonth.year, month: displayedMonth.month)),
      let movedDate = calendar.date(byAdding: .month, value: value, to: currentDate)
    else {
      return
    }
    displayedMonth = VoteMonth(day: VoteDay(date: movedDate, calendar: calendar))
  }

  func createRoom() async -> VoteRoom? {
    guard canCreate else {
      state = .failed(.validation)
      return nil
    }

    state = .creating
    do {
      let room = try await voteService.createRoom(
        name: trimmedName,
        candidateEndDay: selectedCandidateEndDay
      )
      state = .created(room)
      return room
    } catch is CancellationError {
      state = .editing
      return nil
    } catch let error as VoteServiceError {
      state = .failed(failure(from: error))
      return nil
    } catch {
      state = .failed(.unexpected)
      return nil
    }
  }

  private var trimmedName: String {
    name.trimmingCharacters(in: .whitespacesAndNewlines)
  }

  private func clearFailure() {
    if case .failed = state {
      state = .editing
    }
  }

  private func failure(from error: VoteServiceError) -> VoteCreationFailure {
    switch error {
    case .validationFailed:
      return .validation
    case .network:
      return .network
    case .voteRoomNotFound, .participantNicknameConflict, .participantCredentialInvalid, .decoding,
      .unexpected:
      return .unexpected
    }
  }

  private func dateComponents(for day: VoteDay) -> DateComponents {
    DateComponents(year: day.year, month: day.month, day: day.day)
  }

  private static var koreaCalendar: Calendar {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "Asia/Seoul")!
    return calendar
  }
}
