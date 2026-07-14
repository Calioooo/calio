//
//  CalioApp.swift
//  Calio
//
//  Created by 김준하 on 6/6/26.
//

import SwiftUI
import GoogleSignIn

@main
struct CalioApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
