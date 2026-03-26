import { createI18n } from 'vue-i18n'
import en from './locales/en'
import nl from './locales/nl'

const LOCALE_KEY = 'ps_locale'

function getSavedLocale(): 'en' | 'nl' {
  const stored = localStorage.getItem(LOCALE_KEY)
  if (stored === 'en' || stored === 'nl') return stored
  // Detect browser language
  const browserLang = navigator.language.toLowerCase()
  return browserLang.startsWith('nl') ? 'nl' : 'en'
}

export const i18n = createI18n({
  legacy: false,
  locale: getSavedLocale(),
  fallbackLocale: 'en',
  messages: { en, nl },
})

export function setLocale(locale: 'en' | 'nl'): void {
  i18n.global.locale.value = locale
  localStorage.setItem(LOCALE_KEY, locale)
  document.documentElement.lang = locale
}
