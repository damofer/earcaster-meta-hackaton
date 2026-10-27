import { AiSettings, Lang } from "./types";

export const DEFAULT_NAME = "Clara";
/** The assistant's name in English, as in the English video. */
export const DEFAULT_NAME_EN = "Grace";
export const DEFAULT_MAX = 50;

/** Reads the AI settings from the patient's settings JSON. Anything missing is left off or at its default. */
export function aiSettings(config: string | undefined, lang: Lang = "es"): AiSettings {
  let o: Record<string, unknown> = {};
  try {
    const parsed = JSON.parse(config || "{}");
    if (parsed && typeof parsed === "object") o = parsed as Record<string, unknown>;
  } catch {
    // Broken settings: the AI stays off.
  }
  const max = typeof o.ai_max === "number" && o.ai_max > 0 ? Math.min(Math.floor(o.ai_max), 500) : DEFAULT_MAX;
  const name = typeof o.ai_name === "string" && o.ai_name.trim() ? o.ai_name.trim() : lang === "en" ? DEFAULT_NAME_EN : DEFAULT_NAME;
  return {
    on: o.ai_on === true,
    name,
    voice: typeof o.ai_voice === "string" ? o.ai_voice : "",
    max,
  };
}
