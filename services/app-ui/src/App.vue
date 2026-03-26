<script setup lang="ts">
import { useTheme } from '@private-stack/vue-common'
import { useI18n } from 'vue-i18n'
import { RouterView } from 'vue-router'
import { setLocale } from './i18n'

const { mode, setTheme } = useTheme()
const { t, locale } = useI18n()

function toggleLocale(): void {
  setLocale(locale.value === 'en' ? 'nl' : 'en')
}

function cycleTheme(): void {
  const modes = ['light', 'dark', 'system'] as const
  const idx = modes.indexOf(mode.value)
  setTheme(modes[(idx + 1) % modes.length]!)
}
</script>

<template>
  <div class="min-h-screen bg-white text-gray-900 dark:bg-gray-950 dark:text-gray-100">
    <!-- Sticky nav -->
    <nav
      class="fixed top-0 z-50 w-full border-b border-gray-200/60 bg-white/80 backdrop-blur dark:border-gray-800/60 dark:bg-gray-950/80"
    >
      <div class="mx-auto flex max-w-5xl items-center justify-between px-6 py-3">
        <a class="text-sm font-bold tracking-tight" href="#"> JJ </a>

        <div class="hidden items-center gap-6 text-sm sm:flex">
          <a class="hover:text-indigo-600 dark:hover:text-indigo-400" href="#about">
            {{ t('nav.about') }}
          </a>
          <a class="hover:text-indigo-600 dark:hover:text-indigo-400" href="#experience">
            {{ t('nav.experience') }}
          </a>
          <a class="hover:text-indigo-600 dark:hover:text-indigo-400" href="#projects">
            {{ t('nav.projects') }}
          </a>
          <a class="hover:text-indigo-600 dark:hover:text-indigo-400" href="#skills">
            {{ t('nav.skills') }}
          </a>
          <a class="hover:text-indigo-600 dark:hover:text-indigo-400" href="#contact">
            {{ t('nav.contact') }}
          </a>
        </div>

        <div class="flex items-center gap-2">
          <!-- Language toggle -->
          <button
            class="rounded-md px-2 py-1 text-xs font-medium text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
            type="button"
            @click="toggleLocale"
          >
            {{ locale === 'en' ? 'NL' : 'EN' }}
          </button>

          <!-- Theme cycle -->
          <button
            class="rounded-md px-2 py-1 text-xs font-medium text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
            type="button"
            :title="t(`theme.${mode}`)"
            @click="cycleTheme"
          >
            <span v-if="mode === 'light'">&#9728;&#65039;</span>
            <span v-else-if="mode === 'dark'">&#9790;</span>
            <span v-else>&#128187;</span>
          </button>
        </div>
      </div>
    </nav>

    <RouterView />
  </div>
</template>
