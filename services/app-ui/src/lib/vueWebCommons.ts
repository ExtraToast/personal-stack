import type { ThemeApi, ThemeMode, UseThemeOptions } from '@extratoast/vue-web-commons'
import { useTheme as useCommonsTheme } from '@extratoast/vue-web-commons'

export * from '@extratoast/vue-web-commons'

export const personalStackThemeOptions = {
  storageKey: 'ps_theme',
  defaultMode: 'system',
  allowedModes: ['light', 'dark', 'system'],
  target: () => (typeof document === 'undefined' ? null : document.documentElement),
  attribute: 'data-theme',
  className: 'dark',
} satisfies UseThemeOptions<ThemeMode>

export function useTheme(): ThemeApi<ThemeMode> {
  return useCommonsTheme(personalStackThemeOptions)
}
