/** The language the person speaks. Spanish unless the caller says English. */
export type Lang = "es" | "en";

export function langFrom(v: unknown): Lang {
  return v === "en" ? "en" : "es";
}

/** What the caregiver wrote about the person. Free text, in any language. */
export interface Profile {
  name?: string;
  callMe?: string;
  home?: string;
  family?: string;
  routine?: string;
  likes?: string;
  sensitive?: string;
  free?: string;
}

/** What the phone knows right now. The engine never states a day, time or visit outside this. */
export interface Facts {
  now: string;
  visits: string[];
  doses: string[];
}

/**
 * What the assistant may do, as the caregiver allowed it. Phone numbers never reach the engine:
 * the caller maps a name back to its number.
 */
export interface Allowed {
  call: { name: string; relation?: string }[];
  light: boolean;
  doses: string[];
  help: boolean;
}

export interface Turn {
  q: string;
  a: string;
}

export interface Question {
  lang: Lang;
  assistant: string;
  text: string;
  history: Turn[];
  profile: Profile;
  facts: Facts;
  allowed: Allowed;
}

export type Action =
  | { type: "call"; name: string }
  | { type: "light"; on: boolean }
  | { type: "dose"; name: string }
  | { type: "help" };
