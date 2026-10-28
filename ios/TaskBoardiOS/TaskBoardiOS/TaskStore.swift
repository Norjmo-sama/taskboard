import Foundation
import FirebaseAuth
import FirebaseFirestore
public import Combine

struct TaskItem: Identifiable, Equatable {
    let id: String
    let title: String
    let completed: Bool
    let createdAt: Date?
    let createdBy: String
}

@MainActor
final class TaskStore: ObservableObject {
    enum ListState: Equatable {
        case loading
        case error(String)
        case loaded([TaskItem])
    }

    @Published private(set) var user: User?
    @Published private(set) var authChecked = false
    @Published private(set) var listState: ListState = .loading
    @Published var authError: String?
    @Published var actionError: String?

    private let db = Firestore.firestore()
    private var authHandle: AuthStateDidChangeListenerHandle?
    private var listener: ListenerRegistration?

    init() {
        authHandle = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            Task { @MainActor in
                guard let self else { return }
                self.user = user
                self.authChecked = true
                if user != nil { self.startListening() } else { self.stopListening() }
            }
        }
    }

    deinit {
        if let authHandle { Auth.auth().removeStateDidChangeListener(authHandle) }
        listener?.remove()
    }

    // MARK: Auth

    func signIn() async {
        authError = nil
        do { _ = try await Auth.auth().signInAnonymously() }
        catch { authError = Self.friendly(error) }
    }

    func signOut() {
        do { try Auth.auth().signOut() } catch { authError = Self.friendly(error) }
    }

    // MARK: Firestore

    private func startListening() {
        listener?.remove()
        listState = .loading
        listener = db.collection("tasks")
            .order(by: "createdAt", descending: true)
            .addSnapshotListener { [weak self] snapshot, error in
                Task { @MainActor in
                    guard let self else { return }
                    if let error {
                        self.listState = .error(Self.friendly(error))
                        return
                    }
                    let tasks = (snapshot?.documents ?? []).map { doc -> TaskItem in
                        // .estimate gives pending server timestamps a local value so new tasks sort to the top.
                        let ts = doc.get("createdAt", serverTimestampBehavior: .estimate) as? Timestamp
                        return TaskItem(
                            id: doc.documentID,
                            title: doc.get("title") as? String ?? "",
                            completed: doc.get("completed") as? Bool ?? false,
                            createdAt: ts?.dateValue(),
                            createdBy: doc.get("createdBy") as? String ?? ""
                        )
                    }
                    self.listState = .loaded(tasks)
                }
            }
    }

    private func stopListening() {
        listener?.remove()
        listener = nil
        listState = .loading
    }

    /// Returns an error message to show under the field, or nil on success.
    func addTask(rawTitle: String) async -> String? {
        guard let uid = user?.uid else { return "You’re signed out." }
        switch TitleValidator.validate(rawTitle) {
        case .invalid(let message):
            return message
        case .valid(let title):
            do {
                try await db.collection("tasks").addDocument(data: [
                    "title": title,
                    "completed": false,
                    "createdAt": FieldValue.serverTimestamp(),
                    "createdBy": uid,
                ])
                return nil
            } catch {
                return Self.friendly(error)
            }
        }
    }

    func toggle(_ task: TaskItem) async {
        actionError = nil
        do {
            try await db.collection("tasks").document(task.id)
                .updateData(["completed": !task.completed])
        } catch {
            actionError = Self.friendly(error)
        }
    }

    static func friendly(_ error: Error) -> String {
        let ns = error as NSError
        if ns.domain == FirestoreErrorDomain {
            switch FirestoreErrorCode.Code(rawValue: ns.code) {
            case .permissionDenied: return "Permission denied by Firestore security rules."
            case .unavailable: return "You appear to be offline. Changes will sync when you reconnect."
            default: break
            }
        }
        if ns.domain == AuthErrorDomain, AuthErrorCode(rawValue: ns.code) == .operationNotAllowed {
            return "Anonymous sign-in is disabled in the Firebase console."
        }
        return error.localizedDescription
    }
}
