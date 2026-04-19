<script setup lang="ts">
import { useTheme } from '@personal-stack/vue-common'
import { useI18n } from 'vue-i18n'
import { RouterLink, RouterView } from 'vue-router'
import ProfileDropdown from './features/account/components/ProfileDropdown.vue'
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
        <RouterLink
          class="font-mono text-sm font-bold tracking-tight text-terminal-green"
          :to="{ path: '/', hash: '' }"
        >
          <span class="text-gray-600">~/</span>joris<span class="text-accent-light">.dev</span>
        </RouterLink>

        <!--
          Nav links target anchors on the home page. Previously these were bare
          `<a href="#about">` which, when clicked from any non-root route like
          /admin, produced /admin#about and landed on a dead URL. Use RouterLink
          with an explicit `path: '/'` so the router navigates home first and
          then resolves the hash anchor.
        -->
        <div class="hidden items-center gap-1 font-mono text-xs sm:flex">
          <RouterLink
            class="rounded-md px-3 py-1.5 text-gray-400 transition-colors hover:bg-surface-elevated hover:text-terminal-green"
            :to="{ path: '/', hash: '#about' }"
          >
            {{ t('nav.about') }}
          </RouterLink>
          <RouterLink
            class="rounded-md px-3 py-1.5 text-gray-400 transition-colors hover:bg-surface-elevated hover:text-terminal-green"
            :to="{ path: '/', hash: '#experience' }"
          >
            {{ t('nav.experience') }}
          </RouterLink>
          <RouterLink
            class="rounded-md px-3 py-1.5 text-gray-400 transition-colors hover:bg-surface-elevated hover:text-terminal-green"
            :to="{ path: '/', hash: '#projects' }"
          >
            {{ t('nav.projects') }}
          </RouterLink>
          <RouterLink
            class="rounded-md px-3 py-1.5 text-gray-400 transition-colors hover:bg-surface-elevated hover:text-terminal-green"
            :to="{ path: '/', hash: '#skills' }"
          >
            {{ t('nav.skills') }}
          </RouterLink>
          <RouterLink
            class="rounded-md px-3 py-1.5 text-gray-400 transition-colors hover:bg-surface-elevated hover:text-terminal-green"
            :to="{ path: '/', hash: '#contact' }"
          >
            {{ t('nav.contact') }}
          </RouterLink>
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
          <RouterLink
            v-if="authStore.isAdmin"
            class="rounded-md px-3 py-1 font-mono text-xs text-gray-400 transition-colors hover:bg-surface-elevated hover:text-terminal-green"
            to="/admin"
          >
            {{ t('nav.admin') }}
          </RouterLink>
          <ProfileDropdown v-if="authStore.isAuthenticated" />
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
