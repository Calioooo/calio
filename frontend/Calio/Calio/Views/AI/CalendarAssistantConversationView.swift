import SwiftUI

struct CalendarAssistantConversationView: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var viewModel: CalendarAssistantConversationViewModel
    @State private var draft = ""
    private let isPresented: Bool
    private let onDismiss: () -> Void

    init(
        isPresented: Bool,
        onCalendarRefreshNeeded: @escaping () -> Void = {},
        onDismiss: @escaping () -> Void = {}
    ) {
        _viewModel = StateObject(wrappedValue: CalendarAssistantConversationViewModel(onCalendarRefreshNeeded: onCalendarRefreshNeeded))
        self.isPresented = isPresented
        self.onDismiss = onDismiss
    }

    var body: some View {
        NavigationStack {
            Group {
                switch viewModel.state {
                case .connecting:
                    ZStack {
                        Color.calioBackground.ignoresSafeArea()
                        ProgressView("AI 캘린더를 준비하는 중")
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .accessibilityIdentifier("calendar_assistant_connecting")
                case .failed(let failure):
                    VStack(spacing: 14) {
                        ContentUnavailableView("대화를 준비하지 못했어요", systemImage: "sparkles", description: Text(failure.message))
                        Button("다시 시도") { Task { await viewModel.retryConnection() } }.buttonStyle(.borderedProminent).tint(.calioBrand)
                    }.accessibilityIdentifier("calendar_assistant_connection_failed")
                case .ready:
                    conversation
                }
            }
            .background(Color.calioBackground.ignoresSafeArea())
            .navigationTitle("AI 캘린더")
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Color.calioBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { closeSession() } } }
            .task(id: isPresented) {
                guard isPresented else { return }
                await viewModel.start()
            }
        }
    }

    private func closeSession() {
        draft = ""
        viewModel.endSession()
        onDismiss()
    }

    private var conversation: some View {
        VStack(spacing: 0) {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 16) {
                    if viewModel.messages.isEmpty { emptyConversation }
                    ForEach(viewModel.messages) { messageView($0) }
                }
                .padding(.horizontal, 20).padding(.vertical, 16)
                .animation(messageAnimation, value: viewModel.messages.map(\.id))
            }
            if let failure = viewModel.messageFailure {
                HStack(spacing: 10) {
                    Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
                    Text(failure.message).font(.footnote).foregroundStyle(.calioTextSecondary)
                    Spacer()
                    Button("다시 보내기") { Task { await viewModel.retryMessageSend() } }.font(.footnote.weight(.semibold))
                }.padding(12).background(Color.calioSelection, in: RoundedRectangle(cornerRadius: 14)).padding(.horizontal, 16).padding(.bottom, 8).accessibilityIdentifier("calendar_assistant_message_failed")
            }
            composer
        }
    }

    private var emptyConversation: some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: "sparkles").font(.title2.weight(.semibold)).foregroundStyle(.calioPrimary).frame(width: 44, height: 44).background(Color.calioSelection, in: Circle())
            Text("무엇을 도와드릴까요?").font(.headline).foregroundStyle(.calioTextPrimary)
            Text("일정을 찾거나, 빈 시간을 확인하고 일정 변경을 제안받아 보세요.").font(.subheadline).foregroundStyle(.calioTextSecondary)
        }.padding(18).background(Color.calioSurface, in: RoundedRectangle(cornerRadius: 18)).overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.calioDivider, lineWidth: 1)).padding(.top, 12)
    }

    private var composer: some View {
        HStack(alignment: .bottom, spacing: 10) {
            TextField("캘린더에 요청하기", text: $draft, axis: .vertical).lineLimit(1...4).padding(.horizontal, 12).padding(.vertical, 10).background(Color.calioBackground, in: RoundedRectangle(cornerRadius: 14)).overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.calioDivider, lineWidth: 1)).accessibilityIdentifier("calendar_assistant_composer")
            Button { let value = draft; draft = ""; Task { await viewModel.send(value) } } label: {
                Image(systemName: viewModel.isSending ? "ellipsis" : "arrow.up").font(.headline.weight(.bold)).frame(width: 42, height: 42).foregroundStyle(.white).background(Color.calioBrand, in: Circle())
            }.disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || viewModel.isSending).accessibilityLabel("메시지 보내기")
        }.padding(.horizontal, 16).padding(.vertical, 12).background(Color.calioSurface).overlay(alignment: .top) { Rectangle().fill(Color.calioDivider).frame(height: 1) }.shadow(color: .black.opacity(0.04), radius: 8, y: -2)
    }

    @ViewBuilder private func messageView(_ message: CalendarAssistantMessage) -> some View {
        VStack(alignment: message.role == .user ? .trailing : .leading, spacing: 10) {
            HStack(spacing: 8) {
                if message.role == .assistant { Image(systemName: "sparkles").font(.caption.weight(.bold)).foregroundStyle(.calioPrimary) }
                Text(message.role == .user ? "나" : "AI 캘린더").font(.caption.weight(.semibold)).foregroundStyle(message.role == .user ? Color.calioTextSecondary : Color.calioPrimary)
            }
            messageText(message).font(.subheadline).foregroundStyle(message.role == .user ? .white : .calioTextPrimary).padding(.horizontal, 14).padding(.vertical, 12).background(message.role == .user ? Color.calioBrand : Color.calioSurface, in: RoundedRectangle(cornerRadius: 18)).overlay { if message.role == .assistant { RoundedRectangle(cornerRadius: 18).stroke(Color.calioDivider, lineWidth: 1) } }.accessibilityLabel(message.role == .user ? "내 메시지: \(message.text)" : "AI 답변: \(message.text)")
            if message.isPending { ProgressView().controlSize(.small).padding(.horizontal, 8) }
            ForEach(Array(message.results.enumerated()), id: \.offset) { _, result in resultView(result) }
        }.frame(maxWidth: .infinity, alignment: message.role == .user ? .trailing : .leading).transition(messageTransition)
    }

    private func messageText(_ message: CalendarAssistantMessage) -> Text {
        message.role == .assistant
            ? Text(CalendarAssistantMarkdown.attributedText(from: message.text))
            : Text(message.text)
    }

    @ViewBuilder private func resultView(_ result: CalendarAssistantResult) -> some View {
        switch result {
        case .events(let events): VStack(alignment: .leading, spacing: 8) { ForEach(events) { Text("\($0.title) · \(CalendarEventDisplayText.compactDateTimeRange(startAt: $0.startAt, endAt: $0.endAt))") } }.resultCard(title: "일정", icon: "calendar", label: "일정 결과")
        case .freeTimes(let times): VStack(alignment: .leading, spacing: 8) { ForEach(times) { Text("\($0.start) ~ \($0.end)"); ForEach($0.allDayNotices, id: \.self) { Text($0).font(.caption).foregroundStyle(.calioTextSecondary) } } }.resultCard(title: "빈 시간", icon: "clock", label: "빈 시간 결과")
        case .mutationPreviews(let previews):
            VStack(alignment: .leading, spacing: 14) { ForEach(previews) { mutationPreviewView($0) } }
        case .unsupported: Text("지원되지 않는 AI 결과입니다.").resultCard(title: "지원되지 않는 결과", icon: "exclamationmark.circle", label: "지원되지 않는 결과")
        }
    }

    private func mutationPreviewView(_ preview: CalendarAssistantMutationPreview) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                Label("일정 변경 제안", systemImage: "arrow.triangle.2.circlepath")
                    .font(.caption.weight(.bold)).foregroundStyle(.calioTextPrimary)
                Spacer()
                Text(mutationTypeLabel(preview.type)).font(.caption.weight(.bold)).foregroundStyle(.calioPrimary).padding(.horizontal, 8).padding(.vertical, 4).background(Color.calioSurface, in: Capsule())
            }
            Text(mutationScopeLabel(preview.scope)).font(.caption).foregroundStyle(.calioTextSecondary)
            if let before = preview.before { mutationEventView(before, label: preview.after == nil ? "삭제될 일정" : "변경 전") }
            if preview.before != nil, preview.after != nil { Divider() }
            if let after = preview.after { mutationEventView(after, label: preview.before == nil ? "새 일정" : "변경 후") }
            if let recurrenceBefore = preview.recurrenceBefore, let recurrenceAfter = preview.recurrenceAfter {
                Divider()
                Text("반복 규칙").font(.caption.weight(.bold)).foregroundStyle(.calioTextSecondary)
                Text("변경 전  \(recurrenceBefore.joined(separator: ", "))").font(.caption)
                Text("변경 후  \(recurrenceAfter.joined(separator: ", "))").font(.caption.weight(.medium))
            }
        }
        .padding(14)
        .background(Color.calioSelection, in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.calioDivider.opacity(0.75), lineWidth: 1))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(mutationTypeLabel(preview.type)) 일정 변경 제안, \(mutationScopeLabel(preview.scope))")
    }

    private func mutationEventView(_ event: CalendarAssistantMutationEvent, label: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.caption.weight(.bold)).foregroundStyle(.calioTextSecondary)
            Text(event.title).font(.subheadline.weight(.semibold)).foregroundStyle(.calioTextPrimary)
            Label(event.allDay ? "종일" : CalendarEventDisplayText.compactDateTimeRange(startAt: event.startAt, endAt: event.endAt), systemImage: event.allDay ? "sun.max" : "clock")
                .font(.caption).foregroundStyle(.calioTextSecondary)
            HStack(spacing: 6) {
                Circle().fill(Color(hex: event.tag.colorCode)).frame(width: 8, height: 8)
                Text(event.tag.title).font(.caption.weight(.medium)).foregroundStyle(.calioTextPrimary)
            }
        }
    }

    private func mutationTypeLabel(_ type: String) -> String {
        switch type { case "CREATE": return "생성"; case "UPDATE": return "수정"; case "DELETE": return "삭제"; default: return type }
    }

    private func mutationScopeLabel(_ scope: String) -> String {
        switch scope { case "EVENT": return "이 일정"; case "THIS_OCCURRENCE": return "이번 일정"; case "ENTIRE_SERIES": return "전체 반복 일정"; default: return scope }
    }

    private var messageAnimation: Animation { reduceMotion ? .easeOut(duration: 0.12) : .spring(response: 0.35, dampingFraction: 0.86) }
    private var messageTransition: AnyTransition { reduceMotion ? .opacity : .opacity.combined(with: .move(edge: .bottom)) }
}

private extension View {
    func resultCard(title: String, icon: String, label: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 7) { Image(systemName: icon).foregroundStyle(.calioPrimary); Text(title).font(.caption.weight(.bold)).foregroundStyle(.calioTextPrimary) }
            self
        }.padding(14).background(Color.calioSelection, in: RoundedRectangle(cornerRadius: 16)).overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.calioDivider.opacity(0.75), lineWidth: 1)).accessibilityElement(children: .combine).accessibilityLabel(label)
    }
}
