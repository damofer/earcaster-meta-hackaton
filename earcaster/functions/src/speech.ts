import { Facts, Lang } from "./types";

/** At most two sentences per reply (R4). */
export const MAX_SENTENCES = 2;
export const SAFE_SENTENCE = "Eso no lo tengo apuntado.";
export const SAFE_SENTENCE_EN = "I don't have that written down.";

const DAYS = ["domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"];
const HOURS: Record<string, string> = {
  una: "1", dos: "2", tres: "3", cuatro: "4", cinco: "5", seis: "6",
  siete: "7", ocho: "8", nueve: "9", diez: "10", once: "11", doce: "12",
};
const DAYS_EN = ["sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"];
const HOURS_EN: Record<string, string> = {
  one: "1", two: "2", three: "3", four: "4", five: "5", six: "6",
  seven: "7", eight: "8", nine: "9", ten: "10", eleven: "11", twelve: "12",
};

function plain(s: string): string {
  return s.toLowerCase().normalize("NFD").replace(/\p{M}+/gu, "");
}

/**
 * Splits the incoming text into complete sentences. Returns the finished sentences and the remainder,
 * which waits for more text to arrive.
 */
export function takeSentences(buffer: string): { sentences: string[]; rest: string } {
  const sentences: string[] = [];
  const re = /[^.!?…]*[.!?…]+["»”)]*(\s+|$)/y;
  let last = 0;
  let m: RegExpExecArray | null;
  re.lastIndex = 0;
  while ((m = re.exec(buffer)) !== null) {
    // With no space after it, the sentence might continue («3.5», «¿Sí?…»): wait.
    if (m[1] === "" ) break;
    const s = m[0].trim();
    if (s) sentences.push(s);
    last = re.lastIndex;
    if (last >= buffer.length) break;
  }
  return { sentences, rest: buffer.slice(last) };
}

/**
 * Checks a sentence before it plays (R4): it never asks the person to take something and never names a day or a time
 * that is not in the current context. Returns the sentence, a safe replacement, or null to silence it.
 */
export function checkSentence(sentence: string, facts: Facts, lang: Lang = "es"): string | null {
  if (lang === "en") return checkSentenceEn(sentence, facts);
  const s = plain(sentence);
  if (/\b(tomal[ao]s?|tomel[ao]s?|tomatel[ao]s?|tomesel[ao]s?|tomese|tomate)\b/.test(s)) return null;
  const known = plain([facts.now, ...facts.visits, ...facts.doses].join(" "));
  for (const day of DAYS) {
    const d = plain(day);
    if (new RegExp(`\\b${d}\\b`).test(s) && !new RegExp(`\\b${d}\\b`).test(known)) return SAFE_SENTENCE;
  }
  const times = s.match(/\ba las? (\d{1,2}|[a-z]+)\b/g) || [];
  for (const t of times) {
    const word = t.replace(/^a las? /, "");
    const hour = /^\d+$/.test(word) ? word : HOURS[word];
    if (hour === undefined) continue;
    if (!new RegExp(`\\b(la|las) ${hour}\\b`).test(known)) return SAFE_SENTENCE;
  }
  return sentence;
}

/**
 * The same check in English. The current data says times like «at half past 5», «5 o'clock» or «5:10»
 * (SpokenText on the phone), so an hour counts as known if it appears in one of those forms.
 */
function checkSentenceEn(sentence: string, facts: Facts): string | null {
  const s = plain(sentence).replace(/[’]/g, "'");
  // «Take it», «take your pill», «don't forget to take…»: it never asks the person to take something.
  const MEDICINE = "(pill|pills|medicine|medication|medications|dose|tablet|tablets)";
  if (
    new RegExp(`\\btake (it|them|this|that|one|your ${MEDICINE}|the ${MEDICINE}|a ${MEDICINE})\\b`).test(s) ||
    /\b(go ahead and|don't forget to|remember to|time to|you should|you need to) take\b/.test(s)
  ) {
    return null;
  }
  const known = plain([facts.now, ...facts.visits, ...facts.doses].join(" "));
  for (const day of DAYS_EN) {
    if (new RegExp(`\\b${day}\\b`).test(s) && !new RegExp(`\\b${day}\\b`).test(known)) return SAFE_SENTENCE_EN;
  }
  const times = s.match(/\b(at|by|around|until) (\d{1,2}|[a-z]+)\b/g) || [];
  for (const t of times) {
    const word = t.replace(/^\w+ /, "");
    const hour = /^\d+$/.test(word) ? word : HOURS_EN[word];
    if (hour === undefined) continue;
    if (!new RegExp(`(\\b${hour} o'clock|\\bpast ${hour}\\b|\\b${hour}:\\d\\d|\\bat ${hour}\\b)`).test(known)) return SAFE_SENTENCE_EN;
  }
  return sentence;
}
