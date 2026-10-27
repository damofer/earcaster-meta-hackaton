import { test } from "node:test";
import assert from "node:assert/strict";
import { Budget, demoQuestion, spokenTime } from "../demo";
import { check, knownText } from "../guard";

test("times are written the way the phone says them", () => {
  assert.equal(spokenTime(18, 34, "es"), "las 6 y media de la tarde");
  assert.equal(spokenTime(13, 0, "es"), "la 1 en punto de la tarde");
  assert.equal(spokenTime(9, 12, "es"), "las 9 y 10 de la mañana");
  assert.equal(spokenTime(17, 0, "en"), "5 o'clock in the afternoon");
  assert.equal(spokenTime(20, 30, "en"), "half past 8 in the evening");
});

test("the demo person has a visit next Sunday, and the guard accepts it", () => {
  // Monday, October 26, 2026, 6:34 pm in Bogotá.
  const q = demoQuestion("¿cuándo viene Lucía?", "es", new Date("2026-10-26T23:34:00Z"));
  assert.equal(q.facts.now, "lunes 26 de octubre de 2026, las 6 y media de la tarde");
  assert.deepEqual(q.facts.visits, ["Lucía: el domingo 1 de noviembre a las 5 de la tarde"]);
  const ctx = { lang: q.lang, assistant: q.assistant, profile: q.profile, facts: q.facts, said: [q.text] };
  assert.equal(check("Lucía viene el domingo a las 5 de la tarde, Rosita.", ctx).ok, true);
  assert.equal(check("Lucía viene el martes.", ctx).ok, false);
  assert.match(knownText(ctx), /Andrés/);
  const en = demoQuestion("when is Lucía coming?", "en", new Date("2026-11-01T15:00:00Z"));
  assert.deepEqual(en.facts.visits, ["Lucía: Sunday, November 8 at 5 o'clock in the afternoon"]);
});

test("the demo budget stops one visitor and the whole day", () => {
  const b = new Budget(3, 2);
  const day = new Date("2026-10-27T12:00:00Z");
  assert.equal(b.take("a", day), true);
  assert.equal(b.take("a", day), true);
  assert.equal(b.take("a", day), false);
  assert.equal(b.take("b", day), true);
  assert.equal(b.take("c", day), false);
  assert.equal(b.take("a", new Date("2026-10-28T12:00:00Z")), true);
});
