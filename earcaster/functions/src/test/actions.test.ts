import { test } from "node:test";
import assert from "node:assert/strict";
import { resolve, tools } from "../actions";
import { Profile } from "../types";

const profile: Profile = {
  calls: [
    { name: "Carlos", number: "+34 600 111 222", relation: "su hijo" },
    { name: "Ana María", number: "600333444", relation: "su hija" },
    { name: "Sin número", number: "", relation: "" },
  ],
  light: true,
  doses: ["Pastilla de la mañana"],
  help: false,
};

test("solo ofrece lo que permite la ficha", () => {
  const names = tools(profile, ["Pastilla de la mañana"]).map((t) => t.name);
  assert.deepEqual(names, ["llamar", "linterna", "anotar_toma"]);
  assert.deepEqual(tools({}, []), []);
});

test("las tomas deben existir en sus frases", () => {
  assert.deepEqual(tools({ doses: ["Pastilla vieja"] }, ["Pastilla de la noche"]), []);
});

test("el número sale de la ficha", () => {
  assert.deepEqual(resolve("llamar", { quien: "carlos" }, profile), { type: "call", name: "Carlos", number: "+34 600 111 222" });
  assert.deepEqual(resolve("llamar", { quien: "Su hija" }, profile), { type: "call", name: "Ana María", number: "600333444" });
  assert.deepEqual(resolve("llamar", { quien: "Ana" }, profile), { type: "call", name: "Ana María", number: "600333444" });
});

test("rechaza lo que no está permitido", () => {
  assert.equal(resolve("llamar", { quien: "Pedro" }, profile), null);
  assert.equal(resolve("llamar", { quien: "Sin número" }, profile), null);
  assert.equal(resolve("avisar_ayuda", {}, profile), null);
  assert.equal(resolve("anotar_toma", { toma: "Otra" }, profile), null);
  assert.equal(resolve("linterna", { encender: "sí" }, profile), null);
  assert.equal(resolve("borrar_todo", {}, profile), null);
});

test("acepta lo permitido", () => {
  assert.deepEqual(resolve("linterna", { encender: true }, profile), { type: "light", on: true });
  assert.deepEqual(resolve("anotar_toma", { toma: "Pastilla de la mañana" }, profile), { type: "dose", name: "Pastilla de la mañana" });
  assert.deepEqual(resolve("avisar_ayuda", {}, { help: true }), { type: "help" });
});

test("in English the tools keep their names and speak English", () => {
  const es = tools(profile, ["Pastilla de la mañana"]);
  const en = tools(profile, ["Pastilla de la mañana"], "en");
  assert.deepEqual(en.map((t) => t.name), es.map((t) => t.name));
  assert.match(en[0].description, /[A-Za-z]/);
  assert.notEqual(en[0].description, es[0].description);
});
