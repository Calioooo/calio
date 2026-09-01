import Foundation

struct URLSessionTagRepository: TagRepository {
    private let apiClient: APIClient

    init(
        baseURL: URL = CalioAPIConfig.baseURL,
        session: URLSession = .shared,
        jsonDecoder: JSONDecoder = APIJSONCoding.makeDecoder(),
        jsonEncoder: JSONEncoder = APIJSONCoding.makeEncoder(),
        authTokenProvider: AuthTokenProvider? = KeychainAuthTokenStore.shared
    ) {
        self.apiClient = APIClient(
            baseURL: baseURL,
            session: session,
            jsonDecoder: jsonDecoder,
            jsonEncoder: jsonEncoder,
            authTokenProvider: authTokenProvider
        )
    }

    func fetchTags() async throws -> [TagResponseDTO] {
        try await apiClient.send(
            [TagResponseDTO].self,
            method: .get,
            pathComponents: ["api", "tags"],
            authorization: .bearer
        )
    }

    func createCustomTag(_ request: CustomTagRequestDTO) async throws -> TagResponseDTO {
        try await apiClient.send(
            TagResponseDTO.self,
            method: .post,
            pathComponents: ["api", "custom-tags"],
            authorization: .bearer,
            body: request
        )
    }

    func updateCustomTag(tagId: Int64, request: CustomTagRequestDTO) async throws -> TagResponseDTO {
        try await apiClient.send(
            TagResponseDTO.self,
            method: .put,
            pathComponents: ["api", "custom-tags", String(tagId)],
            authorization: .bearer,
            body: request
        )
    }

    func deleteCustomTag(tagId: Int64) async throws {
        try await apiClient.sendWithoutResponse(
            method: .delete,
            pathComponents: ["api", "custom-tags", String(tagId)],
            authorization: .bearer
        )
    }
}
