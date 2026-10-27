# 023 · Scam shield

**Stage:** 4 · Connected caregiver and AI
**Status:** Draft, needs more planning

> Written during the Meta Global AI Developer Hackathon, in English.

## Why

Older adults, and even more people with memory loss, are a common target of phone scams: "this is your bank, give me the code", "your grandson had an accident, send money". They often tell someone right after the call, and Earcaster is the one listening. If the person tells Earcaster about it, Earcaster can tell them calmly not to give anything and let the family know.

## Stories

1. As a person living alone, when I tell Earcaster that someone asked me for a code or money, I want it to tell me what to do in simple words.
2. As a caregiver, I want to know right away when that happens, with what she said.
3. As a caregiver, I do not want false alarms every time she talks about the bank or money.

## Requirements (draft)

- **R1.** When the person says something that sounds like a scam has just happened or is happening (someone asked for a code, PIN, password, card number or money, or claimed a relative is in trouble), Earcaster answers with one fixed, calm sentence written by the caregiver, for example "No le des ningún número a nadie. Ya le aviso a Lucía."
- **R2.** It sends the same alert as the help alert (009), marked as a possible scam, with the exact words heard.
- **R3.** Detection is two steps: a list of words checked in code (code, clave, PIN, tarjeta, transferencia, banco, accidente, consignar…) and, only when the list matches, Muse Spark decides whether it is about a request for money or data. The answer the person hears never comes from the model; it is the caregiver's fixed sentence.
- **R4.** The caregiver turns it on, writes the sentence and chooses who is alerted. At most one alert every 30 minutes.

## Open questions (to plan before building)

- **During or after the call:** Earcaster cannot hear the phone call itself (Android does not allow it, and it would not be right). It only knows what the person tells it afterwards, or says aloud near the phone. Is that enough to be useful?
- **False alarms:** "me llamó el banco para lo del crédito" can be normal. How strict should step 2 be, and should the caregiver see a test screen with example sentences?
- **Tone:** the sentence must not scare her. Should the caregiver record it in their own voice?
- **Responsibility:** the app must say clearly that it does not block calls or protect the account; it only reminds and alerts.
- **Other languages and countries:** the word list depends on local banks and scam scripts (Colombia first).

## Acceptance criteria (draft)

1. **Given** the shield is on, **when** Rosa says "me llamaron del banco y me pidieron la clave", **then** she hears the caregiver's sentence and Lucía gets an alert with those words.
2. **Given** the shield is on, **when** Rosa says "hoy fui al banco con Andrés", **then** there is no alert.
3. **Given** an alert 10 minutes ago, **when** she says it again, **then** she hears the sentence but no second alert is sent.
