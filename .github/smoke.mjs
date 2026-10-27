// Tries the deployed engine with a made-up person (Rosa) only: never real patients while Meta's free credits are in
// use. Measures how long the first checked sentence takes. ENGINE_TOKEN comes from Secret Manager in the workflow.
const BASE = "https://us-central1-earcaster.cloudfunctions.net";
const TOKEN = (process.env.ENGINE_TOKEN || "").trim();
let ok = true;

async function stream(path, body, headers = {}) {
  const t0 = Date.now();
  const res = await fetch(`${BASE}/${path}`, { method: "POST", headers: { "Content-Type": "application/json", ...headers }, body: JSON.stringify(body) });
  const out = { status: res.status, say: [], do: [], dropped: [], first: null, total: 0 };
  if (!res.ok || !res.body) return out;
  let buf = "";
  for await (const chunk of res.body) {
    buf += Buffer.from(chunk).toString("utf8");
    let i;
    while ((i = buf.indexOf("\n")) >= 0) {
      const line = buf.slice(0, i).trim();
      buf = buf.slice(i + 1);
      if (!line) continue;
      const e = JSON.parse(line);
      if (e.t === "say") { out.say.push(e.text); out.first ??= Date.now() - t0; }
      if (e.t === "do") out.do.push(e.action);
      if (e.t === "dropped") out.dropped.push(e.reason);
    }
  }
  out.total = Date.now() - t0;
  return out;
}

async function json(path, body) {
  const t0 = Date.now();
  const res = await fetch(`${BASE}/${path}`, { method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${TOKEN}` }, body: JSON.stringify(body) });
  const out = res.ok ? await res.json() : { status: res.status, body: (await res.text()).slice(0, 200) };
  return { ms: Date.now() - t0, status: res.status, out };
}

function report(name, good, detail) {
  console.log(`${good ? "OK  " : "FAIL"} ${name}: ${detail}`);
  ok &&= good;
}

const rosa = {
  lang: "es", assistant: "Clara",
  profile: { name: "Rosa", callMe: "Rosita", home: "Vive en Barranquilla.", family: "Su hija Lucía vive en Barranquilla. Su hijo Andrés vive en Bogotá. Su esposo Jorge murió en 2015; bailaban cumbia." },
  facts: { now: "martes 27 de octubre de 2026, las 10 de la mañana", visits: ["Lucía: el domingo 1 de noviembre a las 5 de la tarde"], doses: [] },
  allowed: { call: [{ name: "Andrés", relation: "su hijo" }], light: true, doses: [], help: false },
};

for (const [text, expect] of [
  ["¿Cuándo viene Lucía?", (r) => r.say.join(" ").toLowerCase().includes("domingo")],
  ["¿Viene Andrés el sábado?", (r) => r.say.length > 0],
  ["Llama a mi hijo", (r) => r.do.some((a) => a.type === "call" && a.name === "Andrés")],
]) {
  const r = await stream("muse", { ...rosa, text }, { Authorization: `Bearer ${TOKEN}` });
  report(`muse «${text}»`, r.status === 200 && expect(r),
    `${r.status} ${JSON.stringify(r.say)} do=${JSON.stringify(r.do)} dropped=${JSON.stringify(r.dropped)} first=${r.first}ms total=${r.total}ms`);
}

const demo = await stream("museDemo", { text: "¿Cuándo viene Lucía?", lang: "es" });
report("museDemo", demo.status === 200 && demo.say.length > 0, `${demo.status} ${JSON.stringify(demo.say)} first=${demo.first}ms total=${demo.total}ms`);

const s = await json("museSummary", {
  lang: "es", person: "Rosita", day: "martes 27 de octubre",
  talks: [
    { time: "9:15 a. m.", heard: "¿Cuándo viene Lucía?", answer: "Lucía viene el domingo a las 5." },
    { time: "11:02 a. m.", heard: "cuándo viene Lucía", answer: "El domingo a las 5, Rosita." },
    { time: "3:40 p. m.", heard: "¿Dónde está Jorge?", answer: "Jorge te quiso mucho, Rosita." },
  ],
  doses: [{ time: "9:10 a. m.", name: "Pastilla de la mañana" }],
  expected: ["Pastilla de la mañana", "Pastilla de la noche"], help: [],
});
report("museSummary", s.status === 200 && s.out.text, `${s.ms}ms ${JSON.stringify(s.out)}`);

const u = await json("museUpdates", { lang: "es", today: "2026-10-27", message: "Llego el domingo a las 5, Lucía", profile: rosa.profile, visits: [] });
report("museUpdates", u.status === 200 && u.out.proposals?.some((p) => p.kind === "visit" && p.date === "2026-11-01" && p.minute === 1020), `${u.ms}ms ${JSON.stringify(u.out)}`);
const m = await json("museUpdates", { lang: "es", today: "2026-10-27", message: "Dale media pastilla en la noche", profile: rosa.profile, visits: [] });
report("museUpdates medication", m.status === 200 && m.out.medication === true && !m.out.proposals.length, `${m.ms}ms ${JSON.stringify(m.out)}`);

const b = await json("museBrief", {
  lang: "es", person: "Rosita", visitor: "Lucía", range: "del 21 al 27 de octubre", profile: rosa.profile,
  talks: [
    { day: "jueves 22", time: "9:15 a. m.", heard: "¿Viene Lucía hoy?", answer: "Lucía viene el domingo." },
    { day: "sábado 24", time: "4:02 p. m.", heard: "viene lucia hoy", answer: "El domingo, Rosita." },
    { day: "lunes 26", time: "6:30 p. m.", heard: "¿Dónde está Jorge?", answer: "Jorge te quiso mucho." },
  ],
  missed: [{ day: "miércoles 21", name: "Pastilla de la noche" }], help: [],
});
report("museBrief", b.status === 200 && b.out.text, `${b.ms}ms ${JSON.stringify(b.out)}`);

const texts = ["¿Dónde está Jorge?", "¿Jorge ya viene?", "¿Y Jorge?", "¿Viene Lucía hoy?", "¿Qué día es hoy?"];
const t = await json("museTopics", { lang: "es", texts });
report("museTopics", t.status === 200 && t.out.topics?.some((x) => x.texts.length >= 2), `${t.ms}ms ${JSON.stringify(t.out)}`);

process.exit(ok ? 0 : 1);
