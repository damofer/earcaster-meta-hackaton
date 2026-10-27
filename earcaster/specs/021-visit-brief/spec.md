# 021 · Before the visit

**Stage:** 4 · Connected caregiver and AI
**Status:** In progress

> Written during the Meta Global AI Developer Hackathon, in English.

## Why

A relative who visits once a week arrives without knowing how the week went, and often corrects the person or asks "don't you remember?". A short brief before the visit, made from what the person actually said, helps them arrive ready: what she asked about, who she mentioned, what went well, and a few gentle topics to talk about.

## Stories

1. As a relative about to visit, I want to know in a minute how the week went and what she has been asking about.
2. As a relative, I want ideas for what to talk about that will make her feel good.
3. As a caregiver, I want to choose who gets the brief and when.

## Requirements

- **R1.** For each visit in the schedule (014, 020), the caregiver can turn on **Send a brief before the visit**. On the day of the visit, two hours before (or when the caregiver taps **Write it now**), Earcaster writes the brief.
- **R2.** The brief covers the last 7 days: how many times the person asked about the visitor, the people and topics she mentioned most, missed doses, and help alerts. The counts are made in code (as in 019).
- **R3.** Muse Spark writes at most six sentences: what she has been asking, what seemed to worry her, and three topics to talk about, taken only from the profile (016) and the week's conversations. Every sentence goes through the guard (017 R4, 019 R4).
- **R4.** It never gives medical advice and never tells the visitor to correct or test the person's memory.
- **R5.** The caregiver sends the brief with Android's **Share** (WhatsApp or any app), so Earcaster stores no visitor phone numbers. It is also kept 7 days in **Assistant**.
- **R6.** It uses the same consent as the conversations (017 R13).

## Acceptance criteria

1. **Given** Rosa asked "¿viene Lucía?" 12 times this week and Lucía visits on Sunday, **when** the brief is written, **then** it says she asked about Lucía 12 times.
2. **Given** Rosa talked about her late husband Jorge and the profile says they danced cumbia, **when** the brief is written, **then** one of the topics can be dancing cumbia, and nothing appears that is not in the profile or the conversations.
3. **Given** a week with two missed evening doses, **when** the brief is written, **then** it mentions them without advising what to do.
4. **Given** the caregiver taps **Share**, **when** they choose WhatsApp, **then** the brief arrives as plain text.

## Plan

- **Engine:** `museBrief`, built on `summary.ts` with a 7-day log and the visitor's name.
- **Server:** `briefNow` (on demand) reuses `dayLog` across 7 days; an automatic brief would need Cloud Scheduler and is left for later, as in 019.
- **App:** a **Before the visit** section in **Assistant**: pick the visitor from the weekly and one-off visits (or type a name), read the brief, **Share**. Kept 7 days in `briefs/{day-visitor}`.

## Tasks

- [x] T1. `museBrief` with tests (R2, R3, R4).
- [x] T2. `briefNow` over 7 days (R1, R6).
- [x] T3. Brief card with the visitors from the schedule and **Share** (R1, R5). The switch per visit waits for the automatic brief.
- [ ] T4. Check criteria 1 to 4 on a phone.
