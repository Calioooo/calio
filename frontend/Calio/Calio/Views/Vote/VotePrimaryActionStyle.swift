import SwiftUI

enum VotePrimaryActionStyle {
  static let gradient = LinearGradient(
    gradient: Gradient(colors: [Color.voteAccentHighlight, Color.voteAccent]),
    startPoint: .topLeading,
    endPoint: .bottomTrailing
  )
}
