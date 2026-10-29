# Task Board — Web · iOS · Android on Firebase

A shared task list with three native clients that all read and write the same Firestore
collection (`tasks`). There is **no custom server**: Firebase Auth (anonymous sign-in) identifies
users and Firestore security rules enforce every validation that matters.

| Client  | Stack | Folder | Status |
|---------|-------|--------|--------|
| Web     | Next.js 15 (React 19, App Router), Firebase JS SDK 11 | `web/` | ✅ Working |
| iOS     | SwiftUI, Firebase iOS SDK (Swift Package Manager) | `ios/TaskBoardiOS/` | ✅ Working |
| Android | Jetpack Compose, Firebase Android BoM 35.1.0, Kotlin 2.4 | `android/` | ✅ Working |
| Rules   | [`firestore.rules`](firestore.rules) | repo root | ✅ Deployed and tested — see [test results](docs/TEST_RESULTS.md) |

**Test results for the 8 required cases:** [`docs/TEST_RESULTS.md`](docs/TEST_RESULTS.md) (all passing, with screenshots).

## Features (all three clients)

- Live task list sorted **newest first** (`orderBy("createdAt", "desc")` + real-time listener).
- Add a task: the title is trimmed and must be 1–80 characters; a live `n/80` counter turns red past the limit.
- Tap a task / checkbox to mark it complete ↔ incomplete; changes sync to the other clients in about a second.
- **Loading**, **empty** ("No tasks yet") and **error** states (permission denied, offline, sign-in disabled).
- Anonymous sign-in plus a **Sign out** button, so the signed-out state can be demonstrated.
- Each client uses its platform's native look (web CSS, SwiftUI, Material 3), sharing the same data and rules.

## Data model — collection `tasks`

| Field       | Type      | Rule |
|-------------|-----------|------|
| `title`     | string    | trimmed, 1–80 characters |
| `completed` | boolean   | `false` on create; the **only** field that can change afterwards |
| `createdAt` | timestamp | must equal `request.time` (set with `serverTimestamp()`) |
| `createdBy` | string    | must equal the signed-in user's Firebase Auth UID |

No other fields are allowed, deletes are always denied, and every read/write requires sign-in.

---

## 1. Firebase setup (once)

1. Create a project at <https://console.firebase.google.com>.
2. **Authentication → Get started → Sign-in method → Anonymous → Enable → Save.**
3. **Databases & Storage → Firestore → Create database** → Standard edition → **Start in production mode** (never test mode).
4. **Firestore → Rules** tab → replace the contents with [`firestore.rules`](firestore.rules) → **Publish**.
   (Or with the CLI: `firebase deploy --only firestore:rules`.)
5. **⚙ Project settings → Your apps → Add app**, once for each client:

   | Client | Identifier to enter | Download | Put it in |
   |---|---|---|---|
   | Web (`</>`) | nickname only | copy the `firebaseConfig` values | `web/.env.local` (see step 2 below) |
   | iOS | Apple bundle ID — must match the Xcode target's **Bundle Identifier** | `GoogleService-Info.plist` | `ios/TaskBoardiOS/TaskBoardiOS/` (add to the app target) |
   | Android | package name `com.example.taskboardandroid` | `google-services.json` | `android/app/` |

These three config files are **git-ignored** and shared with reviewers privately
(see [`docs/ENV_FOR_REVIEWERS.md`](docs/ENV_FOR_REVIEWERS.md)).

---

## 2. Run the web client (Next.js)

Requires Node 20+.

```bash
cd web
cp .env.example .env.local      # fill in the six NEXT_PUBLIC_FIREBASE_* values
npm install
npm run dev                     # open http://localhost:3000
```

Restart `npm run dev` after editing `.env.local`. If the file is missing, the page shows a
"Firebase isn't configured" banner. `npm test` runs the title-validation unit tests.

## 3. Run the iOS client (SwiftUI)

Requires a Mac with Xcode 15+.

1. Put your `GoogleService-Info.plist` in `ios/TaskBoardiOS/TaskBoardiOS/`.
2. Open `ios/TaskBoardiOS/TaskBoardiOS.xcodeproj` in Xcode.
3. Wait for Xcode to resolve the **firebase-ios-sdk** package (first open takes a few minutes).
   The **TaskBoardiOS** target must link **FirebaseAuth**, **FirebaseCore** and **FirebaseFirestore**
   (Target → General → Frameworks, Libraries, and Embedded Content).
4. In the plist's Target Membership, make sure **TaskBoardiOS** is ticked, and that the target's
   **Bundle Identifier** matches the iOS app registered in Firebase.
5. Choose an iPhone simulator and press **⌘R**, then tap **Sign in anonymously**.

The SwiftUI preview canvas crashes because Firebase isn't configured in previews — that's expected;
run the app in the simulator instead.

## 4. Run the Android client (Jetpack Compose)

Requires a current Android Studio (it supplies the JDK and Android SDK).

1. Put your `google-services.json` in `android/app/` (package `com.example.taskboardandroid`).
2. Android Studio → **File → Open** → select the `android/` folder → **Trust Project**.
3. Let Gradle sync (first time downloads Gradle and dependencies).
4. Create/select an emulator (Device Manager → Pixel, recommended image) and press **▶ Run**,
   then tap **Sign in anonymously**.

Key build settings: Firebase BoM `35.1.0` (requires Kotlin `2.4.0`, set in `gradle/libs.versions.toml`),
the `com.google.gms.google-services` plugin, and `firebase-auth` + `firebase-firestore`.

---

## How the rules were tested

All 8 required cases are recorded in [`docs/TEST_RESULTS.md`](docs/TEST_RESULTS.md):

- **Cross-client sync (1–4):** tasks created and completed in each client, verified in the other two.
- **Validation (5–6):** blank and 81+ character titles rejected by the clients and by the rules.
- **Signed out (7):** the app hides tasks; an unauthenticated Firestore REST request returns
  `403 PERMISSION_DENIED`; Rules Playground denies `get` and `create` without auth.
- **Protected data (8):** Rules Playground (authenticated) denies `delete` and updates to `title`/`createdBy`.

`rules-tests/` contains an automated `@firebase/rules-unit-testing` suite for the same rules
(`cd rules-tests && npm install && npm test`; needs Java for the Firestore emulator).

## Repository layout

```
firestore.rules              security rules (deployed to the project)
web/                         Next.js client   — app/page.tsx is the UI, lib/firebase.ts the setup
ios/TaskBoardiOS/            SwiftUI client   — TaskStore.swift holds the Firestore logic
android/                     Compose client   — TaskViewModel.kt holds the Firestore logic
rules-tests/                 automated security-rules tests (emulator)
docs/TEST_RESULTS.md         results + screenshots for the 8 required cases
docs/DEMO_SCRIPT.md          2–3 minute demo run-of-show
docs/ENV_FOR_REVIEWERS.md    which config files reviewers need
docs/PEER_REVIEW.md          peer review template
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| "Anonymous sign-in is disabled" | Enable Anonymous in Authentication → Sign-in method. |
| "Permission denied by Firestore security rules" | You're signed out, or the rules weren't published. |
| Web shows "Firebase isn't configured" | Create `web/.env.local`, then restart `npm run dev`. |
| iOS: `No such module 'FirebaseCore'` | Add FirebaseCore to the target's Frameworks list; Product → Clean Build Folder. |
| iOS: "could not find a valid GoogleService-Info.plist" | The plist isn't in the app target — tick it under Target Membership. |
| Android: `google-services.json is missing` | Put the file in `android/app/` with that exact name. |
| Android: "compiled with an incompatible version of Kotlin" | Set `kotlin = "2.4.0"` in `gradle/libs.versions.toml` and sync. |
| Clients show different lists | They point at different Firebase projects — compare the project IDs in the config files. |
