import { test } from "node:test";
import assert from "node:assert/strict";
import { dayLabel, missedDoses } from "../brief";

test("missed doses are counted day by day, and today is not over (spec 021)", () => {
  // Midnight in Bogotá (UTC-5) on Wednesday, October 21, 2026, and three days and a half later.
  const from = Date.UTC(2026, 9, 21, 5);
  const day = 86_400_000;
  const logged = [
    { ms: from + 9 * 3_600_000, name: "Mañana" },
    { ms: from + 21 * 3_600_000, name: "Noche" },
    { ms: from + day + 9 * 3_600_000, name: "Mañana" },
    { ms: from + 3 * day + 9 * 3_600_000, name: "Mañana" },
  ];
  const out = missedDoses(["Mañana", "Noche"], logged, from, from + 3.5 * day, "America/Bogota", "es");
  assert.deepEqual(out.map((m) => m.name), ["Noche", "Mañana", "Noche"]);
  assert.match(out[0].day, /jueves/);
  assert.match(dayLabel(from + day / 2, "America/Bogota", "en"), /Wednesday/);
});
