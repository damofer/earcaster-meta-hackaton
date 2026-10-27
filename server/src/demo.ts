import { Lang, Question } from "./types";

/**
 * The public demo: a made-up person, so judges can try the engine from a web page without an account.
 * Nothing here is real data. The facts are built from the current time in Colombia, as the phone would send them.
 */
const DAYS = {
  es: ["domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"],
  en: ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"],
};
const MONTHS = {
  es: ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"],
  en: ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"],
};

/** Wall-clock parts in Bogotá (UTC-5 all year). */
function bogota(d: Date) {
  const t = new Date(d.getTime() - 5 * 3600_000);
  return { year: t.getUTCFullYear(), month: t.getUTCMonth(), day: t.getUTCDate(), weekday: t.getUTCDay(), hour: t.getUTCHours(), minute: t.getUTCMinutes() };
}

/** "las 6 y media de la tarde" / "half past 6 in the evening", the way the phone says times. */
export function spokenTime(hour: number, minute: number, lang: Lang): string {
  const h12 = hour % 12 === 0 ? 12 : hour % 12;
  const m = Math.floor(minute / 5) * 5;
  if (lang === "en") {
    const part = hour < 12 ? "in the morning" : hour < 18 ? "in the afternoon" : "in the evening";
    const when = m === 0 ? `${h12} o'clock` : m === 30 ? `half past ${h12}` : `${h12}:${String(m).padStart(2, "0")}`;
    return `${when} ${part}`;
  }
  const part = hour < 12 ? "de la mañana" : hour < 19 ? "de la tarde" : "de la noche";
  const art = h12 === 1 ? "la" : "las";
  const when = m === 0 ? `${art} ${h12} en punto` : m === 30 ? `${art} ${h12} y media` : `${art} ${h12} y ${m}`;
  return `${when} ${part}`;
}

function spokenDate(d: Date, lang: Lang): string {
  const b = bogota(d);
  return lang === "en"
    ? `${DAYS.en[b.weekday]}, ${MONTHS.en[b.month]} ${b.day}`
    : `${DAYS.es[b.weekday]} ${b.day} de ${MONTHS.es[b.month]}`;
}

export function demoQuestion(text: string, lang: Lang, now = new Date()): Question {
  const b = bogota(now);
  // Lucía visits on the next Sunday at 5 in the afternoon (a week from today if today is Sunday).
  const daysToSunday = ((7 - b.weekday) % 7) || 7;
  const sunday = new Date(now.getTime() + daysToSunday * 86_400_000);
  const es = lang === "es";
  const nowText = es
    ? `${spokenDate(now, lang)} de ${b.year}, ${spokenTime(b.hour, b.minute, lang)}`
    : `${spokenDate(now, lang)}, ${b.year}, ${spokenTime(b.hour, b.minute, lang)}`;
  const visit = es
    ? `Lucía: el ${spokenDate(sunday, lang)} a ${spokenTime(17, 0, lang).replace(" en punto", "")}`
    : `Lucía: ${spokenDate(sunday, lang)} at 5 o'clock in the afternoon`;
  const doses = b.hour >= 9 ? [es ? "Pastilla de la mañana, anotada a las 9 y 10" : "Morning pill, logged at 9:10"] : [];
  return {
    lang,
    assistant: es ? "Clara" : "Grace",
    text,
    history: [],
    profile: es
      ? {
          name: "Rosa",
          callMe: "Rosita",
          home: "Vive en Barranquilla con su hija Lucía.",
          family: "Su hija Lucía trabaja de día y la visita los domingos. Su hijo Andrés vive en Bogotá y la llama los viernes.",
          routine: "Desayuna a las 8, camina por el patio en la tarde y ve la novela a las 7 de la noche.",
          likes: "Le gustan los boleros, las plantas y hablar de cuando era maestra.",
          sensitive: "Su esposo Jorge murió hace años. Si pregunta por él, recuérdale con cariño lo mucho que la quería, sin decirle de golpe que murió.",
        }
      : {
          name: "Rose",
          callMe: "Rosie",
          home: "Lives in Barranquilla with her daughter Lucía.",
          family: "Her daughter Lucía works during the day and visits on Sundays. Her son Andrés lives in Bogotá and calls on Fridays.",
          routine: "Breakfast at 8, a walk in the garden in the afternoon, her soap opera at 7 in the evening.",
          likes: "She loves boleros, plants and talking about when she was a teacher.",
          sensitive: "Her husband Jorge died years ago. If she asks about him, kindly remind her how much he loved her, without bluntly saying he died.",
        },
    facts: { now: nowText, visits: [visit], doses },
    allowed: {
      call: [{ name: "Andrés", relation: es ? "su hijo" : "her son" }, { name: "Lucía", relation: es ? "su hija" : "her daughter" }],
      light: true,
      doses: [es ? "Pastilla de la noche" : "Evening pill"],
      help: true,
    },
  };
}

/** A small per-instance budget, so the open demo cannot run up the Meta bill. */
export class Budget {
  private day = "";
  private total = 0;
  private perIp = new Map<string, number>();

  constructor(private readonly maxPerDay: number, private readonly maxPerIp: number) {}

  take(ip: string, now = new Date()): boolean {
    const day = now.toISOString().slice(0, 10);
    if (day !== this.day) {
      this.day = day;
      this.total = 0;
      this.perIp.clear();
    }
    const used = this.perIp.get(ip) || 0;
    if (this.total >= this.maxPerDay || used >= this.maxPerIp) return false;
    this.total++;
    this.perIp.set(ip, used + 1);
    return true;
  }
}
