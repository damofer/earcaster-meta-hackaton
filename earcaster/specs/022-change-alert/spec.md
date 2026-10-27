# 022 · Alert when something changes

**Stage:** 4 · Connected caregiver and AI
**Status:** Planned

> Written during the Meta Global AI Developer Hackathon, in English.

## Why

The same questions every day are expected. What a family wants to notice early is a **change**: a question that never came up before and now repeats ("where is Jorge?" about a husband who died years ago), many more questions than usual, questions at night, or help alerts on days that used to be calm. Families often notice weeks late. Earcaster already has the log; it can point out the change, without ever giving a diagnosis.

## Stories

1. As a caregiver, I want to know when a new question starts repeating, so I can talk to her or her doctor sooner.
2. As a caregiver, I want to be told about real changes only, not every day.
3. As a caregiver, I want the alert to say what changed, with the actual words, and nothing about illness.

## Requirements

- **R1.** Once a day, and when the caregiver opens **Assistant**, Earcaster compares the last 3 days with the 14 days before.
- **R2.** The changes are detected **in code**, not by the model: (a) a new topic asked at least 3 times in 3 days that was not asked before; (b) twice as many questions as the usual daily average, with at least 10; (c) questions between 10 PM and 5 AM on 2 or more nights, when there were none before; (d) more help alerts than in the previous 14 days.
- **R3.** Grouping questions into topics ("¿dónde está Jorge?" and "¿Jorge ya llegó?" are the same topic) is done by Muse Spark, which may only group the questions it receives and must return the exact original texts.
- **R4.** The alert is one notification to the caregiver and a card in **Assistant**: what changed, since when, and two or three of the real questions, quoted. Muse Spark writes the card's sentence; the guard checks it (017 R4) and it may not contain any word about illness, diagnosis, decline or stage.
- **R5.** At most one alert per kind every 7 days. The caregiver can mark it **Seen** or **Not useful**; **Not useful** silences that topic for 30 days.
- **R6.** With fewer than 7 days of history, there are no alerts. It uses the same consent as the conversations (017 R13).

## Acceptance criteria

1. **Given** Rosa never asked about Jorge in two weeks, **when** she asks "¿dónde está Jorge?", "¿Jorge ya viene?" and "¿y Jorge?" over three days, **then** the caregiver gets one alert: "Since Saturday, Rosa has asked about Jorge 3 times", with the three questions.
2. **Given** she asks "¿viene Lucía hoy?" every day as always, **when** the check runs, **then** there is no alert.
3. **Given** questions at 2 AM on two nights after two weeks with none, **when** the check runs, **then** the alert says she has been asking questions at night.
4. **Given** an alert, **when** the caregiver marks it **Not useful**, **then** that topic does not alert again for 30 days.
5. **Given** any alert, **then** its text contains no medical word.

## Plan

- **Engine:** `museTopics` (grouping with exact texts, checked in code) and the alert sentence through the guard plus a blocklist of medical words.
- **Server:** `changesNow` on demand; reads 17 days of `talks` and `events`. Conversations are kept 7 days today (017), so the check keeps only daily per-topic counts for 30 days in `patients/{pid}/trends/{day}`, not the conversations themselves.
- **App:** alert card in **Assistant** with **Seen** and **Not useful**, and a notification.

## Tasks

- [ ] T1. Daily per-topic counts in `trends` (R1, R6).
- [ ] T2. Change rules in code with tests (R2, R5).
- [ ] T3. `museTopics` and the alert sentence with the medical blocklist (R3, R4).
- [ ] T4. Card and notification (R4, R5).
- [ ] T5. Check criteria 1 to 5 with a seeded history.

## Open questions

- **Daily run:** a check without opening the app needs Cloud Scheduler; until then it runs when the caregiver opens **Assistant** or when the summary (019) is written.
