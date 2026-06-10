//
//  CalendarDateStripView.swift
//  Calio
//
//  Created by 김준하 on 6/6/26.
//

import SwiftUI
import UIKit

struct CalendarDateStripView: View {
    let items: [CalendarDateCellItem]
    let focusedDay: DayKey
    let onFocusedDayChanged: (DayKey) -> Void
    var targetOffset: CalendarScrollTarget?
    var onScrollProgressChanged: (CGFloat, CalendarVisibleIndexRange) -> Void = { _, _ in }
    var onScrollEnded: (CGFloat) -> Void = { _ in }
    var onCellWidthChanged: (CGFloat) -> Void = { _ in }
    
    var body: some View {
        GeometryReader { geometry in
            let cellWidth = CalendarScrollMetrics.stripCellWidth(
                containerWidth: geometry.size.width
            )
            
            CalendarOffsetScrollView(
                axis: .horizontal,
                showsIndicators: false,
                targetOffset: targetOffset,
                onUserOffsetChanged: { contentOffset, viewportLength in
                    reportScrollProgress(
                        contentOffset: contentOffset,
                        viewportLength: viewportLength,
                        cellWidth: cellWidth
                    )
                },
                onUserScrollEnded: { contentOffset, _ in
                    let progress = CalendarScrollMetrics.progress(
                        contentOffset: contentOffset,
                        itemExtent: cellWidth
                    )
                    onScrollEnded(progress)
                }
            ) {
                LazyHStack(spacing: 0) {
                    ForEach(items) { item in
                        CalendarDateCellView(
                            weekday: item.weekday,
                            dayText: item.dayText,
                            isToday: item.isToday,
                            onTap: {
                                reportUserFocusedDay(item.id)
                            },
                            events: item.events
                        )
                        .frame(width: cellWidth)
                        .id(item.id)
                    }

                    Color.clear
                        .frame(width: cellWidth * CGFloat(CalendarScrollMetrics.stripVisibleCellCount - 1))
                }
            }
            .onAppear {
                onCellWidthChanged(cellWidth)
            }
            .onChange(of: cellWidth) { _, newCellWidth in
                onCellWidthChanged(newCellWidth)
            }
        }
    }
    
    private func reportScrollProgress(
        contentOffset: CGFloat,
        viewportLength: CGFloat,
        cellWidth: CGFloat
    ) {
        let progress = CalendarScrollMetrics.progress(
            contentOffset: contentOffset,
            itemExtent: cellWidth
        )

        guard let visibleRange = CalendarScrollMetrics.visibleIndexRange(
            contentOffset: contentOffset,
            viewportLength: viewportLength,
            itemExtent: cellWidth,
            itemCount: items.count
        ) else {
            return
        }

        onScrollProgressChanged(progress, visibleRange)
    }

    private func reportUserFocusedDay(_ day: DayKey) {
        onFocusedDayChanged(day)
    }
}

enum CalendarOffsetScrollAxis: Equatable {
    case horizontal
    case vertical
}

struct CalendarOffsetScrollView<Content: View>: UIViewRepresentable {
    let axis: CalendarOffsetScrollAxis
    let showsIndicators: Bool
    let targetOffset: CalendarScrollTarget?
    let onUserOffsetChanged: (CGFloat, CGFloat) -> Void
    let onUserScrollEnded: (CGFloat, CGFloat) -> Void
    @ViewBuilder let content: () -> Content

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> UIScrollView {
        let scrollView = UIScrollView()
        let hostingController = UIHostingController(rootView: content())

        context.coordinator.hostingController = hostingController
        scrollView.delegate = context.coordinator
        scrollView.alwaysBounceHorizontal = axis == .horizontal
        scrollView.alwaysBounceVertical = axis == .vertical
        scrollView.showsHorizontalScrollIndicator = showsIndicators && axis == .horizontal
        scrollView.showsVerticalScrollIndicator = showsIndicators && axis == .vertical
        scrollView.contentInsetAdjustmentBehavior = .never
        scrollView.backgroundColor = .clear

        hostingController.view.backgroundColor = .clear
        hostingController.view.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(hostingController.view)
        NSLayoutConstraint.activate(contentConstraints(
            for: hostingController.view,
            in: scrollView
        ))

        return scrollView
    }

    func updateUIView(_ scrollView: UIScrollView, context: Context) {
        context.coordinator.axis = axis
        context.coordinator.onUserOffsetChanged = onUserOffsetChanged
        context.coordinator.onUserScrollEnded = onUserScrollEnded
        context.coordinator.hostingController?.rootView = content()
        context.coordinator.applyTargetOffsetIfNeeded(targetOffset, to: scrollView)
    }

    private func contentConstraints(
        for contentView: UIView,
        in scrollView: UIScrollView
    ) -> [NSLayoutConstraint] {
        switch axis {
        case .horizontal:
            return [
                contentView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
                contentView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
                contentView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
                contentView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
                contentView.heightAnchor.constraint(equalTo: scrollView.frameLayoutGuide.heightAnchor)
            ]

        case .vertical:
            return [
                contentView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
                contentView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
                contentView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
                contentView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
                contentView.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor)
            ]
        }
    }

    final class Coordinator: NSObject, UIScrollViewDelegate {
        var axis: CalendarOffsetScrollAxis = .vertical
        var onUserOffsetChanged: (CGFloat, CGFloat) -> Void = { _, _ in }
        var onUserScrollEnded: (CGFloat, CGFloat) -> Void = { _, _ in }
        var hostingController: UIHostingController<Content>?
        private var appliedTargetID: UUID?
        private var isApplyingProgrammaticOffset = false

        func applyTargetOffsetIfNeeded(
            _ targetOffset: CalendarScrollTarget?,
            to scrollView: UIScrollView
        ) {
            guard let targetOffset, appliedTargetID != targetOffset.id else {
                return
            }

            scrollView.layoutIfNeeded()
            appliedTargetID = targetOffset.id
            isApplyingProgrammaticOffset = true
            scrollView.setContentOffset(
                point(for: clampedOffset(targetOffset.offset, in: scrollView)),
                animated: targetOffset.animated
            )

            guard !targetOffset.animated else {
                return
            }

            DispatchQueue.main.async {
                self.isApplyingProgrammaticOffset = false
            }
        }

        func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
            isApplyingProgrammaticOffset = false
        }

        func scrollViewDidScroll(_ scrollView: UIScrollView) {
            guard !isApplyingProgrammaticOffset, scrollView.isTracking || scrollView.isDragging || scrollView.isDecelerating else {
                return
            }

            onUserOffsetChanged(offset(from: scrollView), viewportLength(from: scrollView))
        }

        func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
            guard !decelerate else {
                return
            }

            reportScrollEnded(scrollView)
        }

        func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
            reportScrollEnded(scrollView)
        }

        func scrollViewDidEndScrollingAnimation(_ scrollView: UIScrollView) {
            isApplyingProgrammaticOffset = false
        }

        private func reportScrollEnded(_ scrollView: UIScrollView) {
            guard !isApplyingProgrammaticOffset else {
                return
            }

            onUserScrollEnded(offset(from: scrollView), viewportLength(from: scrollView))
        }

        private func point(for offset: CGFloat) -> CGPoint {
            switch axis {
            case .horizontal:
                return CGPoint(x: offset, y: 0)

            case .vertical:
                return CGPoint(x: 0, y: offset)
            }
        }

        private func offset(from scrollView: UIScrollView) -> CGFloat {
            switch axis {
            case .horizontal:
                return scrollView.contentOffset.x

            case .vertical:
                return scrollView.contentOffset.y
            }
        }

        private func viewportLength(from scrollView: UIScrollView) -> CGFloat {
            switch axis {
            case .horizontal:
                return scrollView.bounds.width

            case .vertical:
                return scrollView.bounds.height
            }
        }

        private func clampedOffset(_ offset: CGFloat, in scrollView: UIScrollView) -> CGFloat {
            let maximumOffset: CGFloat

            switch axis {
            case .horizontal:
                maximumOffset = max(scrollView.contentSize.width - scrollView.bounds.width, 0)

            case .vertical:
                maximumOffset = max(scrollView.contentSize.height - scrollView.bounds.height, 0)
            }

            return min(max(offset, 0), maximumOffset)
        }
    }
}

#Preview("iPhone") {
    let calendar = Calendar.current
    let dateService = CalendarDateService(calendar: calendar)
    let today = Date()
    
    let makeEvent: (Int64, String, Date, Int, String) -> Event = { id, title, date, hour, colorCode in
        let startOfDay = calendar.startOfDay(for: date)
        
        let startAt = calendar.date(
            bySettingHour: hour,
            minute: 0,
            second: 0,
            of: startOfDay
        ) ?? startOfDay
        
        let endAt = calendar.date(
            byAdding: .hour,
            value: 1,
            to: startAt
        ) ?? startAt
        
        return Event(
            id: id,
            title: title,
            description: "",
            startAt: startAt,
            endAt: endAt,
            colorCode: colorCode
        )
    }
    
    let items: [CalendarDateCellItem] = (0..<7).map { offset in
        let date = calendar.date(
            byAdding: .day,
            value: offset,
            to: today
        ) ?? today
        
        let day = DayKey(date: date, calendar: calendar)
        
        let events: [Event] = switch offset {
        case 0:
            [
                makeEvent(1, "팀 미팅", date, 9, "#4F46E5"),
                makeEvent(2, "제품 리뷰", date, 13, "#059669")
            ]
            
        case 1:
            [
                makeEvent(3, "1:1", date, 10, "#DC2626")
            ]
            
        case 2:
            [
                makeEvent(4, "운동", date, 8, "#D97706"),
                makeEvent(5, "API 정리", date, 15, "#0891B2"),
                makeEvent(6, "저녁 약속", date, 19, "#4F46E5")
            ]
            
        case 4:
            [
                makeEvent(7, "디자인 리뷰", date, 14, "#059669"),
                makeEvent(8, "회고", date, 16, "#DC2626"),
                makeEvent(9, "문서 정리", date, 17, "#D97706"),
                makeEvent(10, "개인 일정", date, 20, "#0891B2"),
                makeEvent(11, "추가 일정", date, 21, "#4F46E5")
            ]
            
        default:
            []
        }
        
        return CalendarDateCellItem(
            id: day,
            weekday: dateService.getWeekday(from: date),
            monthText: dateService.monthText(from: date),
            dayText: dateService.dayText(from: date),
            isToday: calendar.isDateInToday(date),
            isSelected: offset == 0,
            events: events
        )
    }
    
    CalendarDateStripView(
        items: items,
        focusedDay: items[0].id,
        onFocusedDayChanged: { _ in }
    )
    .frame(height: 110)
}
