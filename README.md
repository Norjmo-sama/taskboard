# Task Board — Web · iOS · Android on Firebase

A shared task list with three native clients that all read and write the same Firestore
collection (`tasks`). There is **no custom server**: Firebase Auth identifies users and
Firestore security rules enforce every validation that matters.

| Client  | Stack                                  | Folder      | Status |
|---------|----------------------------------------|-------------|--------|
| Web     | Next.js 15 (React 19, App Router)      | `web/`      | Complete — builds clean |
| iOS     | SwiftUI, Firebase iOS SDK 11 (SPM)     | `ios/`      | Complete |
| Android | Jetpack Compose, Firebase BoM 33       | `android/`  | Complete — see status notes |
| Rules   | `firestore.rules` + emulator test suite| `rules-tests/` | 24 automated tests |

## Features (all three clients)

- Live task list sorted **newest first** (`orderBy("createdAt", "desc")` + real-time listener).
- Add a task: title is trimmed, must be 1–80 chars; a live `n/80` counter turns red past the limit.
- Tap/checkbox to mark complete ↔ incomplete; changes sync to other clients in ~1s.
- **Loading** (skeleton / spinner), **empty** ("No tasks yet"), and **error** states
  (permission denied, offline, sign-in disabled) are all handled and shown inline.
- Anonymous sign-in plus a **Sign out** button, so the signed-out state is demonstrable.
- New tasks appear instantly at the top because pending `serverTimestamp()` values are read with
  the *estimate* behavior on every platform.

## Data model — collection `tasks`

| Field       | Type      | Rule |
|-------------|-----------|------|
| `title`     | string    | trimmed, 1–80 characters |
| `completed` | boolean   | `false` on create; the **only** field that can change later |
| `createdAt` | timestamp | must equal `request.time` (i.e. `serverTimestamp()`) |
| `createdBy` | string    | must equal `request.auth.uid` |

No other fields are allowed. Deletes are always denied. See [`firestore.rules`](firestore.rules).

---

## 1. Firebase project setup (once)

1. Create a project at <https://console.firebase.google.com>.
2. **Build → Authentication → Get started → Sign-in method → Anonymous → Enable.**
3. **Build → Firestore Database → Create database** → choose **production mode** (never test mode).
4. Deploy the rules from this repo:
   ```bash
   npm install -g firebase-tools
   firebase login
   cp .firebaserc.example .firebaserc      # then put your project ID in it
   firebase deploy --only firestore:rules
   ```
   Confirm in **Firestore → Rules** that the published rules match `firestore.rules`.
5. Register three apps in **Project settings → General → Your apps**:
   - **Web** → copy the config values into `web/.env.local` (step 2 below).
   - **iOS**, bundle ID `com.example.taskboard` → download `GoogleService-Info.plist` into `ios/TaskBoard/`.
   - **Android**, package `com.example.taskboard` → download `google-services.json` into `android/app/`.

   > Want a different bundle/package ID? Change it in `ios/project.yml` and
   > `android/app/build.gradle.kts` (`applicationId`) *before* registering the apps.

6. **Share with the instructor:** Project settings → **Users and permissions** → **Add member** →
   instructor's email → role **Viewer** (or **Editor** if they need to change rules).

All three config files are git-ignored. Share them with reviewers privately (see
[`docs/ENV_FOR_REVIEWERS.md`](docs/ENV_FOR_REVIEWERS.md)).

---

## 2. Run the web client (Next.js)

Requires Node 18.18+ (Node 20/22 recommended).

```bash
cd web
cp .env.example .env.local      # fill in the six NEXT_PUBLIC_FIREBASE_* values
npm install
npm run dev                     # http://localhost:3000
```

Other scripts: `npm run build` (production build), `npm test` (title-validation unit tests).

If `.env.local` is missing, the page shows a "Firebase isn't configured" banner instead of crashing.

## 3. Run the iOS client (SwiftUI)

Requires macOS with Xcode 15+. The project file is generated from `ios/project.yml` with XcodeGen
so it stays readable in Git.

```bash
brew install xcodegen
cd ios
# put GoogleService-Info.plist in ios/TaskBoard/ first
xcodegen
open TaskBoard.xcodeproj
```

Xcode resolves the Firebase Swift package on first open (takes a minute). Pick an iPhone
simulator and press **⌘R**. Unit tests: **⌘U**.

<details><summary>Without XcodeGen</summary>

File → New → Project → iOS App (SwiftUI, name `TaskBoard`, bundle `com.example.taskboard`).
Delete the generated `ContentView.swift`/`TaskBoardApp.swift`, drag in everything from
`ios/TaskBoard/`, then File → Add Package Dependencies →
`https://github.com/firebase/firebase-ios-sdk` → add **FirebaseAuth** and **FirebaseFirestore**.
Add `GoogleService-Info.plist` to the app target.
</details>

## 4. Run the Android client (Jetpack Compose)

Requires Android Studio Ladybug (2024.2) or newer, JDK 17.

1. Put `google-services.json` in `android/app/`.
2. Android Studio → **Open** → select the `android/` folder. Let Gradle sync
   (it downloads Gradle 8.11.1 per `gradle/wrapper/gradle-wrapper.properties`;
   if prompted to create a wrapper, accept).
3. Pick an emulator (API 24+) and press **Run**.

Unit tests: right-click `app/src/test` → Run, or `./gradlew test` once the wrapper exists.

## 5. Security-rules tests (emulator)

Requires Java 11+ for the Firestore emulator.

```bash
cd rules-tests
npm install
npm test           # starts the Firestore emulator, runs 24 tests, shuts it down
```

These tests cover required cases 5–8 automatically (blank/whitespace titles, >80 chars,
signed-out access, delete and protected-field changes) plus type/field validation.

---

## Repository layout

```
firestore.rules            security rules (deployed)
firebase.json              rules + emulator config
rules-tests/               @firebase/rules-unit-testing suite
web/                       Next.js client   (app/page.tsx is the whole UI)
ios/                       SwiftUI client   (TaskStore.swift = Firestore logic)
android/                   Compose client   (TaskViewModel.kt = Firestore logic)
docs/TEST_RESULTS.md       results for the 8 required cases
docs/DEMO_SCRIPT.md        2–3 minute demo run-of-show
docs/PEER_REVIEW.md        peer review template
docs/ENV_FOR_REVIEWERS.md  what config to hand reviewers and how
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| "Anonymous sign-in is disabled" | Enable Anonymous in Authentication → Sign-in method. |
| "Permission denied by Firestore security rules" on load | You're signed out, or rules aren't deployed. Run `firebase deploy --only firestore:rules`. |
| Web shows "Firebase isn't configured" | Create `web/.env.local`, then restart `npm run dev`. |
| iOS crash on launch: `FirebaseApp.configure()` | `GoogleService-Info.plist` is missing from the app target, or its bundle ID doesn't match. |
| Android build error mentioning `google-services.json` | File missing from `android/app/`, or the package name doesn't match. |
| Clients show different lists | They're pointed at different Firebase projects — compare `projectId` in each config file. |