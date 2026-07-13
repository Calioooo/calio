//
//  KeychainAuthTokenStore.swift
//  Calio
//
//  Created by Codex on 7/13/26.
//

import Foundation
import Security

final class KeychainAuthTokenStore: AuthTokenStore {
    static let shared = KeychainAuthTokenStore()

    private let service: String
    private let account: String

    init(
        service: String = Bundle.main.bundleIdentifier ?? "com.calio.calendar",
        account: String = "guest-access-token"
    ) {
        self.service = service
        self.account = account
    }

    var accessToken: String? {
        try? loadAccessToken()
    }

    func loadAccessToken() throws -> String? {
        var query = baseQuery()
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        query[kSecReturnData as String] = true

        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        if status == errSecItemNotFound {
            return nil
        }

        guard status == errSecSuccess else {
            throw KeychainAuthTokenStoreError.unexpectedStatus(status)
        }

        guard let data = result as? Data,
              let token = String(data: data, encoding: .utf8) else {
            throw KeychainAuthTokenStoreError.invalidStoredToken
        }

        return token
    }

    func saveAccessToken(_ accessToken: String) throws {
        try deleteAccessToken()

        guard let tokenData = accessToken.data(using: .utf8) else {
            throw KeychainAuthTokenStoreError.invalidTokenEncoding
        }

        var query = baseQuery()
        query[kSecValueData as String] = tokenData
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly

        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw KeychainAuthTokenStoreError.unexpectedStatus(status)
        }
    }

    func deleteAccessToken() throws {
        let status = SecItemDelete(baseQuery() as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainAuthTokenStoreError.unexpectedStatus(status)
        }
    }

    private func baseQuery() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
    }
}

enum KeychainAuthTokenStoreError: Error, Equatable {
    case unexpectedStatus(OSStatus)
    case invalidStoredToken
    case invalidTokenEncoding
}
