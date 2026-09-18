import Foundation
import Testing

@testable import Calio

@Suite(.serialized)
struct VoteRoomViewModelTests {
  @Test @MainActor func restoresExistingSelectionAsSavedAndDraftState() async {
    let repository = VoteRoomRepositoryStub(
      lookupResponse: VoteParticipantSelectionResponseDTO(
        nickname: "민지",
        status: .submitted,
        unavailableDates: ["2026-10-16", "2026-10-17"]
      )
    )
    let viewModel = VoteRoomViewModel(room: room, voteService: VoteService(repository: repository))
    viewModel.nickname = "민지"
    viewModel.password = "1234"

    await viewModel.restoreParticipantSelection()

    #expect(viewModel.participantFlow == .editing)
    #expect(
      viewModel.savedUnavailableDays == [
        VoteDay(year: 2026, month: 10, day: 16), VoteDay(year: 2026, month: 10, day: 17),
      ])
    #expect(viewModel.draftUnavailableDays == viewModel.savedUnavailableDays)
    #expect(!viewModel.hasUnsavedChanges)
  }

  @Test @MainActor func saveShowsResultAfterRefreshingPublicResult() async {
    let repository = VoteRoomRepositoryStub(
      resultResponse: resultResponse(unavailableCount: 1),
      lookupResponse: VoteParticipantSelectionResponseDTO(
        nickname: "민지", status: .registered, unavailableDates: []
      ),
      submitResponse: VoteSubmissionResponseDTO(
        nickname: "민지", status: .submitted, unavailableDates: ["2026-10-16"]
      )
    )
    let viewModel = VoteRoomViewModel(room: room, voteService: VoteService(repository: repository))
    viewModel.nickname = "민지"
    await viewModel.restoreParticipantSelection()
    viewModel.toggleUnavailableDay(VoteDay(year: 2026, month: 10, day: 16))

    await viewModel.submitVotes()

    #expect(viewModel.participantFlow == .result)
    #expect(!viewModel.hasUnsavedChanges)
    #expect(viewModel.result?.dateResults.first?.unavailableCount == 1)
    #expect(repository.submitRequests.count == 1)
    #expect(repository.fetchResultCount == 1)
  }

  @Test @MainActor func notFoundMakesRoomUnavailableAndClearsDraft() async {
    let repository = VoteRoomRepositoryStub(resultError: VoteServiceError.voteRoomNotFound)
    let viewModel = VoteRoomViewModel(room: room, voteService: VoteService(repository: repository))

    await viewModel.load()

    #expect(viewModel.loadState == .unavailable)
    #expect(viewModel.result == nil)
    #expect(viewModel.draftUnavailableDays.isEmpty)
  }

  @Test @MainActor func credentialFailureDuringSaveReturnsToExistingParticipantFlow() async {
    let repository = VoteRoomRepositoryStub(
      lookupResponse: VoteParticipantSelectionResponseDTO(
        nickname: "민지", status: .registered, unavailableDates: []
      ),
      submitError: VoteServiceError.participantCredentialInvalid
    )
    let viewModel = VoteRoomViewModel(room: room, voteService: VoteService(repository: repository))
    viewModel.nickname = "민지"
    viewModel.password = "1234"
    await viewModel.restoreParticipantSelection()
    viewModel.toggleUnavailableDay(VoteDay(year: 2026, month: 10, day: 16))

    await viewModel.submitVotes()

    #expect(viewModel.participantFlow == .existingParticipant)
    #expect(viewModel.actionFailure == .credentialInvalid)
    #expect(viewModel.draftUnavailableDays == [VoteDay(year: 2026, month: 10, day: 16)])
  }

  @Test @MainActor func clearingEveryDraftDayStillRequiresScheduleReloadConfirmation() async {
    let repository = VoteRoomRepositoryStub(
      lookupResponse: VoteParticipantSelectionResponseDTO(
        nickname: "민지", status: .submitted, unavailableDates: ["2026-10-16"]
      )
    )
    let viewModel = VoteRoomViewModel(
      room: room,
      voteService: VoteService(repository: repository),
      personalScheduleService: VotePersonalScheduleStub()
    )
    viewModel.nickname = "민지"
    await viewModel.restoreParticipantSelection()
    viewModel.toggleUnavailableDay(VoteDay(year: 2026, month: 10, day: 16))

    await viewModel.requestPersonalSchedule()

    #expect(viewModel.draftUnavailableDays.isEmpty)
    #expect(viewModel.needsScheduleReloadConfirmation)
  }

  private var room: VoteRoom {
    VoteRoom(
      publicId: UUID(uuidString: "A170EA5C-357C-4261-BD9F-9FCD31751398")!,
      name: "가을 여행 일정",
      candidateStartDay: VoteDay(year: 2026, month: 10, day: 1),
      candidateEndDay: VoteDay(year: 2026, month: 10, day: 31)
    )
  }

  private func resultResponse(unavailableCount: Int) -> VoteResultResponseDTO {
    VoteResultResponseDTO(
      publicId: room.publicId,
      name: room.name,
      candidateStartDate: room.candidateStartDay.apiDateString,
      candidateEndDate: room.candidateEndDay.apiDateString,
      dates: [
        VoteDateResultResponseDTO(
          date: "2026-10-16",
          unavailableCount: unavailableCount,
          unavailableNicknames: unavailableCount == 0 ? [] : ["민지"]
        )
      ],
      submittedNicknames: unavailableCount == 0 ? [] : ["민지"]
    )
  }
}

private final class VoteRoomRepositoryStub: VoteRepository {
  private let resultResponse: VoteResultResponseDTO
  private let resultError: Error?
  private let lookupResponse: VoteParticipantSelectionResponseDTO
  private let submitResponse: VoteSubmissionResponseDTO
  private let submitError: Error?
  private(set) var submitRequests: [SubmitVoteRequestDTO] = []
  private(set) var fetchResultCount = 0

  init(
    resultResponse: VoteResultResponseDTO? = nil,
    resultError: Error? = nil,
    lookupResponse: VoteParticipantSelectionResponseDTO = VoteParticipantSelectionResponseDTO(
      nickname: "민지", status: .registered, unavailableDates: []
    ),
    submitResponse: VoteSubmissionResponseDTO = VoteSubmissionResponseDTO(
      nickname: "민지", status: .submitted, unavailableDates: []
    ),
    submitError: Error? = nil
  ) {
    self.resultResponse =
      resultResponse
      ?? VoteResultResponseDTO(
        publicId: UUID(), name: "투표", candidateStartDate: "2026-10-01",
        candidateEndDate: "2026-10-01",
        dates: [], submittedNicknames: []
      )
    self.resultError = resultError
    self.lookupResponse = lookupResponse
    self.submitResponse = submitResponse
    self.submitError = submitError
  }

  func createVoteRoom(_: CreateVoteRoomRequestDTO) async throws -> VoteRoomResponseDTO {
    fatalError()
  }

  func fetchMyVoteRooms() async throws -> [VoteRoomResponseDTO] { fatalError() }

  func fetchVoteResult(publicId _: UUID) async throws -> VoteResultResponseDTO {
    fetchResultCount += 1
    if let resultError { throw resultError }
    return resultResponse
  }

  func createVoteParticipant(
    publicId _: UUID,
    request _: CreateVoteParticipantRequestDTO
  ) async throws -> VoteParticipantResponseDTO { fatalError() }

  func lookupVoteParticipantSelection(
    publicId _: UUID,
    request _: LookupVoteParticipantSelectionRequestDTO
  ) async throws -> VoteParticipantSelectionResponseDTO {
    lookupResponse
  }

  func submitVotes(publicId _: UUID, request: SubmitVoteRequestDTO) async throws
    -> VoteSubmissionResponseDTO
  {
    submitRequests.append(request)
    if let submitError { throw submitError }
    return submitResponse
  }
}

private struct VotePersonalScheduleStub: VotePersonalScheduleProviding {
  func unavailableDays(in _: VoteRoom) async throws -> Set<VoteDay> {
    []
  }
}
