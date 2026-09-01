import Foundation
import Combine

@MainActor final class GroupSpaceListViewModel: ObservableObject {
    @Published private(set) var spaces: [GroupSpaceResponseDTO] = []
    @Published private(set) var isLoading = false
    @Published private(set) var didFailLoading = false
    @Published var errorMessage: String?
    private let service: GroupSpaceService
    init(service: GroupSpaceService = GroupSpaceService()) { self.service = service }
    func load() async {
        isLoading = true
        defer { isLoading = false }
        do {
            spaces = try await service.fetchGroupSpaces()
            didFailLoading = false
            errorMessage = nil
        } catch {
            didFailLoading = true
            errorMessage = "그룹 공간을 불러오지 못했습니다. 다시 시도해 주세요."
        }
    }
    func refreshAfterJoiningInvitation() async {
        await load()
    }
    func create(name: String, nickname: String) async -> Bool {
        do {
            let space = try await service.create(name: name, emoji: nil, nickname: nickname)
            spaces.append(space)
            errorMessage = nil
            return true
        } catch {
            errorMessage = "그룹 공간을 만들지 못했습니다. 다시 시도해 주세요."
            return false
        }
    }
    func replace(_ updatedSpace: GroupSpaceResponseDTO) {
        guard let index = spaces.firstIndex(where: { $0.groupSpaceId == updatedSpace.groupSpaceId }) else { return }
        spaces[index] = updatedSpace
    }
    func remove(groupSpaceId: Int64) {
        spaces.removeAll { $0.groupSpaceId == groupSpaceId }
    }
    func clearError() { errorMessage = nil }
}
