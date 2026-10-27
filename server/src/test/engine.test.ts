import { test } from "node:test";
import assert from "node:assert/strict";
import { createServer, IncomingMessage, Server } from "http";
import { AddressInfo } from "net";
import { answer, Event } from "../engine";
import { questionFrom } from "../request";
import { ChatStreamParser } from "../sse";
import { resolve, toolsFor } from "../tools";

/** A fake Meta Model API that streams the given chunks and records what it received. */
async function fakeMeta(chunks: object[]): Promise<{ server: Server; received: () => any }> {
  let body = "";
  const server = createServer((req: IncomingMessage, res) => {
    req.on("data", (d) => (body += d));
    req.on("end", () => {
      res.writeHead(200, { "Content-Type": "text/event-stream" });
      for (const c of chunks) res.write(`data: ${JSON.stringify(c)}\n\n`);
      res.end("data: [DONE]\n\n");
    });
  });
  await new Promise<void>((ok) => server.listen(0, ok));
  process.env.META_BASE_URL = `http://127.0.0.1:${(server.address() as AddressInfo).port}/v1`;
  return { server, received: () => JSON.parse(body) };
}

const text = (s: string) => ({ choices: [{ index: 0, delta: { content: s } }] });

const question = questionFrom({
  lang: "es",
  assistant: "Clara",
  text: "¿viene Ana hoy?",
  profile: { callMe: "Mari", family: "Su hija Ana vive cerca. Su hijo Carlos vive en Madrid." },
  facts: { now: "lunes 19 de octubre, las 6 de la tarde", visits: ["Ana: el domingo a las 5 en punto de la tarde"], doses: [] },
  allowed: { call: [{ name: "Carlos", relation: "su hijo", number: "+34600000000" }], light: true, doses: [], help: false },
})!;

test("streams checked sentences and stops at two", async () => {
  const meta = await fakeMeta([text("Hoy no, Mari. Ana vie"), text("ne el domingo a las 5. Te va a encantar. Otra frase.")]);
  const events: Event[] = [];
  const action = await answer(question, "key", (e) => events.push(e));
  meta.server.close();
  assert.equal(action, null);
  assert.deepEqual(events, [{ t: "say", text: "Hoy no, Mari." }, { t: "say", text: "Ana viene el domingo a las 5." }]);
  const sent = meta.received();
  assert.equal(sent.model, "muse-spark-1.3");
  assert.equal(sent.stream, true);
  assert.equal(sent.messages[0].role, "developer");
  assert.match(sent.messages[0].content, /Ana: el domingo a las 5/);
  // Phone numbers never reach Meta.
  assert.doesNotMatch(JSON.stringify(sent), /600000000/);
  assert.deepEqual(sent.tools.map((t: any) => t.function.name), ["call_person", "flashlight"]);
});

test("a made-up visit is replaced and nothing after it is said", async () => {
  const meta = await fakeMeta([text("Ana viene el martes. Y Pedro también.")]);
  const events: Event[] = [];
  await answer(question, "key", (e) => events.push(e));
  meta.server.close();
  assert.deepEqual(events, [{ t: "dropped", reason: "weekday martes" }, { t: "say", text: "Eso no lo tengo apuntado." }]);
});

test("a tool call streamed in pieces becomes an allowed action", async () => {
  const meta = await fakeMeta([
    { choices: [{ index: 0, delta: { tool_calls: [{ index: 0, id: "c1", type: "function", function: { name: "call_person", arguments: "" } }] } }] },
    { choices: [{ index: 0, delta: { tool_calls: [{ index: 0, function: { arguments: '{"who":' } }] } }] },
    { choices: [{ index: 0, delta: { tool_calls: [{ index: 0, function: { arguments: '"carlos"}' } }] }, finish_reason: "tool_calls" }] },
  ]);
  const events: Event[] = [];
  const action = await answer(question, "key", (e) => events.push(e));
  meta.server.close();
  assert.deepEqual(action, { type: "call", name: "Carlos" });
  assert.deepEqual(events, [{ t: "do", action: { type: "call", name: "Carlos" } }]);
});

test("a sentence cut by the token limit is not said", async () => {
  const meta = await fakeMeta([text("Hola Mari. Ana viene el"), { choices: [{ index: 0, delta: {}, finish_reason: "length" }] }]);
  const events: Event[] = [];
  await answer(question, "key", (e) => events.push(e));
  meta.server.close();
  assert.deepEqual(events, [{ t: "say", text: "Hola Mari." }]);
});

test("only allowed actions resolve", () => {
  const allowed = { call: [{ name: "Carlos" }], light: false, doses: ["Pastilla de la mañana"], help: false };
  assert.equal(resolve("flashlight", '{"on":true}', allowed), null);
  assert.equal(resolve("alert_family", "{}", allowed), null);
  assert.equal(resolve("call_person", '{"who":"Pedro"}', allowed), null);
  assert.equal(resolve("call_person", "not json", allowed), null);
  assert.deepEqual(resolve("log_dose", '{"dose":"Pastilla de la mañana"}', allowed), { type: "dose", name: "Pastilla de la mañana" });
  assert.equal(resolve("log_dose", '{"dose":"Otra"}', allowed), null);
  assert.deepEqual(toolsFor({ call: [], light: false, doses: [], help: false }, "en"), []);
});

test("the request is bounded and needs a question", () => {
  assert.equal(questionFrom({}), null);
  assert.equal(questionFrom({ text: "   " }), null);
  const q = questionFrom({ text: "x".repeat(1000), lang: "en", history: Array(20).fill({ q: "a", a: "b" }) })!;
  assert.equal(q.text.length, 300);
  assert.equal(q.assistant, "Grace");
  assert.equal(q.history.length, 6);
});

test("the stream parser survives lines cut anywhere", () => {
  let out = "";
  const p = new ChatStreamParser((d) => (out += d));
  const raw = `data: ${JSON.stringify(text("Hola"))}\n\ndata: ${JSON.stringify(text(" Mari"))}\n\ndata: [DONE]\n\n`;
  for (let i = 0; i < raw.length; i += 7) p.push(raw.slice(i, i + 7));
  assert.equal(out, "Hola Mari");
  assert.deepEqual(p.end(), { toolCalls: [], finishReason: null });
});
