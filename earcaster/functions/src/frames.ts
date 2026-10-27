/**
 * The reply travels to the phone in frames: 1 type byte, 4 length bytes (big endian) and the content.
 * T = text of a sentence, A = 16-bit PCM audio at 22,050 Hz, X = action (JSON), E = end (JSON).
 */
export type FrameType = "T" | "A" | "X" | "E";

export function frame(type: FrameType, payload: Buffer | string): Buffer {
  const body = typeof payload === "string" ? Buffer.from(payload, "utf8") : payload;
  const head = Buffer.alloc(5);
  head.write(type, 0, "ascii");
  head.writeUInt32BE(body.length, 1);
  return Buffer.concat([head, body]);
}

/** Groups the audio into even-length byte chunks: AudioTrack does not accept half a 16-bit sample. */
export class EvenChunker {
  private carry: Buffer = Buffer.alloc(0);

  push(chunk: Buffer): Buffer {
    const all = this.carry.length ? Buffer.concat([this.carry, chunk]) : chunk;
    const even = all.length - (all.length % 2);
    this.carry = all.subarray(even);
    return all.subarray(0, even);
  }
}
