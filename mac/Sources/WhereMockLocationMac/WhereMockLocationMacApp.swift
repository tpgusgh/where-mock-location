import SwiftUI

@main
struct WhereMockLocationMacApp: App {
    var body: some Scene {
        MenuBarExtra("가짜 위치", systemImage: "location.fill") {
            ContentView()
        }
        .menuBarExtraStyle(.window)
    }
}
