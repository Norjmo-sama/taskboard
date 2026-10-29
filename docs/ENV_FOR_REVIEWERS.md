# Config to hand reviewers and the instructor

These files are git-ignored. Send them through the class's private channel (LMS submission,
DM, or a shared drive folder) — not in the public repo.

| Client | File | Where it goes |
|---|---|---|
| Web | `.env.local` (template: `web/.env.example`) | `web/.env.local` |
| iOS | `GoogleService-Info.plist` | `ios/TaskBoardiOS/TaskBoardiOS/` |
| Android | `google-services.json` | `android/app/` |

**Are these secret?** Firebase client config (API key, app ID, project ID) only identifies your
project; it doesn't grant access. Access is controlled by `firestore.rules` and Firebase Auth,
which is why the rules are strict. Still, keep them out of public repos so bots don't spam your
project. If you want extra protection, enable **App Check** later.

Fill-in for your submission:

```
NEXT_PUBLIC_FIREBASE_API_KEY=
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=
NEXT_PUBLIC_FIREBASE_PROJECT_ID=
NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=
NEXT_PUBLIC_FIREBASE_APP_ID=
```

Instructor access: Firebase console → ⚙ Project settings → **Users and permissions** →
**Add member** → instructor's email → **Viewer**.
