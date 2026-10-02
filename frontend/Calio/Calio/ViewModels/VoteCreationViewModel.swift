import Foundation

private let voteKoreaCalendar: Calendar = {
  var calendar = Calendar(identifier: .gregorian)
  calendar.timeZone = TimeZone(identifier: "Asia/Seoul")!
  return calendar
}()

@MainActor
final class VoteCreationViewModel: ObservableObject {
  @Published private(set) var name = ""
  @Published private(set) var selectedCandidateStartDay: VoteDay?
  @Published private(set) var selectedCandidateEndDay: VoteDay?
  @Published private(set) var displayedMonth: VoteMonth
  @Published private(set) var state: VoteCreationState = .editing

  let earliestCandidateStartDay: VoteDay

  private let voteService: VoteService
  private let calendar: Calendar

  init(
    voteService: VoteService = VoteService(),
    currentDate: Date = Date(),
    calendar: Calendar = voteKoreaCalendar
  ) {
    self.voteService = voteService
    self.calendar = calendar

    earliestCandidateStartDay = VoteDay(date: currentDate, calendar: calendar)
    displayedMonth = VoteMonth(day: earliestCandidateStartDay)
  }

  var canCreate: Bool {
    !trimmedName.isEmpty && selectedCandidatePeriod != nil && !state.isCreating
  }

  var selectedDayCount: Int {
    guard let selectedCandidatePeriod,
      let startDate = calendar.date(from: dateComponents(for: selectedCandidatePeriod.startDay)),
      let endDate = calendar.date(from: dateComponents(for: selectedCandidatePeriod.endDay))
    else {
      return 0
    }
    return calendar.dateComponents([.day], from: startDate, to: endDate).day.map { $0 + 1 } ?? 0
  }

  func updateName(_ name: String) {
    self.name = name
    clearFailure()
  }

  var selectedCandidatePeriod: VoteCandidatePeriod? {
    guard let selectedCandidateStartDay, let selectedCandidateEndDay else {
      return nil
    }
    return VoteCandidatePeriod(startDay: selectedCandidateStartDay, endDay: selectedCandidateEndDay)
  }

  func isSelectableCandidateDay(_ day: VoteDay) -> Bool {
    guard day >= earliestCandidateStartDay else {
      return false
    }
    guard let selectedCandidateStartDay, selectedCandidateEndDay == nil else {
      return true
    }
    return day >= selectedCandidateStartDay
      && day <= latestCandidateEndDay(for: selectedCandidateStartDay)
  }

  func selectCandidateDay(_ day: VoteDay) {
    guard isSelectableCandidateDay(day) else {
      return
    }

    if selectedCandidateStartDay == nil || selectedCandidateEndDay != nil {
      selectedCandidateStartDay = day
      selectedCandidateEndDay = nil
    } else {
      selectedCandidateEndDay = day
    }
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
    guard let selectedCandidatePeriod, canCreate else {
      state = .failed(.validation)
      return nil
    }

    state = .creating
    do {
      let room = try await voteService.createRoom(
        name: trimmedName,
        candidateStartDay: selectedCandidatePeriod.startDay,
        candidateEndDay: selectedCandidatePeriod.endDay
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

  private func latestCandidateEndDay(for startDay: VoteDay) -> VoteDay {
    guard let date = calendar.date(from: dateComponents(for: startDay)),
      let latestEndDate = calendar.date(byAdding: .day, value: 30, to: date)
    else {
      preconditionFailure("Failed to calculate VoteRoom candidate period")
    }
    return VoteDay(date: latestEndDate, calendar: calendar)
  }
}
