import { test } from "node:test";
import assert from "node:assert/strict";
import { profileTexts, weeklyVisitsFrom, withoutNumbers } from "../updates";

test("the weekly visits come from the settings' fixed_visits (spec 020)", () => {
  const config = JSON.stringify({ fixed_visits: JSON.stringify([{ name: " Lucía ", days: 1, minute: 780 }, { name: "", days: 2, minute: 0 }]) });
  assert.deepEqual(weeklyVisitsFrom(config), [{ name: "Lucía", days: 1, minute: 780 }]);
  assert.deepEqual(weeklyVisitsFrom("roto"), []);
  assert.deepEqual(weeklyVisitsFrom(undefined), []);
});

test("no phone number reaches Meta (spec 020)", () => {
  assert.equal(withoutNumbers("mi número nuevo es 300 123 4567, llego a las 5"), "mi número nuevo es […], llego a las 5");
  const texts = profileTexts({ family: "Su hijo Carlos", calls: [{ name: "Carlos", number: "+34600000000", relation: "su hijo" }] });
  assert.ok(!JSON.stringify(texts).includes("600000000"));
  assert.equal(texts.family, "Su hijo Carlos");
});
