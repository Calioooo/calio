import Foundation
import Testing

@testable import Calio

@Suite(.serialized)
struct VoteListViewModelTests {
  @Test @MainActor func loadingCreatedRoomsShowsServerRooms() async {
    let publicId = UUID(uuidString: "9F17BFC0-D2ED-48EA-9253-7A98EBCA4C2F")!
    let service = VoteService(
      repository: VoteListRepositoryStub(
        myVoteRooms: [
          VoteRoomResponseDTO(
            publicId: publicId,
            name: "가을 여행 일정",
            candidateStartDate: "2026-09-18",
            candidateEndDate: "2026-10-18"
          )
        ]
      )
    )
    let viewModel = VoteListViewModel(voteService: service)

    await viewModel.loadCreatedRoomsIfNeeded()

    #expect(
      viewModel.createdRoomState
        == .loaded([
          VoteRoom(
            publicId: publicId,
            name: "가을 여행 일정",
            candidateStartDay: VoteDay(year: 2026, month: 9, day: 18),
            candidateEndDay: VoteDay(year: 2026, month: 10, day: 18)
          )
        ])
    )
  }

  @Test @MainActor func loadingFailureShowsFailureState() async {
    let service = VoteService(repository: VoteListRepositoryStub(error: VoteServiceError.network))
    let viewModel = VoteListViewModel(voteService: service)

    await viewModel.loadCreatedRoomsIfNeeded()

    #expect(viewModel.createdRoomState == .failed)
  }
}

private final class VoteListRepositoryStub: VoteRepository {
  private let myVoteRooms: [VoteRoomResponseDTO]
  private let error: Error?

  init(myVoteRooms: [VoteRoomResponseDTO] = [], error: Error? = nil) {
    self.myVoteRooms = myVoteRooms
    self.error = error
  }

  func createVoteRoom(_: CreateVoteRoomRequestDTO) async throws -> VoteRoomResponseDTO {
    fatalError()
  }

  func fetchMyVoteRooms() async throws -> [VoteRoomResponseDTO] {
    if let error { throw error }
    return myVoteRooms
  }

  func fetchVoteResult(publicId _: UUID) async throws -> VoteResultResponseDTO { fatalError() }

  func createVoteParticipant(
    publicId _: UUID,
    request _: CreateVoteParticipantRequestDTO
  ) async throws -> VoteParticipantResponseDTO { fatalError() }

  func lookupVoteParticipantSelection(
    publicId _: UUID,
    request _: LookupVoteParticipantSelectionRequestDTO
  ) async throws -> VoteParticipantSelectionResponseDTO { fatalError() }

  func submitVotes(
    publicId _: UUID,
    request _: SubmitVoteRequestDTO
  ) async throws -> VoteSubmissionResponseDTO { fatalError() }
}
