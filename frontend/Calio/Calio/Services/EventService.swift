//
//  EventService.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation

struct EventService {
    private let repository: EventRepository
    
    init(repository: EventRepository = StubEventRepository()) {
        self.repository = repository
    }
    
    func fetchEvents(from startDate: Date, to endDate: Date) async throws -> [Event] {
        let response = try await repository.fetchEvents(from: startDate, to: endDate)
        
        return response.map{ dto in
            Event(
                id: dto.id,
                title: dto.title,
                description: dto.description ?? "",
                startAt: dto.startAt,
                endAt: dto.endAt,
                colorCode: "#4F46E5"
            )
        }
    }
}
