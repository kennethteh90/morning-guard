import Foundation
import HealthKit

@MainActor
final class HealthKitStepReader: ObservableObject {
    private let healthStore = HKHealthStore()

    @Published private(set) var isAvailable: Bool
    @Published private(set) var authorizationDenied: Bool

    init() {
        isAvailable = HKHealthStore.isHealthDataAvailable()
        authorizationDenied = false
    }

    func requestAuthorization() async throws {
        guard isAvailable else { return }
        let stepType = HKQuantityType(.stepCount)
        try await healthStore.requestAuthorization(toShare: [], read: [stepType])
    }

    func todayStepCount() async throws -> Int {
        guard isAvailable else { return 0 }
        let stepType = HKQuantityType(.stepCount)
        let calendar = Calendar.current
        let start = calendar.startOfDay(for: Date())
        let predicate = HKQuery.predicateForSamples(withStart: start, end: Date(), options: .strictStartDate)
        return try await withCheckedThrowingContinuation { continuation in
            let query = HKStatisticsQuery(
                quantityType: stepType,
                quantitySamplePredicate: predicate,
                options: .cumulativeSum
            ) { _, statistics, error in
                if let error {
                    continuation.resume(throwing: error)
                    return
                }
                let steps = statistics?.sumQuantity()?.doubleValue(for: .count()) ?? 0
                continuation.resume(returning: Int(steps))
            }
            healthStore.execute(query)
        }
    }
}
