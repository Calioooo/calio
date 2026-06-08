//
//  CalendarWeekday.swift
//  Calio
//
//  Created by 김준하 on 6/6/26.
//

import Foundation

enum CalendarWeekday: Int, CaseIterable, Identifiable {
    case sunday = 1
    case monday = 2
    case tuesday = 3
    case wednesday = 4
    case thursday = 5
    case friday = 6
    case saturday = 7

    var id: Int {
        rawValue
    }

    var shortKoreanText: String {
        switch self {
        case .sunday:
            return "일"
        case .monday:
            return "월"
        case .tuesday:
            return "화"
        case .wednesday:
            return "수"
        case .thursday:
            return "목"
        case .friday:
            return "금"
        case .saturday:
            return "토"
        }
    }

    var fullKoreanText: String {
        switch self {
        case .sunday:
            return "일요일"
        case .monday:
            return "월요일"
        case .tuesday:
            return "화요일"
        case .wednesday:
            return "수요일"
        case .thursday:
            return "목요일"
        case .friday:
            return "금요일"
        case .saturday:
            return "토요일"
        }
    }
}
