# 019 · The caregiver's daily summary

**Stage:** 4 · Connected caregiver and AI
**Status:** In progress

> Written during the Meta Global AI Developer Hackathon, in English.

## Why

A caregiver who lives away does not have time to read every conversation of the day (017 R10). They want to know, in a few sentences, how the day went: what the person asked about, what they asked about again and again, which doses were logged and which are missing, and whether they asked for help. Muse Spark writes that summary from the day's log, and it may only say what the log says.

## Stories

1. As a caregiver, I want a short summary of today so I know how the day went without reading every conversation.
2. As a caregiver, I want to know which questions keep coming back, because that tells me what worries them.
3. As a caregiver, I want to trust that the summary does not invent anything.

## Requirements

- **R1.** In **Assistant**, a **Today's summary** section with a **Write it now** button. The summary covers the phone's day, from local midnight until now.
- **R2.** The summary uses: the AI conversations (017), the doses logged (009) and the help alerts (006) of the day, plus the names of the expected doses. It never sends phone numbers.
- **R3.** Muse Spark writes it through the `earcaster-muse` engine, in at most five sentences, in the app's language. The numbers (questions, repeated questions, doses logged and missing, help alerts) are counted in code, not by the model.
- **R4.** Every sentence goes through the same guard as the spoken answers: a sentence with a time, day, date or name that is not in the log is dropped, and so is any medical advice.
- **R5.** It only runs with the caregiver's consent (017 R13), which already names Meta.
- **R6.** The summary is kept 30 days in `patients/{pid}/summaries/{day}`; only the function writes it, only the caregiver reads and deletes it, and it is deleted with the patient. At most 20 summaries per patient per day.

## Acceptance criteria

1. **Given** a day where the person asked "¿viene Ana hoy?" three times and took the morning pill, **when** the caregiver taps **Write it now**, **then** the summary says they asked about Ana three times and that the morning dose is logged.
2. **Given** an evening dose that was not logged, **when** the summary is written, **then** it says the evening dose is missing.
3. **Given** a help alert at 4 pm, **when** the summary is written, **then** it mentions the alert at that time.
4. **Given** a day with nothing logged, **when** the caregiver taps the button, **then** they see that nothing is written down yet.
5. **Given** the caregiver has not accepted the data notice, **when** they tap the button, **then** no data is sent.

## Plan

- **Engine (public repo):** `museSummary` in `server/src/summary.ts`, protected by `ENGINE_TOKEN`; it counts the statistics, asks Muse Spark for the text and runs the guard on each sentence.
- **Server (this repo):** `summaryNow` in `functions/src/summary.ts` and `index.ts`: checks that the caller is the patient's caregiver and that consent exists, reads the day's log with the phone's time zone, calls the engine and saves the result.
- **Rules:** `summaries/{day}` readable and deletable by the caregiver only.
- **App:** a section in `AiActivity` that calls `summaryNow` with the phone's day, midnight, time zone and date label.

## Tasks

- [x] T1. Engine `museSummary` with tests (R3, R4).
- [x] T2. `summaryNow`, Firestore rule and deleting summaries with the patient (R2, R5, R6).
- [x] T3. **Today's summary** section in **Assistant** (R1).
- [ ] T4. Check criteria 1 to 5 with the Meta key and on a phone.

## Open questions

- **Automatic summary:** a summary every evening, without tapping, would need Cloud Scheduler; left for later.
