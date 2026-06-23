//
//  Color.swift
//  Calio
//
//  Created by 김준하 on 6/6/26.
//

import SwiftUI

extension Color {
    init(hex: String) {
        let sanitizedHex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)

        var value: UInt64 = 0
        Scanner(string: sanitizedHex).scanHexInt64(&value)

        let red: Double
        let green: Double
        let blue: Double
        let opacity: Double

        switch sanitizedHex.count {
        case 6:
            red = Double((value & 0xFF0000) >> 16) / 255.0
            green = Double((value & 0x00FF00) >> 8) / 255.0
            blue = Double(value & 0x0000FF) / 255.0
            opacity = 1.0

        case 8:
            red = Double((value & 0xFF000000) >> 24) / 255.0
            green = Double((value & 0x00FF0000) >> 16) / 255.0
            blue = Double((value & 0x0000FF00) >> 8) / 255.0
            opacity = Double(value & 0x000000FF) / 255.0

        default:
            red = 0.56
            green = 0.76
            blue = 0.96
            opacity = 1.0
        }

        self.init(.sRGB, red: red, green: green, blue: blue, opacity: opacity)
    }
}
