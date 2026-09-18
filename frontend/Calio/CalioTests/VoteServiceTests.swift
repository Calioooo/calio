import Foundation
import Testing

@testable import Calio

struct VoteServiceTests {
  private let publicId = UUID(uuidString: "9F17BFC0-D2ED-48EA-9253-7A98EBCA4C2F")!

  @Test func createRoomMapsDateOnlyResponseToVoteRoom() async throws {
    let repository = RecordingVoteRepository(
      createRoomResponse: VoteRoomResponseDTO(
        publicId: publicId,
        name: "가을 여행 일정",
        candidateStartDate: "2026-09-18",
        candidateEndDate: "2026-10-18"
      )
    )
    let service = VoteService(repository: repository)

    let room = try await service.createRoom(
      name: "가을 여행 일정",
      candidateEndDay: VoteDay(year: 2026, month: 10, day: 18)
    )

    #expect(
      repository.createRoomRequest
        == CreateVoteRoomRequestDTO(
          name: "가을 여행 일정",
          candidateEndDate: "2026-10-18"
        ))
    #expect(
      room
        == VoteRoom(
          publicId: publicId,
          name: "가을 여행 일정",
          candidateStartDay: VoteDay(year: 2026, month: 9, day: 18),
          candidateEndDay: VoteDay(year: 2026, month: 10, day: 18)
        ))
  }

  @Test func fetchMyCreatedRoomsMapsVoteRoomResponses() async throws {
    let repository = RecordingVoteRepository(
      myVoteRoomResponses: [
        VoteRoomResponseDTO(
          publicId: publicId,
          name: "가을 여행 일정",
          candidateStartDate: "2026-09-18",
          candidateEndDate: "2026-10-18"
        )
      ]
    )
    let service = VoteService(repository: repository)

    let rooms = try await service.fetchMyCreatedRooms()

    #expect(
      rooms == [
        VoteRoom(
          publicId: publicId,
          name: "가을 여행 일정",
          candidateStartDay: VoteDay(year: 2026, month: 9, day: 18),
          candidateEndDay: VoteDay(year: 2026, month: 10, day: 18)
        )
      ])
  }

  @Test func fetchResultMapsCanonicalCountsAndNicknamesWithoutRecalculation() async throws {
    let repository = RecordingVoteRepository(
      resultResponse: VoteResultResponseDTO(
        publicId: publicId,
        name: "가을 여행 일정",
        candidateStartDate: "2026-09-18",
        candidateEndDate: "2026-09-20",
        dates: [
          VoteDateResultResponseDTO(
            date: "2026-09-18",
            unavailableCount: 0,
            unavailableNicknames: []
          ),
          VoteDateResultResponseDTO(
            date: "2026-09-19",
            unavailableCount: 2,
            unavailableNicknames: ["민지", "준호"]
          ),
        ],
        submittedNicknames: ["민지", "준호"]
      )
    )
    let service = VoteService(repository: repository)

    let result = try await service.fetchResult(publicId: publicId)

    #expect(
      result.dateResults == [
        VoteDateResult(
          day: VoteDay(year: 2026, month: 9, day: 18),
          unavailableCount: 0,
          unavailableNicknames: []
        ),
        VoteDateResult(
          day: VoteDay(year: 2026, month: 9, day: 19),
          unavailableCount: 2,
          unavailableNicknames: ["민지", "준호"]
        ),
      ])
    #expect(result.submittedNicknames == ["민지", "준호"])
  }

  @Test func lookupAndSubmissionMapDateOnlySelections() async throws {
    let repository = RecordingVoteRepository(
      selectionResponse: VoteParticipantSelectionResponseDTO(
        nickname: "민지",
        status: .submitted,
        unavailableDates: ["2026-09-19"]
      ),
      submissionResponse: VoteSubmissionResponseDTO(
        nickname: "민지",
        status: .submitted,
        unavailableDates: ["2026-09-19", "2026-09-20"]
      )
    )
    let service = VoteService(repository: repository)

    let selection = try await service.lookupParticipantSelection(
      publicId: publicId,
      nickname: "민지",
      password: "secret"
    )
    let submission = try await service.submitVotes(
      publicId: publicId,
      nickname: "민지",
      password: "secret",
      unavailableDays: [
        VoteDay(year: 2026, month: 9, day: 19),
        VoteDay(year: 2026, month: 9, day: 20),
      ]
    )

    #expect(selection.participant.status == .submitted)
    #expect(selection.unavailableDays == [VoteDay(year: 2026, month: 9, day: 19)])
    #expect(
      repository.lookupRequest
        == LookupVoteParticipantSelectionRequestDTO(
          nickname: "민지",
          password: "secret"
        ))
    #expect(
      submission.unavailableDays == [
        VoteDay(year: 2026, month: 9, day: 19),
        VoteDay(year: 2026, month: 9, day: 20),
      ])
    #expect(
      repository.submitRequest
        == SubmitVoteRequestDTO(
          nickname: "민지",
          password: "secret",
          unavailableDates: ["2026-09-19", "2026-09-20"]
        ))
  }

  @Test func knownBackendErrorCodeBecomesVoteServiceError() async {
    let repository = RecordingVoteRepository(
      resultError: APIError.backend(
        statusCode: 404,
        problem: ProblemDetailDTO(
          type: "about:blank",
          title: "VOTE_ROOM_NOT_FOUND",
          status: 404,
          detail: "Vote room not found.",
          errorCode: "VOTE_ROOM_NOT_FOUND"
        )
      )
    )
    let service = VoteService(repository: repository)

    do {
      try await service.fetchResult(publicId: publicId)
      Issue.record("Expected VoteServiceError.voteRoomNotFound")
    } catch let error as VoteServiceError {
      #expect(error == .voteRoomNotFound)
    } catch {
      Issue.record("Expected VoteServiceError.voteRoomNotFound, got \(error)")
    }
  }

  @Test func malformedBackendDateBecomesDecodingFailure() async {
    let repository = RecordingVoteRepository(
      resultResponse: VoteResultResponseDTO(
        publicId: publicId,
        name: "가을 여행 일정",
        candidateStartDate: "2026-09-18",
        candidateEndDate: "2026-09-20",
        dates: [
          VoteDateResultResponseDTO(
            date: "2026-02-30",
            unavailableCount: 1,
            unavailableNicknames: ["민지"]
          )
        ],
        submittedNicknames: ["민지"]
      )
    )
    let service = VoteService(repository: repository)

    do {
      try await service.fetchResult(publicId: publicId)
      Issue.record("Expected VoteServiceError.decoding")
    } catch let error as VoteServiceError {
      #expect(error == .decoding)
    } catch {
      Issue.record("Expected VoteServiceError.decoding, got \(error)")
    }
  }
}

private final class RecordingVoteRepository: VoteRepository {
  var createRoomRequest: CreateVoteRoomRequestDTO?
  var lookupRequest: LookupVoteParticipantSelectionRequestDTO?
  var submitRequest: SubmitVoteRequestDTO?

  private let createRoomResponse: VoteRoomResponseDTO
  private let myVoteRoomResponses: [VoteRoomResponseDTO]
  private let resultResponse: VoteResultResponseDTO
  private let selectionResponse: VoteParticipantSelectionResponseDTO
  private let submissionResponse: VoteSubmissionResponseDTO
  private let resultError: Error?

  init(
    createRoomResponse: VoteRoomResponseDTO = VoteRoomResponseDTO(
      publicId: UUID(),
      name: "투표방",
      candidateStartDate: "2026-09-18",
      candidateEndDate: "2026-09-18"
    ),
    myVoteRoomResponses: [VoteRoomResponseDTO] = [],
    resultResponse: VoteResultResponseDTO = VoteResultResponseDTO(
      publicId: UUID(),
      name: "투표방",
      candidateStartDate: "2026-09-18",
      candidateEndDate: "2026-09-18",
      dates: [],
      submittedNicknames: []
    ),
    selectionResponse: VoteParticipantSelectionResponseDTO = VoteParticipantSelectionResponseDTO(
      nickname: "민지",
      status: .registered,
      unavailableDates: []
    ),
    submissionResponse: VoteSubmissionResponseDTO = VoteSubmissionResponseDTO(
      nickname: "민지",
      status: .submitted,
      unavailableDates: []
    ),
    resultError: Error? = nil
  ) {
    self.createRoomResponse = createRoomResponse
    self.myVoteRoomResponses = myVoteRoomResponses
    self.resultResponse = resultResponse
    self.selectionResponse = selectionResponse
    self.submissionResponse = submissionResponse
    self.resultError = resultError
  }

  func createVoteRoom(_ request: CreateVoteRoomRequestDTO) async throws -> VoteRoomResponseDTO {
    createRoomRequest = request
    return createRoomResponse
  }

  func fetchMyVoteRooms() async throws -> [VoteRoomResponseDTO] {
    myVoteRoomResponses
  }

  func fetchVoteResult(publicId: UUID) async throws -> VoteResultResponseDTO {
    if let resultError {
      throw resultError
    }
    return resultResponse
  }

  func createVoteParticipant(
    publicId: UUID,
    request: CreateVoteParticipantRequestDTO
  ) async throws -> VoteParticipantResponseDTO {
    VoteParticipantResponseDTO(nickname: request.nickname, status: .registered)
  }

  func lookupVoteParticipantSelection(
    publicId: UUID,
    request: LookupVoteParticipantSelectionRequestDTO
  ) async throws -> VoteParticipantSelectionResponseDTO {
    lookupRequest = request
    return selectionResponse
  }

  func submitVotes(
    publicId: UUID,
    request: SubmitVoteRequestDTO
  ) async throws -> VoteSubmissionResponseDTO {
    submitRequest = request
    return submissionResponse
  }
}
