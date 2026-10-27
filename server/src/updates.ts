import { datesIn, hoursIn, namesIn, plain } from "./guard";
import { streamMuse } from "./muse";
import { Lang, Profile, langFrom } from "./types";

/**
 * Spec 020: the family updates the profile by writing. The caregiver shares a family message ("Llego el domingo a
 * las 5, Lucía") and Muse Spark proposes the change. Nothing is saved here: the caregiver approves each proposal on
 * the phone. Every proposal must be traceable to the message, and that is checked in code, not trusted to the model.
 */
export const PROFILE_FIELDS = ["home", "family", "routine", "likes", "free"] as const;
export type ProfileField = (typeof PROFILE_FIELDS)[number];
export const MAX_PROPOSALS = 3;
/** How far ahead a one-off visit may be. */
const MAX_DAYS_AHEAD = 60;

export interface WeeklyVisit { name: string; days: number; minute: number }

export interface Request {
  lang: Lang;
  /** The phone's date, YYYY-MM-DD. */
  today: string;
  message: string;
  profile: Profile;
  visits: WeeklyVisit[];
}

export type Proposal =
  | { kind: "visit"; name: string; date: string; minute: number }
  /** days is the same bit set as the app's weekly visits: Sunday is 1, Monday 2, and so on. */
  | { kind: "weekly"; name: string; days: number; minute: number; replaces: string | null }
  | { kind: "profile"; field: ProfileField; old: string; new: string };

export interface Result {
  proposals: Proposal[];
  /** The message is about medication: doses are changed in Reminders, never from a message. */
  medication: boolean;
  dropped: string[];
}

function str(v: unknown, max: number): string {
  return typeof v === "string" ? v.trim().slice(0, max) : "";
}

/** Phone numbers never go to Meta, even if a relative pasted one in the message. */
export function withoutNumbers(s: string): string {
  return s.replace(/\+?\d[\d\s().-]{5,}\d/g, "[…]");
}

export function requestFrom(body: unknown): Request | null {
  const b = (body && typeof body === "object" ? body : {}) as Record<string, unknown>;
  const today = str(b.today, 10);
  const message = withoutNumbers(str(b.message, 1000));
  if (!/^\d{4}-\d{2}-\d{2}$/.test(today) || !message) return null;
  const p = (b.profile && typeof b.profile === "object" ? b.profile : {}) as Record<string, unknown>;
  const profile: Profile = {};
  for (const k of ["name", "callMe", ...PROFILE_FIELDS] as const) if (str(p[k], 1000)) profile[k] = str(p[k], 1000);
  const visits = (Array.isArray(b.visits) ? b.visits : [])
    .map((v) => (v && typeof v === "object" ? (v as Record<string, unknown>) : {}))
    .map((v) => ({ name: str(v.name, 60), days: Number(v.days) & 127, minute: Number(v.minute) }))
    .filter((v) => v.name && Number.isInteger(v.minute) && v.minute >= 0 && v.minute < 1440)
    .slice(0, 20);
  return { lang: langFrom(b.lang), today, message, profile, visits };
}

const MEDICATION = /\b(pastillas?|medicamentos?|medicinas?|remedios?|dosis|tabletas?|capsulas?|gotas|jarabe|insulina|mg|pills?|medications?|medicines?|doses?|tablets?|capsules?|drops|syrup|insulin)\b/;

export function aboutMedication(message: string): boolean {
  return MEDICATION.test(plain(message));
}

const WEEKDAYS_SUNDAY_FIRST = {
  es: ["domingo", "lunes", "martes", "miercoles", "jueves", "viernes", "sabado"],
  en: ["sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"],
};

function dateOf(iso: string): Date | null {
  const d = new Date(`${iso}T12:00:00Z`);
  return isNaN(d.getTime()) || d.toISOString().slice(0, 10) !== iso ? null : d;
}

/** The weekday words, relative words or calendar dates in the message that point to this date. */
function dateTraced(date: string, today: string, message: string): boolean {
  const d = dateOf(date);
  const t = dateOf(today);
  if (!d || !t) return false;
  const ahead = Math.round((d.getTime() - t.getTime()) / 86_400_000);
  if (ahead < 0 || ahead > MAX_DAYS_AHEAD) return false;
  const m = plain(message);
  if (ahead === 0 && /\b(hoy|today|tonight|esta (tarde|noche|manana))\b/.test(m)) return true;
  if (ahead === 1 && /\b(manana|tomorrow)\b/.test(m.replace(/\b(por|en|de|esta) la manana\b/g, ""))) return true;
  if (ahead === 2 && /\b(pasado manana|day after tomorrow)\b/.test(m)) return true;
  if (datesIn(message).has(`${d.getUTCDate()}-${d.getUTCMonth() + 1}`)) return true;
  // A weekday word means the next one, within a week; "el otro domingo" or "next Sunday" may be the one after.
  const wd = d.getUTCDay();
  const named = [WEEKDAYS_SUNDAY_FIRST.es[wd], WEEKDAYS_SUNDAY_FIRST.en[wd]].some((w) => new RegExp(`\\b${w}s?\\b`).test(m));
  return named && ahead <= 14;
}

function hourTraced(minute: number, message: string): boolean {
  if (!Number.isInteger(minute) || minute < 0 || minute >= 1440) return false;
  return hoursIn(message, "es").has(Math.floor(minute / 60) % 12);
}

function appears(word: string, text: string): boolean {
  return new RegExp(`(^|[^\\p{L}])${plain(word)}($|[^\\p{L}])`, "u").test(plain(text));
}

/** The visitor's name must be in the message, word by word. */
function nameTraced(name: string, message: string): boolean {
  return name.split(/\s+/).every((w) => appears(w, message));
}

/** Every capitalized name in a profile sentence must be in the message, or already in what it replaces. */
function namesTraced(text: string, message: string, old: string): string | null {
  for (const n of namesIn(text)) if (!appears(n, `${message} ${old}`)) return n;
  return null;
}

function daysFrom(v: unknown): number {
  let bits = 0;
  for (const x of Array.isArray(v) ? v : []) {
    const i = WEEKDAYS_SUNDAY_FIRST.en.indexOf(plain(String(x)).replace(/s$/, ""));
    if (i >= 0) bits |= 1 << i;
  }
  return bits;
}

function daysTraced(days: number, message: string): boolean {
  const m = plain(message);
  if (days === 127 && /\b(todos los dias|cada dia|every day|daily)\b/.test(m)) return true;
  if (days === 62 && /\b(de lunes a viernes|entre semana|weekdays|monday to friday)\b/.test(m)) return true;
  for (let i = 0; i < 7; i++) {
    if (!(days & (1 << i))) continue;
    const named = [WEEKDAYS_SUNDAY_FIRST.es[i], WEEKDAYS_SUNDAY_FIRST.en[i]].some((w) => new RegExp(`\\b${w}s?\\b`).test(m));
    if (!named) return false;
  }
  return days > 0;
}

/** Checks one proposal from the model. Returns it cleaned, or the reason it was dropped. */
export function checkProposal(raw: Record<string, unknown>, r: Request): Proposal | string {
  const quote = str(raw.quote, 300);
  if (!quote || !plain(r.message).includes(plain(quote))) return "not in the message";
  if (raw.kind === "visit") {
    const name = str(raw.name, 60);
    const date = str(raw.date, 10);
    const minute = Number(raw.minute);
    if (!name || !nameTraced(name, r.message)) return `name ${name}`;
    if (!dateTraced(date, r.today, r.message)) return `date ${date}`;
    if (!hourTraced(minute, r.message)) return `hour ${minute}`;
    return { kind: "visit", name, date, minute };
  }
  if (raw.kind === "weekly") {
    const name = str(raw.name, 60);
    const days = daysFrom(raw.days);
    const minute = Number(raw.minute);
    const replaces = str(raw.replaces, 60);
    if (!name || !nameTraced(name, r.message)) return `name ${name}`;
    if (!daysTraced(days, r.message)) return `days ${days}`;
    if (!hourTraced(minute, r.message)) return `hour ${minute}`;
    const existing = r.visits.find((v) => plain(v.name) === plain(replaces || name));
    return { kind: "weekly", name, days, minute, replaces: existing ? existing.name : null };
  }
  if (raw.kind === "profile") {
    const field = str(raw.field, 20) as ProfileField;
    if (!PROFILE_FIELDS.includes(field)) return `field ${field}`;
    const current = r.profile[field] || "";
    const old = str(raw.old, 1000);
    const text = str(raw.new, 300);
    if (!text) return "empty";
    if (old && !current.includes(old)) return "old text not in the profile";
    const unknown = namesTraced(text, r.message, old);
    if (unknown) return `name ${unknown}`;
    if (aboutMedication(text)) return "medication";
    return { kind: "profile", field, old, new: text };
  }
  return `kind ${String(raw.kind)}`;
}

export function updatesPrompt(r: Request): string {
  const p = r.profile;
  const weekday = WEEKDAYS_SUNDAY_FIRST.en[dateOf(r.today)!.getUTCDay()];
  const visits = r.visits.map((v) => {
    const days = WEEKDAYS_SUNDAY_FIRST.en.filter((_, i) => v.days & (1 << i)).join(", ");
    return `- ${v.name}: ${days} at ${String(Math.floor(v.minute / 60)).padStart(2, "0")}:${String(v.minute % 60).padStart(2, "0")}`;
  }).join("\n");
  const profile = PROFILE_FIELDS.map((f) => `${f}: ${p[f] || "(empty)"}`).join("\n");
  return [
    "You help the family of a person with memory loss keep Earcaster up to date. Earcaster answers the person's",
    "questions about visits and family from a profile and a visit schedule. A relative wrote the message below.",
    "Propose only the changes the message clearly states. If it states nothing to change, return no proposals.",
    "",
    "Kinds of proposal:",
    '- {"kind":"visit","name","date":"YYYY-MM-DD","minute":minutes after midnight,"quote"}: a one-off visit.',
    '- {"kind":"weekly","name","days":["sunday",...],"minute","replaces":name of the weekly visit it changes or null,"quote"}: a visit that repeats every week.',
    '- {"kind":"profile","field":"home"|"family"|"routine"|"likes"|"free","old":exact sentence it replaces or "","new":one sentence,"quote"}: a fact about the person or the family.',
    "",
    "Rules:",
    `- "quote" is the exact words of the message the proposal comes from. At most ${MAX_PROPOSALS} proposals.`,
    "- A weekday means the next one after today. An hour from 1 to 7 without a.m./p.m. is in the afternoon.",
    "- Write \"new\" in the same language as the profile, in the third person, about the person or the family.",
    "- Never propose anything about medication or doses. Never invent a name, date or time.",
    "",
    `Today is ${weekday}, ${r.today}.`,
    "",
    `<weekly_visits>\n${visits || "(none)"}\n</weekly_visits>`,
    "",
    `<profile>\n${profile}\n</profile>`,
    "",
    'Answer with JSON only: {"proposals":[...]}',
  ].join("\n");
}

function jsonIn(text: string): Record<string, unknown> {
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start < 0 || end <= start) return {};
  try {
    return JSON.parse(text.slice(start, end + 1));
  } catch {
    return {};
  }
}

export async function proposeUpdates(r: Request, apiKey: string): Promise<Result> {
  // Doses are never changed from a message, so a message about them does not even reach the model.
  if (aboutMedication(r.message)) return { proposals: [], medication: true, dropped: [] };
  let raw = "";
  await streamMuse({
    apiKey,
    messages: [
      { role: "developer", content: updatesPrompt(r) },
      { role: "user", content: `<message>\n${r.message}\n</message>` },
    ],
    tools: [],
    timeoutMs: 25_000,
    maxTokens: 4000,
    onText: (delta) => (raw += delta),
  });
  const list = jsonIn(raw).proposals;
  const proposals: Proposal[] = [];
  const dropped: string[] = [];
  for (const p of Array.isArray(list) ? list : []) {
    if (proposals.length >= MAX_PROPOSALS) break;
    const v = p && typeof p === "object" ? checkProposal(p as Record<string, unknown>, r) : "not an object";
    if (typeof v === "string") dropped.push(v);
    else if (proposals.some((q) => JSON.stringify(q) === JSON.stringify(v))) dropped.push("repeated");
    else proposals.push(v);
  }
  return { proposals, medication: false, dropped };
}
