import { computed, ref, watch } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'

const THEME_KEY = 'ps_theme'

const isBrowser = typeof window !== 'undefined' && typeof document !== 'undefined'

function getSystemPreference(): 'light' | 'dark' {
  if (!isBrowser || typeof window.matchMedia !== 'function') return 'light'
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function getStoredMode(): ThemeMode {
  if (!isBrowser) return 'system'
  const stored = localStorage.getItem(THEME_KEY)
  if (stored === 'light' || stored === 'dark' || stored === 'system') return stored
  return 'system'
}

const mode = ref<ThemeMode>(getStoredMode())

function applyTheme(m: ThemeMode): void {
  if (!isBrowser) return
  const resolved = m === 'system' ? getSystemPreference() : m
  document.documentElement.classList.toggle('dark', resolved === 'dark')
}

// Apply on load
applyTheme(mode.value)

// Listen for system preference changes
if (isBrowser && typeof window.matchMedia === 'function') {
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (mode.value === 'system') applyTheme('system')
  })
}

watch(mode, (m) => {
  if (isBrowser) localStorage.setItem(THEME_KEY, m)
  applyTheme(m)
})

// eslint-disable-next-line ts/explicit-function-return-type -- composable return type is complex and inferred
export function useTheme() {
  const isDark = computed(() => {
    const resolved = mode.value === 'system' ? getSystemPreference() : mode.value
    return resolved === 'dark'
  })

  function setTheme(m: ThemeMode): void {
    mode.value = m
  }

  function toggle(): void {
    mode.value = mode.value === 'dark' ? 'light' : 'dark'
  }

  return {
    mode,
    isDark,
    setTheme,
    toggle,
  }
}
