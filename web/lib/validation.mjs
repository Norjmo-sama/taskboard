// Shared title validation. Mirrors the rule in firestore.rules.
export const MAX_TITLE = 80;

/** Returns { ok: true, title } with the trimmed title, or { ok: false, error }. */
export function validateTitle(raw) {
  const title = (raw ?? '').trim();
  if (title.length === 0) return { ok: false, error: 'Title can’t be blank.' };
  if (title.length > MAX_TITLE)
    return { ok: false, error: `Title must be ${MAX_TITLE} characters or fewer (currently ${title.length}).` };
  return { ok: true, title };
}
