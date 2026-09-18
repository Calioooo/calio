import Foundation

@MainActor
final class VoteRoomViewModel: ObservableObject {
  @Published private(set) var result: VoteResult?
  @Published private(set) var loadState: VoteRoomLoadState = .loading
  @Published private(set) var participantFlow: VoteParticipantFlow = .result
  @Published private(set) var savedUnavailableDays: Set<VoteDay> = []
  @Published private(set) var draftUnavailableDays: Set<VoteDay> = []
  @Published private(set) var actionFailure: VoteRoomFailure?
  @Published private(set) var isSubmitting = false
  @Published private(set) var isLoadingSchedule = false
  @Published private(set) var needsScheduleReloadConfirmation = false
  @Published var nickname = ""
  @Published var password = ""

  private let publicId: UUID
  private let initialRoom: VoteRoom?
  private let voteService: VoteService
  private let personalScheduleService: any VotePersonalScheduleProviding
  private var pollingTask: Task<Void, Never>?

  init(
    room: VoteRoom,
    voteService: VoteService = VoteService(),
    personalScheduleService: any VotePersonalScheduleProviding = VotePersonalScheduleService()
  ) {
    publicId = room.publicId
    initialRoom = room
    self.voteService = voteService
    self.personalScheduleService = personalScheduleService
  }

  init(
    publicId: UUID,
    voteService: VoteService = VoteService(),
    personalScheduleService: any VotePersonalScheduleProviding = VotePersonalScheduleService()
  ) {
    self.publicId = publicId
    initialRoom = nil
    self.voteService = voteService
    self.personalScheduleService = personalScheduleService
  }

  var room: VoteRoom? {
    result?.room ?? initialRoom
  }

  var hasUnsavedChanges: Bool {
    savedUnavailableDays != draftUnavailableDays
  }

  var canSubmitCredentials: Bool {
    !nickname.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !isSubmitting
  }

  func load() async {
    guard loadState != .unavailable else { return }
    loadState = .loading
    await refreshResult(setsLoadingState: true)
  }

  func showExistingParticipant() {
    participantFlow = .existingParticipant
    actionFailure = nil
  }

  func showNewParticipant() {
    participantFlow = .newParticipant
    actionFailure = nil
  }

  func cancelParticipantFlow() {
    participantFlow = .result
    actionFailure = nil
    password = ""
  }

  func registerParticipant() async {
    guard canSubmitCredentials else { return }
    await performParticipantAction { [self] in
      let participant = try await voteService.createParticipant(
        publicId: publicId,
        nickname: trimmedNickname,
        password: password.nilIfEmpty
      )
      savedUnavailableDays = []
      draftUnavailableDays = []
      nickname = participant.nickname
      participantFlow = .editing
    }
  }

  func restoreParticipantSelection() async {
    guard canSubmitCredentials else { return }
    await performParticipantAction { [self] in
      let selection = try await voteService.lookupParticipantSelection(
        publicId: publicId,
        nickname: trimmedNickname,
        password: password.nilIfEmpty
      )
      let unavailableDays = Set(selection.unavailableDays)
      savedUnavailableDays = unavailableDays
      draftUnavailableDays = unavailableDays
      nickname = selection.participant.nickname
      participantFlow = .editing
    }
  }

  func toggleUnavailableDay(_ day: VoteDay) {
    guard room.map({ VoteRoomCalendar.days(in: $0).contains(day) }) == true else { return }
    if draftUnavailableDays.contains(day) {
      draftUnavailableDays.remove(day)
    } else {
      draftUnavailableDays.insert(day)
    }
  }

  func requestPersonalSchedule() async {
    guard participantFlow == .editing, !isLoadingSchedule else { return }
    if hasUnsavedChanges {
      needsScheduleReloadConfirmation = true
      return
    }
    await loadPersonalSchedule(replacingDraft: false)
  }

  func confirmPersonalScheduleReload() async {
    needsScheduleReloadConfirmation = false
    await loadPersonalSchedule(replacingDraft: true)
  }

  func cancelPersonalScheduleReload() {
    needsScheduleReloadConfirmation = false
  }

  func submitVotes() async {
    guard participantFlow == .editing, !isSubmitting else { return }
    isSubmitting = true
    actionFailure = nil
    defer { isSubmitting = false }

    do {
      let submission = try await voteService.submitVotes(
        publicId: publicId,
        nickname: trimmedNickname,
        password: password.nilIfEmpty,
        unavailableDays: draftUnavailableDays.sorted()
      )
      let unavailableDays = Set(submission.unavailableDays)
      savedUnavailableDays = unavailableDays
      draftUnavailableDays = unavailableDays
      await refreshResult(setsLoadingState: false)
      participantFlow = .result
    } catch is CancellationError {
      return
    } catch let error as VoteServiceError {
      handle(error)
    } catch {
      actionFailure = .unexpected
    }
  }

  func startPolling() {
    guard pollingTask == nil, loadState != .unavailable else { return }
    pollingTask = Task { [weak self] in
      while !Task.isCancelled {
        try? await Task.sleep(nanoseconds: 15_000_000_000)
        guard !Task.isCancelled, let self else { return }
        await self.refreshResult(setsLoadingState: false)
      }
    }
  }

  func stopPolling() {
    pollingTask?.cancel()
    pollingTask = nil
  }

  deinit {
    pollingTask?.cancel()
  }

  private var trimmedNickname: String {
    nickname.trimmingCharacters(in: .whitespacesAndNewlines)
  }

  private func performParticipantAction(_ operation: () async throws -> Void) async {
    isSubmitting = true
    actionFailure = nil
    defer { isSubmitting = false }
    do {
      try await operation()
    } catch is CancellationError {
      return
    } catch let error as VoteServiceError {
      handle(error)
    } catch {
      actionFailure = .unexpected
    }
  }

  private func loadPersonalSchedule(replacingDraft: Bool) async {
    guard let room else { return }
    isLoadingSchedule = true
    actionFailure = nil
    defer { isLoadingSchedule = false }
    do {
      let suggestedDays = try await personalScheduleService.unavailableDays(in: room)
      if replacingDraft {
        draftUnavailableDays = suggestedDays
      } else {
        draftUnavailableDays.formUnion(suggestedDays)
      }
    } catch {
      actionFailure = .network
    }
  }

  private func refreshResult(setsLoadingState: Bool) async {
    do {
      result = try await voteService.fetchResult(publicId: publicId)
      loadState = .loaded
    } catch is CancellationError {
      return
    } catch let error as VoteServiceError {
      if setsLoadingState || result == nil {
        handle(error)
      }
    } catch {
      if setsLoadingState || result == nil {
        actionFailure = .unexpected
        loadState = .failed(.unexpected)
      }
    }
  }

  private func handle(_ error: VoteServiceError) {
    if error == .voteRoomNotFound {
      stopPolling()
      result = nil
      draftUnavailableDays = []
      savedUnavailableDays = []
      loadState = .unavailable
      return
    }

    let failure: VoteRoomFailure
    switch error {
    case .participantCredentialInvalid:
      failure = .credentialInvalid
    case .participantNicknameConflict:
      failure = .nicknameConflict
    case .validationFailed:
      failure = .validation
    case .network:
      failure = .network
    case .voteRoomNotFound, .decoding, .unexpected:
      failure = .unexpected
    }
    actionFailure = failure
    if error == .participantCredentialInvalid {
      participantFlow = .existingParticipant
    }
    if result == nil {
      loadState = .failed(failure)
    }
  }
}

extension String {
  fileprivate var nilIfEmpty: String? {
    isEmpty ? nil : self
  }
}
