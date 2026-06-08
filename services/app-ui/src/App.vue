<script setup lang="ts">
import type { AppShellNavItem } from '@/lib/vueWebCommons'
import { useI18n } from 'vue-i18n'
import { RouterView } from 'vue-router'
import { AppShell, personalStackThemeOptions } from '@/lib/vueWebCommons'
import LocaleToggle from './components/LocaleToggle.vue'
import ProfileDropdown from './features/account/components/ProfileDropdown.vue'
import { useAuthStore } from './features/auth'

const { t } = useI18n()
const authStore = useAuthStore()

// Home is a single scrollable layout with section anchors. Each
// nav link routes back to `/` first so anchors resolve correctly
// even when the user is on another route like /admin.
const navItems: AppShellNavItem[] = [
  { label: t('nav.about'), to: { path: '/', hash: '#about' }, testid: 'nav-about' },
  { label: t('nav.experience'), to: { path: '/', hash: '#experience' }, testid: 'nav-experience' },
  { label: t('nav.projects'), to: { path: '/', hash: '#projects' }, testid: 'nav-projects' },
  { label: t('nav.skills'), to: { path: '/', hash: '#skills' }, testid: 'nav-skills' },
  { label: t('nav.contact'), to: { path: '/', hash: '#contact' }, testid: 'nav-contact' },
]
</script>

<template>
  <AppShell
    brand-main="joris"
    brand-suffix=".dev"
    :brand-to="{ path: '/', hash: '' }"
    :nav-items="navItems"
    :show-account-link="false"
    :theme-options="personalStackThemeOptions"
  >
    <template #extras>
      <LocaleToggle />
      <ProfileDropdown v-if="authStore.isAuthenticated" />
      <button
        v-else
        type="button"
        class="font-mono text-xs text-[var(--color-terminal-green)] transition-colors hover:text-[var(--color-text-primary)]"
        data-testid="nav-login"
        @click="authStore.login()"
      >
        {{ t('nav.login') }}
      </button>
    </template>

    <RouterView />
  </AppShell>
</template>
