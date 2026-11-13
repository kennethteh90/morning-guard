import Foundation

public enum BibleProxyUserMessages {
    public static func friendlyMessage(httpStatus: Int, serverError: String?) -> String {
        if httpStatus == 429 {
            return "Too many Bible chapter requests from this network. Wait about a minute, then try again."
        }
        if httpStatus == 401 {
            return "Could not verify this device for licensed translations (NIV, NKJV, NLT). Use a physical iPhone, install the latest app build, and try again in a minute. You can also read this chapter in WEB or KJV."
        }
        if httpStatus == 502 || httpStatus == 503 || httpStatus == 504 {
            return "The Bible service is temporarily unavailable (HTTP \(httpStatus)). Wait a moment, tap Retry, or switch to WEB or KJV."
        }
        if let serverError, !serverError.isEmpty {
            if httpStatus == 403, serverError.localizedCaseInsensitiveContains("Licensed") {
                return "\(serverError) Switch to WEB or KJV in the reader."
            }
            return serverError
        }
        return "Bible server error (HTTP \(httpStatus))."
    }
}
