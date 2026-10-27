import { test } from "node:test";
import assert from "node:assert/strict";
import { checkTopics, textsFrom } from "../topics";

const texts = ["¿Dónde está Jorge?", "¿Jorge ya viene?", "¿Viene Lucía hoy?", "¿Qué día es hoy?"];

test("groups keep only the received texts, and labels come from them (spec 022)", () => {
  const out = checkTopics({
    topics: [
      { label: "Jorge", texts: ["¿Dónde está Jorge?", "¿Jorge ya viene?", "¿Dónde está Pedro?"] },
      { label: "su hija", texts: ["¿Viene Lucía hoy?"] },
      { label: "Lucía", texts: ["¿Viene Lucía hoy?", "¿Jorge ya viene?"] },
    ],
  }, texts);
  assert.deepEqual(out.topics, [
    { label: "Jorge", texts: ["¿Dónde está Jorge?", "¿Jorge ya viene?"] },
    { label: "Lucía", texts: ["¿Viene Lucía hoy?"] },
    { label: "", texts: ["¿Qué día es hoy?"] },
  ]);
  assert.deepEqual(out.dropped, ["unknown text", "label su hija", "unknown text"]);
});

test("a broken answer leaves every question on its own (spec 022)", () => {
  assert.equal(checkTopics("nada", texts).topics.length, 4);
  assert.deepEqual(textsFrom({ texts: [" a ", "a", 3, ""] })?.texts, ["a"]);
  assert.equal(textsFrom({ texts: [] }), null);
});
