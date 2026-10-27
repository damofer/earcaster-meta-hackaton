import { test } from "node:test";
import assert from "node:assert/strict";
import { SAFE_SENTENCE, SAFE_SENTENCE_EN, checkSentence, takeSentences } from "../speech";
import { Facts } from "../types";

const facts: Facts = {
  now: "Hoy es domingo 19 de octubre. Son las 10:15 de la mañana.",
  visits: ["Ana viene el martes a las 5:00 de la tarde."],
  doses: ["Pastilla de la mañana: anotada hoy a las 9:05."],
};

test("corta solo las frases terminadas", () => {
  assert.deepEqual(takeSentences("Hola, soy Clara. Ana viene"), { sentences: ["Hola, soy Clara."], rest: "Ana viene" });
  assert.deepEqual(takeSentences("Hola. ¿Estás bien? "), { sentences: ["Hola.", "¿Estás bien?"], rest: "" });
});

test("espera si no hay espacio después del punto", () => {
  assert.deepEqual(takeSentences("Son las 3.5"), { sentences: [], rest: "Son las 3.5" });
  assert.deepEqual(takeSentences("Ana viene."), { sentences: [], rest: "Ana viene." });
});

test("nunca dice que se tome algo", () => {
  assert.equal(checkSentence("Tómala ahora, por favor.", facts), null);
  assert.equal(checkSentence("Tómese la pastilla.", facts), null);
  assert.equal(checkSentence("Ya la tomaste esta mañana.", facts), "Ya la tomaste esta mañana.");
});

test("los días deben estar en los datos", () => {
  assert.equal(checkSentence("Ana viene el martes.", facts), "Ana viene el martes.");
  assert.equal(checkSentence("Hoy es domingo.", facts), "Hoy es domingo.");
  assert.equal(checkSentence("Carlos viene el jueves.", facts), SAFE_SENTENCE);
  assert.equal(checkSentence("Carlos viene el miercoles.", facts), SAFE_SENTENCE);
});

test("las horas deben estar en los datos", () => {
  assert.equal(checkSentence("Ana llega a las cinco.", facts), "Ana llega a las cinco.");
  assert.equal(checkSentence("Ana llega a las 5.", facts), "Ana llega a las 5.");
  assert.equal(checkSentence("Ana llega a las ocho.", facts), SAFE_SENTENCE);
  assert.equal(checkSentence("Ana llega a la una.", facts), SAFE_SENTENCE);
});

const factsEn: Facts = {
  now: "Today is Sunday, October 19. It's a quarter past 10 in the morning.",
  visits: ["Ana is coming on Tuesday at half past 5 in the afternoon."],
  doses: ["Morning pill: noted today at 9:05."],
};

test("in English it never says to take something", () => {
  assert.equal(checkSentence("Take it now, please.", factsEn, "en"), null);
  assert.equal(checkSentence("Don't forget to take your pill.", factsEn, "en"), null);
  assert.equal(checkSentence("You already took it this morning.", factsEn, "en"), "You already took it this morning.");
});

test("in English the days must be in the data", () => {
  assert.equal(checkSentence("Ana is coming on Tuesday.", factsEn, "en"), "Ana is coming on Tuesday.");
  assert.equal(checkSentence("Carlos is coming on Thursday.", factsEn, "en"), SAFE_SENTENCE_EN);
});

test("in English the hours must be in the data", () => {
  assert.equal(checkSentence("Ana arrives at five.", factsEn, "en"), "Ana arrives at five.");
  assert.equal(checkSentence("Ana arrives at 5.", factsEn, "en"), "Ana arrives at 5.");
  assert.equal(checkSentence("Ana arrives at eight.", factsEn, "en"), SAFE_SENTENCE_EN);
});
