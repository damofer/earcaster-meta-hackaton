import { DocumentReference, Timestamp } from "firebase-admin/firestore";
import { clock } from "./summary";
import { Lang, Profile } from "./types";

/**
 * Spec 021: the brief for a relative about to visit, written by the earcaster-muse engine with Muse Spark from the
 * last 7 days. This side reads the week from Firestore and counts the missed doses day by day; no phone numbers.
 */
export const MUSE_BRIEF_URL = process.env.MUSE_BRIEF_URL || "https://us-central1-earcaster.cloudfunctions.net/museBrief";

const DAY_MS = 86_400_000;

function millis(v: unknown): number {
  if (v instanceof Timestamp) return v.toMillis();
  return typeof v === "number" ? v : 0;
}

/** "martes 27" in the phone's time zone. */
export function dayLabel(ms: number, zone: string, lang: Lang): string {
  const fmt = (z: string) => new Intl.DateTimeFormat(lang === "en" ? "en-US" : "es-CO", { weekday: "long", day: "numeric", timeZone: z }).format(ms);
  try {
    return fmt(zone);
  } catch {
    return fmt("America/Bogota");
  }
}

/** Expected doses that were not logged, day by day. A day counts from its local midnight (from + k days). */
export function missedDoses(expected: string[], logged: { ms: number; name: string }[], from: number, to: number,
  zone: string, lang: Lang): { day: string; name: string }[] {
  const out: { day: string; name: string }[] = [];
  for (let start = from; start < to; start += DAY_MS) {
    // Today is not over: a dose not logged yet is not missed.
    if (start + DAY_MS > to) break;
    const names = new Set(logged.filter((d) => d.ms >= start && d.ms < start + DAY_MS).map((d) => d.name));
    for (const e of expected) if (!names.has(e)) out.push({ day: dayLabel(start + DAY_MS / 2, zone, lang), name: e });
  }
  return out;
}

export async function weekLog(patient: DocumentReference, profile: Profile, expected: string[], visitor: string,
  from: number, to: number, range: string, zone: string, lang: Lang): Promise<object> {
  const [talks, doses, events] = await Promise.all([
    patient.collection("talks").where("at", ">=", Timestamp.fromMillis(from)).where("at", "<=", Timestamp.fromMillis(to)).get(),
    patient.collection("doses").where("at", ">=", from).where("at", "<=", to).get(),
    patient.collection("events").where("at", ">=", Timestamp.fromMillis(from)).where("at", "<=", Timestamp.fromMillis(to)).get(),
  ]);
  const byTime = <T extends { ms: number }>(a: T, b: T) => a.ms - b.ms;
  const logged = doses.docs.map((d) => ({ ms: millis(d.get("at")), name: String(d.get("name") || "") })).filter((d) => d.name);
  return {
    lang,
    person: profile.callMe?.trim() || profile.name?.trim() || "",
    visitor,
    range,
    profile: { name: profile.name, callMe: profile.callMe, home: profile.home, family: profile.family, routine: profile.routine, likes: profile.likes, free: profile.free },
    talks: talks.docs
      .map((d) => ({ ms: millis(d.get("at")), heard: String(d.get("heard") || ""), answer: String(d.get("answer") || "") }))
      .sort(byTime)
      .map((t) => ({ day: dayLabel(t.ms, zone, lang), time: clock(t.ms, zone, lang), heard: t.heard, answer: t.answer })),
    missed: missedDoses(expected, logged, from, to, zone, lang),
    help: events.docs
      .map((d) => ({ ms: millis(d.get("at")), said: String(d.get("said") || ""), sent: d.get("sent") === true }))
      .sort(byTime)
      .map((h) => ({ day: dayLabel(h.ms, zone, lang), time: clock(h.ms, zone, lang), said: h.said, sent: h.sent })),
  };
}

export async function askBrief(token: string, week: object): Promise<{ text: string; stats: unknown }> {
  const res = await fetch(MUSE_BRIEF_URL, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(week),
    signal: AbortSignal.timeout(50_000),
  });
  if (!res.ok) throw new Error(`museBrief ${res.status}`);
  const out = (await res.json()) as { text?: string; stats?: unknown };
  return { text: String(out.text || ""), stats: out.stats };
}
