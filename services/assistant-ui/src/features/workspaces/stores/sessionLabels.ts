import { defineStore } from 'pinia'
import { ref } from 'vue'

// Session tabs default to "<KIND> <id8>", which is unreadable once a
// workspace has several agents. A custom label is a purely cosmetic,
// per-device convenience, so it lives in localStorage keyed by session
// id rather than the assistant-api (no schema/contract churn for a
// display string). Cleared labels fall back to the default in the tab.
const STORAGE_KEY = 'assistant-ui:session-labels'

function load(): Record<string, string> {
  const out: Record<string, string> = {}
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const parsed: unknown = raw ? JSON.parse(raw) : null
    if (parsed && typeof parsed === 'object') {
      for (const [id, label] of Object.entries(parsed)) {
        if (typeof label === 'string') out[id] = label
      }
    }
  } catch {
    // malformed or unavailable storage — start empty
  }
  return out
}

export const useSessionLabelsStore = defineStore('sessionLabels', () => {
  const labels = ref<Record<string, string>>(load())

  function persist(value: Record<string, string>): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(value))
    } catch {
      // storage full/unavailable — labels stay in-memory for the session
    }
  }

  function rename(id: string, label: string): void {
    const trimmed = label.trim()
    const next = { ...labels.value }
    if (trimmed) next[id] = trimmed
    else delete next[id]
    labels.value = next
    persist(next)
  }

  function labelFor(id: string): string | null {
    return labels.value[id] ?? null
  }

  return { labels, rename, labelFor }
})
