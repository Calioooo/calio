//
//  EventRepository.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation

protocol EventRepository {
    func fetchEvents(from startDate: Date, to endDate: Date) async throws -> [EventResponseDTO]
}
