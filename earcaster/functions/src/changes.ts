/**
 * Spec 022: alert when something changes. The same questions every day are expected; a new topic that starts
 * repeating, many more questions than usual, questions at night or more help alerts are worth telling the family.
 * Muse Spark only groups the questions into topics (museTopics); the rules and the alert sentences are here, in code,
 * so an alert never contains a word about illness.
 */
export const MUSE_TOPICS_URL = process.env.MUSE_TOPICS_URL || "https://us-central1-earcaster.cloudfunctions.net/museTopics";

export const RECENT_DAYS = 3;
export const BASE_DAYS = 14;
/** With fewer days of history than this, there are no alerts (R6). */
export const MIN_HISTORY_DAYS = 7;

export interface DayTrend {
  /** YYYY-MM-DD on the phone. */
  day: string;
  questions: number;
  night: number;
  /** Each question as heard, with how many times it was asked that day. */
  texts: Record<string, number>;
}

export interface Topic { label: string; texts: string[] }

export interface Change {
  kind: "topic" | "volume" | "night" | "help";
  key: string;
  /** The day the change started. */
  since: string;
  count: number;
  /** The usual amount, for volume and help. */
  usual: number;
  label: string;
  quotes: string[];
}

/** The date n days before an ISO date. */
export function daysBefore(day: string, n: number): string {
  const d = new Date(`${day}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() - n);
  return d.toISOString().slice(0, 10);
}

export function plain(s: string): string {
  return s.toLowerCase().normalize("NFD").replace(/\p{M}+/gu, "").replace(/[^\p{L}\p{N} ]/gu, " ").replace(/\s+/g, " ").trim();
}

export function detect(day: string, trends: DayTrend[], topics: Topic[], helpRecent: number, helpBase: number): Change[] {
  if (!trends.length) return [];
  const earliest = trends.map((t) => t.day).sort()[0];
  if (earliest > daysBefore(day, MIN_HISTORY_DAYS)) return [];
  const recentFrom = daysBefore(day, RECENT_DAYS - 1);
  const baseFrom = daysBefore(day, RECENT_DAYS + BASE_DAYS - 1);
  const recent = trends.filter((t) => t.day >= recentFrom && t.day <= day).sort((a, b) => a.day.localeCompare(b.day));
  const base = trends.filter((t) => t.day >= baseFrom && t.day < recentFrom);
  const out: Change[] = [];

  // (a) A topic asked at least 3 times in the last 3 days that was never asked in the 14 before.
  for (const topic of topics) {
    const count = (list: DayTrend[]) => list.reduce((n, t) => n + topic.texts.reduce((m, x) => m + (t.texts[x] || 0), 0), 0);
    const now = count(recent);
    if (now < 3 || count(base) > 0) continue;
    const first = recent.find((t) => topic.texts.some((x) => t.texts[x]));
    const quotes = topic.texts.filter((x) => recent.some((t) => t.texts[x])).slice(0, 3);
    const label = topic.label || topic.texts[0];
    out.push({ kind: "topic", key: plain(label).slice(0, 60), since: first ? first.day : recentFrom, count: now, usual: 0, label, quotes });
  }

  // (b) A day with twice the usual daily questions, and at least 10.
  const usual = base.reduce((n, t) => n + t.questions, 0) / BASE_DAYS;
  const busiest = [...recent].sort((a, b) => b.questions - a.questions)[0];
  if (busiest && busiest.questions >= Math.max(10, 2 * usual)) {
    out.push({ kind: "volume", key: "volume", since: busiest.day, count: busiest.questions, usual: Math.round(usual), label: "", quotes: [] });
  }

  // (c) Questions at night (10 PM to 5 AM) on 2 or more of the last 3 days, after none.
  const nights = recent.filter((t) => t.night > 0);
  if (nights.length >= 2 && base.every((t) => t.night === 0)) {
    out.push({ kind: "night", key: "night", since: nights[0].day, count: nights.length, usual: 0, label: "", quotes: [] });
  }

  // (d) More help alerts in the last 3 days than in the 14 before, and at least 2.
  if (helpRecent >= 2 && helpRecent > helpBase) {
    out.push({ kind: "help", key: "help", since: recentFrom, count: helpRecent, usual: helpBase, label: "", quotes: [] });
  }
  return out;
}

const WEEKDAYS = {
  es: ["domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"],
  en: ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"],
};

/** The alert's sentence, from a template: what changed and since when, with nothing about illness (R4). */
export function sentence(c: Change, person: string, lang: "es" | "en"): string {
  const wd = WEEKDAYS[lang][new Date(`${c.since}T12:00:00Z`).getUTCDay()];
  const who = person || (lang === "en" ? "They" : "La persona");
  if (lang === "en") {
    switch (c.kind) {
      case "topic": return `Since ${wd}, ${who} has asked about "${c.label}" ${c.count} times, and did not before.`;
      case "volume": return `On ${wd}, ${who} asked ${c.count} questions, more than twice the usual (about ${c.usual} a day).`;
      case "night": return `In the last 3 days, ${who} asked questions at night on ${c.count} nights, and did not before.`;
      default: return `In the last 3 days there were ${c.count} help alerts, more than in the two weeks before (${c.usual}).`;
    }
  }
  switch (c.kind) {
    case "topic": return `Desde el ${wd}, ${who} ha preguntado por «${c.label}» ${c.count} veces, y antes no lo hacía.`;
    case "volume": return `El ${wd}, ${who} hizo ${c.count} preguntas, más del doble de lo normal (unas ${c.usual} al día).`;
    case "night": return `En los últimos 3 días, ${who} hizo preguntas de noche ${c.count} noches, y antes no lo hacía.`;
    default: return `En los últimos 3 días hubo ${c.count} avisos de ayuda, más que en las dos semanas anteriores (${c.usual}).`;
  }
}

/** The question as it is kept in the daily trend: trimmed, single spaces, at most 120 characters. */
export function trendKey(heard: string): string {
  return heard.replace(/\s+/g, " ").trim().slice(0, 120);
}

/** Night is from 10 PM to 5 AM on the phone. */
export function isNight(hour: unknown): boolean {
  return typeof hour === "number" && Number.isInteger(hour) && (hour >= 22 || hour < 5);
}

export async function askTopics(token: string, lang: string, texts: string[]): Promise<Topic[]> {
  const res = await fetch(MUSE_TOPICS_URL, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ lang, texts }),
    signal: AbortSignal.timeout(45_000),
  });
  if (!res.ok) throw new Error(`museTopics ${res.status}`);
  const out = (await res.json()) as { topics?: Topic[] };
  return Array.isArray(out.topics) ? out.topics : [];
}
