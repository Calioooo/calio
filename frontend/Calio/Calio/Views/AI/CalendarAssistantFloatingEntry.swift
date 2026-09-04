import SwiftUI

struct CalendarAssistantFloatingEntry: ViewModifier {
    @Binding var isPresented: Bool
    let onCalendarRefreshNeeded: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .overlay {
                GeometryReader { proxy in
                    ZStack(alignment: .bottomTrailing) {
                        if isPresented {
                            Color.black.opacity(0.06)
                                .ignoresSafeArea()
                                .contentShape(Rectangle())
                                .onTapGesture { isPresented = false }

                        }

                        CalendarAssistantConversationView(
                            isPresented: isPresented,
                            onCalendarRefreshNeeded: onCalendarRefreshNeeded,
                            onDismiss: { isPresented = false }
                        )
                        .frame(
                            width: min(max(proxy.size.width - 32, 280), 380),
                            height: min(max(proxy.size.height * 0.64, 360), 520)
                        )
                        .background(Color.calioBackground, in: RoundedRectangle(cornerRadius: 24))
                        .clipShape(RoundedRectangle(cornerRadius: 24))
                        .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.calioDivider, lineWidth: 1))
                        .shadow(color: .black.opacity(0.22), radius: 18, y: 8)
                        .padding(.trailing, 16)
                        .padding(.bottom, 84)
                        .opacity(isPresented ? 1 : 0)
                        .scaleEffect(isPresented ? 1 : 0.94, anchor: .bottomTrailing)
                        .allowsHitTesting(isPresented)
                        .accessibilityHidden(!isPresented)

                    }
                    .animation(modalAnimation, value: isPresented)
                }
            }
            .overlay(alignment: .bottomTrailing) {
                Button { isPresented.toggle() } label: {
                    Image(systemName: isPresented ? "chevron.down" : "sparkles")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.white)
                        .frame(width: 54, height: 54)
                        .background(Color.calioBrand, in: Circle())
                        .overlay(Circle().stroke(Color.calioSurface, lineWidth: 3))
                        .shadow(color: .black.opacity(0.18), radius: 8, y: 4)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(isPresented ? "AI 캘린더 도우미 창 접기" : "AI 캘린더 도우미")
                .accessibilityHint(isPresented ? "대화를 유지한 채 창을 접습니다" : "AI와 대화로 일정을 조회하거나 변경을 제안받습니다")
                .accessibilityIdentifier("calendar_floating_ai_assistant")
                .padding(.trailing, 20)
                .padding(.bottom, 20)
            }
    }

    private var modalAnimation: Animation { reduceMotion ? .easeOut(duration: 0.12) : .spring(response: 0.32, dampingFraction: 0.86) }
}

extension View {
    func calendarAssistantFloatingEntry(
        isPresented: Binding<Bool>,
        onCalendarRefreshNeeded: @escaping () -> Void
    ) -> some View {
        modifier(CalendarAssistantFloatingEntry(
            isPresented: isPresented,
            onCalendarRefreshNeeded: onCalendarRefreshNeeded
        ))
    }
}
