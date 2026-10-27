/**
 * Reads an OpenAI-style chat completions stream, as Meta's Model API sends it: `data: {json}` lines and a final
 * `data: [DONE]`. Text deltas go to onText as they arrive; tool calls are put together from their pieces.
 */
export interface ToolCall {
  name: string;
  arguments: string;
}

export interface StreamResult {
  toolCalls: ToolCall[];
  finishReason: string | null;
}

export class ChatStreamParser {
  private pending = "";
  private calls: ToolCall[] = [];
  private finish: string | null = null;

  constructor(private readonly onText: (delta: string) => void) {}

  /** Feeds raw text from the response body; it may cut a line anywhere. */
  push(chunk: string): void {
    this.pending += chunk;
    let nl: number;
    while ((nl = this.pending.indexOf("\n")) >= 0) {
      const line = this.pending.slice(0, nl).replace(/\r$/, "");
      this.pending = this.pending.slice(nl + 1);
      this.line(line);
    }
  }

  end(): StreamResult {
    if (this.pending.trim()) this.line(this.pending.trim());
    this.pending = "";
    return { toolCalls: this.calls.filter((c) => c.name), finishReason: this.finish };
  }

  private line(line: string): void {
    if (!line.startsWith("data:")) return;
    const data = line.slice(5).trim();
    if (!data || data === "[DONE]") return;
    let json: any;
    try {
      json = JSON.parse(data);
    } catch {
      return;
    }
    const choice = json?.choices?.[0];
    if (!choice) return;
    if (choice.finish_reason) this.finish = choice.finish_reason;
    const delta = choice.delta || {};
    if (typeof delta.content === "string" && delta.content) this.onText(delta.content);
    for (const tc of delta.tool_calls || []) {
      const i = typeof tc.index === "number" ? tc.index : this.calls.length;
      const call = (this.calls[i] ||= { name: "", arguments: "" });
      if (tc.function?.name) call.name += tc.function.name;
      if (tc.function?.arguments) call.arguments += tc.function.arguments;
    }
  }
}
