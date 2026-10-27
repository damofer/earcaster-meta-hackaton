import { test } from "node:test";
import assert from "node:assert/strict";
import { DayTrend, daysBefore, detect, isNight, sentence } from "../changes";

const today = "2026-10-27";
/** Two weeks of "¿viene Lucía hoy?" every day, as always. */
function usual(): DayTrend[] {
  const out: DayTrend[] = [];
  for (let k = 0; k < 17; k++) out.push({ day: daysBefore(today, k), questions: 4, night: 0, texts: { "¿Viene Lucía hoy?": 4 } });
  return out;
}
const lucia = { label: "Lucía", texts: ["¿Viene Lucía hoy?"] };
const jorge = { label: "Jorge", texts: ["¿Dónde está Jorge?", "¿Jorge ya viene?", "¿Y Jorge?"] };

test("the same question every day is not a change (spec 022)", () => {
  assert.deepEqual(detect(today, usual(), [lucia], 0, 0), []);
});

test("a new topic asked 3 times in 3 days alerts once, with the real questions (spec 022)", () => {
  const t = usual();
  t[2].texts["¿Dónde está Jorge?"] = 1; // Sunday the 25th
  t[1].texts["¿Jorge ya viene?"] = 1;
  t[0].texts["¿Y Jorge?"] = 1;
  const out = detect(today, t, [lucia, jorge], 0, 0);
  assert.equal(out.length, 1);
  assert.deepEqual({ ...out[0] }, { kind: "topic", key: "jorge", since: "2026-10-25", count: 3, usual: 0, label: "Jorge",
    quotes: ["¿Dónde está Jorge?", "¿Jorge ya viene?", "¿Y Jorge?"] });
  assert.equal(sentence(out[0], "Rosa", "es"), "Desde el domingo, Rosa ha preguntado por «Jorge» 3 veces, y antes no lo hacía.");
  // If it was asked once two weeks ago, it is not new.
  t[10].texts["¿Y Jorge?"] = 1;
  assert.deepEqual(detect(today, t, [lucia, jorge], 0, 0), []);
});

test("night questions, a busy day and help alerts (spec 022)", () => {
  const t = usual();
  t[0].night = 1;
  t[1].night = 2;
  t[1].questions = 12;
  const kinds = detect(today, t, [lucia], 2, 0).map((c) => c.kind);
  assert.deepEqual(kinds, ["volume", "night", "help"]);
  assert.equal(isNight(23), true);
  assert.equal(isNight(4), true);
  assert.equal(isNight(5), false);
  assert.equal(isNight("x"), false);
});

test("with less than a week of history there are no alerts (spec 022)", () => {
  const t = usual().slice(0, 5);
  t[0].night = 1;
  t[1].night = 1;
  assert.deepEqual(detect(today, t, [lucia], 5, 0), []);
});

test("no alert sentence mentions illness (spec 022)", () => {
  const banned = /demencia|alzheimer|deterioro|diagn|enfermedad|etapa|dementia|decline|diagnos|disease|stage|illness/i;
  for (const kind of ["topic", "volume", "night", "help"] as const) {
    for (const lang of ["es", "en"] as const) {
      const s = sentence({ kind, key: "k", since: today, count: 3, usual: 1, label: "Jorge", quotes: [] }, "Rosa", lang);
      assert.ok(!banned.test(s), s);
    }
  }
});
