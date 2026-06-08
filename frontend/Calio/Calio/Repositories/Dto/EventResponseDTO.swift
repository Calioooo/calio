//
//  EventResponseDTO.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation

struct EventResponseDTO: Decodable {
    let id: Int64
    let title: String
    let description: String?
    let startAt: Date
    let endAt: Date
    let createdAt: Date
    let updatedAt: Date
}
