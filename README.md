# Earcaster × Meta: a voice companion that never makes things up

Built for the **Meta Global AI Developer Hackathon** (October 26 to November 4, 2026) · Indie track · Theme: *Build for Your World*.

My great-aunt has Alzheimer's. Every day she asks the same questions: what day is it, when is my family coming, did I take my pill. I built [Earcaster](https://earcaster.web.app), an Android app that listens offline and answers her in a calm voice, the week before this event. **What I built during the hackathon** is the AI that answers the questions nobody predicted, on Meta's models, with code-level guarantees that it never invents a visit, a time, a person or a dose, and the changes that connect Earcaster to it.

What existed before and what was built here is spelled out in [HACKATHON.md](HACKATHON.md).

## What it does with Meta's Muse Spark

For the person, by voice:
- **Answers any question** in a calm voice, from the caregiver's profile, the shared family calendar and today's doses. It can also call a relative when asked ("llama a mi hijo"). Every sentence is checked in code before it is spoken (below).

For the caregiver, in Earcaster's caregiver mode:
- **Daily summary** (spec 019): how the day went, from the conversations, doses and help alerts. Counts are made in code; the model only words them.
- **Update from a family message** (spec 020): share a WhatsApp message like "I'll come Sunday at 5" to Earcaster and it proposes the visit or profile change. Each proposal must be traced to the message in code, nothing changes until the caregiver taps Approve, and messages about medication never reach the model.
- **Visit brief** (spec 021): before visiting, a relative gets the week in a few sentences and three good topics to talk about, never a suggestion to test her memory.
- **Change alert** (spec 022): when a new worry starts repeating, nights get restless or help alerts rise. Muse Spark groups the questions into topics; the rules and the alert sentence are code.

## What is in here

| Part | What it does |
| --- | --- |
| [`server/`](server) | The `muse` Cloud Functions: `muse` (answers, streamed), `museSummary`, `museUpdates`, `museBrief`, `museTopics` and `museDemo`, all on Muse Spark 1.3 through the Meta Model API |
| [`earcaster/`](earcaster) | The Android app and its backend. **Pre-hackathon base** up to the tag `earcaster-pre-hackathon`; the event changes (model choice, the four caregiver features) come after it |
| [`demo/`](demo) | A web page to talk to the engine with a made-up person, no account needed; it shows which sentences the guard removed and what the phone would do |
| [`.github/smoke.mjs`](.github/smoke.mjs) | The live test that runs after every deploy, with a fictional person |

## How an answer is made safe

A person with dementia cannot tell a made-up visit from a real one, so the rules are enforced in code, not only asked for in the prompt. Each sentence is checked as it streams in ([`guard.ts`](server/src/guard.ts)):

1. It never tells the person to take a medication: the sentence is dropped.
2. Every weekday, clock hour and calendar date must appear in the facts the phone sent (the time, the shared family calendar, today's logged doses).
3. Every person or place it names must appear in the caregiver's profile, the facts or the conversation.

A sentence that breaks 2 or 3 is replaced by "I don't have that written down", and nothing after it is said. Answers are capped at two sentences, so the voice can start speaking while the rest arrives.

Privacy: only text reaches the engine (audio never leaves the phone), and phone numbers never reach Meta: the model only sees names, and numbers are removed from family messages before they are sent. Meta is used only when the caregiver chooses it for a patient or taps a Meta feature.

## Try it

Talk to the engine at **https://damofer.github.io/earcaster-meta-hackaton/**. It uses `museDemo`, the same engine with a made-up person, a daily budget and no real actions. The first sentence usually arrives in 2 to 4 seconds.

## Running the server tests

```sh
cd server
npm ci
npm test
```

## License

Apache License 2.0.
