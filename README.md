# Earcaster × Meta: a voice companion that never makes things up

Built for the **Meta Global AI Developer Hackathon** (October 26 to November 4, 2026) · Indie track · Theme: *Build for Your World*.

My great-aunt has Alzheimer's. Every day she asks the same questions: what day is it, when is my family coming, did I take my pill. I built [Earcaster](https://earcaster.web.app), an Android app that listens offline and answers her in a calm voice, before this event. **This repository is the new part, built during the hackathon:** the AI that answers the questions nobody predicted, on Meta's models, with code-level guarantees that it never invents a visit, a time, a person or a dose.

What existed before and what was built here is spelled out in [HACKATHON.md](HACKATHON.md).

## What is in here

| Part | What it does | Meta model | Status |
| --- | --- | --- | --- |
| [`server/`](server) | Answer engine: streams an answer from Muse Spark, checks every sentence before it is spoken, and turns tool calls into actions the caregiver allowed | Muse Spark 1.3 (Meta Model API) | Working, tested against a fake API; live test pending the API key |
| `demo/` | A small app to try the engine without Earcaster | | Next |

## How an answer is made safe

A person with dementia cannot tell a made-up visit from a real one, so the rules are enforced in code, not only asked for in the prompt. Each sentence is checked as it streams in ([`guard.ts`](server/src/guard.ts)):

1. It never tells the person to take a medication: the sentence is dropped.
2. Every weekday, clock hour and calendar date must appear in the facts the phone sent (the time, the shared family calendar, today's logged doses).
3. Every person or place it names must appear in the caregiver's profile, the facts or the conversation.

A sentence that breaks 2 or 3 is replaced by "I don't have that written down", and nothing after it is said. Answers are capped at two sentences, so the voice can start speaking while the rest arrives.

Privacy: only text reaches the engine (audio never leaves the phone), and phone numbers never reach Meta. The model only sees names; the caller maps a name back to its number.

## Running the server tests

```sh
cd server
npm ci
npm test
```

## License

Apache License 2.0. Llama 3.2 weights are used under the Llama 3.2 Community License.
