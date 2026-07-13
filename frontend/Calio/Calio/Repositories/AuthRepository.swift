//
//  AuthRepository.swift
//  Calio
//
//  Created by Codex on 7/13/26.
//

import Foundation

protocol AuthRepository {
    func issueGuestToken() async throws -> GuestAuthResponseDTO
}

enum AuthRepositoryError: Error {
    case invalidResponse
    case network(URLError)
    case backend(statusCode: Int, response: ErrorResponseDTO?)
    case decoding(Error)
    case unexpected(Error)
}
