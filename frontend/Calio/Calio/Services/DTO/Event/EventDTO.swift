import Foundation

struct CreateEventRequestDTO: Encodable {
    let title: String
    let description: String?
    let startAt: Date
    let endAt: Date
    let allDay: Bool
    let timeZone: String?
    let tagId: Int64?

    init(
        title: String,
        description: String?,
        startAt: Date,
        endAt: Date,
        allDay: Bool,
        timeZone: String?,
        tagId: Int64? = nil
    ) {
        self.title = title
        self.description = description
        self.startAt = startAt
        self.endAt = endAt
        self.allDay = allDay
        self.timeZone = timeZone
        self.tagId = tagId
    }
}

struct UpdateEventRequestDTO: Encodable, Equatable {
    let title: String
    let description: String?
    let startAt: Date
    let endAt: Date
    let allDay: Bool
    let timeZone: String?
    let tagId: Int64?

    init(
        title: String,
        description: String?,
        startAt: Date,
        endAt: Date,
        allDay: Bool,
        timeZone: String?,
        tagId: Int64? = nil
    ) {
        self.title = title
        self.description = description
        self.startAt = startAt
        self.endAt = endAt
        self.allDay = allDay
        self.timeZone = timeZone
        self.tagId = tagId
    }
}

struct EventResponseDTO: Decodable {
    let id: Int64?
    let title: String
    let description: String?
    let startAt: Date
    let endAt: Date
    let allDay: Bool
    let timeZone: String?
    let importantEvent: Bool
    let recurrenceId: Int64?
    let isRecurrenceOccurrence: Bool
    let originStartAt: Date?
    let tag: TagResponseDTO
    let createdAt: Date
    let updatedAt: Date

    init(
        id: Int64?,
        title: String,
        description: String?,
        startAt: Date,
        endAt: Date,
        allDay: Bool = false,
        timeZone: String? = nil,
        importantEvent: Bool = false,
        recurrenceId: Int64? = nil,
        isRecurrenceOccurrence: Bool = false,
        originStartAt: Date? = nil,
        tag: TagResponseDTO = TagResponseDTO(id: 0, title: "기타", colorCode: "#64748B", tagType: .defaultTag),
        createdAt: Date,
        updatedAt: Date
    ) {
        self.id = id
        self.title = title
        self.description = description
        self.startAt = startAt
        self.endAt = endAt
        self.allDay = allDay
        self.timeZone = timeZone
        self.importantEvent = importantEvent
        self.recurrenceId = recurrenceId
        self.isRecurrenceOccurrence = isRecurrenceOccurrence
        self.originStartAt = originStartAt
        self.tag = tag
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    private enum CodingKeys: String, CodingKey {
        case id, title, description, startAt, endAt, allDay, timeZone, importantEvent, recurrenceId
        case isRecurrenceOccurrence, originStartAt, tag, createdAt, updatedAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(Int64.self, forKey: .id)
        title = try container.decode(String.self, forKey: .title)
        description = try container.decodeIfPresent(String.self, forKey: .description)
        startAt = try container.decode(Date.self, forKey: .startAt)
        endAt = try container.decode(Date.self, forKey: .endAt)
        allDay = try container.decode(Bool.self, forKey: .allDay)
        timeZone = try container.decodeIfPresent(String.self, forKey: .timeZone)
        importantEvent = try container.decodeIfPresent(Bool.self, forKey: .importantEvent) ?? false
        recurrenceId = try container.decodeIfPresent(Int64.self, forKey: .recurrenceId)
        isRecurrenceOccurrence = try container.decodeIfPresent(Bool.self, forKey: .isRecurrenceOccurrence) ?? false
        originStartAt = try container.decodeIfPresent(Date.self, forKey: .originStartAt)
        tag = try container.decode(TagResponseDTO.self, forKey: .tag)
        createdAt = try container.decode(Date.self, forKey: .createdAt)
        updatedAt = try container.decode(Date.self, forKey: .updatedAt)
    }
}
