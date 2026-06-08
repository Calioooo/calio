//
//  EventColor.swift
//  Calio
//
//  Created by 김준하 on 6/6/26.
//

import Foundation

struct EventColor: Hashable, Codable {
    let hex: String
    
        init(hex: String) {
            self.hex = EventColor.normalize(hex)
        }

        private static func normalize(_ hex: String) -> String {
            let value = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
            return "#\(value.uppercased())"
        }
}
