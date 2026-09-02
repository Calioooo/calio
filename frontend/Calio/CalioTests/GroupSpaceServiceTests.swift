import Foundation
import Testing
@testable import Calio

@Suite(.serialized)
struct GroupSpaceServiceTests {
    @Test func servicePreservesNetworkDecodingAndBackendFailures() async {
        await expectFetchFailure(
            .network(URLError(.notConnectedToInternet)),
            equals: .network
        )
        await expectFetchFailure(
            .decoding(DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "invalid response"))),
            equals: .decoding
        )
        await expectFetchFailure(
            .backend(
                statusCode: 409,
                problem: .init(type: nil, title: "CONFLICT", status: 409, detail: nil, errorCode: "GROUP_SPACE_CONFLICT")
            ),
            equals: .backend(errorCode: "GROUP_SPACE_CONFLICT")
        )
    }

    @Test func servicePreservesTaskCancellation() async {
        let service = GroupSpaceService(repository: CancelledGroupSpaceRepository())

        do {
            _ = try await service.fetchGroupSpaces()
            Issue.record("Expected task cancellation")
        } catch is CancellationError {
        } catch {
            Issue.record("Unexpected failure: \(error)")
        }
    }

    @Test func serviceMapsGroupSpaceDTOsToAppModels() async throws {
        let repository = GroupSpaceRepositoryStub(fetchResponses: [[GroupSpaceRepositoryStub.sampleSpace()]])
        let service = GroupSpaceService(repository: repository)

        let spaces = try await service.fetchGroupSpaces()
        let members = try await service.members(groupSpaceId: 7)
        let transfer = try await service.transferOwnership(groupSpaceId: 7, targetMemberId: 11)

        #expect(spaces == [GroupSpaceRepositoryStub.sampleGroupSpace()])
        #expect(members == [
            GroupMember(memberId: 10, nickname: "준하", role: .owner),
            GroupMember(memberId: 11, nickname: "민지", role: .member)
        ])
        #expect(transfer.previousOwner.role == .member)
        #expect(transfer.owner.role == .owner)
    }

    @Test func serviceForwardsLifecycleAndMembershipOperationsToRepository() async throws {
        let repository = GroupSpaceRepositoryStub()
        let service = GroupSpaceService(repository: repository)

        let created = try await service.create(name: "프로젝트 팀", emoji: nil, nickname: "준하")
        let updated = try await service.update(groupSpaceId: 7, name: "새 프로젝트 팀", emoji: nil)
        let members = try await service.members(groupSpaceId: 7)
        _ = try await service.transferOwnership(groupSpaceId: 7, targetMemberId: 11)
        try await service.removeMember(groupSpaceId: 7, memberId: 11)
        try await service.leave(groupSpaceId: 7)
        try await service.delete(groupSpaceId: 7)

        #expect(created.name == "프로젝트 팀")
        #expect(created.myMembership.role == .owner)
        #expect(updated.name == "새 프로젝트 팀")
        #expect(members.map(\.memberId) == [10, 11])
        #expect(repository.operations == ["create", "update:7", "members:7", "transfer:7:11", "remove:7:11", "leave:7", "delete:7"])
    }

    @MainActor @Test func listViewModelPreservesConfirmedSpacesWhenRefreshFails() async {
        let confirmedSpace = GroupSpaceRepositoryStub.sampleSpace(name: "확정된 공간")
        let repository = GroupSpaceRepositoryStub(fetchResponses: [[confirmedSpace], []], fetchErrorOnCall: 2)
        let viewModel = GroupSpaceListViewModel(service: GroupSpaceService(repository: repository))

        await viewModel.load()
        await viewModel.load()

        #expect(viewModel.spaces.map(\.name) == ["확정된 공간"])
        #expect(viewModel.errorMessage == "그룹 공간을 불러오지 못했습니다. 다시 시도해 주세요.")
    }

    @MainActor @Test func listViewModelRefreshesToIncludeGroupJoinedByInvitation() async {
        let existingSpace = GroupSpaceRepositoryStub.sampleSpace(name: "기존 그룹")
        let joinedSpace = GroupSpaceRepositoryStub.sampleSpace(name: "새로 참여한 그룹")
        let repository = GroupSpaceRepositoryStub(
            fetchResponses: [[existingSpace], [existingSpace, joinedSpace]]
        )
        let viewModel = GroupSpaceListViewModel(service: GroupSpaceService(repository: repository))

        await viewModel.load()
        await viewModel.refreshAfterJoiningInvitation()

        #expect(viewModel.spaces.map(\.name) == ["기존 그룹", "새로 참여한 그룹"])
        #expect(viewModel.didFailLoading == false)
    }

    @MainActor @Test func listViewModelIgnoresAnOlderLoadThatFinishesAfterTheLatestLoad() async {
        let repository = OutOfOrderGroupSpaceRepository()
        let viewModel = GroupSpaceListViewModel(service: GroupSpaceService(repository: repository))

        let firstLoad = Task { await viewModel.load() }
        while await repository.startedFetchCount() == 0 { await Task.yield() }
        let secondLoad = Task { await viewModel.load() }

        while await repository.startedFetchCount() < 2 { await Task.yield() }
        await secondLoad.value
        await repository.finishFirstFetch()
        await firstLoad.value

        #expect(viewModel.spaces.map(\.name) == ["최신 공간"])
        #expect(!viewModel.isLoading)
        #expect(!viewModel.didFailLoading)
        #expect(viewModel.failure == nil)
    }

    @MainActor @Test func listViewModelKeepsCreatedSpaceWhenAnOlderLoadFinishesLater() async {
        let repository = CreateWhileLoadingGroupSpaceRepository()
        let viewModel = GroupSpaceListViewModel(service: GroupSpaceService(repository: repository))

        let load = Task { await viewModel.load() }
        while await repository.startedFetchCount() == 0 { await Task.yield() }

        let created = await viewModel.create(name: "새 그룹", nickname: "준하")
        await load.value

        #expect(created)
        #expect(viewModel.spaces.map(\.name) == ["새 그룹"])
        #expect(!viewModel.isLoading)
    }

    @MainActor @Test func listViewModelDoesNotPublishFailureWhenLoadingIsCancelled() async {
        let repository = CancellableGroupSpaceRepository()
        let viewModel = GroupSpaceListViewModel(service: GroupSpaceService(repository: repository))

        let load = Task { await viewModel.load() }
        while await repository.startedFetchCount() == 0 { await Task.yield() }
        load.cancel()
        await load.value

        #expect(!viewModel.isLoading)
        #expect(viewModel.failure == nil)
        #expect(viewModel.failedOperation == nil)
        #expect(viewModel.errorMessage == nil)
    }

    @MainActor @Test func listViewModelShowsInitialFailureThenRecoversOnRetry() async {
        let confirmedSpace = GroupSpaceRepositoryStub.sampleSpace()
        let repository = GroupSpaceRepositoryStub(fetchResponses: [[confirmedSpace]], fetchErrorOnCall: 1)
        let viewModel = GroupSpaceListViewModel(service: GroupSpaceService(repository: repository))

        await viewModel.load()

        #expect(viewModel.didFailLoading)
        #expect(viewModel.failure == .unexpected)
        #expect(viewModel.failedOperation == .load)

        await viewModel.load()

        #expect(viewModel.spaces.map(\.name) == ["프로젝트 팀"])
        #expect(!viewModel.didFailLoading)
        #expect(viewModel.failure == nil)
        #expect(viewModel.failedOperation == nil)
        #expect(viewModel.errorMessage == nil)
    }

    @MainActor @Test func listViewModelExposesCreationFailureEvenAfterInitialLoadFailure() async {
        let viewModel = GroupSpaceListViewModel(
            service: GroupSpaceService(repository: FailingGroupSpaceRepository(error: .backend(
                statusCode: 409,
                problem: .init(type: nil, title: "CONFLICT", status: 409, detail: nil, errorCode: "GROUP_SPACE_CONFLICT")
            )))
        )

        await viewModel.load()
        let succeeded = await viewModel.create(name: "프로젝트 팀", nickname: "준하")

        #expect(!succeeded)
        #expect(viewModel.didFailLoading)
        #expect(viewModel.failure == .backend(errorCode: "GROUP_SPACE_CONFLICT"))
        #expect(viewModel.failedOperation == .create)
        #expect(viewModel.errorMessage?.contains("GROUP_SPACE_CONFLICT") == true)
    }

    @MainActor @Test func ownershipTransferAppliesBackendConfirmedRolesToDetailState() async throws {
        let repository = GroupSpaceRepositoryStub()
        let viewModel = GroupSpaceDetailViewModel(
            groupSpace: GroupSpaceRepositoryStub.sampleGroupSpace(),
            service: GroupSpaceService(repository: repository)
        )

        await viewModel.loadMembers()
        let target = try #require(viewModel.members.first { $0.memberId == 11 })
        let succeeded = await viewModel.transferOwnership(to: target)

        #expect(succeeded)
        #expect(viewModel.groupSpace.myMembership.role == .member)
        #expect(viewModel.members.first { $0.memberId == 10 }?.role == .member)
        #expect(viewModel.members.first { $0.memberId == 11 }?.role == .owner)
    }

    @MainActor @Test func invitationViewModelRetainsPreviewAfterAcceptanceErrorAndClearsItOnDismissal() async {
        let repository = GroupSpaceRepositoryStub(acceptError: GroupSpaceRepositoryStub.StubError.failed)
        let viewModel = GroupInvitationViewModel(service: GroupInvitationService(repository: repository))

        let previewed = await viewModel.preview(type: .inviteCode, credential: "CALIO-2026")
        let accepted = await viewModel.accept(type: .inviteCode, credential: "CALIO-2026", nickname: "준하")

        #expect(previewed)
        #expect(!accepted)
        #expect(viewModel.preview?.name == "프로젝트 팀")
        #expect(viewModel.acceptanceResult == nil)
        #expect(viewModel.errorMessage == "그룹 공간에 참여하지 못했습니다. 다시 시도해 주세요.")

        viewModel.clearAcceptanceFlow()

        #expect(viewModel.preview == nil)
        #expect(viewModel.acceptanceResult == nil)
        #expect(viewModel.errorMessage == nil)
    }

    @MainActor @Test func invitationViewModelPublishesBackendConfirmedAlreadyMemberResult() async {
        let repository = GroupSpaceRepositoryStub(
            previewResponse: .init(name: "프로젝트 팀", emoji: "🗓️", memberCount: 2, expiresAt: GroupSpaceRepositoryStub.date),
            acceptResponse: .init(
                joinResult: .alreadyMember,
                groupSpace: .init(id: 7, name: "프로젝트 팀", emoji: "🗓️", myMembership: .init(memberId: 10, nickname: "준하", role: .member), memberCount: 2, createdAt: GroupSpaceRepositoryStub.date),
                membership: .init(memberId: 10, nickname: "준하", role: .member)
            )
        )
        let viewModel = GroupInvitationViewModel(service: GroupInvitationService(repository: repository))

        let previewed = await viewModel.preview(type: .linkToken, credential: "token")
        let accepted = await viewModel.accept(type: .linkToken, credential: "token", nickname: "준하")

        #expect(previewed)
        #expect(accepted)
        #expect(viewModel.preview?.name == "프로젝트 팀")
        #expect(viewModel.acceptanceResult?.joinResult == .alreadyMember)
        #expect(viewModel.acceptanceResult?.groupSpaceName == "프로젝트 팀")
    }

    @MainActor @Test func invitationViewModelIgnoresIssueRequestsWhileAnotherIssueIsInProgress() async {
        let repository = GroupSpaceRepositoryStub(issueDelayNanoseconds: 100_000_000)
        let viewModel = GroupInvitationViewModel(service: GroupInvitationService(repository: repository))

        let firstIssue = Task { await viewModel.issue(groupSpaceId: 7) }
        await Task.yield()
        await viewModel.issue(groupSpaceId: 7)
        await firstIssue.value

        #expect(repository.issueCallCount == 1)
    }

    @MainActor @Test func invitationViewModelClearsPriorIssuedInvitationWhenTheNextIssueFails() async {
        let repository = GroupSpaceRepositoryStub(issueErrorOnCall: 2)
        let viewModel = GroupInvitationViewModel(service: GroupInvitationService(repository: repository))

        await viewModel.issue(groupSpaceId: 7)
        #expect(viewModel.issuedInvitation?.code == "CALIO-2026")

        await viewModel.issue(groupSpaceId: 7)

        #expect(viewModel.issuedInvitation == nil)
        #expect(viewModel.errorMessage == "초대를 만들지 못했습니다.")
    }

    @MainActor @Test func invitationViewModelIgnoresAnOlderInvitationLoadThatFinishesLast() async {
        let oldInvitation = GroupInvitationSummaryResponseDTO(invitationId: 1, expiresAt: GroupSpaceRepositoryStub.date)
        let latestInvitation = GroupInvitationSummaryResponseDTO(invitationId: 2, expiresAt: GroupSpaceRepositoryStub.date)
        let repository = GroupSpaceRepositoryStub(
            invitationFetchResponses: [[oldInvitation], [latestInvitation]],
            invitationFetchDelays: [50_000_000, 1_000_000]
        )
        let viewModel = GroupInvitationViewModel(service: GroupInvitationService(repository: repository))

        let firstLoad = Task { await viewModel.load(groupSpaceId: 7) }
        while repository.invitationFetchCallCount == 0 { await Task.yield() }
        let secondLoad = Task { await viewModel.load(groupSpaceId: 7) }

        await firstLoad.value
        await secondLoad.value

        #expect(viewModel.invitations.map(\.id) == [2])
        #expect(viewModel.errorMessage == nil)
    }

    @MainActor @Test func invitationViewModelExposesLoadingUntilAnEmptyListCompletes() async {
        let repository = GroupSpaceRepositoryStub(invitationFetchDelays: [50_000_000])
        let viewModel = GroupInvitationViewModel(service: GroupInvitationService(repository: repository))

        let load = Task { await viewModel.load(groupSpaceId: 7) }
        while repository.invitationFetchCallCount == 0 { await Task.yield() }

        #expect(viewModel.isLoadingInvitations)
        #expect(viewModel.invitations.isEmpty)

        await load.value

        #expect(!viewModel.isLoadingInvitations)
        #expect(viewModel.invitations.isEmpty)
    }

    @MainActor @Test func invitationViewModelKeepsIssueSuccessWhenTheRefreshedListIsEmpty() async {
        let viewModel = GroupInvitationViewModel(
            service: GroupInvitationService(repository: GroupSpaceRepositoryStub())
        )

        await viewModel.issue(groupSpaceId: 7)

        #expect(viewModel.issuedInvitation?.code == "CALIO-2026")
        #expect(viewModel.errorMessage == nil)
    }

    @MainActor @Test func invitationViewModelShowsListFailureFromAnExplicitStubbedError() async {
        let repository = GroupSpaceRepositoryStub(invitationFetchErrorOnCall: 1)
        let viewModel = GroupInvitationViewModel(service: GroupInvitationService(repository: repository))

        await viewModel.load(groupSpaceId: 7)

        #expect(viewModel.invitations.isEmpty)
        #expect(viewModel.errorMessage == "초대 목록을 불러오지 못했습니다.")
    }

    @Test func invitationServiceForwardsCanonicalCredentialContracts() async throws {
        let repository = GroupSpaceRepositoryStub()
        let service = GroupInvitationService(repository: repository)

        _ = try await service.preview(type: .inviteCode, credential: "CALIO-2026")
        _ = try await service.preview(type: .linkToken, credential: "token")
        _ = try await service.accept(type: .inviteCode, credential: "CALIO-2026", nickname: "준하")
        _ = try await service.accept(type: .linkToken, credential: "token", nickname: "민지")

        #expect(repository.previewRequests.map(\.credentialType) == [.code, .linkToken])
        #expect(repository.previewRequests.map(\.credential) == ["CALIO-2026", "token"])
        #expect(repository.acceptRequests.map(\.credentialType) == [.inviteCode, .linkToken])
        #expect(repository.acceptRequests.map(\.credential) == ["CALIO-2026", "token"])
        #expect(repository.acceptRequests.map(\.nickname) == ["준하", "민지"])
    }

    @MainActor @Test func removingMemberRefreshesGroupSpaceFromBackend() async throws {
        let repository = GroupSpaceRepositoryStub(memberCountAfterRemoval: 1)
        let viewModel = GroupSpaceDetailViewModel(
            groupSpace: GroupSpaceRepositoryStub.sampleGroupSpace(),
            service: GroupSpaceService(repository: repository)
        )

        await viewModel.loadMembers()
        let member = try #require(viewModel.members.first { $0.memberId == 11 })
        let succeeded = await viewModel.remove(member: member)

        #expect(succeeded)
        #expect(viewModel.groupSpace.memberCount == 1)
        #expect(viewModel.members.map(\.memberId) == [10])
        #expect(repository.operations == ["members:7", "remove:7:11", "fetch:7"])
    }

    @MainActor @Test func removingMemberRemainsSuccessfulWhenThePostRemovalRefreshFails() async throws {
        let repository = GroupSpaceRepositoryStub(fetchGroupSpaceError: .network(URLError(.notConnectedToInternet)))
        let viewModel = GroupSpaceDetailViewModel(
            groupSpace: GroupSpaceRepositoryStub.sampleGroupSpace(),
            service: GroupSpaceService(repository: repository)
        )

        await viewModel.loadMembers()
        let member = try #require(viewModel.members.first { $0.memberId == 11 })
        let succeeded = await viewModel.remove(member: member)

        #expect(succeeded)
        #expect(viewModel.members.map(\.memberId) == [10])
        #expect(viewModel.groupSpace.memberCount == 2)
        #expect(viewModel.postRemovalRefreshMessage == "멤버를 내보냈지만 최신 그룹 정보를 불러오지 못했습니다.")
        #expect(viewModel.failure == nil)
        #expect(viewModel.failedOperation == nil)
    }

    @MainActor @Test func detailViewModelRecordsOperationFailuresWithoutDiscardingBackendErrorCode() async {
        let viewModel = GroupSpaceDetailViewModel(
            groupSpace: GroupSpaceRepositoryStub.sampleGroupSpace(),
            service: GroupSpaceService(repository: FailingGroupSpaceRepository(error: .backend(
                statusCode: 403,
                problem: .init(type: nil, title: "FORBIDDEN", status: 403, detail: nil, errorCode: "GROUP_SPACE_FORBIDDEN")
            )))
        )
        let member = GroupMember(memberId: 11, nickname: "민지", role: .member)

        await viewModel.loadMembers()
        #expect(viewModel.failedOperation == .loadMembers)
        #expect(viewModel.failure == .backend(errorCode: "GROUP_SPACE_FORBIDDEN"))

        let updateSucceeded = await viewModel.update(name: "수정 팀")
        #expect(!updateSucceeded)
        #expect(viewModel.failedOperation == .update)
        #expect(viewModel.failure == .backend(errorCode: "GROUP_SPACE_FORBIDDEN"))
        let deletionSucceeded = await viewModel.delete()
        #expect(!deletionSucceeded)
        #expect(viewModel.failedOperation == .delete)
        #expect(viewModel.failure == .backend(errorCode: "GROUP_SPACE_FORBIDDEN"))
        let leaveSucceeded = await viewModel.leave()
        #expect(!leaveSucceeded)
        #expect(viewModel.failedOperation == .leave)
        #expect(viewModel.failure == .backend(errorCode: "GROUP_SPACE_FORBIDDEN"))
        let removalSucceeded = await viewModel.remove(member: member)
        #expect(!removalSucceeded)
        #expect(viewModel.failedOperation == .removeMember)
        #expect(viewModel.failure == .backend(errorCode: "GROUP_SPACE_FORBIDDEN"))
        let transferSucceeded = await viewModel.transferOwnership(to: member)
        #expect(!transferSucceeded)
        #expect(viewModel.failedOperation == .transferOwnership)
        #expect(viewModel.failure == .backend(errorCode: "GROUP_SPACE_FORBIDDEN"))
    }

    @Test func groupSpaceManagementPermissionFollowsCanonicalMembershipRole() {
        #expect(GroupSpaceRepositoryStub.sampleGroupSpace().canManageGroupSpace)
        #expect(!GroupSpaceRepositoryStub.sampleGroupSpace(role: .member).canManageGroupSpace)
    }

    private func expectFetchFailure(_ apiError: APIError, equals expected: GroupSpaceFailure) async {
        let service = GroupSpaceService(repository: FailingGroupSpaceRepository(error: apiError))

        do {
            _ = try await service.fetchGroupSpaces()
            Issue.record("Expected group space service failure")
        } catch let failure as GroupSpaceFailure {
            #expect(failure == expected)
        } catch {
            Issue.record("Unexpected failure: \(error)")
        }
    }

}

private final class GroupSpaceRepositoryStub: GroupSpaceRepository {
    static let date = Date(timeIntervalSince1970: 0)
    var operations: [String] = []
    private var fetchResponses: [[GroupSpaceResponseDTO]]
    private let fetchErrorOnCall: Int?
    private let memberCountAfterRemoval: Int
    private let fetchGroupSpaceError: APIError?
    private let previewResponse: PreviewGroupInvitationResponseDTO
    private let acceptResponse: AcceptGroupInvitationResponseDTO
    private let invitationError: Error?
    private let acceptError: Error?
    private let issueDelayNanoseconds: UInt64
    private let issueErrorOnCall: Int?
    private var invitationFetchResponses: [[GroupInvitationSummaryResponseDTO]]
    private let invitationFetchDelays: [UInt64]
    private let invitationFetchErrorOnCall: Int?
    private var fetchCallCount = 0
    private(set) var issueCallCount = 0
    private(set) var invitationFetchCallCount = 0
    private(set) var previewRequests: [PreviewGroupInvitationRequestDTO] = []
    private(set) var acceptRequests: [AcceptGroupInvitationRequestDTO] = []

    init(
        fetchResponses: [[GroupSpaceResponseDTO]] = [],
        fetchErrorOnCall: Int? = nil,
        memberCountAfterRemoval: Int = 2,
        fetchGroupSpaceError: APIError? = nil,
        previewResponse: PreviewGroupInvitationResponseDTO = .init(name: "프로젝트 팀", emoji: nil, memberCount: 2, expiresAt: Date(timeIntervalSince1970: 0)),
        acceptResponse: AcceptGroupInvitationResponseDTO = .init(
            joinResult: .joined,
            groupSpace: .init(id: 7, name: "프로젝트 팀", emoji: nil, myMembership: .init(memberId: 10, nickname: "준하", role: .member), memberCount: 2, createdAt: Date(timeIntervalSince1970: 0)),
            membership: .init(memberId: 10, nickname: "준하", role: .member)
        ),
        invitationError: Error? = nil,
        acceptError: Error? = nil,
        issueDelayNanoseconds: UInt64 = 0,
        issueErrorOnCall: Int? = nil,
        invitationFetchResponses: [[GroupInvitationSummaryResponseDTO]] = [[]],
        invitationFetchDelays: [UInt64] = [],
        invitationFetchErrorOnCall: Int? = nil
    ) {
        self.fetchResponses = fetchResponses
        self.fetchErrorOnCall = fetchErrorOnCall
        self.memberCountAfterRemoval = memberCountAfterRemoval
        self.fetchGroupSpaceError = fetchGroupSpaceError
        self.previewResponse = previewResponse
        self.acceptResponse = acceptResponse
        self.invitationError = invitationError
        self.acceptError = acceptError
        self.issueDelayNanoseconds = issueDelayNanoseconds
        self.issueErrorOnCall = issueErrorOnCall
        self.invitationFetchResponses = invitationFetchResponses
        self.invitationFetchDelays = invitationFetchDelays
        self.invitationFetchErrorOnCall = invitationFetchErrorOnCall
    }

    static func sampleSpace(name: String = "프로젝트 팀") -> GroupSpaceResponseDTO {
        .init(groupSpaceId: 7, name: name, emoji: nil, memberCount: 2,
              myMembership: .init(nickname: "준하", role: .owner, createdAt: date, updatedAt: date, statusChangedAt: date),
              createdAt: date, updatedAt: date)
    }

    static func sampleGroupSpace(name: String = "프로젝트 팀", role: GroupMemberRole = .owner) -> GroupSpace {
        GroupSpace(
            groupSpaceId: 7,
            name: name,
            emoji: nil,
            memberCount: 2,
            myMembership: GroupMembership(
                nickname: "준하",
                role: role,
                createdAt: date,
                updatedAt: date,
                statusChangedAt: date
            ),
            createdAt: date,
            updatedAt: date
        )
    }

    func fetchGroupSpaces() async throws -> GroupSpaceListResponseDTO {
        fetchCallCount += 1
        if fetchErrorOnCall == fetchCallCount { throw StubError.failed }
        return .init(groupSpaces: fetchResponses.isEmpty ? [] : fetchResponses.removeFirst())
    }
    func fetchGroupSpace(groupSpaceId: Int64) async throws -> GroupSpaceResponseDTO {
        operations.append("fetch:\(groupSpaceId)")
        if let fetchGroupSpaceError { throw fetchGroupSpaceError }
        var response = Self.sampleSpace()
        response = .init(
            groupSpaceId: response.groupSpaceId,
            name: response.name,
            emoji: response.emoji,
            memberCount: memberCountAfterRemoval,
            myMembership: response.myMembership,
            createdAt: response.createdAt,
            updatedAt: response.updatedAt
        )
        return response
    }
    func createGroupSpace(_ request: CreateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO {
        operations.append("create")
        return Self.sampleSpace(name: request.name)
    }

    func updateGroupSpace(
        groupSpaceId: Int64,
        request: UpdateGroupSpaceRequestDTO
    ) async throws -> GroupSpaceResponseDTO {
        operations.append("update:\(groupSpaceId)")
        return Self.sampleSpace(name: request.name)
    }

    func deleteGroupSpace(groupSpaceId: Int64) async throws {
        operations.append("delete:\(groupSpaceId)")
    }

    func fetchMembers(groupSpaceId: Int64) async throws -> GroupMemberListResponseDTO {
        operations.append("members:\(groupSpaceId)")
        return .init(
            members: [
                .init(memberId: 10, nickname: "준하", role: .owner),
                .init(memberId: 11, nickname: "민지", role: .member)
            ]
        )
    }

    func transferOwnership(
        groupSpaceId: Int64,
        request: TransferGroupOwnerRequestDTO
    ) async throws -> TransferGroupOwnerResponseDTO {
        operations.append("transfer:\(groupSpaceId):\(request.targetMemberId)")
        return .init(
            previousOwner: .init(memberId: 10, nickname: "준하", role: .member),
            owner: .init(memberId: request.targetMemberId, nickname: "민지", role: .owner)
        )
    }

    func leaveGroupSpace(groupSpaceId: Int64) async throws {
        operations.append("leave:\(groupSpaceId)")
    }

    func removeMember(groupSpaceId: Int64, memberId: Int64) async throws {
        operations.append("remove:\(groupSpaceId):\(memberId)")
    }

    func issueInvitation(groupSpaceId: Int64) async throws -> GroupInvitationResponseDTO {
        issueCallCount += 1
        if issueErrorOnCall == issueCallCount { throw StubError.failed }
        if issueDelayNanoseconds > 0 {
            try? await Task.sleep(nanoseconds: issueDelayNanoseconds)
        }
        return .init(invitationId: 3, inviteUrl: "https://calio.app/invite/token", inviteCode: "CALIO-2026", expiresAt: Self.date)
    }

    func fetchInvitations(groupSpaceId: Int64) async throws -> GroupInvitationListResponseDTO {
        invitationFetchCallCount += 1
        let responseIndex = invitationFetchCallCount - 1
        if invitationFetchErrorOnCall == invitationFetchCallCount {
            throw StubError.failed
        }
        let invitations = invitationFetchResponses.isEmpty
            ? []
            : invitationFetchResponses.removeFirst()
        if invitationFetchDelays.indices.contains(responseIndex) {
            try? await Task.sleep(nanoseconds: invitationFetchDelays[responseIndex])
        }
        return .init(invitations: invitations)
    }

    func revokeInvitation(groupSpaceId: Int64, invitationId: Int64) async throws {
        throw StubError.failed
    }

    func previewInvitation(_ request: PreviewGroupInvitationRequestDTO) async throws -> PreviewGroupInvitationResponseDTO {
        previewRequests.append(request)
        if let invitationError { throw invitationError }
        return previewResponse
    }

    func acceptInvitation(_ request: AcceptGroupInvitationRequestDTO) async throws -> AcceptGroupInvitationResponseDTO {
        acceptRequests.append(request)
        if let acceptError { throw acceptError }
        if let invitationError { throw invitationError }
        return acceptResponse
    }

    enum StubError: Error { case failed }

    deinit {}
}

private extension GroupSpaceRepository {
    func issueInvitation(groupSpaceId: Int64) async throws -> GroupInvitationResponseDTO {
        throw GroupSpaceRepositoryStub.StubError.failed
    }

    func fetchInvitations(groupSpaceId: Int64) async throws -> GroupInvitationListResponseDTO {
        throw GroupSpaceRepositoryStub.StubError.failed
    }

    func revokeInvitation(groupSpaceId: Int64, invitationId: Int64) async throws {
        throw GroupSpaceRepositoryStub.StubError.failed
    }

    func previewInvitation(_ request: PreviewGroupInvitationRequestDTO) async throws -> PreviewGroupInvitationResponseDTO {
        throw GroupSpaceRepositoryStub.StubError.failed
    }

    func acceptInvitation(_ request: AcceptGroupInvitationRequestDTO) async throws -> AcceptGroupInvitationResponseDTO {
        throw GroupSpaceRepositoryStub.StubError.failed
    }
}

private struct FailingGroupSpaceRepository: GroupSpaceRepository {
    let error: APIError

    func fetchGroupSpaces() async throws -> GroupSpaceListResponseDTO { throw error }
    func fetchGroupSpace(groupSpaceId: Int64) async throws -> GroupSpaceResponseDTO { throw error }
    func createGroupSpace(_ request: CreateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { throw error }
    func updateGroupSpace(groupSpaceId: Int64, request: UpdateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { throw error }
    func deleteGroupSpace(groupSpaceId: Int64) async throws { throw error }
    func fetchMembers(groupSpaceId: Int64) async throws -> GroupMemberListResponseDTO { throw error }
    func transferOwnership(groupSpaceId: Int64, request: TransferGroupOwnerRequestDTO) async throws -> TransferGroupOwnerResponseDTO { throw error }
    func leaveGroupSpace(groupSpaceId: Int64) async throws { throw error }
    func removeMember(groupSpaceId: Int64, memberId: Int64) async throws { throw error }
}

private actor OutOfOrderGroupSpaceRepository: GroupSpaceRepository {
    private var fetchCount = 0
    private var firstFetchContinuation: CheckedContinuation<Void, Never>?

    func startedFetchCount() -> Int { fetchCount }

    func finishFirstFetch() {
        firstFetchContinuation?.resume()
        firstFetchContinuation = nil
    }

    func fetchGroupSpaces() async throws -> GroupSpaceListResponseDTO {
        fetchCount += 1
        let requestNumber = fetchCount
        if requestNumber == 1 {
            await withCheckedContinuation { continuation in
                firstFetchContinuation = continuation
            }
        }
        return .init(groupSpaces: [GroupSpaceRepositoryStub.sampleSpace(name: requestNumber == 1 ? "이전 공간" : "최신 공간")])
    }

    func fetchGroupSpace(groupSpaceId: Int64) async throws -> GroupSpaceResponseDTO { fatalError("Unexpected call") }
    func createGroupSpace(_ request: CreateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { fatalError("Unexpected call") }
    func updateGroupSpace(groupSpaceId: Int64, request: UpdateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { fatalError("Unexpected call") }
    func deleteGroupSpace(groupSpaceId: Int64) async throws { fatalError("Unexpected call") }
    func fetchMembers(groupSpaceId: Int64) async throws -> GroupMemberListResponseDTO { fatalError("Unexpected call") }
    func transferOwnership(groupSpaceId: Int64, request: TransferGroupOwnerRequestDTO) async throws -> TransferGroupOwnerResponseDTO { fatalError("Unexpected call") }
    func leaveGroupSpace(groupSpaceId: Int64) async throws { fatalError("Unexpected call") }
    func removeMember(groupSpaceId: Int64, memberId: Int64) async throws { fatalError("Unexpected call") }
}

private actor CreateWhileLoadingGroupSpaceRepository: GroupSpaceRepository {
    private var fetchCount = 0

    func startedFetchCount() -> Int { fetchCount }

    func fetchGroupSpaces() async throws -> GroupSpaceListResponseDTO {
        fetchCount += 1
        try await Task.sleep(for: .milliseconds(50))
        return .init(groupSpaces: [GroupSpaceRepositoryStub.sampleSpace(name: "이전 그룹")])
    }

    func createGroupSpace(_ request: CreateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO {
        GroupSpaceRepositoryStub.sampleSpace(name: request.name)
    }

    func fetchGroupSpace(groupSpaceId: Int64) async throws -> GroupSpaceResponseDTO { fatalError("Unexpected call") }
    func updateGroupSpace(groupSpaceId: Int64, request: UpdateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { fatalError("Unexpected call") }
    func deleteGroupSpace(groupSpaceId: Int64) async throws { fatalError("Unexpected call") }
    func fetchMembers(groupSpaceId: Int64) async throws -> GroupMemberListResponseDTO { fatalError("Unexpected call") }
    func transferOwnership(groupSpaceId: Int64, request: TransferGroupOwnerRequestDTO) async throws -> TransferGroupOwnerResponseDTO { fatalError("Unexpected call") }
    func leaveGroupSpace(groupSpaceId: Int64) async throws { fatalError("Unexpected call") }
    func removeMember(groupSpaceId: Int64, memberId: Int64) async throws { fatalError("Unexpected call") }
}

private struct CancelledGroupSpaceRepository: GroupSpaceRepository {
    func fetchGroupSpaces() async throws -> GroupSpaceListResponseDTO { throw CancellationError() }
    func fetchGroupSpace(groupSpaceId: Int64) async throws -> GroupSpaceResponseDTO { throw CancellationError() }
    func createGroupSpace(_ request: CreateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { throw CancellationError() }
    func updateGroupSpace(groupSpaceId: Int64, request: UpdateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { throw CancellationError() }
    func deleteGroupSpace(groupSpaceId: Int64) async throws { throw CancellationError() }
    func fetchMembers(groupSpaceId: Int64) async throws -> GroupMemberListResponseDTO { throw CancellationError() }
    func transferOwnership(groupSpaceId: Int64, request: TransferGroupOwnerRequestDTO) async throws -> TransferGroupOwnerResponseDTO { throw CancellationError() }
    func leaveGroupSpace(groupSpaceId: Int64) async throws { throw CancellationError() }
    func removeMember(groupSpaceId: Int64, memberId: Int64) async throws { throw CancellationError() }
}

private actor CancellableGroupSpaceRepository: GroupSpaceRepository {
    private var fetchCount = 0

    func startedFetchCount() -> Int { fetchCount }

    func fetchGroupSpaces() async throws -> GroupSpaceListResponseDTO {
        fetchCount += 1
        try await Task.sleep(for: .seconds(1))
        return .init(groupSpaces: [])
    }

    func fetchGroupSpace(groupSpaceId: Int64) async throws -> GroupSpaceResponseDTO { fatalError("Unexpected call") }
    func createGroupSpace(_ request: CreateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { fatalError("Unexpected call") }
    func updateGroupSpace(groupSpaceId: Int64, request: UpdateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { fatalError("Unexpected call") }
    func deleteGroupSpace(groupSpaceId: Int64) async throws { fatalError("Unexpected call") }
    func fetchMembers(groupSpaceId: Int64) async throws -> GroupMemberListResponseDTO { fatalError("Unexpected call") }
    func transferOwnership(groupSpaceId: Int64, request: TransferGroupOwnerRequestDTO) async throws -> TransferGroupOwnerResponseDTO { fatalError("Unexpected call") }
    func leaveGroupSpace(groupSpaceId: Int64) async throws { fatalError("Unexpected call") }
    func removeMember(groupSpaceId: Int64, memberId: Int64) async throws { fatalError("Unexpected call") }
}
