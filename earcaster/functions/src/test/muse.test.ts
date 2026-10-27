import { test } from "node:test";
import assert from "node:assert/strict";
import { actionFrom, allowedFor } from "../muse";
import { Profile } from "../types";

const profile: Profile = {
  calls: [
    { name: "Carlos", number: "+34600000000", relation: "su hijo" },
    { name: "Sin número", number: "" },
  ],
  light: true,
  doses: ["Pastilla de la mañana", "Pastilla de la noche"],
  help: false,
};

test("el motor de Meta recibe las acciones permitidas sin números (spec 018)", () => {
  const allowed = allowedFor(profile, ["Pastilla de la mañana"]);
  assert.deepEqual(allowed, {
    call: [{ name: "Carlos", relation: "su hijo" }],
    light: true,
    doses: ["Pastilla de la mañana"],
    help: false,
  });
  assert.doesNotMatch(JSON.stringify(allowed), /600000000/);
});

test("la acción del motor vuelve a pasar por resolve() y toma el número de la ficha (spec 018)", () => {
  assert.deepEqual(actionFrom({ type: "call", name: "Carlos" }, profile), { type: "call", name: "Carlos", number: "+34600000000" });
  assert.deepEqual(actionFrom({ type: "light", on: true }, profile), { type: "light", on: true });
  assert.equal(actionFrom({ type: "help" }, profile), null);
  assert.equal(actionFrom({ type: "call", name: "Pedro" }, profile), null);
  assert.equal(actionFrom("basura", profile), null);
});
