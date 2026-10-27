# What was built during the hackathon

The rules ask that judges can tell what was created during the event. This file keeps that line clear.

## Before October 26, 2026 (not part of this repository)

Earcaster, my existing Android app, lives in a separate private repository. It already did, with no AI from Meta:

- Offline speech recognition on the phone (Vosk) and fixed phrases set by a caregiver.
- Spoken answers with the real time, date and next visit from a shared calendar; medication reminders and a dose log; a help alert by SMS.
- A caregiver mode on a second phone, and an optional AI assistant using Claude.

None of that code is in this repository. Earcaster appears in the demo only as the app that calls what is built here.

## From October 26 to November 4, 2026 (this repository)

Everything in this repository was written during the event. The commit history is the record.

| Date | What |
| --- | --- |
| Oct 27 | Answer engine on Muse Spark 1.3: streaming chat completions client, tool calling restricted to caregiver-allowed actions, per-sentence guard (medication, weekdays, hours, dates, names), tests against a fake Meta API |

Outside this repository, also during the event: Earcaster got a setting for the caregiver to choose between Claude and Meta, which is how the app reaches this engine.
