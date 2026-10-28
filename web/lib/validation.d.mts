export declare const MAX_TITLE: number;
export declare function validateTitle(
  raw: string | null | undefined
): { ok: true; title: string } | { ok: false; error: string };
