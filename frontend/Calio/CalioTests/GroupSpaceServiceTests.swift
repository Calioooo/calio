import Foundation
import Testing
@testable import Calio

@Suite(.serialized)
struct GroupSpaceServiceTests {
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
        await viewModel.load()

        #expect(viewModel.spaces.map(\.name) == ["기존 그룹", "새로 참여한 그룹"])
        #expect(viewModel.didFailLoading == false)
    }

    @MainActor @Test func ownershipTransferAppliesBackendConfirmedRolesToDetailState() async throws {
        let repository = GroupSpaceRepositoryStub()
        let viewModel = GroupSpaceDetailViewModel(
            groupSpace: GroupSpaceRepositoryStub.sampleSpace(),
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

    @MainActor @Test func invitationViewModelRetainsAcceptanceContextAfterRecoverableErrorAndClearsItOnDismissal() async {
        let repository = GroupSpaceRepositoryStub(invitationError: GroupSpaceRepositoryStub.StubError.failed)
        let viewModel = GroupInvitationViewModel(service: GroupInvitationService(repository: repository))

        let previewed = await viewModel.preview(type: .inviteCode, credential: "CALIO-2026")
        let accepted = await viewModel.accept(type: .inviteCode, credential: "CALIO-2026", nickname: "준하")

        #expect(!previewed)
        #expect(!accepted)
        #expect(viewModel.preview == nil)
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
}

private final class GroupSpaceRepositoryStub: GroupSpaceRepository {
    static let date = Date(timeIntervalSince1970: 0)
    var operations: [String] = []
    private var fetchResponses: [[GroupSpaceResponseDTO]]
    private let fetchErrorOnCall: Int?
    private let previewResponse: PreviewGroupInvitationResponseDTO
    private let acceptResponse: AcceptGroupInvitationResponseDTO
    private let invitationError: Error?
    private let issueDelayNanoseconds: UInt64
    private var fetchCallCount = 0
    private(set) var issueCallCount = 0

    init(
        fetchResponses: [[GroupSpaceResponseDTO]] = [],
        fetchErrorOnCall: Int? = nil,
        previewResponse: PreviewGroupInvitationResponseDTO = .init(name: "프로젝트 팀", emoji: nil, memberCount: 2, expiresAt: Date(timeIntervalSince1970: 0)),
        acceptResponse: AcceptGroupInvitationResponseDTO = .init(
            joinResult: .joined,
            groupSpace: .init(id: 7, name: "프로젝트 팀", emoji: nil, myMembership: .init(memberId: 10, nickname: "준하", role: .member), memberCount: 2, createdAt: Date(timeIntervalSince1970: 0)),
            membership: .init(memberId: 10, nickname: "준하", role: .member)
        ),
        invitationError: Error? = nil,
        issueDelayNanoseconds: UInt64 = 0
    ) {
        self.fetchResponses = fetchResponses
        self.fetchErrorOnCall = fetchErrorOnCall
        self.previewResponse = previewResponse
        self.acceptResponse = acceptResponse
        self.invitationError = invitationError
        self.issueDelayNanoseconds = issueDelayNanoseconds
    }

    static func sampleSpace(name: String = "프로젝트 팀") -> GroupSpaceResponseDTO {
        .init(groupSpaceId: 7, name: name, emoji: nil, memberCount: 2,
              myMembership: .init(nickname: "준하", role: .owner, createdAt: date, updatedAt: date, statusChangedAt: date),
              createdAt: date, updatedAt: date)
    }

    func fetchGroupSpaces() async throws -> GroupSpaceListResponseDTO {
        fetchCallCount += 1
        if fetchErrorOnCall == fetchCallCount { throw StubError.failed }
        return .init(groupSpaces: fetchResponses.isEmpty ? [] : fetchResponses.removeFirst())
    }
    func createGroupSpace(_ request: CreateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { operations.append("create"); return Self.sampleSpace(name: request.name) }
    func updateGroupSpace(groupSpaceId: Int64, request: UpdateGroupSpaceRequestDTO) async throws -> GroupSpaceResponseDTO { operations.append("update:\(groupSpaceId)"); return Self.sampleSpace(name: request.name) }
    func deleteGroupSpace(groupSpaceId: Int64) async throws { operations.append("delete:\(groupSpaceId)") }
    func fetchMembers(groupSpaceId: Int64) async throws -> GroupMemberListResponseDTO { operations.append("members:\(groupSpaceId)"); return .init(members: [.init(memberId: 10, nickname: "준하", role: .owner), .init(memberId: 11, nickname: "민지", role: .member)]) }
    func transferOwnership(groupSpaceId: Int64, request: TransferGroupOwnerRequestDTO) async throws -> TransferGroupOwnerResponseDTO { operations.append("transfer:\(groupSpaceId):\(request.targetMemberId)"); return .init(previousOwner: .init(memberId: 10, nickname: "준하", role: .member), owner: .init(memberId: request.targetMemberId, nickname: "민지", role: .owner)) }
    func leaveGroupSpace(groupSpaceId: Int64) async throws { operations.append("leave:\(groupSpaceId)") }
    func removeMember(groupSpaceId: Int64, memberId: Int64) async throws { operations.append("remove:\(groupSpaceId):\(memberId)") }
    func issueInvitation(groupSpaceId: Int64) async throws -> GroupInvitationResponseDTO {
        issueCallCount += 1
        if issueDelayNanoseconds > 0 {
            try? await Task.sleep(nanoseconds: issueDelayNanoseconds)
        }
        return .init(invitationId: 3, inviteUrl: "https://calio.app/invite/token", inviteCode: "CALIO-2026", expiresAt: Self.date)
    }
    func fetchInvitations(groupSpaceId: Int64) async throws -> GroupInvitationListResponseDTO { throw StubError.failed }
    func revokeInvitation(groupSpaceId: Int64, invitationId: Int64) async throws { throw StubError.failed }
    func previewInvitation(_ request: PreviewGroupInvitationRequestDTO) async throws -> PreviewGroupInvitationResponseDTO { if let invitationError { throw invitationError }; return previewResponse }
    func acceptInvitation(_ request: AcceptGroupInvitationRequestDTO) async throws -> AcceptGroupInvitationResponseDTO { if let invitationError { throw invitationError }; return acceptResponse }

    enum StubError: Error { case failed }
}
