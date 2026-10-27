import { Context, check, takeSentences } from "./guard";
import { ChatMessage, streamMuse } from "./muse";
import { developerPrompt } from "./prompt";
import { resolve, toolsFor } from "./tools";
import { Action, Question } from "./types";

export const MAX_SENTENCES = 2;

export type Event =
  | { t: "say"; text: string }
  | { t: "do"; action: Action }
  | { t: "dropped"; reason: string };

/**
 * Answers one question: asks Muse Spark, checks each sentence as it streams in and emits it right away, so the
 * caller can start the voice before the answer is finished. Returns the action, if the model asked for an allowed one.
 */
export async function answer(q: Question, apiKey: string, emit: (e: Event) => void, timeoutMs = 9000): Promise<Action | null> {
  const messages: ChatMessage[] = [{ role: "developer", content: developerPrompt(q) }];
  for (const turn of q.history) {
    messages.push({ role: "user", content: turn.q }, { role: "assistant", content: turn.a });
  }
  messages.push({ role: "user", content: q.text });

  const ctx: Context = {
    lang: q.lang,
    assistant: q.assistant,
    profile: q.profile,
    facts: q.facts,
    said: [q.text, ...q.history.flatMap((t) => [t.q, t.a])],
  };
  let spoken = 0;
  let buffer = "";
  const say = (list: string[]) => {
    for (const raw of list) {
      if (spoken >= MAX_SENTENCES) return;
      const v = check(raw, ctx);
      if (v.ok) {
        if (!v.text) continue;
        spoken++;
        emit({ t: "say", text: v.text });
      } else {
        emit({ t: "dropped", reason: v.reason });
        if (v.replacement) {
          spoken++;
          emit({ t: "say", text: v.replacement });
          // After a replaced sentence the rest is not trusted either.
          spoken = MAX_SENTENCES;
        }
      }
    }
  };

  const result = await streamMuse({
    apiKey,
    messages,
    tools: toolsFor(q.allowed, q.lang),
    timeoutMs,
    onText: (delta) => {
      buffer += delta;
      const { sentences, rest } = takeSentences(buffer);
      buffer = rest;
      say(sentences);
    },
  });
  // A sentence cut by the token limit is not said.
  if (buffer.trim() && result.finishReason !== "length") say([buffer.trim()]);

  for (const call of result.toolCalls) {
    const action = resolve(call.name, call.arguments, q.allowed);
    if (action) {
      emit({ t: "do", action });
      return action;
    }
  }
  return null;
}
