/**
 * earcaster-muse: the answer engine Earcaster calls when the caregiver picks Meta. Built for the Meta Global AI
 * Developer Hackathon (Oct 26 to Nov 4, 2026). Only text arrives here: the audio never leaves the phone, and
 * phone numbers stay with the caller.
 *
 * POST /muse with a Question (request.ts) and `Authorization: Bearer <ENGINE_TOKEN>`. The reply is NDJSON, one
 * event per line as soon as it is ready: {"t":"say","text"} for each checked sentence, {"t":"do","action"} for an
 * allowed action, {"t":"dropped","reason"} when the guard removed a sentence, and a final {"t":"end"}.
 */
import { timingSafeEqual } from "crypto";
import { setGlobalOptions } from "firebase-functions/v2";
import { onRequest } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import * as logger from "firebase-functions/logger";

import type { Response } from "express";

import { Budget, demoQuestion } from "./demo";
import { answer } from "./engine";
import { MUSE_MODEL } from "./muse";
import { questionFrom } from "./request";
import { dayFrom, summarize } from "./summary";
import { Question, langFrom } from "./types";
import { proposeUpdates, requestFrom } from "./updates";

const META_API_KEY = defineSecret("META_API_KEY");
/** Shared with Earcaster's own function, the only caller. */
const ENGINE_TOKEN = defineSecret("ENGINE_TOKEN");

setGlobalOptions({ region: "us-central1", maxInstances: 10 });

function authorized(header: string | undefined, token: string): boolean {
  const given = Buffer.from((header || "").replace(/^Bearer\s+/i, ""));
  const want = Buffer.from(token);
  return given.length === want.length && want.length > 0 && timingSafeEqual(given, want);
}

export const muse = onRequest(
  { secrets: [META_API_KEY, ENGINE_TOKEN], timeoutSeconds: 30, memory: "256MiB", concurrency: 40 },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).end();
      return;
    }
    if (!authorized(req.get("authorization"), ENGINE_TOKEN.value().trim())) {
      res.status(401).end();
      return;
    }
    const q = questionFrom(req.body);
    if (!q) {
      res.status(400).end();
      return;
    }
    await stream(q, META_API_KEY.value().trim(), res);
  },
);

/** Writes the answer as NDJSON, one event per line as soon as it is ready. */
async function stream(q: Question, apiKey: string, res: Response): Promise<void> {
  res.status(200);
  res.setHeader("Content-Type", "application/x-ndjson");
  res.setHeader("Cache-Control", "no-store");
  const started = Date.now();
  let first = 0;
  try {
    await answer(q, apiKey, (e) => {
      if (e.t === "say" && !first) first = Date.now() - started;
      res.write(JSON.stringify(e) + "\n");
    });
    res.write(JSON.stringify({ t: "end", ok: true, model: MUSE_MODEL, firstMs: first, totalMs: Date.now() - started }) + "\n");
  } catch (e) {
    logger.error("Muse Spark could not answer", e);
    res.write(JSON.stringify({ t: "end", ok: false }) + "\n");
  }
  res.end();
}

const demoBudget = new Budget(300, 25);

/**
 * The public demo for the judges: the same engine with a made-up person (demo.ts). Open to any page, limited to
 * two instances and a daily budget, and it never runs an action: the page only shows what the phone would do.
 */
export const museDemo = onRequest(
  { secrets: [META_API_KEY], timeoutSeconds: 30, memory: "256MiB", maxInstances: 2, cors: true },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).end();
      return;
    }
    const body = (req.body || {}) as Record<string, unknown>;
    const text = typeof body.text === "string" ? body.text.trim().slice(0, 200) : "";
    if (!text) {
      res.status(400).end();
      return;
    }
    if (!demoBudget.take(req.ip || "?")) {
      res.status(429).json({ error: "The demo reached its limit for today. Please try again tomorrow." });
      return;
    }
    await stream(demoQuestion(text, langFrom(body.lang)), META_API_KEY.value().trim(), res);
  },
);

/**
 * The caregiver's daily summary (summary.ts). Earcaster's backend sends the day's log, already without phone numbers,
 * and gets back a few checked sentences and the counts behind them.
 */
export const museSummary = onRequest(
  { secrets: [META_API_KEY, ENGINE_TOKEN], timeoutSeconds: 60, memory: "256MiB" },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).end();
      return;
    }
    if (!authorized(req.get("authorization"), ENGINE_TOKEN.value().trim())) {
      res.status(401).end();
      return;
    }
    const day = dayFrom(req.body);
    if (!day) {
      res.status(400).end();
      return;
    }
    try {
      res.json({ ...(await summarize(day, META_API_KEY.value().trim())), model: MUSE_MODEL });
    } catch (e) {
      logger.error("Muse Spark could not write the summary", e);
      res.status(502).end();
    }
  },
);

/**
 * Spec 020: proposes profile and visit changes from a family message. Saves nothing; the caregiver approves each
 * proposal on the phone. POST with `Authorization: Bearer <ENGINE_TOKEN>`; replies {proposals, medication, dropped}.
 */
export const museUpdates = onRequest(
  { secrets: [META_API_KEY, ENGINE_TOKEN], timeoutSeconds: 40, memory: "256MiB" },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).end();
      return;
    }
    if (!authorized(req.get("authorization"), ENGINE_TOKEN.value().trim())) {
      res.status(401).end();
      return;
    }
    const r = requestFrom(req.body);
    if (!r) {
      res.status(400).end();
      return;
    }
    try {
      res.json({ ...(await proposeUpdates(r, META_API_KEY.value().trim())), model: MUSE_MODEL });
    } catch (e) {
      logger.error("Muse Spark could not read the message", e);
      res.status(502).end();
    }
  },
);
