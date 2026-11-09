import Foundation
import MorningGuardCore

/// App-target alias for shared sign-in callback policy (see `AuthSignInCallbackURL`).
enum AuthCallbackURL {
    static func isSignInCallback(_ url: URL) -> Bool {
        AuthSignInCallbackURL.isSignInCallback(url)
    }
}
