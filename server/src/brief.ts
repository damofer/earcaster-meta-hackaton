import { check, plain, takeSentences } from "./guard";
import { streamMuse } from "./muse";
import { Help, Talk, statsOf } from "./summary";
import { Lang, Profile, langFrom } from "./types";

/**
 * Spec 021: before the visit. A relative who is about to visit gets a short brief of the person's week: what she
 * asked about, how often she asked about them, missed doses and help alerts, and three gentle topics to talk about
 * taken only from the profile and the week's conversations. The counts are made here; every sentence goes through
 * the guard, and a sentence that tells the visitor to correct or test her memory is dropped.
 */
export interface WeekTalk extends Talk { day: string }
export interface WeekHelp extends Help { day: string }

export interface Week {
  lang: Lang;
  person: string;
  visitor: string;
  /** "del 21 al 27 de octubre" / "October 21 to 27". */
  range: string;
  profile: Profile;
  talks: WeekTalk[];
  /** Doses that were expected and not logged, counted by the caller day by day. */
  missed: { day: string; name: string }[];
  help: WeekHelp[];
}

export interface WeekStats {
  questions: number;
  aboutVisitor: number;
  repeated: { text: string; times: number }[];
  missedDoses: number;
  helpAlerts: number;
}

export const MAX_BRIEF_SENTENCES = 6;

const TESTING_MEMORY = {
  es: /\b(te acuerdas|se acuerda|recuerdas|corrigela|corregirla|correg[ií]rle|recuerdale que|preguntale si (se )?(acuerda|recuerda)|no la corrijas no)\b/,
  en: /\b(do you remember|does she remember|remind her that|correct her|test her|ask (her )?if she remembers)\b/,
};

function str(v: unknown, max: number): string {
  return typeof v === "string" ? v.trim().slice(0, max) : "";
}

function list<T>(v: unknown, max: number, map: (o: Record<string, unknown>) => T | null): T[] {
  return (Array.isArray(v) ? v : [])
    .map((x) => (x && typeof x === "object" ? map(x as Record<string, unknown>) : null))
    .filter((x): x is T => x !== null)
    .slice(0, max);
}

export function weekFrom(body: unknown): Week | null {
  const b = (body && typeof body === "object" ? body : {}) as Record<string, unknown>;
  const visitor = str(b.visitor, 60);
  const range = str(b.range, 80);
  if (!visitor || !range) return null;
  const p = (b.profile && typeof b.profile === "object" ? b.profile : {}) as Record<string, unknown>;
  const profile: Profile = {};
  for (const k of ["name", "callMe", "home", "family", "routine", "likes", "free"] as const) if (str(p[k], 1000)) profile[k] = str(p[k], 1000);
  return {
    lang: langFrom(b.lang),
    person: str(b.person, 60),
    visitor,
    range,
    profile,
    talks: list(b.talks, 600, (o) => (str(o.heard, 300)
      ? { day: str(o.day, 40), time: str(o.time, 20), heard: str(o.heard, 300), answer: str(o.answer, 400), action: str(o.action, 80) || undefined }
      : null)),
    missed: list(b.missed, 100, (o) => (str(o.name, 60) ? { day: str(o.day, 40), name: str(o.name, 60) } : null)),
    help: list(b.help, 50, (o) => ({ day: str(o.day, 40), time: str(o.time, 20), said: str(o.said, 200), sent: o.sent === true })),
  };
}

export function weekStatsOf(w: Week): WeekStats {
  const visitor = plain(w.visitor);
  const day = statsOf({ lang: w.lang, person: w.person, day: w.range, talks: w.talks, doses: [], expected: [], help: [] });
  return {
    questions: w.talks.length,
    aboutVisitor: w.talks.filter((t) => new RegExp(`\\b${visitor}\\b`).test(plain(t.heard))).length,
    repeated: day.repeated,
    missedDoses: w.missed.length,
    helpAlerts: w.help.length,
  };
}

export function briefPrompt(w: Week, s: WeekStats): string {
  const es = w.lang === "es";
  const who = w.person || (es ? "la persona" : "the person");
  const rules = es
    ? [
        `${w.visitor} va a visitar hoy a ${who}, que tiene problemas de memoria. Escribes para ${w.visitor} un resumen de la semana para que llegue preparado.`,
        `Como máximo ${MAX_BRIEF_SENTENCES} frases cortas, en español, cálidas y concretas, hablándole de tú a ${w.visitor}. Sin listas, títulos ni emojis.`,
        "Primero, qué preguntó y qué pareció preocuparle. Luego, tres temas para conversar que le hagan sentir bien, sacados solo de <ficha> y de <registro>.",
        `Las preguntas sobre ${w.visitor} se cuentan como «preguntó por ti».`,
        "Usa exactamente los números de <conteo>, escritos una sola vez (por ejemplo «preguntó 3 veces si venías»). Solo menciona personas, días y temas que aparezcan en <ficha> o <registro>.",
        "Nunca des consejos médicos. Nunca sugieras corregirla ni preguntarle si se acuerda de algo.",
      ]
    : [
        `${w.visitor} is visiting ${who}, who has memory problems, today. You write ${w.visitor} a brief of the week so they arrive ready.`,
        `At most ${MAX_BRIEF_SENTENCES} short sentences in English, warm and concrete, speaking to ${w.visitor} as "you". No lists, headings or emoji.`,
        "First, what they asked about and what seemed to worry them. Then three topics to talk about that will make them feel good, taken only from <profile> and <log>.",
        "Use exactly the numbers in <counts>, each written once (for example \"asked 3 times whether you were coming\"). Only mention people, days and topics that appear in <profile> or <log>.",
        "Never give medical advice. Never suggest correcting them or asking whether they remember something.",
      ];
  const counts = es
    ? `Semana: ${w.range}\nPreguntas al asistente: ${s.questions}\nVeces que preguntó por ${w.visitor} (por ti): ${s.aboutVisitor}\nPreguntas repetidas: ${s.repeated.map((r) => `«${r.text}», ${r.times}`).join("; ") || "ninguna"}\nTomas sin anotar: ${s.missedDoses}\nAvisos de ayuda: ${s.helpAlerts}`
    : `Week: ${w.range}\nQuestions to the assistant: ${s.questions}\nTimes they asked about ${w.visitor} (you): ${s.aboutVisitor}\nRepeated questions: ${s.repeated.map((r) => `"${r.text}" (${r.times} times)`).join(", ") || "none"}\nDoses not logged: ${s.missedDoses}\nHelp alerts: ${s.helpAlerts}`;
  const p = w.profile;
  const profile = [p.family, p.home, p.routine, p.likes, p.free].filter(Boolean).join("\n");
  const log = [
    ...w.talks.map((t) => `${t.day} ${t.time} · ${es ? "preguntó" : "asked"}: ${t.heard}`),
    ...w.missed.map((m) => `${m.day} · ${es ? "toma sin anotar" : "dose not logged"}: ${m.name}`),
    ...w.help.map((h) => `${h.day} ${h.time} · ${es ? "aviso de ayuda" : "help alert"}: ${h.said}`),
  ].join("\n");
  const tags = es ? ["conteo", "ficha", "registro"] : ["counts", "profile", "log"];
  return `${rules.join("\n")}\n\n<${tags[0]}>\n${counts}\n</${tags[0]}>\n\n<${tags[1]}>\n${profile || "-"}\n</${tags[1]}>\n\n<${tags[2]}>\n${log || "-"}\n</${tags[2]}>`;
}

/** Writes the brief. Sentences that fail the guard, or that test her memory, are dropped. */
export async function brief(w: Week, apiKey: string): Promise<{ text: string; stats: WeekStats; dropped: string[] }> {
  const stats = weekStatsOf(w);
  let raw = "";
  const end = await streamMuse({
    apiKey,
    messages: [
      { role: "developer", content: briefPrompt(w, stats) },
      { role: "user", content: w.lang === "es" ? "Escribe el resumen para la visita." : "Write the visit brief." },
    ],
    tools: [],
    timeoutMs: 40_000,
    maxTokens: 6000,
    onText: (delta) => (raw += delta),
  });
  const { sentences, rest } = takeSentences(raw + "\n");
  const said = [...w.talks.flatMap((t) => [t.day, t.time, t.heard, t.answer]), ...w.missed.map((m) => `${m.day} ${m.name}`), ...w.help.map((h) => `${h.day} ${h.time} ${h.said}`)];
  const ctx = {
    lang: w.lang,
    assistant: w.visitor,
    profile: { ...w.profile, name: w.person || w.profile.name },
    facts: { now: w.range, visits: [], doses: said },
    said,
  };
  const kept: string[] = [];
  const dropped: string[] = raw.trim() ? [] : [`no text (${end.finishReason || "no finish reason"})`];
  for (const s of [...sentences, rest.trim()].filter(Boolean)) {
    if (kept.length >= MAX_BRIEF_SENTENCES) break;
    if (TESTING_MEMORY.es.test(plain(s)) || TESTING_MEMORY.en.test(plain(s))) {
      dropped.push("tests memory");
      continue;
    }
    const v = check(s, ctx);
    if (v.ok && v.text) kept.push(v.text);
    else if (!v.ok) dropped.push(v.reason);
  }
  return { text: kept.join(" "), stats, dropped };
}
