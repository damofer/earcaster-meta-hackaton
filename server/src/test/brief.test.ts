import { test } from "node:test";
import assert from "node:assert/strict";
import { createServer } from "http";
import { AddressInfo } from "net";
import { brief, briefPrompt, weekFrom, weekStatsOf } from "../brief";

const week = weekFrom({
  lang: "es",
  person: "Rosita",
  visitor: "Lucía",
  range: "del 21 al 27 de octubre",
  profile: { family: "Su hija Lucía. Su esposo Jorge murió en 2015; bailaban cumbia.", likes: "Le gustan las arepas de huevo." },
  talks: [
    { day: "martes", time: "9:15 am", heard: "¿Viene Lucía hoy?", answer: "Lucía viene el domingo." },
    { day: "jueves", time: "4:02 pm", heard: "viene lucia hoy", answer: "El domingo, Rosita." },
    { day: "jueves", time: "6:30 pm", heard: "¿Dónde está Jorge?", answer: "Jorge te quiso mucho." },
  ],
  missed: [{ day: "miércoles", name: "Pastilla de la noche" }],
  help: [],
})!;

test("the week's counts are made in code (spec 021)", () => {
  assert.deepEqual(weekStatsOf(week), {
    questions: 3,
    aboutVisitor: 2,
    repeated: [{ text: "¿Viene Lucía hoy?", times: 2 }],
    missedDoses: 1,
    helpAlerts: 0,
  });
  assert.match(briefPrompt(week, weekStatsOf(week)), /Preguntas por Lucía: 2/);
  assert.equal(weekFrom({ range: "x" }), null);
});

test("the brief never tells the visitor to test her memory, and names must be known (spec 021)", async () => {
  const text = "Esta semana Rosita preguntó 2 veces por ti. El jueves preguntó por Jorge. " +
    "Pregúntale si se acuerda de Jorge. Pueden hablar de cuando bailaba cumbia. Cuéntale de Pedro. Le gustan las arepas de huevo.";
  const server = createServer((req, res) => {
    req.resume();
    req.on("end", () => {
      res.writeHead(200, { "Content-Type": "text/event-stream" });
      res.write(`data: ${JSON.stringify({ choices: [{ index: 0, delta: { content: text } }] })}\n\n`);
      res.end("data: [DONE]\n\n");
    });
  });
  await new Promise<void>((ok) => server.listen(0, ok));
  process.env.META_BASE_URL = `http://127.0.0.1:${(server.address() as AddressInfo).port}/v1`;
  const out = await brief(week, "key");
  server.close();
  assert.equal(out.text, "Esta semana Rosita preguntó 2 veces por ti. El jueves preguntó por Jorge. Pueden hablar de cuando bailaba cumbia. Le gustan las arepas de huevo.");
  assert.deepEqual(out.dropped, ["tests memory", "name Pedro"]);
});
