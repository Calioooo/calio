//
//  LocalEventTextParser.swift
//  Calio
//
//  Created by Codex on 7/14/26.
//

import Foundation

struct LocalEventTextParseResult: Equatable {
    let title: String
    let startAt: Date?
    let endAt: Date?
    let recurrenceFrequency: RecurrenceFrequency?
}

struct LocalEventTextParser {
    private let calendar: Calendar

    init(calendar: Calendar = .current) {
        self.calendar = calendar
    }

    func parse(
        _ text: String,
        referenceDate: Date = Date()
    ) -> LocalEventTextParseResult? {
        let trimmedText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedText.isEmpty else {
            return nil
        }
        guard !containsUnsupportedExpression(trimmedText) else {
            return nil
        }

        var matchedRanges: [Range<String.Index>] = []
        let recurrenceFrequency = parseRecurrenceFrequency(in: trimmedText, matchedRanges: &matchedRanges)
        let parsedDate = parseDate(in: trimmedText, referenceDate: referenceDate, matchedRanges: &matchedRanges)
        let parsedTime = parseTime(in: trimmedText, matchedRanges: &matchedRanges)

        guard parsedDate != nil || parsedTime != nil || recurrenceFrequency != nil else {
            return nil
        }

        let startAt = makeStartAt(
            date: parsedDate,
            time: parsedTime?.start,
            referenceDate: referenceDate
        )
        let endAt = makeEndAt(
            startAt: startAt,
            endTime: parsedTime?.end,
            referenceDate: referenceDate
        )

        return LocalEventTextParseResult(
            title: cleanedTitle(from: trimmedText, removing: matchedRanges),
            startAt: startAt,
            endAt: endAt,
            recurrenceFrequency: recurrenceFrequency
        )
    }

    private func parseRecurrenceFrequency(
        in text: String,
        matchedRanges: inout [Range<String.Index>]
    ) -> RecurrenceFrequency? {
        let candidates: [(keyword: String, frequency: RecurrenceFrequency)] = [
            ("매일", .daily),
            ("매주", .weekly),
            ("매월", .monthly),
            ("매년", .yearly)
        ]

        for candidate in candidates {
            if let range = text.range(of: candidate.keyword) {
                matchedRanges.append(range)
                return candidate.frequency
            }
        }

        return nil
    }

    private func containsUnsupportedExpression(_ text: String) -> Bool {
        let unsupportedKeywords = [
            "격주",
            "평일",
            "주말",
            "첫째",
            "둘째",
            "셋째",
            "넷째",
            "다섯째",
            "마지막",
            "말일",
            "매번"
        ]

        return unsupportedKeywords.contains { text.contains($0) }
    }

    private func parseDate(
        in text: String,
        referenceDate: Date,
        matchedRanges: inout [Range<String.Index>]
    ) -> Date? {
        let referenceDay = calendar.startOfDay(for: referenceDate)

        if let range = text.range(of: "모레") {
            matchedRanges.append(range)
            return calendar.date(byAdding: .day, value: 2, to: referenceDay)
        }

        if let range = text.range(of: "내일") {
            matchedRanges.append(range)
            return calendar.date(byAdding: .day, value: 1, to: referenceDay)
        }

        if let range = text.range(of: "오늘") {
            matchedRanges.append(range)
            return referenceDay
        }

        if let match = firstMatch(
            in: text,
            pattern: #"다음\s*주\s*([월화수목금토일])(?:요일)?"#
        ), let weekdayText = match.value(at: 1),
           let weekday = koreanWeekdayIndex(weekdayText) {
            matchedRanges.append(match.range)
            return dateInWeek(containing: referenceDay, weekOffset: 1, weekdayIndex: weekday)
        }

        if let match = firstMatch(
            in: text,
            pattern: #"이번\s*주\s*([월화수목금토일])(?:요일)?"#
        ), let weekdayText = match.value(at: 1),
           let weekday = koreanWeekdayIndex(weekdayText) {
            matchedRanges.append(match.range)
            return dateInWeek(containing: referenceDay, weekOffset: 0, weekdayIndex: weekday)
        }

        if let match = firstMatch(
            in: text,
            pattern: #"(\d{1,2})\s*월\s*(\d{1,2})\s*일"#
        ), let month = match.intValue(at: 1),
           let day = match.intValue(at: 2) {
            matchedRanges.append(match.range)
            return date(month: month, day: day, referenceDate: referenceDate)
        }

        if let match = firstMatch(
            in: text,
            pattern: #"(\d{1,2})\s*/\s*(\d{1,2})"#
        ), let month = match.intValue(at: 1),
           let day = match.intValue(at: 2) {
            matchedRanges.append(match.range)
            return date(month: month, day: day, referenceDate: referenceDate)
        }

        if let match = firstMatch(
            in: text,
            pattern: #"(?<!\d)(\d{1,2})\s*일(?!\s*(?:동안|간))"#
        ), let day = match.intValue(at: 1) {
            matchedRanges.append(match.range)
            return date(day: day, referenceDate: referenceDate)
        }

        if let match = firstMatch(
            in: text,
            pattern: #"([월화수목금토일])요일"#
        ), let weekdayText = match.value(at: 1),
           let weekday = koreanWeekdayIndex(weekdayText) {
            matchedRanges.append(match.range)
            return nearestFutureWeekday(containing: referenceDay, weekdayIndex: weekday)
        }

        return nil
    }

    private func parseTime(
        in text: String,
        matchedRanges: inout [Range<String.Index>]
    ) -> (start: TimeOfDay, end: TimeOfDay?)? {
        if let match = firstMatch(
            in: text,
            pattern: #"(오전|오후|아침|저녁|밤)?\s*(\d{1,2})(?::(\d{2})|시(?:\s*(\d{1,2})분)?)\s*(?:부터|-|~)\s*(오전|오후|아침|저녁|밤)?\s*(\d{1,2})(?::(\d{2})|시(?:\s*(\d{1,2})분)?)(?:\s*까지)?"#
        ), let startHour = match.intValue(at: 2),
           let endHour = match.intValue(at: 6),
           let start = timeOfDay(
            marker: match.value(at: 1),
            hour: startHour,
            minute: match.intValue(at: 3) ?? match.intValue(at: 4) ?? 0,
            fullText: text
           ),
           let end = timeOfDay(
            marker: match.value(at: 5) ?? match.value(at: 1),
            hour: endHour,
            minute: match.intValue(at: 7) ?? match.intValue(at: 8) ?? 0,
            fullText: text
           ) {
            matchedRanges.append(match.range)
            return (start, end)
        }

        if let match = firstMatch(
            in: text,
            pattern: #"(오전|오후|아침|저녁|밤)?\s*(\d{1,2})(?::(\d{2})|시(?:\s*(\d{1,2})분)?)"#
        ), let hour = match.intValue(at: 2),
           let start = timeOfDay(
            marker: match.value(at: 1),
            hour: hour,
            minute: match.intValue(at: 3) ?? match.intValue(at: 4) ?? 0,
            fullText: text
           ) {
            matchedRanges.append(match.range)
            if match.value(at: 1) == nil {
                appendContextualTimeMarkerRange(in: text, matchedRanges: &matchedRanges)
            }
            return (start, nil)
        }

        return nil
    }

    private func appendContextualTimeMarkerRange(
        in text: String,
        matchedRanges: inout [Range<String.Index>]
    ) {
        for marker in ["저녁", "밤", "아침"] {
            if let range = text.range(of: marker) {
                matchedRanges.append(range)
                return
            }
        }
    }

    private func timeOfDay(
        marker: String?,
        hour: Int,
        minute: Int,
        fullText: String
    ) -> TimeOfDay? {
        guard (0...23).contains(hour), (0...59).contains(minute) else {
            return nil
        }

        var resolvedHour = hour

        if let marker {
            switch marker {
            case "오후", "저녁", "밤":
                if resolvedHour < 12 {
                    resolvedHour += 12
                }
            case "오전", "아침":
                if resolvedHour == 12 {
                    resolvedHour = 0
                }
            default:
                break
            }
        } else if fullText.contains("저녁") || fullText.contains("밤") {
            if resolvedHour < 12 {
                resolvedHour += 12
            }
        } else if fullText.contains("아침") {
            if resolvedHour == 12 {
                resolvedHour = 0
            }
        }

        return TimeOfDay(hour: resolvedHour, minute: minute)
    }

    private func makeStartAt(
        date: Date?,
        time: TimeOfDay?,
        referenceDate: Date
    ) -> Date? {
        guard date != nil || time != nil else {
            return nil
        }

        let baseDate = date ?? calendar.startOfDay(for: referenceDate)
        let referenceComponents = calendar.dateComponents([.hour, .minute], from: referenceDate)
        let resolvedTime = time ?? TimeOfDay(
            hour: referenceComponents.hour ?? 9,
            minute: referenceComponents.minute ?? 0
        )

        guard let startAt = calendar.date(
            bySettingHour: resolvedTime.hour,
            minute: resolvedTime.minute,
            second: 0,
            of: baseDate
        ) else {
            return nil
        }

        if date == nil, startAt <= referenceDate {
            return calendar.date(byAdding: .day, value: 1, to: startAt)
        }

        return startAt
    }

    private func makeEndAt(
        startAt: Date?,
        endTime: TimeOfDay?,
        referenceDate: Date
    ) -> Date? {
        guard let startAt else {
            return nil
        }

        guard let endTime else {
            return calendar.date(byAdding: .hour, value: 1, to: startAt)
        }

        var endAt = calendar.date(
            bySettingHour: endTime.hour,
            minute: endTime.minute,
            second: 0,
            of: startAt
        )

        if let currentEndAt = endAt, currentEndAt <= startAt {
            endAt = calendar.date(byAdding: .day, value: 1, to: currentEndAt)
        }

        return endAt ?? calendar.date(byAdding: .hour, value: 1, to: referenceDate)
    }

    private func cleanedTitle(
        from text: String,
        removing ranges: [Range<String.Index>]
    ) -> String {
        var cleanedText = text

        for range in ranges.sorted(by: { $0.lowerBound > $1.lowerBound }) {
            cleanedText.removeSubrange(range)
        }

        return cleanedText
            .split(whereSeparator: { $0.isWhitespace })
            .joined(separator: " ")
    }

    private func date(month: Int, day: Int, referenceDate: Date) -> Date? {
        guard (1...12).contains(month), (1...31).contains(day) else {
            return nil
        }

        let referenceYear = calendar.component(.year, from: referenceDate)
        var components = DateComponents()
        components.year = referenceYear
        components.month = month
        components.day = day

        guard let candidate = calendar.date(from: components) else {
            return nil
        }

        if candidate < calendar.startOfDay(for: referenceDate) {
            components.year = referenceYear + 1
            return calendar.date(from: components)
        }

        return candidate
    }

    private func date(day: Int, referenceDate: Date) -> Date? {
        guard (1...31).contains(day) else {
            return nil
        }

        var components = calendar.dateComponents([.year, .month], from: referenceDate)
        components.day = day

        guard let candidate = calendar.date(from: components) else {
            return nil
        }

        if candidate < calendar.startOfDay(for: referenceDate) {
            return calendar.date(byAdding: .month, value: 1, to: candidate)
        }

        return candidate
    }

    private func nearestFutureWeekday(containing date: Date, weekdayIndex: Int) -> Date? {
        let currentWeekday = calendar.component(.weekday, from: date)
        let currentIndex = weekdayIndexFromCalendarWeekday(currentWeekday)
        let dayOffset = (weekdayIndex - currentIndex + 7) % 7

        return calendar.date(byAdding: .day, value: dayOffset, to: date)
    }

    private func dateInWeek(
        containing date: Date,
        weekOffset: Int,
        weekdayIndex: Int
    ) -> Date? {
        guard let weekStart = mondayStartOfWeek(containing: date),
              let targetWeekStart = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: weekStart) else {
            return nil
        }

        return calendar.date(byAdding: .day, value: weekdayIndex, to: targetWeekStart)
    }

    private func mondayStartOfWeek(containing date: Date) -> Date? {
        let startOfDay = calendar.startOfDay(for: date)
        let weekday = calendar.component(.weekday, from: startOfDay)
        let daysFromMonday = (weekday + 5) % 7

        return calendar.date(byAdding: .day, value: -daysFromMonday, to: startOfDay)
    }

    private func koreanWeekdayIndex(_ text: String) -> Int? {
        switch text {
        case "월":
            return 0
        case "화":
            return 1
        case "수":
            return 2
        case "목":
            return 3
        case "금":
            return 4
        case "토":
            return 5
        case "일":
            return 6
        default:
            return nil
        }
    }

    private func weekdayIndexFromCalendarWeekday(_ weekday: Int) -> Int {
        (weekday + 5) % 7
    }

    private func firstMatch(
        in text: String,
        pattern: String
    ) -> RegexMatch? {
        guard let regex = try? NSRegularExpression(pattern: pattern) else {
            return nil
        }

        let nsRange = NSRange(text.startIndex..<text.endIndex, in: text)
        guard let match = regex.firstMatch(in: text, range: nsRange),
              let range = Range(match.range, in: text) else {
            return nil
        }

        return RegexMatch(text: text, match: match, range: range)
    }
}

private struct TimeOfDay: Equatable {
    let hour: Int
    let minute: Int
}

private struct RegexMatch {
    let text: String
    let match: NSTextCheckingResult
    let range: Range<String.Index>

    func value(at index: Int) -> String? {
        guard index < match.numberOfRanges,
              match.range(at: index).location != NSNotFound,
              let range = Range(match.range(at: index), in: text) else {
            return nil
        }

        return String(text[range])
    }

    func intValue(at index: Int) -> Int? {
        value(at: index).flatMap(Int.init)
    }
}
