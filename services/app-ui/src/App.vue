<script setup lang="ts">
import { useTheme } from '@personal-stack/vue-common'
import { useI18n } from 'vue-i18n'
import { RouterView } from 'vue-router'
import { useAuthStore } from './features/auth'
import { setLocale } from './i18n'

const { mode, setTheme } = useTheme()
const { t, locale } = useI18n()
const authStore = useAuthStore()

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
  <div class="min-h-screen bg-surface-dark text-gray-200">
    <nav class="fixed top-0 z-50 w-full border-b border-surface-border/50 bg-surface-dark/90 backdrop-blur-md">
      <div class="mx-auto flex max-w-6xl items-center justify-between px-6 py-3">
        <a class="font-mono text-sm font-bold tracking-tight text-terminal-green" href="#">
          <span class="text-gray-600">~/</span>joris<span class="text-accent-light">.dev</span>
        </a>

        <div class="hidden items-center gap-1 font-mono text-xs sm:flex">
          <a
            class="rounded-md px-3 py-1.5 text-gray-400 transition-colors hover:bg-surface-elevated hover:text-terminal-green"
            href="#about"
          >
            {{ t('nav.about') }}
          </a>
          <a
            class="rounded-md px-3 py-1.5 text-gray-400 transition-colors hover:bg-surface-elevated hover:text-terminal-green"
            href="#experience"
          >
            {{ t('nav.experience') }}
          </a>
          <a
            class="rounded-md px-3 py-1.5 text-gray-400 transition-colors hover:bg-surface-elevated hover:text-terminal-green"
            href="#projects"
          >
            {{ t('nav.projects') }}
          </a>
          <a
            class="rounded-md px-3 py-1.5 text-gray-400 transition-colors hover:bg-surface-elevated hover:text-terminal-green"
            href="#skills"
          >
            {{ t('nav.skills') }}
          </a>
          <a
            class="rounded-md px-3 py-1.5 text-gray-400 transition-colors hover:bg-surface-elevated hover:text-terminal-green"
            href="#contact"
          >
            {{ t('nav.contact') }}
          </a>
        </div>

        <div class="flex items-center gap-1">
          <button
            class="rounded-md px-2 py-1 font-mono text-xs text-gray-500 transition-colors hover:bg-surface-elevated hover:text-terminal-cyan"
            type="button"
            @click="toggleLocale"
          >
            {{ locale === 'en' ? 'NL' : 'EN' }}
          </button>
          <button
            class="rounded-md px-2 py-1 font-mono text-xs text-gray-500 transition-colors hover:bg-surface-elevated hover:text-terminal-amber"
            type="button"
            :title="t(`theme.${mode}`)"
            @click="cycleTheme"
          >
            <span v-if="mode === 'light'">&#9728;&#65039;</span>
            <span v-else-if="mode === 'dark'">&#9790;</span>
            <span v-else>&#128187;</span>
          </button>
          <button
            v-if="authStore.isAuthenticated"
            class="rounded-md px-3 py-1 font-mono text-xs text-gray-400 transition-colors hover:bg-surface-elevated hover:text-terminal-green"
            type="button"
            @click="authStore.logout()"
          >
            {{ authStore.user?.username ?? t('nav.logout') }}
          </button>
          <button
            v-else
            class="rounded-md px-3 py-1 font-mono text-xs text-terminal-green transition-colors hover:bg-surface-elevated"
            type="button"
            @click="authStore.login()"
          >
            {{ t('nav.login') }}
          </button>
        </div>
      </div>
    </nav>

    <RouterView />
  </div>
</template>
