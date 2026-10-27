import { plain } from "./guard";
import { streamMuse } from "./muse";
import { Lang, langFrom } from "./types";

/**
 * Spec 022: groups the person's questions into topics ("¿dónde está Jorge?" and "¿Jorge ya llegó?" are one topic),
 * so the caller can tell a new worry from the usual ones. The model may only group the questions it receives: every
 * text in a group must be one of them, exactly, and each label must come from the texts of its group. Detecting a
 * change is done by the caller, in code.
 */
export interface Topic { label: string; texts: string[] }

export const MAX_TEXTS = 300;

export function textsFrom(body: unknown): { lang: Lang; texts: string[] } | null {
  const b = (body && typeof body === "object" ? body : {}) as Record<string, unknown>;
  const texts = [...new Set((Array.isArray(b.texts) ? b.texts : [])
    .map((t) => (typeof t === "string" ? t.trim().slice(0, 200) : ""))
    .filter(Boolean))].slice(0, MAX_TEXTS);
  return texts.length ? { lang: langFrom(b.lang), texts } : null;
}

export function topicsPrompt(lang: Lang): string {
  return [
    "You group the questions a person with memory loss asked a voice assistant, so their family can see what they ask about.",
    "Put questions about the same thing in one topic: the same person, the same place, the same worry.",
    "Every question you receive must go in exactly one topic, copied exactly as you received it. A question alone is a topic too.",
    `Each label is one to three words taken from the questions themselves, in ${lang === "en" ? "English" : "Spanish"}, usually a name or a thing ("Jorge", "Lucía", "the keys").`,
    'Answer with JSON only: {"topics":[{"label":"...","texts":["...", "..."]}]}',
  ].join("\n");
}

/** Keeps only groups made of the received texts; a text the model left out becomes a topic of its own. */
export function checkTopics(raw: unknown, texts: string[]): { topics: Topic[]; dropped: string[] } {
  const known = new Set(texts);
  const used = new Set<string>();
  const topics: Topic[] = [];
  const dropped: string[] = [];
  const list = raw && typeof raw === "object" ? (raw as Record<string, unknown>).topics : null;
  for (const t of Array.isArray(list) ? list : []) {
    const o = (t && typeof t === "object" ? t : {}) as Record<string, unknown>;
    const label = typeof o.label === "string" ? o.label.trim().slice(0, 40) : "";
    const group = (Array.isArray(o.texts) ? o.texts : []).filter((x): x is string => typeof x === "string").map((x) => x.trim());
    const own = group.filter((x) => known.has(x) && !used.has(x));
    if (own.length < group.length) dropped.push("unknown text");
    if (!own.length) continue;
    const words = plain(own.join(" "));
    if (!label || !label.split(/\s+/).every((w) => words.includes(plain(w)))) {
      dropped.push(`label ${label}`);
      continue;
    }
    own.forEach((x) => used.add(x));
    topics.push({ label, texts: own });
  }
  for (const x of texts) if (!used.has(x)) topics.push({ label: "", texts: [x] });
  return { topics, dropped };
}

function jsonIn(text: string): unknown {
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start < 0 || end <= start) return {};
  try {
    return JSON.parse(text.slice(start, end + 1));
  } catch {
    return {};
  }
}

export async function groupTopics(lang: Lang, texts: string[], apiKey: string): Promise<{ topics: Topic[]; dropped: string[] }> {
  let raw = "";
  await streamMuse({
    apiKey,
    messages: [
      { role: "developer", content: topicsPrompt(lang) },
      { role: "user", content: `<questions>\n${texts.map((t) => JSON.stringify(t)).join("\n")}\n</questions>` },
    ],
    tools: [],
    timeoutMs: 30_000,
    maxTokens: 6000,
    onText: (delta) => (raw += delta),
  });
  return checkTopics(jsonIn(raw), texts);
}
