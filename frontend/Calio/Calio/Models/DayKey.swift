//
//  DayKey.swift
//  Calio
//
//  Created by 김준하 on 6/7/26.
//

import Foundation

struct DayKey: Hashable {
    let year: Int
    let month: Int
    let day: Int
    
    init(date: Date, calendar: Calendar = .current) {
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        
        guard
            let year = components.year,
            let month = components.month,
            let day = components.day
                
        else {
            preconditionFailure("Failed to create DayKey from date: \(date)")
        }
        
        self.year = year
        self.month = month
        self.day = day
    }
    
    func toDate(calendar: Calendar = .current) -> Date {
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = day
        
        guard let date = calendar.date(from: components) else {
            preconditionFailure("Failed to create date from DayKey: \(self)")
        }
        
        return date
    }
}
