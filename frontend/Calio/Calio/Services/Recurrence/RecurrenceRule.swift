import Foundation

struct EditableRecurrenceRule: Equatable {
    let frequency: RecurrenceFrequency
    let until: Date?
}

enum RecurrenceRule {
    static func make(frequency: RecurrenceFrequency, until: Date?, allDay: Bool) -> String {
        guard let until else { return "RRULE:FREQ=\(frequency.rawValue)" }
        return "RRULE:FREQ=\(frequency.rawValue);UNTIL=\(untilValue(until, allDay: allDay))"
    }

    static func editableRule(from lines: [String], allDay: Bool) -> EditableRecurrenceRule? {
        guard lines.count == 1, lines[0].hasPrefix("RRULE:") else { return nil }
        let parts = lines[0].dropFirst("RRULE:".count)
            .split(separator: ";", omittingEmptySubsequences: false)
            .map(String.init)
        guard parts.count == 1 || parts.count == 2 else { return nil }
        let values = Dictionary(uniqueKeysWithValues: parts.compactMap { part -> (String, String)? in
            let pair = part.split(separator: "=", maxSplits: 1).map(String.init)
            return pair.count == 2 ? (pair[0], pair[1]) : nil
        })
        guard values.count == parts.count,
              let frequencyValue = values["FREQ"],
              let frequency = RecurrenceFrequency(rawValue: frequencyValue) else {
            return nil
        }
        if values.count == 1 {
            return EditableRecurrenceRule(frequency: frequency, until: nil)
        }
        guard let untilValue = values["UNTIL"],
              let until = parseUntil(untilValue, allDay: allDay) else { return nil }
        return EditableRecurrenceRule(frequency: frequency, until: until)
    }

    private static func untilValue(_ date: Date, allDay: Bool) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = allDay ? .current : TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = allDay ? "yyyyMMdd" : "yyyyMMdd'T'HHmmss'Z'"
        return formatter.string(from: date)
    }

    private static func parseUntil(_ value: String, allDay: Bool) -> Date? {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.timeZone = allDay ? .current : TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = allDay ? "yyyyMMdd" : "yyyyMMdd'T'HHmmss'Z'"
        return formatter.date(from: value)
    }
}
