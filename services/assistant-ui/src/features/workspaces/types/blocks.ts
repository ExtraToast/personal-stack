export interface ChoiceOption {
  id: string
  label: string
}

// Prettier wants `=` at the end of the line; eslint's
// `style/operator-linebreak` wants it at the start of the next.
// Suppress the eslint rule rather than fight prettier — the
// formatter is the authority on layout. Reviewed via CI on every PR.
// eslint-disable-next-line style/operator-linebreak
export type Block =
  | { kind: 'text'; md: string }
  | { kind: 'choice'; prompt: string; options: ChoiceOption[] }
  | { kind: 'form'; schema: Record<string, unknown>; submitLabel?: string }
  | { kind: 'diff'; path: string; patch: string }
  | { kind: 'tool-call'; name: string; args: Record<string, unknown>; result?: unknown }
  | { kind: 'approval'; action: string; payload: Record<string, unknown> }

/**
 * Mirror of kotlin-commons-blocks BlockParser. Used when an agent's
 * Turn body contains fenced ```block``` regions; the SessionTranscript
 * splits the body up so each block renders with its own component.
 *
 * Regex notes: the opening fence is matched as
 * `\`\`\`block[^\n]*\n` rather than `\`\`\`block\s*\n` so the inner
 * `[\s\S]*?` cannot share characters with the prefix — otherwise
 * eslint's regexp/no-super-linear-backtracking flags the alternation
 * as exploitable.
 */
const FENCE = /```block[^\n]*\n([\s\S]*?)```/g

export function parseBlocks(stream: string): Block[] {
  if (!stream) return []
  const blocks: Block[] = []
  let cursor = 0
  for (const match of stream.matchAll(FENCE)) {
    const idx = match.index ?? 0
    const plain = stream.slice(cursor, idx)
    if (plain.trim().length > 0) blocks.push({ kind: 'text', md: plain })
    try {
      const parsed: unknown = JSON.parse(match[1]!.trim())
      // The Block union is the JSON shape the API contract guarantees;
      // a tighter runtime validator lives behind a future shared schema.
      // eslint-disable-next-line ts/consistent-type-assertions
      blocks.push(parsed as Block)
    } catch {
      blocks.push({ kind: 'text', md: `[unparsed block: ${match[1]}]` })
    }
    cursor = idx + match[0].length
  }
  const trailing = stream.slice(cursor)
  if (trailing.trim().length > 0) blocks.push({ kind: 'text', md: trailing })
  return blocks
}
