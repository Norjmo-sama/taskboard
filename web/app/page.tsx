'use client';

import { useEffect, useState, type FormEvent } from 'react';
import { onAuthStateChanged, signInAnonymously, signOut, type User } from 'firebase/auth';
import {
  addDoc, collection, doc, onSnapshot, orderBy, query,
  serverTimestamp, updateDoc, type Timestamp,
} from 'firebase/firestore';
import { auth, db, isConfigured } from '@/lib/firebase';
import { MAX_TITLE, validateTitle } from '@/lib/validation.mjs';

type Task = {
  id: string;
  title: string;
  completed: boolean;
  createdAt: Timestamp | null;
  createdBy: string;
};

type ListState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; tasks: Task[] };

export default function Home() {
  const [user, setUser] = useState<User | null>(null);
  const [authReady, setAuthReady] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);

  useEffect(() => {
    if (!auth) return;
    return onAuthStateChanged(auth, (u) => { setUser(u); setAuthReady(true); });
  }, []);

  if (!isConfigured) {
    return (
      <main className="shell">
        <Header />
        <div className="banner error">
          Firebase isn’t configured. Copy <code>web/.env.example</code> to <code>web/.env.local</code>,
          fill in your project’s web config, and restart <code>npm run dev</code>.
        </div>
      </main>
    );
  }

  async function handleSignIn() {
    setAuthError(null);
    try { await signInAnonymously(auth!); }
    catch (e) { setAuthError(friendly(e)); }
  }

  return (
    <main className="shell">
      <Header
        right={
          user ? (
            <div className="who">
              <span title={user.uid}>Signed in · {user.uid.slice(0, 6)}…</span>
              <button className="ghost" onClick={() => signOut(auth!)}>Sign out</button>
            </div>
          ) : null
        }
      />
      {!authReady ? (
        <p className="muted center">Checking sign-in…</p>
      ) : !user ? (
        <section className="card center">
          <h2>You’re signed out</h2>
          <p className="muted">Tasks are only visible to signed-in users.</p>
          <button onClick={handleSignIn}>Sign in anonymously</button>
          {authError && <p className="banner error">{authError}</p>}
        </section>
      ) : (
        <Board user={user} />
      )}
    </main>
  );
}

function Header({ right }: { right?: React.ReactNode }) {
  return (
    <header className="top">
      <h1>Task Board <span className="tag">Web</span></h1>
      {right}
    </header>
  );
}

function Board({ user }: { user: User }) {
  const [state, setState] = useState<ListState>({ kind: 'loading' });
  const [draft, setDraft] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    const q = query(collection(db!, 'tasks'), orderBy('createdAt', 'desc'));
    return onSnapshot(
      q,
      (snap) => {
        const tasks = snap.docs.map((d) => {
          // 'estimate' fills in a local time for our own pending serverTimestamp writes,
          // so a just-added task sorts to the top immediately.
          const data = d.data({ serverTimestamps: 'estimate' });
          return {
            id: d.id,
            title: String(data.title ?? ''),
            completed: Boolean(data.completed),
            createdAt: (data.createdAt as Timestamp) ?? null,
            createdBy: String(data.createdBy ?? ''),
          };
        });
        setState({ kind: 'ready', tasks });
      },
      (err) => setState({ kind: 'error', message: friendly(err) }),
    );
  }, [user.uid]);

  async function addTask(e: FormEvent) {
    e.preventDefault();
    const v = validateTitle(draft);
    if (!v.ok) { setFormError(v.error); return; }
    setFormError(null);
    setSaving(true);
    try {
      await addDoc(collection(db!, 'tasks'), {
        title: v.title,
        completed: false,
        createdAt: serverTimestamp(),
        createdBy: user.uid,
      });
      setDraft('');
    } catch (err) {
      setFormError(friendly(err));
    } finally {
      setSaving(false);
    }
  }

  async function toggle(t: Task) {
    setActionError(null);
    try { await updateDoc(doc(db!, 'tasks', t.id), { completed: !t.completed }); }
    catch (err) { setActionError(friendly(err)); }
  }

  const trimmedLen = draft.trim().length;

  return (
    <>
      <form className="card add" onSubmit={addTask} noValidate>
        <input
          aria-label="Task title"
          placeholder="What needs doing?"
          value={draft}
          onChange={(e) => { setDraft(e.target.value); if (formError) setFormError(null); }}
          disabled={saving}
        />
        <button type="submit" disabled={saving}>{saving ? 'Adding…' : 'Add'}</button>
        <div className={`count ${trimmedLen > MAX_TITLE ? 'over' : ''}`}>{trimmedLen}/{MAX_TITLE}</div>
        {formError && <p className="banner error full" role="alert">{formError}</p>}
      </form>

      {actionError && <p className="banner error" role="alert">{actionError}</p>}

      {state.kind === 'loading' && (
        <ul className="list" aria-busy="true">
          {[0, 1, 2].map((i) => <li key={i} className="row skeleton" />)}
        </ul>
      )}

      {state.kind === 'error' && (
        <div className="banner error" role="alert">
          Couldn’t load tasks: {state.message}
        </div>
      )}

      {state.kind === 'ready' && state.tasks.length === 0 && (
        <div className="card center empty">
          <p><strong>No tasks yet.</strong></p>
          <p className="muted">Add the first one above — it’ll show up on every device.</p>
        </div>
      )}

      {state.kind === 'ready' && state.tasks.length > 0 && (
        <ul className="list">
          {state.tasks.map((t) => (
            <li key={t.id} className={`row ${t.completed ? 'done' : ''}`}>
              <label>
                <input type="checkbox" checked={t.completed} onChange={() => toggle(t)} />
                <span className="title">{t.title}</span>
              </label>
              <span className="meta">
                {t.createdAt ? t.createdAt.toDate().toLocaleString() : '…'}
                {t.createdBy === user.uid ? ' · you' : ''}
              </span>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

function friendly(e: unknown): string {
  const code = (e as { code?: string })?.code ?? '';
  if (code.includes('permission-denied')) return 'Permission denied by Firestore security rules.';
  if (code.includes('unavailable')) return 'You appear to be offline. Changes will sync when you reconnect.';
  if (code.includes('operation-not-allowed')) return 'Anonymous sign-in is disabled in the Firebase console.';
  return (e as Error)?.message ?? 'Something went wrong.';
}
