import Foundation

/// Mirrors the title rule in firestore.rules: 1–80 characters after trimming.
enum TitleValidator {
    static let maxLength = 80

    enum Result: Equatable {
        case valid(String)
        case invalid(String)
    }

    static func validate(_ raw: String) -> Result {
        let title = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if title.isEmpty { return .invalid("Title can’t be blank.") }
        if title.count > maxLength {
            return .invalid("Title must be \(maxLength) characters or fewer (currently \(title.count)).")
        }
        return .valid(title)
    }
}
