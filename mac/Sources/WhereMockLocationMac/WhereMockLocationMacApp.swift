import SwiftUI

@main
struct WhereMockLocationMacApp: App {
    var body: some Scene {
        MenuBarExtra("Where Mock Location", systemImage: "location.fill") {
            ContentView()
        }
        .menuBarExtraStyle(.window)
    }
}
