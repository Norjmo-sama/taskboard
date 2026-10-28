//
//  ContentView.swift
//  TaskBoardiOS
//
//  Created by Molly Norman on 10/28/26.
//

import SwiftUI
import FirebaseAuth

struct ContentView: View {
    @StateObject private var store = TaskStore()

    var body: some View {
        NavigationStack {
            Group {
                if !store.authChecked {
                    ProgressView("Checking sign-in…")
                } else if store.user == nil {
                    SignedOutView(store: store)
                } else {
                    BoardView(store: store)
                }
            }
            .navigationTitle("Task Board")
            .toolbar {
                if let user = store.user {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Menu {
                            Text("UID: \(user.uid)")
                            Button("Sign out", role: .destructive) { store.signOut() }
                        } label: {
                            Image(systemName: "person.crop.circle")
                        }
                    }
                }
            }
        }
    }
}

private struct SignedOutView: View {
    @ObservedObject var store: TaskStore
    @State private var working = false

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "lock").font(.largeTitle).foregroundStyle(.secondary)
            Text("You’re signed out").font(.title2.bold())
            Text("Tasks are only visible to signed-in users.").foregroundStyle(.secondary)
            Button {
                working = true
                Task { await store.signIn(); working = false }
            } label: {
                if working { ProgressView() } else { Text("Sign in anonymously") }
            }
            .buttonStyle(.borderedProminent)
            .disabled(working)
            if let err = store.authError {
                Text(err).foregroundStyle(.red).font(.footnote)
            }
        }
        .padding()
    }
}

private struct BoardView: View {
    @ObservedObject var store: TaskStore
    @State private var draft = ""
    @State private var formError: String?
    @State private var saving = false

    private var trimmedCount: Int {
        draft.trimmingCharacters(in: .whitespacesAndNewlines).count
    }

    var body: some View {
        VStack(spacing: 0) {
            addBar
            if let err = store.actionError {
                Text(err).foregroundStyle(.red).font(.footnote).padding(.horizontal)
            }
            content
        }
    }

    private var addBar: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                TextField("What needs doing?", text: $draft)
                    .textFieldStyle(.roundedBorder)
                    .submitLabel(.done)
                    .onSubmit(add)
                    .onChange(of: draft) { _ in formError = nil }
                    .accessibilityIdentifier("titleField")
                Button(action: add) {
                    if saving { ProgressView() } else { Text("Add").bold() }
                }
                .buttonStyle(.borderedProminent)
                .disabled(saving)
                .accessibilityIdentifier("addButton")
            }
            HStack {
                if let formError {
                    Text(formError).foregroundStyle(.red)
                }
                Spacer()
                Text("\(trimmedCount)/\(TitleValidator.maxLength)")
                    .foregroundStyle(trimmedCount > TitleValidator.maxLength ? .red : .secondary)
            }
            .font(.caption)
        }
        .padding()
    }

    @ViewBuilder
    private var content: some View {
        switch store.listState {
        case .loading:
            Spacer()
            ProgressView("Loading tasks…")
            Spacer()
        case .error(let message):
            Spacer()
            VStack(spacing: 8) {
                Image(systemName: "exclamationmark.triangle").font(.largeTitle).foregroundStyle(.red)
                Text("Couldn’t load tasks").font(.headline)
                Text(message).font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
            }
            .padding()
            Spacer()
        case .loaded(let tasks) where tasks.isEmpty:
            Spacer()
            VStack(spacing: 8) {
                Image(systemName: "checklist").font(.largeTitle).foregroundStyle(.secondary)
                Text("No tasks yet").font(.headline)
                Text("Add the first one above — it’ll show up on every device.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            .padding()
            Spacer()
        case .loaded(let tasks):
            List(tasks) { task in
                TaskRow(task: task, isMine: task.createdBy == store.user?.uid) {
                    Task { await store.toggle(task) }
                }
            }
            .listStyle(.plain)
        }
    }

    private func add() {
        guard !saving else { return }
        saving = true
        Task {
            formError = await store.addTask(rawTitle: draft)
            if formError == nil { draft = "" }
            saving = false
        }
    }
}

private struct TaskRow: View {
    let task: TaskItem
    let isMine: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack(spacing: 12) {
                Image(systemName: task.completed ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(task.completed ? Color.accentColor : .secondary)
                VStack(alignment: .leading, spacing: 2) {
                    Text(task.title)
                        .strikethrough(task.completed)
                        .foregroundStyle(task.completed ? .secondary : .primary)
                    Text(subtitle).font(.caption2).foregroundStyle(.secondary)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(task.title), \(task.completed ? "complete" : "incomplete")")
    }

    private var subtitle: String {
        let date = task.createdAt.map { $0.formatted(date: .abbreviated, time: .shortened) } ?? "…"
        return isMine ? "\(date) · you" : date
    }
}


#Preview {
    ContentView()
}
