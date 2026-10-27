import { resolve } from "./actions";
import { Action, AiSettings, Facts, Lang, Profile, Turn } from "./types";

/**
 * Meta's Muse Spark (spec 018) through the earcaster-muse engine, built in the public repository
 * damofer/earcaster-meta-hackaton and deployed to this same Firebase project. Phone numbers stay here: the engine only
 * gets names, and the action it returns goes through resolve() again, which takes the number from the profile.
 */
export const MUSE_URL = process.env.MUSE_URL || "https://us-central1-earcaster.cloudfunctions.net/muse";

/** What the caregiver allowed, without numbers. The same filters as tools() in actions.ts. */
export function allowedFor(profile: Profile, doseNames: string[]) {
  return {
    call: (profile.calls || [])
      .filter((c) => c.number && c.number.trim() && c.name && c.name.trim())
      .map((c) => ({ name: c.name!.trim(), relation: c.relation?.trim() || undefined })),
    light: profile.light === true,
    doses: (profile.doses || []).filter((d) => doseNames.length === 0 || doseNames.includes(d)),
    help: profile.help === true,
  };
}

/** The engine's action, mapped to the tool names resolve() checks. */
export function actionFrom(a: unknown, profile: Profile): Action | null {
  const o = (a && typeof a === "object" ? a : {}) as Record<string, unknown>;
  switch (o.type) {
    case "call":
      return resolve("llamar", { quien: o.name }, profile);
    case "light":
      return resolve("linterna", { encender: o.on }, profile);
    case "dose":
      return resolve("anotar_toma", { toma: o.name }, profile);
    case "help":
      return resolve("avisar_ayuda", {}, profile);
    default:
      return null;
  }
}

/** Asks the engine and hands each sentence to onSentence as it arrives. Returns the allowed action, if any. */
export async function askMuse(
  token: string,
  settings: AiSettings,
  profile: Profile,
  facts: Facts,
  history: Turn[],
  text: string,
  doseNames: string[],
  lang: Lang,
  onSentence: (s: string) => void,
): Promise<Action | null> {
  const abort = new AbortController();
  const timer = setTimeout(() => abort.abort(), 12_000);
  try {
    const res = await fetch(MUSE_URL, {
      method: "POST",
      signal: abort.signal,
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        lang,
        assistant: settings.name,
        text,
        history,
        profile: {
          name: profile.name, callMe: profile.callMe, home: profile.home, family: profile.family,
          routine: profile.routine, likes: profile.likes, sensitive: profile.sensitive, free: profile.free,
        },
        facts,
        allowed: allowedFor(profile, doseNames),
      }),
    });
    if (!res.ok || !res.body) throw new Error(`muse ${res.status}`);
    let action: Action | null = null;
    let ok = false;
    let pending = "";
    const decoder = new TextDecoder();
    const line = (l: string) => {
      if (!l.trim()) return;
      const e = JSON.parse(l) as { t: string; text?: string; action?: unknown; ok?: boolean };
      if (e.t === "say" && e.text) onSentence(e.text);
      else if (e.t === "do") action = actionFrom(e.action, profile);
      else if (e.t === "end") ok = e.ok === true;
    };
    for await (const chunk of res.body as unknown as AsyncIterable<Uint8Array>) {
      pending += decoder.decode(chunk, { stream: true });
      let nl: number;
      while ((nl = pending.indexOf("\n")) >= 0) {
        line(pending.slice(0, nl));
        pending = pending.slice(nl + 1);
      }
    }
    line(pending + decoder.decode());
    if (!ok) throw new Error("muse could not answer");
    return action;
  } finally {
    clearTimeout(timer);
  }
}
