# Demo script (target 2:30)

**Setup before recording:** all three clients open and signed in, arranged side by side
(browser left, iOS Simulator middle, Android Emulator right). Start with a few tasks in the list.
Record with QuickTime (File → New Screen Recording) or OBS.

| Time | Action | Say |
|---|---|---|
| 0:00 | Show all three windows | "This is one Firestore `tasks` collection behind three clients — Next.js, SwiftUI and Jetpack Compose. No custom server." |
| 0:15 | **Web:** add `Demo from web` | "I'll add a task on the web…" |
| 0:25 | Point at iOS + Android | "…and it's at the top of iOS and Android immediately — sorted newest first by a server timestamp." |
| 0:40 | **iOS:** tap it to complete | "Marking it done on iOS…" |
| 0:50 | Point at Web + Android | "…the strike-through shows up on web and Android." |
| 1:00 | **Android:** add `Demo from Android`, then uncheck the web task | "Same in the other direction from Android." |
| 1:20 | **Web:** submit spaces only, then paste 81 characters | "Blank titles and anything over 80 characters are rejected — the counter turns red." |
| 1:40 | Show `firestore.rules` in the editor | "The client check is a convenience. The real enforcement is here: auth required, exact fields, title 1–80 trimmed, `createdAt` must be the server time, `createdBy` must be your UID, only `completed` can change, deletes are denied." |
| 2:05 | Firebase console → Rules Playground: run a `delete` (Authenticated on) → "Simulated write denied" | "Even a signed-in user can't delete a task or change its title — the server refuses." |
| 2:20 | **Web:** Sign out | "Signed out, there's nothing to read — the rules refuse unauthenticated access." |
| 2:30 | End | |
