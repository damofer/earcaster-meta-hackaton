import { test } from "node:test";
import assert from "node:assert/strict";
import { createServer } from "http";
import { AddressInfo } from "net";
import { checkProposal, proposeUpdates, requestFrom, withoutNumbers } from "../updates";

// Tuesday, October 27, 2026.
const base = {
  lang: "es",
  today: "2026-10-27",
  profile: { name: "Rosa", family: "Su hija Lucía vive en Barranquilla. Su hijo Andrés vive en Bogotá." },
  visits: [{ name: "Lucía", days: 1, minute: 13 * 60 }],
};
const req = (message: string) => requestFrom({ ...base, message })!;

test("a one-off visit is traced to the message: the next Sunday, at 5 in the afternoon", () => {
  const r = req("Llego el domingo a las 5, Lucía");
  assert.deepEqual(checkProposal({ kind: "visit", name: "Lucía", date: "2026-11-01", minute: 17 * 60, quote: "Llego el domingo a las 5" }, r),
    { kind: "visit", name: "Lucía", date: "2026-11-01", minute: 1020 });
  // A Saturday, a name or an hour the message never said is dropped.
  assert.equal(checkProposal({ kind: "visit", name: "Lucía", date: "2026-10-31", minute: 1020, quote: "Llego el domingo" }, r), "date 2026-10-31");
  assert.equal(checkProposal({ kind: "visit", name: "Pedro", date: "2026-11-01", minute: 1020, quote: "Llego el domingo" }, r), "name Pedro");
  assert.equal(checkProposal({ kind: "visit", name: "Lucía", date: "2026-11-01", minute: 18 * 60, quote: "Llego el domingo" }, r), "hour 1080");
  assert.equal(checkProposal({ kind: "visit", name: "Lucía", date: "2026-11-01", minute: 1020, quote: "Llego el sábado" }, r), "not in the message");
});

test("tomorrow and a calendar date also count, but not a past day", () => {
  const r = req("Mañana paso a las 10:30 de la mañana. Andrés");
  assert.equal((checkProposal({ kind: "visit", name: "Andrés", date: "2026-10-28", minute: 630, quote: "Mañana paso" }, r) as { date: string }).date, "2026-10-28");
  const d = req("Voy el 3 de noviembre a las 4, Andrés");
  assert.equal(typeof checkProposal({ kind: "visit", name: "Andrés", date: "2026-11-03", minute: 960, quote: "Voy el 3 de noviembre" }, d), "object");
  assert.equal(checkProposal({ kind: "visit", name: "Andrés", date: "2026-10-20", minute: 960, quote: "Voy" }, d), "date 2026-10-20");
});

test("a weekly visit names its days and replaces the existing one", () => {
  const r = req("Desde ahora Lucía viene los sábados a las 3");
  assert.deepEqual(checkProposal({ kind: "weekly", name: "Lucía", days: ["saturday"], minute: 900, replaces: "Lucía", quote: "Lucía viene los sábados a las 3" }, r),
    { kind: "weekly", name: "Lucía", days: 64, minute: 900, replaces: "Lucía" });
  assert.equal(checkProposal({ kind: "weekly", name: "Lucía", days: ["saturday", "sunday"], minute: 900, replaces: null, quote: "Lucía viene los sábados" }, r), "days 65");
});

test("a profile change keeps names from the message and replaces a sentence that exists", () => {
  const r = req("Andrés se mudó a Medellín");
  assert.deepEqual(checkProposal({ kind: "profile", field: "family", old: "Su hijo Andrés vive en Bogotá.", new: "Su hijo Andrés vive en Medellín.", quote: "Andrés se mudó a Medellín" }, r),
    { kind: "profile", field: "family", old: "Su hijo Andrés vive en Bogotá.", new: "Su hijo Andrés vive en Medellín." });
  assert.equal(checkProposal({ kind: "profile", field: "family", old: "", new: "Su hijo Andrés vive en Cali.", quote: "Andrés se mudó" }, r), "name Cali");
  assert.equal(checkProposal({ kind: "profile", field: "family", old: "Su hijo vive en Bogotá.", new: "Su hijo Andrés vive en Medellín.", quote: "Andrés se mudó" }, r), "old text not in the profile");
  assert.equal(checkProposal({ kind: "profile", field: "sensitive", old: "", new: "x", quote: "Andrés se mudó" }, r), "field sensitive");
});

test("phone numbers are removed before anything reaches Meta", () => {
  assert.equal(withoutNumbers("Llámame al +57 300 123 4567 el domingo a las 5"), "Llámame al […] el domingo a las 5");
  assert.equal(withoutNumbers("a las 5:30"), "a las 5:30");
});

test("a message about medication never reaches the model", async () => {
  const out = await proposeUpdates(req("Dale media pastilla en la noche"), "key");
  assert.deepEqual(out, { proposals: [], medication: true, dropped: [] });
});

test("the model's proposals are checked, and what cannot be traced is dropped", async () => {
  const reply = JSON.stringify({
    proposals: [
      { kind: "visit", name: "Lucía", date: "2026-11-01", minute: 1020, quote: "Llego el domingo a las 5" },
      { kind: "visit", name: "Lucía", date: "2026-11-01", minute: 1020, quote: "a las 5" },
      { kind: "visit", name: "Pedro", date: "2026-11-01", minute: 1020, quote: "y traigo a Pedro" },
      { kind: "profile", field: "family", old: "", new: "Su nieto Mateo vive con Lucía.", quote: "traigo a Mateo" },
    ],
  });
  const server = createServer((r, res) => {
    r.resume();
    r.on("end", () => {
      res.writeHead(200, { "Content-Type": "text/event-stream" });
      res.write(`data: ${JSON.stringify({ choices: [{ index: 0, delta: { content: "```json\n" + reply + "\n```" } }] })}\n\n`);
      res.end("data: [DONE]\n\n");
    });
  });
  await new Promise<void>((ok) => server.listen(0, ok));
  process.env.META_BASE_URL = `http://127.0.0.1:${(server.address() as AddressInfo).port}/v1`;
  const out = await proposeUpdates(req("Llego el domingo a las 5 y traigo a Mateo. Lucía"), "key");
  server.close();
  assert.equal(out.proposals.length, 2);
  assert.deepEqual(out.proposals[0], { kind: "visit", name: "Lucía", date: "2026-11-01", minute: 1020 });
  assert.deepEqual(out.dropped, ["repeated", "not in the message"]);
  assert.equal(out.medication, false);
});
