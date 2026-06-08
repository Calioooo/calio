//
//  CalendarHomeView.swift
//  Calio
//
//  Created by 김준하 on 6/6/26.
//

import SwiftUI

struct CalendarHomeView: View {
    @StateObject private var viewModel = CalendarHomeViewModel()
    
    private let minimumStripViewHeight: CGFloat = 110
    private let stripViewHeightRatio: CGFloat = 0.2
    

    var body: some View {
        GeometryReader { geometry in
            VStack(spacing: 2){
                CalendarDateStripView(
                    items: viewModel.visibleDateCellItems,
                    onSelectedDay: viewModel.focusDay(_:)
                )
                .frame(height: max(minimumStripViewHeight, geometry.size.height * stripViewHeightRatio))
                CalendarDateEventView(items: viewModel.visibleDateCellItems, onSelectedEvent: {_ in })
                
            }
            .task {
                viewModel.loadInitialIfNeeded()
            }
        }
    }
}

#Preview("iPhone SE") {
    CalendarHomeView()
}

#Preview("iPhone 15 Pro") {
    CalendarHomeView()
}

#Preview("Dark Mode") {
    CalendarHomeView()
        .preferredColorScheme(.dark)
}
