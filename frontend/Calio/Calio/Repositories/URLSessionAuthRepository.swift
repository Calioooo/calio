//
//  URLSessionAuthRepository.swift
//  Calio
//
//  Created by Codex on 7/13/26.
//

import Foundation

struct URLSessionAuthRepository: AuthRepository {
    private let apiClient: APIClient

    init(
        baseURL: URL = CalioAPIConfig.baseURL,
        session: URLSession = .shared,
        jsonDecoder: JSONDecoder = APIJSONCoding.makeDecoder()
    ) {
        self.apiClient = APIClient(
            baseURL: baseURL,
            session: session,
            jsonDecoder: jsonDecoder
        )
    }

    func issueGuestToken() async throws -> GuestAuthResponseDTO {
        try await apiClient.send(
            GuestAuthResponseDTO.self,
            method: .post,
            pathComponents: ["api", "auth", "guest"],
            authorization: .none
        )
    }
}
