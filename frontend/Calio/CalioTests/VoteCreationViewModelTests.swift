import Foundation
import Testing

@testable import Calio

@Suite(.serialized)
struct VoteCreationViewModelTests {
  @Test @MainActor func candidatePeriodUsesKoreaTodayAndIncludesThirtyOneDays() {
    let viewModel = VoteCreationViewModel(
      currentDate: fixedDate("2026-09-17T15:30:00Z")
    )

    #expect(viewModel.candidatePeriod.startDay == VoteDay(year: 2026, month: 9, day: 18))
    #expect(
      viewModel.candidatePeriod.lastSelectableEndDay == VoteDay(year: 2026, month: 10, day: 18))
    #expect(viewModel.selectedDayCount == 1)

    viewModel.selectCandidateEndDay(VoteDay(year: 2026, month: 10, day: 18))

    #expect(viewModel.selectedDayCount == 31)
  }

  @Test @MainActor func monthNavigationChangesDisplayedMonthWithoutChangingSelection() {
    let viewModel = VoteCreationViewModel(
      currentDate: fixedDate("2026-09-17T15:30:00Z")
    )

    viewModel.moveMonth(by: 1)

    #expect(viewModel.displayedMonth == VoteMonth(day: VoteDay(year: 2026, month: 10, day: 1)))
    #expect(viewModel.selectedCandidateEndDay == VoteDay(year: 2026, month: 9, day: 18))
  }

  @Test @MainActor func validationFailureKeepsDraftForCorrection() async {
    let service = VoteService(
      repository: VoteCreationRepositoryStub(createError: VoteServiceError.validationFailed))
    let viewModel = VoteCreationViewModel(
      voteService: service,
      currentDate: fixedDate("2026-09-17T15:30:00Z")
    )
    viewModel.updateName("가을 여행 일정")
    viewModel.selectCandidateEndDay(VoteDay(year: 2026, month: 10, day: 10))

    let room = await viewModel.createRoom()

    #expect(room == nil)
    #expect(viewModel.state == VoteCreationState.failed(.validation))
    #expect(viewModel.name == "가을 여행 일정")
    #expect(viewModel.selectedCandidateEndDay == VoteDay(year: 2026, month: 10, day: 10))
  }
}

private final class VoteCreationRepositoryStub: VoteRepository {
  private let createError: Error?

  init(createError: Error? = nil) {
    self.createError = createError
  }

  func createVoteRoom(_ request: CreateVoteRoomRequestDTO) async throws -> VoteRoomResponseDTO {
    if let createError {
      throw createError
    }
    return VoteRoomResponseDTO(
      publicId: UUID(),
      name: request.name,
      candidateStartDate: "2026-09-18",
      candidateEndDate: request.candidateEndDate
    )
  }

  func fetchMyVoteRooms() async throws -> [VoteRoomResponseDTO] { fatalError() }

  func fetchVoteResult(publicId: UUID) async throws -> VoteResultResponseDTO { fatalError() }

  func createVoteParticipant(
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
