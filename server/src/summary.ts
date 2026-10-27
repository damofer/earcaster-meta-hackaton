import { check, takeSentences } from "./guard";
import { streamMuse } from "./muse";
import { Lang, langFrom } from "./types";

/**
 * The caregiver's daily summary: what the person asked the assistant, the doses logged and any help alert, told in a
 * few plain sentences by Muse Spark. The numbers are counted here, not by the model, and every sentence goes through
 * the same guard as the spoken answers, so the summary cannot mention a time or a person that is not in the day's log.
 */
export interface Talk { time: string; heard: string; answer: string; action?: string }
export interface Dose { time: string; name: string }
export interface Help { time: string; said: string; sent: boolean }

export interface Day {
  lang: Lang;
  person: string;
  day: string;
  talks: Talk[];
  doses: Dose[];
  /** The doses the caregiver set up, to tell which ones were not logged. */
  expected: string[];
  help: Help[];
}

export interface Stats {
  questions: number;
  /** Questions asked more than once, most repeated first. */
  repeated: { text: string; times: number }[];
  dosesLogged: number;
  dosesMissing: string[];
  helpAlerts: number;
}

export const MAX_SUMMARY_SENTENCES = 5;

function str(v: unknown, max: number): string {
  return typeof v === "string" ? v.trim().slice(0, max) : "";
}

function list<T>(v: unknown, max: number, map: (o: Record<string, unknown>) => T | null): T[] {
  return (Array.isArray(v) ? v : [])
    .map((x) => (x && typeof x === "object" ? map(x as Record<string, unknown>) : null))
    .filter((x): x is T => x !== null)
    .slice(0, max);
}

export function dayFrom(body: unknown): Day | null {
  const b = (body && typeof body === "object" ? body : {}) as Record<string, unknown>;
  const day = str(b.day, 80);
  if (!day) return null;
  return {
    lang: langFrom(b.lang),
    person: str(b.person, 60),
    day,
    talks: list(b.talks, 200, (o) => (str(o.heard, 300) ? { time: str(o.time, 20), heard: str(o.heard, 300), answer: str(o.answer, 400), action: str(o.action, 80) || undefined } : null)),
    doses: list(b.doses, 50, (o) => (str(o.name, 60) ? { time: str(o.time, 20), name: str(o.name, 60) } : null)),
    expected: (Array.isArray(b.expected) ? b.expected : []).map((x) => str(x, 60)).filter(Boolean).slice(0, 20),
    help: list(b.help, 20, (o) => ({ time: str(o.time, 20), said: str(o.said, 200), sent: o.sent === true })),
  };
}

function plain(s: string): string {
  return s.toLowerCase().normalize("NFD").replace(/\p{M}+/gu, "").replace(/[^\p{L}\p{N} ]/gu, "").replace(/\s+/g, " ").trim();
}

export function statsOf(d: Day): Stats {
  const counts = new Map<string, { text: string; times: number }>();
  for (const t of d.talks) {
    const key = plain(t.heard);
    if (!key) continue;
    const c = counts.get(key) || { text: t.heard, times: 0 };
    c.times++;
    counts.set(key, c);
  }
  const logged = new Set(d.doses.map((x) => x.name));
  return {
    questions: d.talks.length,
    repeated: [...counts.values()].filter((c) => c.times > 1).sort((a, b) => b.times - a.times).slice(0, 5),
    dosesLogged: d.doses.length,
    dosesMissing: d.expected.filter((e) => !logged.has(e)),
    helpAlerts: d.help.length,
  };
}

export function summaryPrompt(d: Day, s: Stats): string {
  const es = d.lang === "es";
  const rules = es
    ? [
        `Escribes el resumen del día para la familia que cuida a ${d.person || "una persona mayor"} con problemas de memoria. Lo leen en su teléfono al final del día.`,
        `Entre tres y ${MAX_SUMMARY_SENTENCES} frases cortas, en español, cálidas y concretas. Sin listas, títulos ni emojis.`,
        "Empieza por lo más importante: un aviso de ayuda, una toma que falta o una preocupación que se repitió.",
        "Usa exactamente los números de <conteo>. Solo menciona horas, personas y temas que aparezcan en <registro>.",
        "No das diagnósticos ni consejos médicos. Si algo preocupa, sugiere hablarlo con su médico, sin alarmar.",
        "Si no hubo conversaciones, dilo en una frase y resume las tomas.",
      ]
    : [
        `You write the end-of-day summary for the family caring for ${d.person || "an older person"} with memory problems. They read it on their phone in the evening.`,
        `Three to ${MAX_SUMMARY_SENTENCES} short sentences in English, warm and concrete. No lists, headings or emoji.`,
        "Start with what matters most: a help alert, a missing dose or a worry that kept coming back.",
        "Use exactly the numbers in <counts>. Only mention times, people and topics that appear in <log>.",
        "No diagnoses or medical advice. If something is worrying, suggest talking to their doctor, without alarm.",
        "If there were no conversations, say so in one sentence and summarize the doses.",
      ];
  const counts = es
    ? `Preguntas al asistente: ${s.questions}\nPreguntas repetidas: ${s.repeated.map((r) => `«${r.text}» (${r.times} veces)`).join(", ") || "ninguna"}\nTomas anotadas: ${s.dosesLogged}\nTomas sin anotar: ${s.dosesMissing.join(", ") || "ninguna"}\nAvisos de ayuda: ${s.helpAlerts}`
    : `Questions to the assistant: ${s.questions}\nRepeated questions: ${s.repeated.map((r) => `"${r.text}" (${r.times} times)`).join(", ") || "none"}\nDoses logged: ${s.dosesLogged}\nDoses not logged: ${s.dosesMissing.join(", ") || "none"}\nHelp alerts: ${s.helpAlerts}`;
  const log = [
    ...d.talks.map((t) => `${t.time} · ${es ? "preguntó" : "asked"}: ${t.heard} · ${es ? "respuesta" : "answer"}: ${t.answer}${t.action ? ` · ${es ? "acción" : "action"}: ${t.action}` : ""}`),
    ...d.doses.map((x) => `${x.time} · ${es ? "toma anotada" : "dose logged"}: ${x.name}`),
    ...d.help.map((h) => `${h.time} · ${es ? "aviso de ayuda" : "help alert"}: ${h.said} (${h.sent ? (es ? "enviado" : "sent") : (es ? "no salió" : "not sent")})`),
  ].join("\n");
  const tags = es ? ["conteo", "registro"] : ["counts", "log"];
  return `${rules.join("\n")}\n\n${es ? "Día" : "Day"}: ${d.day}\n\n<${tags[0]}>\n${counts}\n</${tags[0]}>\n\n<${tags[1]}>\n${log || (es ? "(vacío)" : "(empty)")}\n</${tags[1]}>`;
}

/** Writes the summary with Muse Spark. Sentences that fail the guard are dropped, never replaced, in a summary. */
export async function summarize(d: Day, apiKey: string): Promise<{ text: string; stats: Stats; dropped: string[] }> {
  const stats = statsOf(d);
  let raw = "";
  await streamMuse({
    apiKey,
    messages: [
      { role: "developer", content: summaryPrompt(d, stats) },
      { role: "user", content: d.lang === "es" ? "Escribe el resumen de hoy." : "Write today's summary." },
    ],
    tools: [],
    timeoutMs: 25_000,
    onText: (delta) => (raw += delta),
  });
  const { sentences, rest } = takeSentences(raw + "\n");
  const all = [...sentences, rest.trim()].filter(Boolean);
  const ctx = {
    lang: d.lang,
    assistant: d.person,
    profile: { name: d.person, free: d.expected.join(", ") },
    // Every time and name in the day's log counts as known.
    facts: { now: d.day, visits: [], doses: [] as string[] },
    said: [...d.talks.flatMap((t) => [t.time, t.heard, t.answer, t.action || ""]), ...d.doses.map((x) => `${x.time} ${x.name}`), ...d.help.map((h) => `${h.time} ${h.said}`)],
  };
  ctx.facts.doses = ctx.said;
  const kept: string[] = [];
  const dropped: string[] = [];
  for (const s of all) {
    if (kept.length >= MAX_SUMMARY_SENTENCES) break;
    const v = check(s, ctx);
    if (v.ok && v.text) kept.push(v.text);
    else if (!v.ok) dropped.push(v.reason);
  }
  return { text: kept.join(" "), stats, dropped };
}
