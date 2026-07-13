//
//  AuthService.swift
//  Calio
//
//  Created by Codex on 7/13/26.
//

import Foundation

protocol AuthTokenProvider {
    var accessToken: String? { get }
}

protocol AuthTokenStore: AuthTokenProvider {
    func loadAccessToken() throws -> String?
    func saveAccessToken(_ accessToken: String) throws
    func deleteAccessToken() throws
}

struct AuthService {
    private let repository: AuthRepository
    private let tokenStore: AuthTokenStore

    init(
        repository: AuthRepository = URLSessionAuthRepository(),
        tokenStore: AuthTokenStore = KeychainAuthTokenStore.shared
    ) {
        self.repository = repository
        self.tokenStore = tokenStore
    }

    func ensureGuestAuthentication() async throws -> String {
        if let storedToken = try tokenStore.loadAccessToken(),
           !storedToken.isEmpty {
            return storedToken
        }

        let response = try await repository.issueGuestToken()
        guard response.tokenType.caseInsensitiveCompare("Bearer") == .orderedSame,
              !response.accessToken.isEmpty else {
            throw AuthServiceError.invalidGuestTokenResponse
        }

        try tokenStore.saveAccessToken(response.accessToken)
        return response.accessToken
    }

    func resetAuthentication() throws {
        try tokenStore.deleteAccessToken()
    }
}

enum AuthServiceError: Error, Equatable {
    case invalidGuestTokenResponse
}
