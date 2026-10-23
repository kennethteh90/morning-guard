import Foundation

enum MGPlatform {
    /// True when running in iOS Simulator (compile-time and runtime checks).
    static var isSimulator: Bool {
        #if targetEnvironment(simulator)
        return true
        #else
        return ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != nil
        #endif
    }
}
