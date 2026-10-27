import { test } from "node:test";
import assert from "node:assert/strict";
import { clock } from "../summary";

test("the summary uses the phone's clock (spec 019)", () => {
  const ms = Date.UTC(2026, 9, 26, 14, 5);
  assert.equal(clock(ms, "America/Bogota", "en"), "9:05 AM");
  assert.match(clock(ms, "America/Bogota", "es"), /^9:05/);
  assert.match(clock(ms, "Europe/Madrid", "en"), /^3:05/);
  // An unknown zone falls back to Bogotá instead of failing.
  assert.equal(clock(ms, "Nowhere/Land", "en"), "9:05 AM");
});
