export interface ChoiceOption {
  id: string
  label: string
}

export type Block =
  | { kind: 'text'; md: string }
  | { kind: 'choice'; prompt: string; options: ChoiceOption[] }
  | { kind: 'form'; schema: Record<string, unknown>; submitLabel?: string }
  | { kind: 'diff'; path: string; patch: string }
  | { kind: 'tool-call'; name: string; args: Record<string, unknown>; result?: unknown }
  | { kind: 'approval'; action: string; payload: Record<string, unknown> }

/**
 * Mirror of libs/kotlin-common BlockParser. Used when an agent's
 * Turn body contains fenced ```block``` regions; the SessionTranscript
 * splits the body up so each block renders with its own component.
 */
const FENCE = /```block\s*\n([\s\S]*?)```/g

export function parseBlocks(stream: string): Block[] {
  if (!stream) return []
  const blocks: Block[] = []
  let cursor = 0
  let match: RegExpExecArray | null
  while ((match = FENCE.exec(stream)) !== null) {
    const plain = stream.slice(cursor, match.index)
    if (plain.trim().length > 0) blocks.push({ kind: 'text', md: plain })
    try {
      const block = JSON.parse(match[1]!.trim()) as Block
      blocks.push(block)
    } catch {
      blocks.push({ kind: 'text', md: `[unparsed block: ${match[1]}]` })
    }
    cursor = match.index + match[0].length
  }
  const trailing = stream.slice(cursor)
  if (trailing.trim().length > 0) blocks.push({ kind: 'text', md: trailing })
  return blocks
}
