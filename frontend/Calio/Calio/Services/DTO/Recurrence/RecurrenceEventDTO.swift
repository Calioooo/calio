import Foundation

struct UpdateRecurrenceEventRequestDTO: Encodable, Equatable {
    let title: String
    let description: String?
    let startDate: String
    let endDate: String
    let startTime: String
    let endTime: String
    let recurrenceFrequency: RecurrenceFrequency
    let tagId: Int64?

    init(title: String, description: String?, startDate: String, endDate: String, startTime: String, endTime: String, recurrenceFrequency: RecurrenceFrequency, tagId: Int64? = nil) {
        self.title = title
        self.description = description
        self.startDate = startDate
        self.endDate = endDate
        self.startTime = startTime
        self.endTime = endTime
        self.recurrenceFrequency = recurrenceFrequency
        self.tagId = tagId
    }
}

struct UpdateRecurrenceOccurrenceRequestDTO: Encodable, Equatable {
    let originStartAt: Date
    let startAt: Date
    let endAt: Date
}

struct CreateRecurrenceEventRequestDTO: Encodable, Equatable {
    let recurrenceTitle: String
    let recurrenceDescription: String?
    let recurrenceStartDate: String
    let recurrenceEndDate: String
    let recurrenceStartTime: String
    let recurrenceEndTime: String
    let recurrenceFrequency: RecurrenceFrequency
    let tagId: Int64?

    init(recurrenceTitle: String, recurrenceDescription: String?, recurrenceStartDate: String, recurrenceEndDate: String, recurrenceStartTime: String, recurrenceEndTime: String, recurrenceFrequency: RecurrenceFrequency, tagId: Int64? = nil) {
        self.recurrenceTitle = recurrenceTitle
        self.recurrenceDescription = recurrenceDescription
        self.recurrenceStartDate = recurrenceStartDate
        self.recurrenceEndDate = recurrenceEndDate
        self.recurrenceStartTime = recurrenceStartTime
        self.recurrenceEndTime = recurrenceEndTime
        self.recurrenceFrequency = recurrenceFrequency
        self.tagId = tagId
    }
}

struct RecurrenceEventResponseDTO: Decodable, Equatable {
    let recurrenceId: Int64
    let recurrenceTitle: String
    let recurrenceDescription: String?
    let recurrenceStartDate: String
    let recurrenceEndDate: String
    let recurrenceStartTime: String
    let recurrenceEndTime: String
    let recurrenceFrequency: RecurrenceFrequency
    let tag: TagResponseDTO

    init(recurrenceId: Int64, recurrenceTitle: String, recurrenceDescription: String?, recurrenceStartDate: String, recurrenceEndDate: String, recurrenceStartTime: String, recurrenceEndTime: String, recurrenceFrequency: RecurrenceFrequency, tag: TagResponseDTO = TagResponseDTO(id: 0, title: "기타", colorCode: "#64748B", tagType: .defaultTag)) {
        self.recurrenceId = recurrenceId
        self.recurrenceTitle = recurrenceTitle
        self.recurrenceDescription = recurrenceDescription
        self.recurrenceStartDate = recurrenceStartDate
        self.recurrenceEndDate = recurrenceEndDate
        self.recurrenceStartTime = recurrenceStartTime
        self.recurrenceEndTime = recurrenceEndTime
        self.recurrenceFrequency = recurrenceFrequency
        self.tag = tag
    }
}
