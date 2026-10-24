import Foundation

public enum BibleProxyUserMessages {
    public static func friendlyMessage(httpStatus: Int, serverError: String?) -> String {
        if httpStatus == 429 {
            return "Too many Bible chapter requests from this network. Wait about a minute, then try again."
        }
        if let serverError, !serverError.isEmpty {
            if httpStatus == 403, serverError.localizedCaseInsensitiveContains("Licensed") {
                return "\(serverError) Switch to WEB or KJV, or enable licensed translations on the worker."
            }
            return serverError
        }
        return "Bible server error (HTTP \(httpStatus))."
    }
}
