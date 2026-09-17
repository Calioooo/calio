//
//  NationalHolidayRepository.swift
//  Calio
//
//  Created by Codex on 7/6/26.
//

import Foundation

protocol NationalHolidayRepository {
    func fetchNationalHolidays(from startDay: DayKey, to endDay: DayKey) async throws -> [NationalHolidayResponseDTO]
}
