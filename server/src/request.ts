import { Allowed, Facts, Profile, Question, Turn, langFrom } from "./types";

function str(v: unknown, max: number): string {
  return typeof v === "string" ? v.trim().slice(0, max) : "";
}

function strings(v: unknown, maxItems: number, maxLen: number): string[] {
  return Array.isArray(v) ? v.map((x) => str(x, maxLen)).filter(Boolean).slice(0, maxItems) : [];
}

function obj(v: unknown): Record<string, unknown> {
  return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : {};
}

/** Reads and bounds what the caller sent. Returns null when there is no question to answer. */
export function questionFrom(body: unknown): Question | null {
  const b = obj(body);
  const text = str(b.text, 300);
  if (!text) return null;
  const lang = langFrom(b.lang);
  const p = obj(b.profile);
  const profile: Profile = {};
  for (const k of ["name", "callMe", "home", "family", "routine", "likes", "sensitive", "free"] as const) {
    const v = str(p[k], 1500);
    if (v) profile[k] = v;
  }
  const f = obj(b.facts);
  const facts: Facts = { now: str(f.now, 200), visits: strings(f.visits, 20, 200), doses: strings(f.doses, 20, 200) };
  const a = obj(b.allowed);
  const allowed: Allowed = {
    call: (Array.isArray(a.call) ? a.call : [])
      .map((c) => ({ name: str(obj(c).name, 60), relation: str(obj(c).relation, 60) || undefined }))
      .filter((c) => c.name)
      .slice(0, 10),
    light: a.light === true,
    doses: strings(a.doses, 10, 60),
    help: a.help === true,
  };
  const history: Turn[] = (Array.isArray(b.history) ? b.history : [])
    .map((t) => ({ q: str(obj(t).q, 300), a: str(obj(t).a, 400) }))
    .filter((t) => t.q && t.a)
    .slice(-6);
  const assistant = str(b.assistant, 30) || (lang === "en" ? "Grace" : "Clara");
  return { lang, assistant, text, history, profile, facts, allowed };
}
