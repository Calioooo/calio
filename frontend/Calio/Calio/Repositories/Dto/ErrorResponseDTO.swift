//
//  ErrorResponseDTO.swift
//  Calio
//
//  Created by Codex on 6/19/26.
//

import Foundation

struct ErrorResponseDTO: Decodable {
    let errorCode: String
    let message: String
}
