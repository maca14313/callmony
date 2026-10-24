import SwiftUI
import ComposeApp /* the shared KMP framework */

@main
struct iOSApp: App {
    init() {
        KmpIosBridge.shared.start() // platform services wiring (IosPlatform.init)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/** Thin bridge to callmony's IosPlatform.init() from Swift. */
final class KmpIosBridge: ObservableObject {
    static let shared = KmpIosBridge()

    func start() {
        IosInitKt.doInit()
    }
}
