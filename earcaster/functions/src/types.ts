/** The language the phone speaks: what the AI answers in and the voice it uses. Spanish if the phone does not say. */
export type Lang = "es" | "en";

export function langFrom(v: unknown): Lang {
  return v === "en" ? "en" : "es";
}

/** The patient profile (spec 016), as the app stores it in patients/{pid}/private/profile. */
export interface Profile {
  name?: string;
  callMe?: string;
  home?: string;
  family?: string;
  routine?: string;
  likes?: string;
  sensitive?: string;
  free?: string;
  calls?: { name?: string; number?: string; relation?: string }[];
  light?: boolean;
  doses?: string[];
  help?: boolean;
}

/** What the phone knows at the moment of asking: nothing outside this is ever made up (R4). */
export interface Facts {
  now: string;
  visits: string[];
  doses: string[];
}

/** Who writes the answers (spec 018): Claude (Anthropic) or Muse Spark (Meta). */
export type AiModel = "claude" | "meta";

/** AI settings the caregiver saves in the patient's settings (spec 015). */
export interface AiSettings {
  on: boolean;
  name: string;
  voice: string;
  max: number;
  model: AiModel;
}

export interface Turn {
  q: string;
  a: string;
}

/** An action the phone performs with its usual safeguards (R5). */
export type Action =
  | { type: "call"; name: string; number: string }
  | { type: "light"; on: boolean }
  | { type: "dose"; name: string }
  | { type: "help" };
