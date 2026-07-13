//
//  AuthResponseDTO.swift
//  Calio
//
//  Created by Codex on 7/13/26.
//

import Foundation

struct GuestAuthResponseDTO: Decodable, Equatable {
    let accessToken: String
    let tokenType: String
}
