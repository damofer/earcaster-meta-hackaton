import { Facts, Lang, Profile } from "./types";

/**
 * Every sentence passes through here before it is spoken. The model is good, but a person with dementia cannot tell
 * a made-up visit from a real one, so the rules are enforced in code, not only asked for in the prompt:
 *
 * 1. It never tells the person to take a medication (dropped).
 * 2. Every weekday, clock hour and calendar date it says must be in the facts the phone sent.
 * 3. Every person or place it names must appear in the profile, the facts or the conversation.
 *
 * A sentence that breaks 2 or 3 is replaced by an honest "I don't have that written down".
 */
export const SAFE = { es: "Eso no lo tengo apuntado.", en: "I don't have that written down." };

export type Verdict = { ok: true; text: string } | { ok: false; replacement: string | null; reason: string };

export function plain(s: string): string {
  return s.toLowerCase().normalize("NFD").replace(/\p{M}+/gu, "").replace(/[’]/g, "'");
}

const WEEKDAYS = {
  es: ["lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo"],
  en: ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"],
};
const MONTHS = {
  es: ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"],
  en: ["january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"],
};
const NUMBER_WORDS: Record<string, number> = {
  una: 1, uno: 1, dos: 2, tres: 3, cuatro: 4, cinco: 5, seis: 6, siete: 7, ocho: 8, nueve: 9, diez: 10, once: 11, doce: 12,
  one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7, eight: 8, nine: 9, ten: 10, eleven: 11, twelve: 12,
};
const MEDICATION = {
  es: /\b(tomate|tomese|tomela|tomelo|tomalas?|tomalos?|tomatel[ao]s?|tomesel[ao]s?|no (te )?olvides (de )?tomar|tienes que tomar|debes tomar|hay que tomar)\b/,
  en: /\b(take (it|them|your|the|this|that)|don'?t forget (to take|your)|you (need|have|should) to take|time (to take|for your) )/,
};
/** Hours as the phone writes them in the facts and as the model tends to say them. */
const HOUR_PATTERNS = {
  es: /\ba las? (\d{1,2}|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)\b|\b(\d{1,2}):\d{2}\b/g,
  en: /\bat (\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\b|\b(\d{1,2}):\d{2}\b|\b(\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve) ?(o'clock|am|pm|a\.m\.|p\.m\.)/g,
};

export function hoursIn(text: string, lang: Lang): Set<number> {
  const out = new Set<number>();
  for (const re of [HOUR_PATTERNS.es, HOUR_PATTERNS.en]) {
    for (const m of plain(text).matchAll(re)) {
      const raw = m.slice(1).find((g) => g !== undefined && !/o'clock|am|pm|a\.m\.|p\.m\./.test(g));
      if (raw === undefined) continue;
      const n = /^\d+$/.test(raw) ? Number(raw) : NUMBER_WORDS[raw];
      if (n !== undefined) out.add(n % 12);
    }
  }
  void lang;
  return out;
}

/** Calendar dates as "20 de octubre" or "October 20" / "20 October". */
export function datesIn(text: string): Set<string> {
  const out = new Set<string>();
  const s = plain(text);
  for (const lang of ["es", "en"] as const) {
    MONTHS[lang].forEach((month, i) => {
      for (const m of s.matchAll(new RegExp(`\\b(\\d{1,2}) (de )?${month}\\b|\\b${month} (\\d{1,2})\\b`, "g"))) {
        out.add(`${Number(m[1] ?? m[3])}-${i + 1}`);
      }
    });
  }
  return out;
}

/** Capitalized words that are not the first word of the sentence: names of people and places. */
export function namesIn(sentence: string): string[] {
  const words = sentence.split(/\s+/).map((w) => w.replace(/^[¿¡"«(]+|[.,;:!?…"»)]+$/g, ""));
  const out: string[] = [];
  words.forEach((w, i) => {
    if (i === 0 || !/^\p{Lu}\p{Ll}+$/u.test(w)) return;
    // A word right after an opening mark or a full stop also starts a sentence.
    if (/[.!?…:]$/.test(sentence.split(/\s+/)[i - 1] || "")) return;
    out.push(w);
  });
  return out;
}

export interface Context {
  lang: Lang;
  assistant: string;
  profile: Profile;
  facts: Facts;
  /** What was said in this conversation, the person's words included. */
  said: string[];
}

export function knownText(c: Context): string {
  const p = c.profile;
  return [c.assistant, p.name, p.callMe, p.home, p.family, p.routine, p.likes, p.sensitive, p.free,
    c.facts.now, ...c.facts.visits, ...c.facts.doses, ...c.said].filter(Boolean).join(" \n ");
}

export function check(sentence: string, c: Context): Verdict {
  const s = plain(sentence);
  if (MEDICATION.es.test(s) || MEDICATION.en.test(s)) return { ok: false, replacement: null, reason: "medication" };

  const facts = [c.facts.now, ...c.facts.visits, ...c.facts.doses].join(" \n ");
  const known = plain(facts);
  for (const day of [...WEEKDAYS.es, ...WEEKDAYS.en]) {
    if (new RegExp(`\\b${day}\\b`).test(s) && !new RegExp(`\\b${day}\\b`).test(known)) {
      return { ok: false, replacement: SAFE[c.lang], reason: `weekday ${day}` };
    }
  }
  const knownHours = hoursIn(facts, c.lang);
  for (const h of hoursIn(sentence, c.lang)) {
    if (!knownHours.has(h)) return { ok: false, replacement: SAFE[c.lang], reason: `hour ${h}` };
  }
  const knownDates = datesIn(facts);
  for (const d of datesIn(sentence)) {
    if (!knownDates.has(d)) return { ok: false, replacement: SAFE[c.lang], reason: `date ${d}` };
  }
  const everything = plain(knownText(c));
  for (const name of namesIn(sentence)) {
    if (!new RegExp(`\\b${plain(name)}\\b`).test(everything)) {
      return { ok: false, replacement: SAFE[c.lang], reason: `name ${name}` };
    }
  }
  return { ok: true, text: speakable(sentence) };
}

/** What a voice can say: no markdown, emoji or lists. */
export function speakable(s: string): string {
  return s
    .replace(/[*_#`>|~]/g, "")
    .replace(/\p{Extended_Pictographic}/gu, "")
    .replace(/^\s*[-•\d]+[.)]?\s+/, "")
    .replace(/\s+/g, " ")
    .trim();
}

/** Splits streamed text into finished sentences and the part still waiting for more text. */
export function takeSentences(buffer: string): { sentences: string[]; rest: string } {
  const sentences: string[] = [];
  // A dot between digits ("5.30") does not end a sentence.
  const re = /(?:[^.!?…\n]|\.(?=\d))*[.!?…]+["»”)]*(?=\s|$)|[^\n]*\n/y;
  let last = 0;
  let m: RegExpExecArray | null;
  while ((m = re.exec(buffer)) && m[0].length) {
    if (re.lastIndex === buffer.length && !/\n$/.test(m[0])) break;
    const t = m[0].trim();
    if (t) sentences.push(t);
    last = re.lastIndex;
  }
  return { sentences, rest: buffer.slice(last) };
}
