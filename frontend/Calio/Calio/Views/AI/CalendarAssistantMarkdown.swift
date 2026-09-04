import SwiftUI

enum CalendarAssistantMarkdown {
    static func attributedText(from source: String) -> AttributedString {
        (try? AttributedString(markdown: source)) ?? AttributedString(source)
    }
}
