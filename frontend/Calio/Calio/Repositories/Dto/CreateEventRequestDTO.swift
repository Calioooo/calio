//
//  CreateEventRequestDTO.swift
//  Calio
//
//  Created by Codex on 6/19/26.
//

import Foundation

struct CreateEventRequestDTO: Encodable {
    let title: String
    let description: String
    let startAt: Date
    let endAt: Date
}
