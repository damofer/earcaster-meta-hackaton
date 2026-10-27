# 020 · The family updates the profile by writing

**Stage:** 4 · Connected caregiver and AI
**Status:** In progress

> Written during the Meta Global AI Developer Hackathon, in English.

## Why

The answers are only as good as the profile and the visit schedule (014, 016), and keeping them up to date is a chore. Families already tell each other the news in a chat: "I'll be there Sunday at 5", "Andrés moved to Medellín". The caregiver should be able to pass that message to Earcaster as it is, and Muse Spark turns it into the right change. Nothing changes until the caregiver approves it.

## Stories

1. As a caregiver, I want to share a family message with Earcaster and get the visit or profile change ready to approve.
2. As a caregiver, I want to see exactly what will change before it changes, and reject it if it is wrong.
3. As a relative, I want to keep writing the way I already do, without learning a new app.

## Requirements

- **R1.** The caregiver can send text to Earcaster from any app with Android's **Share** (for example, a WhatsApp message), or paste it in **Assistant → Update from a message**. They choose the patient if they care for more than one.
- **R2.** Muse Spark reads the message with today's date, the time zone, the visit schedule and the profile, and proposes at most three changes of these kinds: a one-off visit (who, date, time), a change to a recurring visit (014), or a sentence added to or replaced in the profile (016).
- **R3.** Each proposal is shown as a before and after card, in plain words ("Lucía · Sunday, November 1 · 5:00 PM"), with **Approve** and **Discard**. Nothing is saved without **Approve**.
- **R4.** Every name, date and time in a proposal must appear in the message or be computed from it ("Sunday" becomes the next Sunday after today). A proposal that cannot be traced to the message is discarded in code, not shown.
- **R5.** Muse Spark never proposes medication or dose changes; a message about medication gets the answer "Change doses in Reminders" and no proposal.
- **R6.** The message is sent without phone numbers (they are removed in code before it leaves the phone) and is not stored; only the approved change is.
- **R7.** One-off visits are spoken like the others (014) and disappear the day after they happen.

## Acceptance criteria

1. **Given** today is Tuesday, October 27, **when** the caregiver shares "Llego el domingo a las 5, Lucía", **then** they see a proposal "Lucía · Sunday, November 1 · 5:00 PM" and, after **Approve**, "Clara, ¿viene Lucía?" answers that Lucía comes on Sunday at 5.
2. **Given** a message "Andrés se mudó a Medellín", **when** it is shared, **then** the proposal replaces the profile sentence about Andrés's city, showing the old and the new text.
3. **Given** a message "dale media pastilla en la noche", **when** it is shared, **then** there is no proposal and the app says doses are changed in Reminders.
4. **Given** a message "nos vemos pronto", **when** it is shared, **then** the app says it found nothing to update.
5. **Given** a proposal, **when** the caregiver taps **Discard**, **then** nothing changes.

## Plan

- **Engine (public repo):** `museUpdates` with a strict JSON schema output; the trace check (R4) and the medication filter (R5) run in code on the result.
- **Server:** `proposeUpdates` checks the caregiver, removes phone numbers, calls the engine and returns the proposals; it saves nothing.
- **App:** an `ACTION_SEND` text intent filter; the proposals screen; one-off visits stored as `once_visits` next to `fixed_visits` in the settings and read by `Agenda`; an approved change is written by the caregiver's phone (settings or `private/profile`), never by the function.

## Tasks

- [x] T1. `museUpdates` with tests for dates, traceability and medication (R2, R4, R5).
- [x] T2. `proposeUpdates` with number removal (R6).
- [x] T3. Share target, paste field and proposal cards (R1, R3).
- [x] T4. One-off visits in `Agenda` (R7).
- [ ] T5. Check criteria 1 to 5 on a phone.
