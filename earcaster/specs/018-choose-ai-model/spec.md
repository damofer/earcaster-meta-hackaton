# 018 · Choose the AI model: Claude or Meta

**Stage:** 4 · Connected caregiver and AI
**Status:** In progress

> Written during the Meta Global AI Developer Hackathon, in English. Specs 001 to 017 predate the event and are in Spanish.

## Why

The AI conversation (017) only used Claude. For Meta's hackathon, and so as not to depend on a single company, the caregiver can choose who writes the answers: Claude (Anthropic) or Muse Spark (Meta). Everything else in 017 stays the same.

## Stories

1. As a caregiver, I want to choose each patient's AI model without anything else changing.
2. As a caregiver, I want to know which company the data goes to before I switch models.

## Requirements

- **R1.** In **Assistant**, the caregiver chooses each patient's AI model: **Claude** or **Meta**. It travels with the patient's settings. If nothing was chosen, Claude stays, as before.
- **R2.** Both models follow the same 017 rules: the profile, the allowed actions, the check of every sentence before it is spoken (R4), the ElevenLabs voice, the daily maximum and the fallback phrase.
- **R3.** With Meta, Muse Spark 1.3 is used, never the Contributor tier: on that tier Meta trains on what it receives, and this data includes health data.
- **R4.** When switching to Meta, the app explains that the data will go to Meta instead of Anthropic; if the caregiver cancels, Claude stays. The notice of what leaves the phone (017 R13) names both companies.
- **R5.** The Meta key lives on the server, like the others (017 R12).

## Acceptance criteria

1. **Given** a patient with no model chosen, **when** they ask "Clara, ¿viene Ana hoy?", **then** Claude answers, as before.
2. **Given** Meta chosen and saved, **when** they ask "Clara, ¿viene Ana hoy?" with a visit from Ana on Sunday, **then** Muse Spark answers that Ana comes on Sunday, with the same voice.
3. **Given** Meta chosen and calling Carlos allowed, **when** they say "Clara, llama a mi hijo", **then** the app says "Llamando a Carlos" and calls.
4. **Given** Claude saved, **when** the caregiver taps **Meta**, **then** they see the notice; **Cancel** goes back to Claude.
5. **Given** Meta chosen, **when** the caregiver uses the profile preview, **then** Muse Spark writes the answer.

## Plan

- **Server:** with Meta, the function does not call Meta directly: it calls `earcaster-muse`, the engine built during the hackathon in the public repository [damofer/earcaster-meta-hackaton](https://github.com/damofer/earcaster-meta-hackaton) and deployed as a separate function in the same Firebase project. The engine receives only text and names, never phone numbers; the action it returns goes through `resolve()` again, which takes the number from the profile. Incoming sentences are checked again with `checkSentence`.
- **Data:** `ai_model` (`"claude"` or `"meta"`) in the patient's settings JSON; any other value counts as Claude.
- **Secrets:** `ENGINE_TOKEN` in Secret Manager, shared with the engine; only the engine uses `META_API_KEY`. The deploy check verifies that `ENGINE_TOKEN` exists.
- **App:** two buttons in `AiActivity` and `Prefs.AI_MODEL` in the settings that travel.

## Tasks

- [x] T1. `ai_model` in the function's settings, with tests (R1).
- [x] T2. Call the `earcaster-muse` engine with the allowed actions without numbers, and check sentences here too (R2, R3, R5).
- [x] T3. `ENGINE_TOKEN` in the deploy check and two Meta questions in the test of the deployed function.
- [x] T4. Selector in **Assistant**, notice when switching to Meta, and data notice naming both companies (R1, R4).
- [ ] T5. Check criteria 1 to 5 with the Meta key and on a phone.

## Open questions

- **Response time:** Muse Spark always reasons before answering; we need to measure whether it reaches the first sentence as fast as Claude.
