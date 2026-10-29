# Test Results

**Tester:** Molly Norman  **Date:** 2026-10-28  **Firebase project:** `taskboard-60e11`
**Clients:** Web (Next.js, `localhost:3000`) · iOS (SwiftUI, iOS Simulator) · Android (Jetpack Compose, Android Emulator)

Legend: ✅ Pass · ❌ Fail · ⏳ Still to capture

## Summary

| # | Case | Result | Evidence |
|---|------|--------|----------|
| 1 | Add on Web → appears on iOS + Android | ✅ | [01](screenshots/01-web-create.png), [02](screenshots/02-ios-create-sees-web.png), [03](screenshots/03-android-create-sees-all.png) |
| 2 | Add on iOS → appears on Web + Android | ✅ | [02](screenshots/02-ios-create-sees-web.png), [03](screenshots/03-android-create-sees-all.png), [05](screenshots/05-blank-rejected-web.png) |
| 3 | Add on Android → appears on Web + iOS | ✅ | [03](screenshots/03-android-create-sees-all.png), [03b](screenshots/03b-ios-sees-android-and-completed.png), [05](screenshots/05-blank-rejected-web.png) |
| 4 | Mark complete in one client → syncs to others | ✅ | [03b](screenshots/03b-ios-sees-android-and-completed.png), [05](screenshots/05-blank-rejected-web.png), [04](screenshots/04-firestore-completed-true.png) |
| 5 | Blank / whitespace-only title rejected | ✅ (Web) | [05](screenshots/05-blank-rejected-web.png) |
| 6 | Title longer than 80 characters rejected | ✅ (Web) | [06](screenshots/06-too-long-rejected-web.png) |
| 7 | Signed-out user cannot read or write | ✅ | [07a](screenshots/07a-signed-out-web.png), [07b](screenshots/07b-rest-unauthenticated-403.png), [07c](screenshots/07c-playground-create-denied.png), [07d](screenshots/07d-playground-get-denied.png) |
| 8 | Cannot delete tasks or change protected fields | ✅ | [08a](screenshots/08a-playground-delete-denied.png), [08b](screenshots/08b-playground-update-denied.png) |

Data model as stored in Firestore (`title` string, `completed` boolean, `createdAt` server timestamp, `createdBy` Auth UID):
[data-model-firestore.png](screenshots/data-model-firestore.png)

---

## Cases 1–3 — Create in one client, see it in the others

| Step | Task created | Seen in |
|---|---|---|
| Web, 4:19 PM | `web test 1` | iOS ([02](screenshots/02-ios-create-sees-web.png)), Android ([03](screenshots/03-android-create-sees-all.png)) |
| iOS, 4:20 PM | `iOS test 1` | Android ([03](screenshots/03-android-create-sees-all.png)), Web ([05](screenshots/05-blank-rejected-web.png)) |
| Android, 4:22 PM | `Android test 1` | iOS ([03b](screenshots/03b-ios-sees-android-and-completed.png)), Web ([05](screenshots/05-blank-rejected-web.png)) |

Each new task appeared at the **top** of the other two lists within a couple of seconds, confirming the
real-time listener and newest-first sort (`orderBy("createdAt", "desc")`). The "· you" label shows on the
client that created the task, confirming `createdBy` is set to that client's Auth UID.

**Result:** ✅ Pass

## Case 4 — Mark complete syncs across clients

`web test 1` was marked complete. It shows as completed (checked, struck through) on iOS
([03b](screenshots/03b-ios-sees-android-and-completed.png)) and Web ([05](screenshots/05-blank-rejected-web.png)),
and Firestore shows `completed: true` with `title`, `createdAt` and `createdBy` unchanged
([04](screenshots/04-firestore-completed-true.png)).

**Result:** ✅ Pass

## Case 5 — Blank / whitespace-only title

Submitting an empty title on Web shows **"Title can't be blank."** and no task is created
([05](screenshots/05-blank-rejected-web.png)). Titles are trimmed before validation, so whitespace-only input
gets the same message. The Firestore rules also reject it server-side (`t.size() >= 1` and `t == t.trim()`).

| Client | Result |
|---|---|
| Web | ✅ ([05](screenshots/05-blank-rejected-web.png)) |
| iOS | ⏳ capture screenshot |
| Android | ⏳ capture screenshot |

## Case 6 — Title over 80 characters

Pasting an 83-character title on Web turns the counter red (**83/80**) and shows
**"Title must be 80 characters or fewer (currently 83)."**; no task is created
([06](screenshots/06-too-long-rejected-web.png)). The rules also enforce `t.size() <= 80` server-side.

| Client | Result |
|---|---|
| Web | ✅ ([06](screenshots/06-too-long-rejected-web.png)) |
| iOS | ⏳ capture screenshot |
| Android | ⏳ capture screenshot |

## Case 7 — Signed-out user cannot read or write

1. **App:** after **Sign out**, the web client shows "You're signed out — Tasks are only visible to signed-in users" and no tasks ([07a](screenshots/07a-signed-out-web.png)).
2. **Server (REST, no auth token):** a direct request to the Firestore REST API for `tasks` returned
   `403 PERMISSION_DENIED — "Missing or insufficient permissions."` ([07b](screenshots/07b-rest-unauthenticated-403.png)).
3. **Rules Playground, Authenticated off:** `create` → *Simulated write denied* ([07c](screenshots/07c-playground-create-denied.png));
   `get` → *Simulated read denied* ([07d](screenshots/07d-playground-get-denied.png)).

**Result:** ✅ Pass

## Case 8 — No deletes, protected fields locked

Rules Playground, **Authenticated on**, location `tasks/5w36BT2Y5XHmuBD62SWm`:

| Simulated request | Expected | Actual |
|---|---|---|
| `delete` | Denied | *Simulated write denied* ✅ ([08a](screenshots/08a-playground-delete-denied.png)) |
| `update` of a protected field (`title`) | Denied | *Simulated write denied* ✅ ([08b](screenshots/08b-playground-update-denied.png)) |
| `update` of `completed` | Allowed | Allowed — shown in practice by Case 4 ✅ |

Enforced by `allow delete: if false;` and
`request.resource.data.diff(resource.data).affectedKeys().hasOnly(['completed'])` in [`firestore.rules`](../firestore.rules).

**Result:** ✅ Pass

---

## Notes

- The Rules Playground screenshots are cropped to the simulation type and result banner; the location and
  Authenticated settings used are listed in the tables above.
- Each client uses its platform's native design system (SwiftUI on iOS, Material 3 on Android), so the
  apps look different while sharing the same Firestore collection and rules.
