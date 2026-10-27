import { ChatStreamParser, StreamResult } from "./sse";
import { Tool } from "./tools";

/** Meta's Model API, chat completions format. The variable exists only to test against a fake server. */
function baseUrl(): string {
  return process.env.META_BASE_URL || "https://api.meta.ai/v1";
}
/** The fast Muse Spark. Not the Contributor tier: Meta trains on its inputs, and these carry health data. */
export const MUSE_MODEL = "muse-spark-1.3";

export interface ChatMessage {
  role: "developer" | "user" | "assistant";
  content: string;
}

export interface MuseRequest {
  apiKey: string;
  messages: ChatMessage[];
  tools: Tool[];
  onText: (delta: string) => void;
  timeoutMs: number;
  /** Reasoning counts toward this limit. Longer inputs (a day or a week of conversations) need more. */
  maxTokens?: number;
}

/** Streams one answer from Muse Spark. Text arrives through onText; tool calls come back at the end. */
export async function streamMuse(r: MuseRequest): Promise<StreamResult> {
  const abort = new AbortController();
  const timer = setTimeout(() => abort.abort(), r.timeoutMs);
  try {
    const res = await fetch(`${baseUrl()}/chat/completions`, {
      method: "POST",
      signal: abort.signal,
      headers: {
        Authorization: `Bearer ${r.apiKey}`,
        "Content-Type": "application/json",
        Accept: "text/event-stream",
      },
      body: JSON.stringify({
        model: MUSE_MODEL,
        messages: r.messages,
        stream: true,
        // Muse Spark always reasons; low keeps the first spoken words close to a second.
        reasoning_effort: "low",
        // Reasoning counts toward this limit; the answer itself is at most two sentences.
        max_completion_tokens: r.maxTokens ?? 1500,
        ...(r.tools.length ? { tools: r.tools, tool_choice: "auto" } : {}),
      }),
    });
    if (!res.ok || !res.body) {
      const detail = await res.text().catch(() => "");
      throw new Error(`Meta Model API ${res.status}: ${detail.slice(0, 300)}`);
    }
    const parser = new ChatStreamParser(r.onText);
    const decoder = new TextDecoder();
    for await (const chunk of res.body as unknown as AsyncIterable<Uint8Array>) {
      parser.push(decoder.decode(chunk, { stream: true }));
    }
    parser.push(decoder.decode());
    return parser.end();
  } finally {
    clearTimeout(timer);
  }
}
