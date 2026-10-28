// Automated evidence for required test cases 5–8 (and the happy paths behind 1–4).
// Run with:  cd rules-tests && npm install && npm test
import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from '@firebase/rules-unit-testing';
import {
  doc, getDoc, getDocs, setDoc, addDoc, updateDoc, deleteDoc,
  collection, query, orderBy, serverTimestamp, Timestamp,
} from 'firebase/firestore';

const here = path.dirname(fileURLToPath(import.meta.url));
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-taskboard',
    firestore: { rules: readFileSync(path.join(here, '../../firestore.rules'), 'utf8') },
  });
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  // Seed one task (bypassing rules) for update/delete tests.
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'tasks/seed'), {
      title: 'Seed task', completed: false,
      createdAt: Timestamp.now(), createdBy: 'alice',
    });
  });
});

const alice = () => env.authenticatedContext('alice').firestore();
const bob = () => env.authenticatedContext('bob').firestore();
const anon = () => env.unauthenticatedContext().firestore();
const newTask = (title, uid = 'alice', extra = {}) => ({
  title, completed: false, createdAt: serverTimestamp(), createdBy: uid, ...extra,
});

// ---- Happy paths (cases 1–4) ----
test('signed-in user can create a valid task', async () => {
  await assertSucceeds(addDoc(collection(alice(), 'tasks'), newTask('Buy milk')));
});
test('80-character title is accepted (boundary)', async () => {
  await assertSucceeds(addDoc(collection(alice(), 'tasks'), newTask('x'.repeat(80))));
});
test('signed-in user can read the list newest-first', async () => {
  await assertSucceeds(getDocs(query(collection(alice(), 'tasks'), orderBy('createdAt', 'desc'))));
});
test('any signed-in user can toggle completed (true and back to false)', async () => {
  await assertSucceeds(updateDoc(doc(bob(), 'tasks/seed'), { completed: true }));
  await assertSucceeds(updateDoc(doc(alice(), 'tasks/seed'), { completed: false }));
});

// ---- Case 5: blank / whitespace-only title ----
test('case 5: empty title rejected', async () => {
  await assertFails(addDoc(collection(alice(), 'tasks'), newTask('')));
});
test('case 5: whitespace-only title rejected', async () => {
  await assertFails(addDoc(collection(alice(), 'tasks'), newTask('    ')));
});
test('case 5: untrimmed title rejected', async () => {
  await assertFails(addDoc(collection(alice(), 'tasks'), newTask('  padded  ')));
});

// ---- Case 6: > 80 characters ----
test('case 6: 81-character title rejected', async () => {
  await assertFails(addDoc(collection(alice(), 'tasks'), newTask('x'.repeat(81))));
});

// ---- Case 7: signed-out user ----
test('case 7: signed-out user cannot read', async () => {
  await assertFails(getDocs(collection(anon(), 'tasks')));
  await assertFails(getDoc(doc(anon(), 'tasks/seed')));
});
test('case 7: signed-out user cannot create', async () => {
  await assertFails(addDoc(collection(anon(), 'tasks'), newTask('Hi', 'nobody')));
});
test('case 7: signed-out user cannot update', async () => {
  await assertFails(updateDoc(doc(anon(), 'tasks/seed'), { completed: true }));
});

// ---- Case 8: delete + protected fields ----
test('case 8: delete is denied (even for creator)', async () => {
  await assertFails(deleteDoc(doc(alice(), 'tasks/seed')));
});
test('case 8: changing title is denied', async () => {
  await assertFails(updateDoc(doc(alice(), 'tasks/seed'), { title: 'Hacked' }));
});
test('case 8: changing createdAt is denied', async () => {
  await assertFails(updateDoc(doc(alice(), 'tasks/seed'), { createdAt: Timestamp.fromMillis(0) }));
});
test('case 8: changing createdBy is denied', async () => {
  await assertFails(updateDoc(doc(alice(), 'tasks/seed'), { createdBy: 'bob' }));
});
test('case 8: toggling completed while sneaking a title change is denied', async () => {
  await assertFails(updateDoc(doc(alice(), 'tasks/seed'), { completed: true, title: 'x' }));
});

// ---- Field / type validation on create ----
test('createdBy must equal the caller UID', async () => {
  await assertFails(addDoc(collection(alice(), 'tasks'), newTask('Spoof', 'bob')));
});
test('new task must start with completed == false', async () => {
  await assertFails(addDoc(collection(alice(), 'tasks'), newTask('Done already', 'alice', { completed: true })));
});
test('createdAt must be the server timestamp', async () => {
  await assertFails(addDoc(collection(alice(), 'tasks'),
    newTask('Old', 'alice', { createdAt: Timestamp.fromMillis(0) })));
});
test('title must be a string', async () => {
  await assertFails(addDoc(collection(alice(), 'tasks'), newTask(42)));
});
test('completed must be a boolean on update', async () => {
  await assertFails(updateDoc(doc(alice(), 'tasks/seed'), { completed: 'yes' }));
});
test('missing fields rejected', async () => {
  await assertFails(addDoc(collection(alice(), 'tasks'), { title: 'Incomplete' }));
});
test('extra fields rejected', async () => {
  await assertFails(addDoc(collection(alice(), 'tasks'), newTask('Extra', 'alice', { priority: 1 })));
});
test('other collections are locked', async () => {
  await assertFails(setDoc(doc(alice(), 'other/x'), { a: 1 }));
});
