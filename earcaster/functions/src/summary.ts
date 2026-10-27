import { DocumentReference, Timestamp } from "firebase-admin/firestore";
import { Lang, Profile } from "./types";

/**
 * The caregiver's daily summary (spec 019), written by the earcaster-muse engine with Muse Spark. This side gathers
 * the day's log from Firestore (AI conversations, logged doses, help alerts) and sends it without phone numbers.
 */
export const MUSE_SUMMARY_URL = process.env.MUSE_SUMMARY_URL || "https://us-central1-earcaster.cloudfunctions.net/museSummary";

/** "9:05 a. m." in the phone's time zone, so the summary uses the same clock the family does. */
export function clock(ms: number, zone: string, lang: Lang): string {
  try {
    return new Intl.DateTimeFormat(lang === "en" ? "en-US" : "es-CO", { hour: "numeric", minute: "2-digit", timeZone: zone }).format(ms);
  } catch {
    return new Intl.DateTimeFormat(lang === "en" ? "en-US" : "es-CO", { hour: "numeric", minute: "2-digit", timeZone: "America/Bogota" }).format(ms);
  }
}

function millis(v: unknown): number {
  if (v instanceof Timestamp) return v.toMillis();
  return typeof v === "number" ? v : 0;
}

export interface DayLog {
  lang: Lang;
  person: string;
  day: string;
  talks: { time: string; heard: string; answer: string; action?: string }[];
  doses: { time: string; name: string }[];
  expected: string[];
  help: { time: string; said: string; sent: boolean }[];
}

/** Reads the day's log of one patient, between from and to (the phone's local midnight to now). */
export async function dayLog(patient: DocumentReference, profile: Profile, expected: string[], from: number, to: number,
  label: string, zone: string, lang: Lang): Promise<DayLog> {
  const [talks, doses, events] = await Promise.all([
    patient.collection("talks").where("at", ">=", Timestamp.fromMillis(from)).where("at", "<=", Timestamp.fromMillis(to)).get(),
    patient.collection("doses").where("at", ">=", from).where("at", "<=", to).get(),
    patient.collection("events").where("at", ">=", Timestamp.fromMillis(from)).where("at", "<=", Timestamp.fromMillis(to)).get(),
  ]);
  const byTime = <T extends { ms: number }>(a: T, b: T) => a.ms - b.ms;
  return {
    lang,
    person: profile.callMe?.trim() || profile.name?.trim() || "",
    day: label,
    talks: talks.docs
      .map((d) => ({ ms: millis(d.get("at")), heard: String(d.get("heard") || ""), answer: String(d.get("answer") || ""), action: String(d.get("action") || "") }))
      .sort(byTime)
      .map((t) => ({ time: clock(t.ms, zone, lang), heard: t.heard, answer: t.answer, action: t.action || undefined })),
    doses: doses.docs
      .map((d) => ({ ms: millis(d.get("at")), name: String(d.get("name") || "") }))
      .filter((d) => d.name)
      .sort(byTime)
      .map((d) => ({ time: clock(d.ms, zone, lang), name: d.name })),
    expected,
    help: events.docs
      .map((d) => ({ ms: millis(d.get("at")), said: String(d.get("said") || ""), sent: d.get("sent") === true }))
      .sort(byTime)
      .map((h) => ({ time: clock(h.ms, zone, lang), said: h.said, sent: h.sent })),
  };
}

export interface Summary {
  text: string;
  stats: { questions: number; repeated: { text: string; times: number }[]; dosesLogged: number; dosesMissing: string[]; helpAlerts: number };
}

export async function askSummary(token: string, log: DayLog): Promise<Summary> {
  const res = await fetch(MUSE_SUMMARY_URL, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(log),
    signal: AbortSignal.timeout(50_000),
  });
  if (!res.ok) throw new Error(`museSummary ${res.status}`);
  const out = (await res.json()) as Summary;
  return { text: String(out.text || ""), stats: out.stats };
}
