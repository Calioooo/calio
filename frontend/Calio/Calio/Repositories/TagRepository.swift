import Foundation

protocol TagRepository {
    func fetchTags() async throws -> [TagResponseDTO]
    func createCustomTag(_ request: CustomTagRequestDTO) async throws -> TagResponseDTO
    func updateCustomTag(tagId: Int64, request: CustomTagRequestDTO) async throws -> TagResponseDTO
    func deleteCustomTag(tagId: Int64) async throws
}
