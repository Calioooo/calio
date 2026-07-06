//
//  NationalHolidayResponseDTO.swift
//  Calio
//
//  Created by Codex on 7/6/26.
//

import Foundation

struct NationalHolidayResponseDTO: Decodable, Equatable {
    let nationalHolidayId: Int64
    let holidayDate: String
    let holidayTitle: String
}
