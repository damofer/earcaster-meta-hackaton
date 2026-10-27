import { test } from "node:test";
import assert from "node:assert/strict";
import { Context, SAFE, check, speakable, takeSentences } from "../guard";

const es: Context = {
  lang: "es",
  assistant: "Clara",
  profile: { callMe: "Mari", family: "Su hija Ana vive cerca. Su hijo Carlos vive en Madrid." },
  facts: {
    now: "lunes 19 de octubre de 2026, las 6 y media de la tarde",
    visits: ["Ana: el domingo 25 de octubre a las 5 en punto de la tarde"],
    doses: ["Pastilla de la mañana a las 9 y 5"],
  },
  said: ["¿viene Ana hoy?"],
};
const en: Context = {
  lang: "en",
  assistant: "Grace",
  profile: { callMe: "Rose", family: "Her daughter Lucy lives nearby." },
  facts: { now: "Monday, October 19, 2026, half past 6 in the evening", visits: ["Lucy: Sunday, October 25 at 5 o'clock"], doses: [] },
  said: [],
};

test("a grounded sentence passes", () => {
  assert.deepEqual(check("Ana viene el domingo a las 5 de la tarde, Mari.", es), { ok: true, text: "Ana viene el domingo a las 5 de la tarde, Mari." });
  assert.equal(check("Lucy is coming on Sunday at 5 o'clock.", en).ok, true);
});

test("a weekday that is not in the facts is replaced", () => {
  const v = check("Ana viene el martes.", es);
  assert.equal(v.ok, false);
  assert.equal(!v.ok && v.replacement, SAFE.es);
  assert.equal(check("Lucy is coming on Tuesday.", en).ok, false);
});

test("an hour that is not in the facts is replaced", () => {
  assert.equal(check("Ana viene el domingo a las 3.", es).ok, false);
  assert.equal(check("Ana viene el domingo a las cinco.", es).ok, true);
  assert.equal(check("Lucy comes at 3 pm.", en).ok, false);
  assert.equal(check("Lucy comes at five o'clock.", en).ok, true);
});

test("a date that is not in the facts is replaced", () => {
  assert.equal(check("Ana viene el 26 de octubre.", es).ok, false);
  assert.equal(check("Ana viene el 25 de octubre.", es).ok, true);
  assert.equal(check("Lucy comes on October 30.", en).ok, false);
});

test("a person that is nowhere in the profile or the facts is replaced", () => {
  const v = check("Hoy viene Pedro a verte.", es);
  assert.equal(v.ok, false);
  assert.equal(!v.ok && v.reason, "name Pedro");
  assert.equal(check("Carlos vive en Madrid, Mari.", es).ok, true);
  assert.equal(check("Soy Clara, la voz de tu teléfono.", es).ok, true);
});

test("it never tells the person to take a medication", () => {
  for (const s of ["Tómate la pastilla, Mari.", "No olvides tomar tu pastilla.", "Don't forget to take your pill.", "Take it with water."]) {
    const v = check(s, s.startsWith("T") && !s.startsWith("Take") ? es : en);
    assert.deepEqual(v, { ok: false, replacement: null, reason: "medication" }, s);
  }
  assert.equal(check("Hoy la pastilla de la mañana está anotada a las 9 y 5.", es).ok, true);
});

test("markdown and emoji are not spoken", () => {
  assert.equal(speakable("**Hola** Mari 😊"), "Hola Mari");
  assert.equal(speakable("- Ana viene"), "Ana viene");
});

test("streamed text is split into finished sentences", () => {
  assert.deepEqual(takeSentences("Hola Mari. Ana vie"), { sentences: ["Hola Mari."], rest: " Ana vie" });
  assert.deepEqual(takeSentences("Hola Mari."), { sentences: [], rest: "Hola Mari." });
  assert.deepEqual(takeSentences("Preguntó a las 9:15 a. m. y a las 11:02 a. m. Luego durmió.\n").sentences,
    ["Preguntó a las 9:15 a. m. y a las 11:02 a. m.", "Luego durmió."]);
  assert.deepEqual(takeSentences("Son las 5.30 ya. Bien"), { sentences: ["Son las 5.30 ya."], rest: " Bien" });
  assert.deepEqual(takeSentences("¿Quién eres? Soy Clara. "), { sentences: ["¿Quién eres?", "Soy Clara."], rest: " " });
});
