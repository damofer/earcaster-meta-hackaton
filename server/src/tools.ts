import { Action, Allowed, Lang } from "./types";

/** A function tool in the chat completions format. */
export interface Tool {
  type: "function";
  function: { name: string; description: string; parameters: Record<string, unknown> };
}

const TEXT = {
  es: {
    call: (list: string) => `Llama por teléfono a una de estas personas: ${list}. Solo si la persona pide llamar a alguien de la lista.`,
    who: "El nombre tal como aparece en la lista",
    light: "Enciende o apaga la linterna del teléfono.",
    dose: (list: string) => `Anota una toma de esta lista, solo si la persona dice que ya se la tomó: ${list}.`,
    help: "Avisa a la familia que la persona necesita ayuda. Solo después de que la persona diga que sí quiere que avises.",
  },
  en: {
    call: (list: string) => `Phones one of these people: ${list}. Only if the person asks to call someone on the list.`,
    who: "The name exactly as it appears in the list",
    light: "Turns the phone's flashlight on or off.",
    dose: (list: string) => `Logs a dose from this list, only if the person says they already took it: ${list}.`,
    help: "Tells the family the person needs help. Only after the person says yes, they want you to tell them.",
  },
};

function fn(name: string, description: string, properties: Record<string, unknown>, required: string[]): Tool {
  return { type: "function", function: { name, description, parameters: { type: "object", properties, required } } };
}

/** Only what the caregiver allowed becomes a tool: the model cannot ask for anything else. */
export function toolsFor(allowed: Allowed, lang: Lang): Tool[] {
  const t = TEXT[lang];
  const out: Tool[] = [];
  const people = allowed.call.filter((c) => c.name.trim());
  if (people.length) {
    const list = people.map((c) => (c.relation ? `${c.name} (${c.relation})` : c.name)).join(", ");
    out.push(fn("call_person", t.call(list), { who: { type: "string", description: t.who } }, ["who"]));
  }
  if (allowed.light) out.push(fn("flashlight", t.light, { on: { type: "boolean" } }, ["on"]));
  if (allowed.doses.length) {
    out.push(fn("log_dose", t.dose(allowed.doses.join(", ")), { dose: { type: "string", enum: allowed.doses } }, ["dose"]));
  }
  if (allowed.help) out.push(fn("alert_family", t.help, {}, []));
  return out;
}

function plain(s: string): string {
  return s.toLowerCase().normalize("NFD").replace(/\p{M}+/gu, "").trim();
}

/** Turns a tool call into an action, checking it against what is allowed once more. Anything else is dropped. */
export function resolve(name: string, rawArgs: string, allowed: Allowed): Action | null {
  let args: Record<string, unknown> = {};
  try {
    const parsed = JSON.parse(rawArgs || "{}");
    if (parsed && typeof parsed === "object") args = parsed;
  } catch {
    return null;
  }
  switch (name) {
    case "call_person": {
      const who = typeof args.who === "string" ? plain(args.who) : "";
      if (!who) return null;
      const found = allowed.call.find((c) => plain(c.name) === who)
        || allowed.call.find((c) => plain(c.name).split(/\s+/)[0] === who.split(/\s+/)[0]);
      return found ? { type: "call", name: found.name } : null;
    }
    case "flashlight":
      return allowed.light && typeof args.on === "boolean" ? { type: "light", on: args.on } : null;
    case "log_dose":
      return typeof args.dose === "string" && allowed.doses.includes(args.dose) ? { type: "dose", name: args.dose } : null;
    case "alert_family":
      return allowed.help ? { type: "help" } : null;
    default:
      return null;
  }
}
