import { test } from "node:test";
import assert from "node:assert/strict";
import { EvenChunker, frame } from "../frames";
import { aiSettings } from "../settings";

test("un trozo lleva tipo, largo y contenido", () => {
  const f = frame("T", "Sí.");
  assert.equal(f.toString("ascii", 0, 1), "T");
  assert.equal(f.readUInt32BE(1), 4);
  assert.equal(f.subarray(5).toString("utf8"), "Sí.");
  assert.equal(frame("A", Buffer.alloc(0)).length, 5);
});

test("el audio va en bytes pares", () => {
  const c = new EvenChunker();
  assert.equal(c.push(Buffer.from([1, 2, 3])).length, 2);
  assert.deepEqual([...c.push(Buffer.from([4]))], [3, 4]);
  assert.equal(c.push(Buffer.from([5])).length, 0);
});

test("ajustes de la IA", () => {
  assert.deepEqual(aiSettings(undefined), { on: false, name: "Clara", voice: "", max: 50 });
  assert.deepEqual(aiSettings("roto"), { on: false, name: "Clara", voice: "", max: 50 });
  assert.deepEqual(aiSettings(JSON.stringify({ ai_on: true, ai_name: " Lucía ", ai_voice: "v1", ai_max: 9999 })),
    { on: true, name: "Lucía", voice: "v1", max: 500 });
});
