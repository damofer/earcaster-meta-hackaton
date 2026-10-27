import { Profile } from "./types";

/**
 * Spec 020: a family message becomes proposals that the caregiver approves on the phone. The earcaster-muse engine
 * reads the message with Muse Spark and checks each proposal against it; this side sends the visit schedule and the
 * profile's free texts, never the contacts' phone numbers.
 */
export const MUSE_UPDATES_URL = process.env.MUSE_UPDATES_URL || "https://us-central1-earcaster.cloudfunctions.net/museUpdates";

export interface WeeklyVisit { name: string; days: number; minute: number }

/** The weekly visits saved in the patient's settings (the app's fixed_visits, a JSON text inside the config). */
export function weeklyVisitsFrom(config: unknown): WeeklyVisit[] {
  try {
    const o = JSON.parse(typeof config === "string" ? config : "{}");
    const list = JSON.parse(typeof o.fixed_visits === "string" ? o.fixed_visits : "[]");
    if (!Array.isArray(list)) return [];
    return list
      .map((v) => ({ name: typeof v?.name === "string" ? v.name.trim() : "", days: Number(v?.days) & 127, minute: Number(v?.minute) }))
      .filter((v) => v.name && Number.isInteger(v.minute));
  } catch {
    return [];
  }
}

/** Phone numbers never go to Meta, even if a relative wrote one in the message. */
export function withoutNumbers(s: string): string {
  return s.replace(/\+?\d[\d\s().-]{5,}\d/g, "[…]");
}

/** Only the free texts of the profile: no contacts, no numbers. */
export function profileTexts(p: Profile): Record<string, string> {
  return { name: p.name || "", callMe: p.callMe || "", home: p.home || "", family: p.family || "", routine: p.routine || "", likes: p.likes || "", free: p.free || "" };
}

export async function askUpdates(token: string, body: object): Promise<unknown> {
  const res = await fetch(MUSE_UPDATES_URL, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(40_000),
  });
  if (!res.ok) throw new Error(`museUpdates ${res.status}`);
  return res.json();
}
