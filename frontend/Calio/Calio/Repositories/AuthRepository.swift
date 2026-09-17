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
