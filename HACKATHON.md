# What was built during the hackathon

The rules ask that judges can tell what was created during the event. This file keeps that line clear.

## Before October 26, 2026: `earcaster/` up to the tag `earcaster-pre-hackathon`

Earcaster is my existing Android app. I started it on October 19, 2026 (its first commit), one week before the hackathon. Before the event it already did, with no AI from Meta:

- Offline speech recognition on the phone (Vosk) and fixed phrases set by a caregiver.
- Spoken answers with the real time, date and next visit from a shared calendar; medication reminders and a dose log; a help alert by SMS.
- A caregiver mode on a second phone, and an optional AI assistant using Claude.

So judges can see the whole product, it was copied into [`earcaster/`](earcaster) in **one commit**, tagged [`earcaster-pre-hackathon`](https://github.com/damofer/earcaster-meta-hackaton/releases/tag/earcaster-pre-hackathon), exactly as it was on October 24 (private repository, commit `051b733`), without its history and without the Firebase client config. **Nothing in that commit is event work.**

To see only what changed in Earcaster during the event: [compare `earcaster-pre-hackathon...main`](https://github.com/damofer/earcaster-meta-hackaton/compare/earcaster-pre-hackathon...main), or `git diff earcaster-pre-hackathon -- earcaster/`.

## From October 26 to November 4, 2026 (this repository)

Everything outside `earcaster/`, and every change to `earcaster/` after the tag, was written during the event. The commit history is the record.

| Date | What |
| --- | --- |
| Oct 27 | Answer engine on Muse Spark 1.3: streaming chat completions client, tool calling restricted to caregiver-allowed actions, per-sentence guard (medication, weekdays, hours, dates, names), tests against a fake Meta API |
| Oct 27 | Earcaster: a setting for the caregiver to choose Claude or Meta per patient, with a notice before data goes to Meta (`earcaster/`, spec 018) |
| Oct 27 | Earcaster's backend calls this engine when Meta is chosen, sending names but never phone numbers (`earcaster/functions/src/muse.ts`) |
| Oct 27 | Public demo: `museDemo` endpoint with a made-up person and a daily budget, and a bilingual web page that streams the answer, reads it aloud with the browser's voice and shows what the guard removed |
| Oct 27 | Caregiver's daily summary written by Muse Spark: counts made in code, every sentence through the guard (`museSummary`, and in Earcaster the Assistant screen's "Today's summary", spec 019) |
| Oct 27 | Specs 020 to 022 (family updates, visit brief, change alert) and a draft of 023 (scam shield), in English |
| Oct 27 | Family updates engine (`museUpdates`, spec 020): a family message becomes visit or profile proposals, each traced to the message in code; medication messages never reach the model; phone numbers removed |
| Oct 27 | Earcaster: share a family message to the app, see each proposed change as a card, approve or discard; one-off visits (`earcaster/`, spec 020) |
| Oct 27 | Visit brief (`museBrief`, and in Earcaster the Assistant screen's "Before the visit", spec 021): the week for a relative about to visit, never suggesting to test her memory, shared with Android's Share |
| Oct 27 | Change alert (`museTopics`, and in Earcaster daily trends, rules in code and alert cards, spec 022): a new worry that starts repeating, a busy day, night questions or more help alerts; sentences from templates, never about illness |
| Oct 27 | Tone: the voice always uses "tú", like the rest of the phone; the visit brief counts read naturally ("asked about you 2 times") |

