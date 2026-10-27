import { test } from "node:test";
import assert from "node:assert/strict";
import { createServer } from "http";
import { AddressInfo } from "net";
import { dayFrom, statsOf, summarize, summaryPrompt } from "../summary";

const day = dayFrom({
  lang: "es",
  person: "Rosita",
  day: "lunes 26 de octubre",
  talks: [
    { time: "9:15 am", heard: "¿Cuándo viene Lucía?", answer: "Lucía viene el domingo a las 5." },
    { time: "11:02 am", heard: "cuándo viene Lucía", answer: "El domingo a las 5, Rosita." },
    { time: "3:40 pm", heard: "¿Dónde está Jorge?", answer: "Jorge te quiso mucho, Rosita." },
    { time: "6:10 pm", heard: "Llama a mi hijo", answer: "", action: "Llamar a Andrés" },
  ],
  doses: [{ time: "9:10 am", name: "Pastilla de la mañana" }],
  expected: ["Pastilla de la mañana", "Pastilla de la noche"],
  help: [],
})!;

test("the counts are made in code, not by the model", () => {
  assert.deepEqual(statsOf(day), {
    questions: 4,
    repeated: [{ text: "¿Cuándo viene Lucía?", times: 2 }],
    dosesLogged: 1,
    dosesMissing: ["Pastilla de la noche"],
    helpAlerts: 0,
  });
  const prompt = summaryPrompt(day, statsOf(day));
  assert.match(prompt, /Preguntas repetidas: «¿Cuándo viene Lucía\?» \(2 veces\)/);
  assert.match(prompt, /Tomas sin anotar: Pastilla de la noche/);
  assert.match(prompt, /6:10 pm · preguntó: Llama a mi hijo · respuesta:  · acción: Llamar a Andrés/);
});

test("a day needs a date, and the log is bounded", () => {
  assert.equal(dayFrom({}), null);
  const d = dayFrom({ day: "x", talks: Array(500).fill({ heard: "hola", answer: "hola" }) })!;
  assert.equal(d.talks.length, 200);
  assert.equal(d.lang, "es");
});

test("summary sentences that mention someone or a time outside the log are dropped", async () => {
  const text = "Hoy Rosita preguntó 4 veces al asistente, dos de ellas por Lucía. Por la tarde preguntó por Jorge. " +
    "A las 6 llamó a Andrés. Pedro la visitó a las 2. La pastilla de la noche no está anotada.";
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
  const out = await summarize(day, "key");
  server.close();
  assert.equal(out.text, "Hoy Rosita preguntó 4 veces al asistente, dos de ellas por Lucía. Por la tarde preguntó por Jorge. " +
    "A las 6 llamó a Andrés. La pastilla de la noche no está anotada.");
  assert.deepEqual(out.dropped, ["hour 2"]);
});
