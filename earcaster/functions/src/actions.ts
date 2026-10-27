import { Action, Lang, Profile } from "./types";

/** A tool in the shape the Claude API expects. */
export interface ToolSpec {
  name: string;
  description: string;
  input_schema: { type: "object"; properties: Record<string, unknown>; required?: string[] };
}

function plain(s: string): string {
  return s.toLowerCase().normalize("NFD").replace(/\p{M}+/gu, "").trim();
}

function callable(profile: Profile) {
  return (profile.calls || []).filter((c) => c.number && c.number.trim() && c.name && c.name.trim());
}

/** The tools' descriptions in each language. Their names and fields stay the same, so resolve() does not change. */
const TEXT = {
  es: {
    call: (list: string) => `Llama por teléfono a una de estas personas: ${list}. Úsala solo si te pide llamar a alguien de esta lista.`,
    who: "El nombre, tal como aparece en la lista",
    light: "Enciende o apaga la linterna del teléfono.",
    dose: (list: string) => `Anota que ya tomó una de estas tomas, solo si ella dice que ya la tomó: ${list}.`,
    help: "Avisa a su familia que necesita ayuda. Úsala solo si ella dice que sí quiere que avises.",
  },
  en: {
    call: (list: string) => `Calls one of these people on the phone: ${list}. Use it only if they ask you to call someone on this list.`,
    who: "The name, exactly as it appears in the list",
    light: "Turns the phone's flashlight on or off.",
    dose: (list: string) => `Logs that they already took one of these doses, only if they say they already took it: ${list}.`,
    help: "Alerts their family that they need help. Use it only if they say yes, they want you to alert them.",
  },
};

/** Only the actions the caregiver checked in the profile (016 R2; 017 R5). */
export function tools(profile: Profile, doseNames: string[], lang: Lang = "es"): ToolSpec[] {
  const t = TEXT[lang];
  const out: ToolSpec[] = [];
  const calls = callable(profile);
  if (calls.length) {
    out.push({
      name: "llamar",
      description: t.call(calls.map((c) => (c.relation ? `${c.name} (${c.relation})` : c.name)).join(", ")),
      input_schema: {
        type: "object",
        properties: { quien: { type: "string", description: t.who } },
        required: ["quien"],
      },
    });
  }
  if (profile.light) {
    out.push({
      name: "linterna",
      description: t.light,
      input_schema: { type: "object", properties: { encender: { type: "boolean" } }, required: ["encender"] },
    });
  }
  const doses = (profile.doses || []).filter((d) => doseNames.length === 0 || doseNames.includes(d));
  if (doses.length) {
    out.push({
      name: "anotar_toma",
      description: t.dose(doses.join(", ")),
      input_schema: { type: "object", properties: { toma: { type: "string", enum: doses } }, required: ["toma"] },
    });
  }
  if (profile.help) {
    out.push({
      name: "avisar_ayuda",
      description: t.help,
      input_schema: { type: "object", properties: {} },
    });
  }
  return out;
}

/**
 * Turns what Claude requested into an action, checking again that it is allowed.
 * The number always comes from the profile, never from the AI.
 */
export function resolve(name: string, input: unknown, profile: Profile): Action | null {
  const args = (input && typeof input === "object" ? input : {}) as Record<string, unknown>;
  if (name === "llamar") {
    const who = typeof args.quien === "string" ? plain(args.quien) : "";
    if (!who) return null;
    const calls = callable(profile);
    const exact = calls.filter((c) => plain(c.name!) === who || (c.relation && plain(c.relation) === who));
    const loose = calls.filter((c) => plain(c.name!).includes(who) || who.includes(plain(c.name!)));
    const found = exact.length === 1 ? exact[0] : loose.length === 1 ? loose[0] : null;
    return found ? { type: "call", name: found.name!.trim(), number: found.number!.trim() } : null;
  }
  if (name === "linterna" && profile.light && typeof args.encender === "boolean") {
    return { type: "light", on: args.encender };
  }
  if (name === "anotar_toma" && typeof args.toma === "string" && (profile.doses || []).includes(args.toma)) {
    return { type: "dose", name: args.toma };
  }
  if (name === "avisar_ayuda" && profile.help) return { type: "help" };
  return null;
}
