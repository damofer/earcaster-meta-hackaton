/**
 * Earcaster's AI conversation (spec 017). Claude, or Meta's Muse Spark through the earcaster-muse engine (spec 018),
 * answers and ElevenLabs provides the voice; the keys
 * live here, in Secret Manager, never in the app. Only text arrives from the phone: audio never leaves it.
 */
import Anthropic from "@anthropic-ai/sdk";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { DocumentReference, FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { setGlobalOptions } from "firebase-functions/v2";
import { Request, onRequest } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import * as logger from "firebase-functions/logger";
import type { Response } from "express";

import { resolve, tools } from "./actions";
import { EvenChunker, frame } from "./frames";
import { askMuse } from "./muse";
import { systemPrompt } from "./prompt";
import { aiSettings } from "./settings";
import { MAX_SENTENCES, checkSentence, takeSentences } from "./speech";
import { Action, Facts, Lang, Profile, Turn, langFrom } from "./types";

const ANTHROPIC_API_KEY = defineSecret("ANTHROPIC_API_KEY");
const ELEVENLABS_API_KEY = defineSecret("ELEVENLABS_API_KEY");
/** The Claude model is a secret so it can be changed without releasing the app or the code. */
const CLAUDE_MODEL = defineSecret("CLAUDE_MODEL");
/** Shared with the earcaster-muse engine, which answers when the caregiver picks Meta (spec 018). */
const ENGINE_TOKEN = defineSecret("ENGINE_TOKEN");

/** Next to Firestore (nam5). */
setGlobalOptions({ region: "us-central1", maxInstances: 10 });
initializeApp();
const db = getFirestore();

const DAY_MS = 86_400_000;
/** Conversations are deleted automatically after 7 days (R10). */
const TALKS_KEEP_MS = 7 * DAY_MS;
const PREVIEWS_PER_DAY = 100;
/** ElevenLabs' fastest voice model, and PCM so the phone can play it as it arrives. */
const TTS_MODEL = "eleven_flash_v2_5";
const TTS_FORMAT = "pcm_22050";
/** Can be changed only to test against a fake server. */
const ELEVENLABS = process.env.ELEVENLABS_BASE_URL || "https://api.elevenlabs.io";

// --- What arrives from the phone ---

/** The phone's language: in the body (responder, preview) or in the address (voices). */
function langOf(req: Request): Lang {
  const body = (req.body || {}) as Record<string, unknown>;
  return langFrom(body.lang ?? req.query.lang);
}

function str(v: unknown, max: number): string {
  return typeof v === "string" ? v.trim().slice(0, max) : "";
}

function strings(v: unknown, maxItems: number, maxLen: number): string[] {
  return Array.isArray(v) ? v.map((x) => str(x, maxLen)).filter(Boolean).slice(0, maxItems) : [];
}

function factsFrom(v: unknown): Facts {
  const o = (v && typeof v === "object" ? v : {}) as Record<string, unknown>;
  return { now: str(o.now, 200), visits: strings(o.visits, 20, 200), doses: strings(o.doses, 20, 200) };
}

function historyFrom(v: unknown): Turn[] {
  if (!Array.isArray(v)) return [];
  return v
    .map((t) => (t && typeof t === "object" ? (t as Record<string, unknown>) : {}))
    .map((t) => ({ q: str(t.q, 300), a: str(t.a, 600) }))
    .filter((t) => t.q && t.a)
    .slice(-2);
}

/** What comes from the saved profile or from the one the caregiver is editing (preview). */
function profileFrom(v: unknown): Profile {
  const o = (v && typeof v === "object" ? v : {}) as Record<string, unknown>;
  const calls = Array.isArray(o.calls)
    ? o.calls.slice(0, 20).map((c) => {
        const x = (c && typeof c === "object" ? c : {}) as Record<string, unknown>;
        return { name: str(x.name, 100), number: str(x.number, 40), relation: str(x.relation, 100) };
      })
    : [];
  return {
    name: str(o.name, 4000),
    callMe: str(o.callMe, 4000),
    home: str(o.home, 4000),
    family: str(o.family, 4000),
    routine: str(o.routine, 4000),
    likes: str(o.likes, 4000),
    sensitive: str(o.sensitive, 4000),
    free: str(o.free, 4000),
    calls,
    light: o.light === true,
    doses: strings(o.doses, 20, 100),
    help: o.help === true,
  };
}

/** The doses their phrases log: those are the only ones the AI may log. */
function doseNamesFrom(config: unknown): string[] {
  try {
    const o = JSON.parse(typeof config === "string" ? config : "{}");
    const list = JSON.parse(typeof o.triggers === "string" ? o.triggers : "[]");
    if (!Array.isArray(list)) return [];
    return list.map((t) => (t && typeof t.dose_name === "string" ? t.dose_name.trim() : "")).filter(Boolean);
  } catch {
    return [];
  }
}

/** The calling Firebase user, or null. */
async function caller(req: Request): Promise<{ uid: string; anonymous: boolean } | null> {
  const m = (req.get("Authorization") || "").match(/^Bearer (.+)$/);
  if (!m) return null;
  try {
    const t = await getAuth().verifyIdToken(m[1]);
    return { uid: t.uid, anonymous: t.firebase?.sign_in_provider === "anonymous" };
  } catch {
    return null;
  }
}

// --- The voice ---

/**
 * Clara's voice from the landing video, from the ElevenLabs library: it plays if the
 * caregiver did not choose another. If the account cannot use it, the account's first Spanish voice plays.
 */
const VIDEO_VOICE = { id: "2Lb1en5ujrODDIqmp7F3", name: "Jhenny, suave y tranquila", search: "Jhenny Antiques" };

let videoVoiceChecked = false;
let videoVoiceRejected = false;
let fallbackVoice = "";
let englishVoice = "";

/** ElevenLabs rejects the voice (does not exist, not allowed or does not work with the model), before sending audio. */
class VoiceRejected extends Error {}

/**
 * The voice that plays if the caregiver did not choose one. In English, the account's first English voice
 * (the video's voice speaks with a Spanish accent); without one, the same as in Spanish.
 */
async function defaultVoice(lang: Lang = "es"): Promise<string> {
  if (lang === "en") {
    if (!englishVoice) englishVoice = (await listVoices()).find((v) => v.english)?.id || "";
    if (englishVoice) return englishVoice;
  }
  if (!videoVoiceRejected) return VIDEO_VOICE.id;
  if (!fallbackVoice) {
    const list = (await listVoices()).filter((v) => v.id !== VIDEO_VOICE.id);
    fallbackVoice = (list.find((v) => v.spanish) || list[0])?.id || "";
  }
  return fallbackVoice;
}

/** Once per instance, says «Hola» (Hello) with the video's voice to find out whether the account can use it. */
async function checkVideoVoice(): Promise<void> {
  if (videoVoiceChecked) return;
  try {
    const r = await tts("Hola.", VIDEO_VOICE.id);
    await r.body?.cancel();
    videoVoiceChecked = true;
  } catch (e) {
    // If ElevenLabs fails for another reason, it is tried again next time.
    if (!(e instanceof VoiceRejected)) return;
    videoVoiceChecked = true;
    videoVoiceRejected = true;
    logger.warn("ElevenLabs rejects the video voice; using the fallback voice", e);
  }
}

interface Voice {
  id: string;
  name: string;
  preview: string;
  spanish: boolean;
  english: boolean;
}

async function listVoices(): Promise<Voice[]> {
  const r = await fetch(`${ELEVENLABS}/v1/voices`, {
    headers: { "xi-api-key": ELEVENLABS_API_KEY.value().trim() },
  });
  if (!r.ok) throw new Error(`ElevenLabs voices ${r.status}`);
  const body = (await r.json()) as { voices?: Record<string, unknown>[] };
  return (body.voices || []).map(voiceFrom).filter((v) => v.id);
}

function voiceFrom(v: Record<string, unknown>): Voice {
  const labels = (v.labels || {}) as Record<string, unknown>;
  const verified = Array.isArray(v.verified_languages) ? (v.verified_languages as Record<string, unknown>[]) : [];
  const speaks = (code: string) =>
    String(labels.language || v.language || "").toLowerCase().startsWith(code) ||
    verified.some((l) => String(l.language || "").toLowerCase().startsWith(code));
  return {
    id: String(v.voice_id || ""),
    name: String(v.name || ""),
    preview: String(v.preview_url || ""),
    spanish: speaks("es"),
    english: speaks("en"),
  };
}

/** The sample of the video's voice, which is not in the account but in the library. Empty if not found. */
async function videoVoicePreview(): Promise<string> {
  try {
    const r = await fetch(`${ELEVENLABS}/v1/shared-voices?page_size=20&search=${encodeURIComponent(VIDEO_VOICE.search)}`, {
      headers: { "xi-api-key": ELEVENLABS_API_KEY.value().trim() },
    });
    if (!r.ok) return "";
    const body = (await r.json()) as { voices?: Record<string, unknown>[] };
    return (body.voices || []).map(voiceFrom).find((v) => v.id === VIDEO_VOICE.id)?.preview || "";
  } catch {
    return "";
  }
}

async function tts(text: string, voice: string, lang: Lang = "es"): Promise<globalThis.Response> {
  const r = await fetch(
    `${ELEVENLABS}/v1/text-to-speech/${encodeURIComponent(voice)}/stream?output_format=${TTS_FORMAT}`,
    {
      method: "POST",
      headers: { "xi-api-key": ELEVENLABS_API_KEY.value().trim(), "Content-Type": "application/json" },
      body: JSON.stringify({ text, model_id: TTS_MODEL, language_code: lang }),
    },
  );
  if (r.ok && r.body) return r;
  const detail = `ElevenLabs ${r.status} ${(await r.text().catch(() => "")).slice(0, 300)}`;
  // 401 is the key and 429 the rate limit: another voice would hit the same thing.
  throw r.status >= 400 && r.status < 500 && r.status !== 401 && r.status !== 429
    ? new VoiceRejected(detail) : new Error(detail);
}

/** Requests the voice for a sentence and streams it to the phone as it arrives. */
async function speak(text: string, voice: string, res: Response, lang: Lang): Promise<void> {
  const r = await tts(text, voice, lang);
  const even = new EvenChunker();
  for await (const chunk of r.body as unknown as AsyncIterable<Uint8Array>) {
    const b = even.push(Buffer.from(chunk));
    if (b.length) res.write(frame("A", b));
  }
}

/** Like speak, but if ElevenLabs rejects the video's voice, says the sentence with the fallback voice. Returns the voice that played. */
async function speakOrFallback(text: string, voice: string, res: Response, lang: Lang): Promise<string> {
  try {
    await speak(text, voice, res, lang);
    return voice;
  } catch (e) {
    if (!(e instanceof VoiceRejected) || voice !== VIDEO_VOICE.id) throw e;
    videoVoiceRejected = true;
    const other = await defaultVoice();
    try {
      await speak(text, other, res, lang);
    } catch (e2) {
      // Not with the other one either: the problem was not the video's voice.
      videoVoiceRejected = false;
      throw e2;
    }
    logger.warn("ElevenLabs rejects the video voice; using the fallback voice", e);
    return other;
  }
}

// --- Claude ---

interface Answer {
  sentences: string[];
  action: Action | null;
}

/**
 * Asks the chosen model for the reply and delivers each checked sentence as soon as it is complete (onSentence),
 * without waiting for the rest. At most two sentences; the action, only if it is allowed.
 */
async function ask(
  settings: ReturnType<typeof aiSettings>,
  profile: Profile,
  facts: Facts,
  history: Turn[],
  text: string,
  doseNames: string[],
  lang: Lang,
  onSentence: (s: string) => void,
): Promise<Answer> {
  const sentences: string[] = [];
  const emit = (list: string[]) => {
    for (const raw of list) {
      if (sentences.length >= MAX_SENTENCES) return;
      const s = checkSentence(raw, facts, lang);
      if (!s) continue;
      sentences.push(s);
      onSentence(s);
    }
  };
  if (settings.model === "meta") {
    // The engine checks every sentence too; checking here again keeps one rule for both models.
    const action = await askMuse(ENGINE_TOKEN.value().trim(), settings, profile, facts, history, text, doseNames, lang,
      (s) => emit([s]));
    return { sentences, action };
  }

  const toolList = tools(profile, doseNames, lang);
  const system = systemPrompt(settings, profile, facts, toolList.some((t) => t.name === "avisar_ayuda"), lang);
  const messages: Anthropic.MessageParam[] = [];
  for (const t of history) {
    messages.push({ role: "user", content: t.q }, { role: "assistant", content: t.a });
  }
  messages.push({ role: "user", content: text });

  const anthropic = new Anthropic({ apiKey: ANTHROPIC_API_KEY.value().trim() });
  let buffer = "";
  const stream = anthropic.messages.stream({
    model: CLAUDE_MODEL.value().trim(),
    // The model may think before answering, and its thinking counts toward max_tokens: with 300 it sometimes
    // ran out mid-sentence. With low effort it thinks little or not at all, and the margin is more than enough.
    max_tokens: 2000,
    output_config: { effort: "low" },
    system,
    messages,
    ...(toolList.length ? { tools: toolList as Anthropic.Tool[] } : {}),
  });
  stream.on("text", (delta) => {
    buffer += delta;
    const { sentences: done, rest } = takeSentences(buffer);
    buffer = rest;
    emit(done);
  });
  const final = await stream.finalMessage();
  // If the margin ran out, the half-finished chunk is not said.
  if (buffer.trim() && final.stop_reason !== "max_tokens") emit([buffer.trim()]);

  let action: Action | null = null;
  for (const block of final.content) {
    if (block.type !== "tool_use") continue;
    action = resolve(block.name, block.input, profile);
    if (action) break;
  }
  return { sentences, action };
}

function describe(a: Action | null, lang: Lang): string {
  if (!a) return "";
  if (lang === "en") {
    switch (a.type) {
      case "call":
        return `Call ${a.name}`;
      case "light":
        return a.on ? "Turn on the flashlight" : "Turn off the flashlight";
      case "dose":
        return `Log the dose: ${a.name}`;
      case "help":
        return "Help alert";
    }
  }
  switch (a.type) {
    case "call":
      return `Llamar a ${a.name}`;
    case "light":
      return a.on ? "Encender la linterna" : "Apagar la linterna";
    case "dose":
      return `Anotar la toma: ${a.name}`;
    case "help":
      return "Aviso de ayuda";
  }
}

/** Counts one reply for the day; false if the maximum was already reached (R11). When reached, notifies the caregiver once. */
async function countAnswer(patient: DocumentReference, day: string, max: number): Promise<boolean> {
  return db.runTransaction(async (tx) => {
    const ref = patient.collection("usage").doc(day);
    const snap = await tx.get(ref);
    const used = snap.exists ? Number(snap.get("count") || 0) : 0;
    if (used >= max) {
      if (!snap.get("warned")) {
        tx.set(ref, { warned: true }, { merge: true });
        tx.update(patient, { aiLimitAt: FieldValue.serverTimestamp() });
      }
      return false;
    }
    tx.set(ref, { count: used + 1, expireAt: Timestamp.fromMillis(Date.now() + 35 * DAY_MS) }, { merge: true });
    return true;
  });
}

// --- Functions ---

/**
 * The patient's phone asks. Replies in frames (frames.ts): each sentence as text plus its audio,
 * then the action if there is one, and finally an end frame. Always warm, so cold starts do not add up.
 */
export const responder = onRequest(
  {
    secrets: [ANTHROPIC_API_KEY, ELEVENLABS_API_KEY, CLAUDE_MODEL, ENGINE_TOKEN],
    minInstances: 1,
    concurrency: 40,
    timeoutSeconds: 30,
    memory: "512MiB",
  },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).end();
      return;
    }
    const who = await caller(req);
    if (!who) {
      res.status(401).end();
      return;
    }
    const body = (req.body || {}) as Record<string, unknown>;
    const pid = str(body.pid, 64);
    const text = str(body.text, 300);
    const day = typeof body.day === "string" && /^\d{4}-\d{2}-\d{2}$/.test(body.day) ? body.day : "";
    if (!pid || !text || !day) {
      res.status(400).end();
      return;
    }
    const patient = db.collection("patients").doc(pid);
    const [device, snap, profileSnap] = await Promise.all([
      patient.collection("devices").doc(who.uid).get(),
      patient.get(),
      patient.collection("private").doc("profile").get(),
    ]);
    if (!device.exists || !snap.exists) {
      res.status(403).end();
      return;
    }
    const lang = langOf(req);
    const settings = aiSettings(snap.get("config"), lang);
    // R13: without the caregiver's consent nothing is sent to the AI.
    if (!settings.on || !snap.get("aiConsentAt")) {
      res.status(403).end();
      return;
    }
    if (!(await countAnswer(patient, day, settings.max))) {
      res.status(429).end();
      return;
    }

    const facts = factsFrom(body.facts);
    const profile = profileFrom(profileSnap.data());
    res.status(200);
    res.setHeader("Content-Type", "application/octet-stream");
    res.setHeader("Cache-Control", "no-store");

    let voiceOk = true;
    let voiceChain: Promise<void> = Promise.resolve();
    try {
      let voice = settings.voice || (await defaultVoice(lang));
      const answer = await ask(settings, profile, facts, historyFrom(body.history), text,
        doseNamesFrom(snap.get("config")), lang, (s) => {
          // Each sentence goes right before its audio: if one arrives without audio, the phone says it with its own voice.
          voiceChain = voiceChain.then(async () => {
            res.write(frame("T", s));
            if (!voiceOk) return;
            try {
              voice = await speakOrFallback(s, voice, res, lang);
            } catch (e) {
              // Without a voice, the phone says the text with its own voice.
              voiceOk = false;
              logger.warn("Voice synthesis failed", e);
            }
          });
        });
      const log = patient.collection("talks").add({
        heard: text,
        answer: answer.sentences.join(" "),
        action: describe(answer.action, lang),
        at: FieldValue.serverTimestamp(),
        expireAt: Timestamp.fromMillis(Date.now() + TALKS_KEEP_MS),
      });
      await Promise.all([voiceChain, log]);
      if (answer.action) res.write(frame("X", JSON.stringify(answer.action)));
      res.write(frame("E", JSON.stringify({ ok: true, voice: voiceOk })));
    } catch (e) {
      logger.error("Could not answer", e);
      await voiceChain.catch(() => undefined);
      res.write(frame("E", JSON.stringify({ ok: false })));
    }
    res.end();
  },
);

/** The profile preview (016 R3): the same reply, without voice and without executing anything. */
export const preview = onRequest(
  { secrets: [ANTHROPIC_API_KEY, CLAUDE_MODEL, ENGINE_TOKEN], timeoutSeconds: 30 },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).end();
      return;
    }
    const who = await caller(req);
    if (!who || who.anonymous) {
      res.status(401).end();
      return;
    }
    const body = (req.body || {}) as Record<string, unknown>;
    const pid = str(body.pid, 64);
    const text = str(body.text, 300);
    const day = typeof body.day === "string" && /^\d{4}-\d{2}-\d{2}$/.test(body.day) ? body.day : "";
    if (!pid || !text || !day) {
      res.status(400).end();
      return;
    }
    const patient = db.collection("patients").doc(pid);
    const snap = await patient.get();
    if (!snap.exists || snap.get("caregiverUid") !== who.uid) {
      res.status(403).end();
      return;
    }
    if (!(await countAnswer(patient, `vista-previa-${day}`, PREVIEWS_PER_DAY))) {
      res.status(429).end();
      return;
    }
    const lang = langOf(req);
    const settings = aiSettings(snap.get("config"), lang);
    const profile = body.profile
      ? profileFrom(body.profile)
      : profileFrom((await patient.collection("private").doc("profile").get()).data());
    try {
      const answer = await ask(settings, profile, factsFrom(body.facts), [], text,
        doseNamesFrom(snap.get("config")), lang, () => undefined);
      res.json({ answer: answer.sentences.join(" "), action: describe(answer.action, lang) });
    } catch (e) {
      logger.error("Could not build the preview", e);
      res.status(502).end();
    }
  },
);

/**
 * The ElevenLabs voices for the caregiver to choose one, with their sample (R7): first the video's
 * if the account can use it, then the Spanish ones. default is the one that plays if none is chosen.
 */
export const voices = onRequest({ secrets: [ELEVENLABS_API_KEY], timeoutSeconds: 30 }, async (req, res) => {
  const who = await caller(req);
  if (!who || who.anonymous) {
    res.status(401).end();
    return;
  }
  try {
    const lang = langOf(req);
    const [list] = await Promise.all([listVoices(), checkVideoVoice()]);
    const speaks = (v: Voice) => (lang === "en" ? v.english : v.spanish);
    list.sort((a, b) => Number(speaks(b)) - Number(speaks(a)) || a.name.localeCompare(b.name));
    const others = list.filter((v) => v.id !== VIDEO_VOICE.id);
    const fallback = await defaultVoice(lang);
    if (videoVoiceRejected) {
      res.json({ voices: others, default: fallback });
      return;
    }
    const preview = list.find((v) => v.id === VIDEO_VOICE.id)?.preview || (await videoVoicePreview());
    const video = { id: VIDEO_VOICE.id, name: VIDEO_VOICE.name, preview, spanish: true, english: false };
    // In English the video's voice goes after the English ones: it is not the default there.
    const all = lang === "en" ? [...others, video] : [video, ...others];
    res.json({ voices: all, default: fallback });
  } catch (e) {
    logger.error("Could not read the voices", e);
    res.status(502).end();
  }
});
