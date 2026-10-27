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

import { answer } from "./engine";
import { MUSE_MODEL } from "./muse";
import { questionFrom } from "./request";

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
    res.status(200);
    res.setHeader("Content-Type", "application/x-ndjson");
    res.setHeader("Cache-Control", "no-store");
    const started = Date.now();
    let first = 0;
    try {
      await answer(q, META_API_KEY.value().trim(), (e) => {
        if (e.t === "say" && !first) first = Date.now() - started;
        res.write(JSON.stringify(e) + "\n");
      });
      res.write(JSON.stringify({ t: "end", ok: true, model: MUSE_MODEL, firstMs: first, totalMs: Date.now() - started }) + "\n");
    } catch (e) {
      logger.error("Muse Spark could not answer", e);
      res.write(JSON.stringify({ t: "end", ok: false }) + "\n");
    }
    res.end();
  },
);
