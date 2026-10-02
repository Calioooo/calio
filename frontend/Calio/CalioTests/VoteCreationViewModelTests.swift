import Foundation
import Testing

@testable import Calio

@Suite(.serialized)
struct VoteCreationViewModelTests {
  @Test @MainActor func selectsFutureCandidateDateRange() {
    let viewModel = VoteCreationViewModel(
      currentDate: fixedDate("2026-09-17T15:30:00Z")
    )

    #expect(viewModel.earliestCandidateStartDay == VoteDay(year: 2026, month: 9, day: 18))
    #expect(viewModel.selectedCandidatePeriod == nil)
    #expect(!viewModel.canCreate)

    viewModel.selectCandidateDay(VoteDay(year: 2026, month: 10, day: 3))
    viewModel.selectCandidateDay(VoteDay(year: 2026, month: 10, day: 12))

    #expect(
      viewModel.selectedCandidatePeriod
        == VoteCandidatePeriod(
          startDay: VoteDay(year: 2026, month: 10, day: 3),
          endDay: VoteDay(year: 2026, month: 10, day: 12)
        ))
    #expect(viewModel.selectedDayCount == 10)
  }

  @Test @MainActor func allowsOneDayCandidatePeriodAndStartsNewRangeAfterCompletion() {
    let viewModel = VoteCreationViewModel(
      currentDate: fixedDate("2026-09-17T15:30:00Z")
    )

    let selectedDay = VoteDay(year: 2026, month: 9, day: 24)
    viewModel.selectCandidateDay(selectedDay)
    viewModel.selectCandidateDay(selectedDay)

    #expect(
      viewModel.selectedCandidatePeriod
        == VoteCandidatePeriod(startDay: selectedDay, endDay: selectedDay))
    #expect(viewModel.selectedDayCount == 1)

    let nextStartDay = VoteDay(year: 2026, month: 10, day: 1)
    viewModel.selectCandidateDay(nextStartDay)

    #expect(viewModel.selectedCandidateStartDay == nextStartDay)
    #expect(viewModel.selectedCandidateEndDay == nil)
  }

  @Test @MainActor func restrictsCandidateEndDayToThirtyDaysAfterSelectedStartDay() {
    let viewModel = VoteCreationViewModel(
      currentDate: fixedDate("2026-09-17T15:30:00Z")
    )
    let startDay = VoteDay(year: 2026, month: 10, day: 3)
    let maximumEndDay = VoteDay(year: 2026, month: 11, day: 2)

    #expect(!viewModel.isSelectableCandidateDay(VoteDay(year: 2026, month: 9, day: 17)))

    viewModel.selectCandidateDay(startDay)

    #expect(!viewModel.isSelectableCandidateDay(VoteDay(year: 2026, month: 10, day: 2)))
    #expect(viewModel.isSelectableCandidateDay(maximumEndDay))
    #expect(!viewModel.isSelectableCandidateDay(VoteDay(year: 2026, month: 11, day: 3)))

    viewModel.selectCandidateDay(VoteDay(year: 2026, month: 11, day: 3))

    #expect(viewModel.selectedCandidatePeriod == nil)

    viewModel.selectCandidateDay(maximumEndDay)

    #expect(
      viewModel.selectedCandidatePeriod
        == VoteCandidatePeriod(startDay: startDay, endDay: maximumEndDay))
    #expect(viewModel.selectedDayCount == 31)
  }

  @Test @MainActor func monthNavigationChangesDisplayedMonthWithoutChangingSelectedRange() {
    let viewModel = VoteCreationViewModel(
      currentDate: fixedDate("2026-09-17T15:30:00Z")
    )
    viewModel.selectCandidateDay(VoteDay(year: 2026, month: 9, day: 20))
    viewModel.selectCandidateDay(VoteDay(year: 2026, month: 10, day: 3))

    viewModel.moveMonth(by: 1)

    #expect(viewModel.displayedMonth == VoteMonth(day: VoteDay(year: 2026, month: 10, day: 1)))
    #expect(
      viewModel.selectedCandidatePeriod
        == VoteCandidatePeriod(
          startDay: VoteDay(year: 2026, month: 9, day: 20),
          endDay: VoteDay(year: 2026, month: 10, day: 3)
        ))
  }

  @Test @MainActor func validationFailureKeepsDraftForCorrection() async {
    let service = VoteService(
      repository: VoteCreationRepositoryStub(createError: VoteServiceError.validationFailed))
    let viewModel = VoteCreationViewModel(
      voteService: service,
      currentDate: fixedDate("2026-09-17T15:30:00Z")
    )
    viewModel.updateName("가을 여행 일정")
    viewModel.selectCandidateDay(VoteDay(year: 2026, month: 10, day: 3))
    viewModel.selectCandidateDay(VoteDay(year: 2026, month: 10, day: 10))

    let room = await viewModel.createRoom()

    #expect(room == nil)
    #expect(viewModel.state == VoteCreationState.failed(.validation))
    #expect(viewModel.name == "가을 여행 일정")
    #expect(
      viewModel.selectedCandidatePeriod
        == VoteCandidatePeriod(
          startDay: VoteDay(year: 2026, month: 10, day: 3),
          endDay: VoteDay(year: 2026, month: 10, day: 10)
        ))
  }

  @Test @MainActor func sendsSelectedCandidateDateRangeWhenCreatingRoom() async {
    let repository = VoteCreationRepositoryStub()
    let viewModel = VoteCreationViewModel(
      voteService: VoteService(repository: repository),
      currentDate: fixedDate("2026-09-17T15:30:00Z")
    )
    viewModel.updateName("가을 여행 일정")
    viewModel.selectCandidateDay(VoteDay(year: 2026, month: 10, day: 3))
    viewModel.selectCandidateDay(VoteDay(year: 2026, month: 10, day: 10))

    _ = await viewModel.createRoom()

    #expect(
      repository.createRequests == [
        CreateVoteRoomRequestDTO(
          name: "가을 여행 일정",
          candidateStartDate: "2026-10-03",
          candidateEndDate: "2026-10-10"
        )
      ])
  }
}

private final class VoteCreationRepositoryStub: VoteRepository {
  private let createError: Error?
  private(set) var createRequests: [CreateVoteRoomRequestDTO] = []

  init(createError: Error? = nil) {
    self.createError = createError
  }

  func createVoteRoom(_ request: CreateVoteRoomRequestDTO) async throws -> VoteRoomResponseDTO {
    createRequests.append(request)
    if let createError {
      throw createError
    }
    return VoteRoomResponseDTO(
      publicId: UUID(),
      name: request.name,
      candidateStartDate: request.candidateStartDate,
      candidateEndDate: request.candidateEndDate
    )
  }

  func fetchMyParticipatedVoteRooms() async throws -> [ParticipatedVoteRoomResponseDTO] {
    fatalError()
  }

  func fetchMyCreatedVoteRooms() async throws -> [VoteRoomResponseDTO] {
    fatalError()
  }

  func fetchVoteResult(publicId: UUID) async throws -> VoteResultResponseDTO { fatalError() }

  func createVoteParticipant(
    publicId: UUID,
    request: CreateVoteParticipantRequestDTO
  ) async throws -> VoteParticipantResponseDTO { fatalError() }

  func createAuthenticatedVoteParticipant(
    publicId: UUID,
    request: CreateVoteParticipantRequestDTO
  ) async throws -> VoteParticipantResponseDTO { fatalError() }

  func lookupVoteParticipantSelection(
    publicId: UUID,
    request: LookupVoteParticipantSelectionRequestDTO
  ) async throws -> VoteParticipantSelectionResponseDTO { fatalError() }

  func submitVotes(
    publicId: UUID,
    request: SubmitVoteRequestDTO
  ) async throws -> VoteSubmissionResponseDTO { fatalError() }
}

private func fixedDate(_ value: String) -> Date {
  ISO8601DateFormatter().date(from: value)!
}
