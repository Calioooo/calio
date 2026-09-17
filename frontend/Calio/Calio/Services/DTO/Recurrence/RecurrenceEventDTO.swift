import Foundation

struct CreateRecurrenceEventRequestDTO: Encodable, Equatable {
    let title: String
    let description: String?
    let allDay: Bool
    let firstOccurrenceStartAt: Date
    let firstOccurrenceEndAt: Date
    let timeZone: String?
    let recurrence: [String]
    let tagId: Int64?

    init(title: String, description: String?, allDay: Bool, firstOccurrenceStartAt: Date, firstOccurrenceEndAt: Date, timeZone: String?, recurrence: [String], tagId: Int64?) {
        self.title = title
        self.description = description
        self.allDay = allDay
        self.firstOccurrenceStartAt = firstOccurrenceStartAt
        self.firstOccurrenceEndAt = firstOccurrenceEndAt
        self.timeZone = timeZone
        self.recurrence = recurrence
        self.tagId = tagId
    }

    init(recurrenceTitle: String, recurrenceDescription: String?, recurrenceStartDate: String, recurrenceEndDate: String, recurrenceStartTime: String, recurrenceEndTime: String, recurrenceFrequency: RecurrenceFrequency, tagId: Int64? = nil) {
        self.init(
            title: recurrenceTitle,
            description: recurrenceDescription,
            allDay: false,
            firstOccurrenceStartAt: Self.legacyDate(recurrenceStartDate, recurrenceStartTime),
            firstOccurrenceEndAt: Self.legacyDate(recurrenceStartDate, recurrenceEndTime),
            timeZone: "UTC",
            recurrence: ["RRULE:FREQ=\(recurrenceFrequency.rawValue);UNTIL=\(recurrenceEndDate.replacingOccurrences(of: "-", with: ""))T000000Z"],
            tagId: tagId
        )
    }

    var recurrenceTitle: String { title }
    var recurrenceDescription: String? { description }
    var recurrenceStartDate: String { Self.legacyDateString(firstOccurrenceStartAt) }
    var recurrenceEndDate: String { Self.legacyUntilDate(in: recurrence) ?? recurrenceStartDate }
    var recurrenceStartTime: String { Self.legacyTimeString(firstOccurrenceStartAt) }
    var recurrenceEndTime: String { allDay ? "23:59:59" : Self.legacyTimeString(firstOccurrenceEndAt) }
    var recurrenceFrequency: RecurrenceFrequency { Self.legacyFrequency(in: recurrence) ?? .daily }
}

struct UpdateRecurrenceEventRequestDTO: Encodable, Equatable {
    let title: String
    let description: String?
    let allDay: Bool
    let firstOccurrenceStartAt: Date
    let firstOccurrenceEndAt: Date
    let timeZone: String?
    let recurrence: [String]
    let tagId: Int64?

    init(title: String, description: String?, allDay: Bool, firstOccurrenceStartAt: Date, firstOccurrenceEndAt: Date, timeZone: String?, recurrence: [String], tagId: Int64?) {
        self.title = title
        self.description = description
        self.allDay = allDay
        self.firstOccurrenceStartAt = firstOccurrenceStartAt
        self.firstOccurrenceEndAt = firstOccurrenceEndAt
        self.timeZone = timeZone
        self.recurrence = recurrence
        self.tagId = tagId
    }

    init(title: String, description: String?, startDate: String, endDate: String, startTime: String, endTime: String, recurrenceFrequency: RecurrenceFrequency, tagId: Int64? = nil) {
        self.init(
            title: title,
            description: description,
            allDay: false,
            firstOccurrenceStartAt: CreateRecurrenceEventRequestDTO.legacyDate(startDate, startTime),
            firstOccurrenceEndAt: CreateRecurrenceEventRequestDTO.legacyDate(startDate, endTime),
            timeZone: "UTC",
            recurrence: ["RRULE:FREQ=\(recurrenceFrequency.rawValue);UNTIL=\(endDate.replacingOccurrences(of: "-", with: ""))T000000Z"],
            tagId: tagId
        )
    }

    var startDate: String { CreateRecurrenceEventRequestDTO.legacyDateString(firstOccurrenceStartAt) }
    var endDate: String { CreateRecurrenceEventRequestDTO.legacyUntilDate(in: recurrence) ?? startDate }
    var startTime: String { CreateRecurrenceEventRequestDTO.legacyTimeString(firstOccurrenceStartAt) }
    var endTime: String { allDay ? "23:59:59" : CreateRecurrenceEventRequestDTO.legacyTimeString(firstOccurrenceEndAt) }
    var recurrenceFrequency: RecurrenceFrequency { CreateRecurrenceEventRequestDTO.legacyFrequency(in: recurrence) ?? .daily }
}

struct UpdateRecurrenceOccurrenceRequestDTO: Encodable, Equatable {
    let originStartAt: Date
    let title: String
    let description: String?
    let startAt: Date
    let endAt: Date
    let allDay: Bool
    let timeZone: String?

    init(originStartAt: Date, title: String, description: String?, startAt: Date, endAt: Date, allDay: Bool, timeZone: String?) {
        self.originStartAt = originStartAt
        self.title = title
        self.description = description
        self.startAt = startAt
        self.endAt = endAt
        self.allDay = allDay
        self.timeZone = timeZone
    }

    init(originStartAt: Date, startAt: Date, endAt: Date) {
        self.init(originStartAt: originStartAt, title: "", description: nil, startAt: startAt, endAt: endAt, allDay: false, timeZone: "UTC")
    }
}

struct RecurrenceEventResponseDTO: Decodable, Equatable {
    let recurrenceId: Int64
    let title: String
    let description: String?
    let allDay: Bool
    let firstOccurrenceStartAt: Date
    let firstOccurrenceEndAt: Date
    let timeZone: String?
    let recurrence: [String]
    let tag: TagResponseDTO
    let createdAt: Date
    let updatedAt: Date
    let canUpdateSeries: Bool

    init(
        recurrenceId: Int64,
        title: String,
        description: String?,
        allDay: Bool,
        firstOccurrenceStartAt: Date,
        firstOccurrenceEndAt: Date,
        timeZone: String?,
        recurrence: [String],
        tag: TagResponseDTO,
        createdAt: Date,
        updatedAt: Date,
        canUpdateSeries: Bool
    ) {
        self.recurrenceId = recurrenceId
        self.title = title
        self.description = description
        self.allDay = allDay
        self.firstOccurrenceStartAt = firstOccurrenceStartAt
        self.firstOccurrenceEndAt = firstOccurrenceEndAt
        self.timeZone = timeZone
        self.recurrence = recurrence
        self.tag = tag
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.canUpdateSeries = canUpdateSeries
    }

    init(recurrenceId: Int64, recurrenceTitle: String, recurrenceDescription: String?, recurrenceStartDate: String, recurrenceEndDate: String, recurrenceStartTime: String, recurrenceEndTime: String, recurrenceFrequency: RecurrenceFrequency, tag: TagResponseDTO = TagResponseDTO(id: 0, title: "기타", colorCode: "#64748B", tagType: .defaultTag)) {
        self.init(
            recurrenceId: recurrenceId,
            title: recurrenceTitle,
            description: recurrenceDescription,
            allDay: recurrenceStartTime == "00:00:00" && recurrenceEndTime == "23:59:59",
            firstOccurrenceStartAt: CreateRecurrenceEventRequestDTO.legacyDate(recurrenceStartDate, recurrenceStartTime),
            firstOccurrenceEndAt: CreateRecurrenceEventRequestDTO.legacyDate(recurrenceStartDate, recurrenceEndTime),
            timeZone: "UTC",
            recurrence: [
                "RRULE:FREQ=\(recurrenceFrequency.rawValue);UNTIL=\(recurrenceEndDate.replacingOccurrences(of: "-", with: ""))\(recurrenceStartTime == "00:00:00" && recurrenceEndTime == "23:59:59" ? "" : "T000000Z")"
            ],
            tag: tag,
            createdAt: Date(timeIntervalSince1970: 0),
            updatedAt: Date(timeIntervalSince1970: 0),
            canUpdateSeries: true
        )
    }
}

private extension CreateRecurrenceEventRequestDTO {
    static func legacyDate(_ date: String, _ time: String) -> Date {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return formatter.date(from: "\(date) \(time)") ?? Date(timeIntervalSince1970: 0)
    }

    static func legacyDateString(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }

    static func legacyTimeString(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "HH:mm:ss"
        return formatter.string(from: date)
    }

    static func legacyUntilDate(in recurrence: [String]) -> String? {
        guard let until = recurrence
            .first?
            .split(separator: ";")
            .first(where: { $0.hasPrefix("UNTIL=") })?
            .dropFirst("UNTIL=".count),
              until.count >= 8 else {
            return nil
        }
        let date = until.prefix(8)
        return "\(date.prefix(4))-\(date.dropFirst(4).prefix(2))-\(date.dropFirst(6).prefix(2))"
    }

    static func legacyFrequency(in recurrence: [String]) -> RecurrenceFrequency? {
        guard let frequency = recurrence
            .first?
            .split(separator: ";")
            .first(where: { $0.hasPrefix("RRULE:FREQ=") })?
            .dropFirst("RRULE:FREQ=".count) else {
            return nil
        }
        return RecurrenceFrequency(rawValue: String(frequency))
    }
}
