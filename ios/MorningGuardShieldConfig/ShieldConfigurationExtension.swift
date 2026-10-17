import ManagedSettings
import ManagedSettingsUI
import UIKit

final class ShieldConfigurationExtension: ShieldConfigurationDataSource {
    private func brandedConfiguration() -> ShieldConfiguration {
        ShieldConfiguration(
            backgroundBlurStyle: .systemMaterial,
            backgroundColor: UIColor.systemBackground,
            icon: UIImage(systemName: "sun.horizon.fill"),
            title: ShieldConfiguration.Label(
                text: "Morning Guard",
                color: UIColor.label
            ),
            subtitle: ShieldConfiguration.Label(
                text: "Finish your morning goal to unlock this app.",
                color: UIColor.secondaryLabel
            ),
            primaryButtonLabel: ShieldConfiguration.Label(
                text: "Open Morning Guard",
                color: UIColor.label
            ),
            primaryButtonBackgroundColor: UIColor.systemOrange,
            secondaryButtonLabel: ShieldConfiguration.Label(
                text: "Not now",
                color: UIColor.secondaryLabel
            )
        )
    }

    override func configuration(shielding application: Application) -> ShieldConfiguration {
        brandedConfiguration()
    }

    override func configuration(shielding application: Application, in category: ActivityCategory) -> ShieldConfiguration {
        brandedConfiguration()
    }

    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration {
        brandedConfiguration()
    }

    override func configuration(shielding webDomain: WebDomain, in category: ActivityCategory) -> ShieldConfiguration {
        brandedConfiguration()
    }
}
