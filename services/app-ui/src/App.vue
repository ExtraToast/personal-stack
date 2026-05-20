<script setup lang="ts">
import type { AppShellNavItem } from '@personal-stack/vue-common'
import { AppShell } from '@personal-stack/vue-common'
import { useI18n } from 'vue-i18n'
import { RouterLink, RouterView } from 'vue-router'
import ProfileDropdown from './features/account/components/ProfileDropdown.vue'
import { useAuthStore } from './features/auth'
import { setLocale } from './i18n'

const { t, locale } = useI18n()
const authStore = useAuthStore()

function toggleLocale(): void {
  setLocale(locale.value === 'en' ? 'nl' : 'en')
}

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
    account-href="/account"
  >
    <template #extras>
      <button
        type="button"
        class="rounded-md px-2 py-1.5 font-mono text-xs text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-terminal-cyan)]"
        data-testid="nav-locale"
        @click="toggleLocale"
      >
        {{ locale === 'en' ? 'NL' : 'EN' }}
      </button>
      <RouterLink
        v-if="authStore.isAuthenticated"
        :to="{ name: 'apps' }"
        class="rounded-md px-3 py-1.5 font-mono text-xs text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-terminal-green)]"
        data-testid="nav-my-apps"
      >
        {{ t('nav.myApps') }}
      </RouterLink>
      <RouterLink
        v-if="authStore.isAdmin"
        to="/admin"
        class="rounded-md px-3 py-1.5 font-mono text-xs text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-terminal-green)]"
        data-testid="nav-admin"
      >
        {{ t('nav.admin') }}
      </RouterLink>
      <ProfileDropdown v-if="authStore.isAuthenticated" />
      <button
        v-else
        type="button"
        class="rounded-md px-3 py-1.5 font-mono text-xs text-[var(--color-terminal-green)] transition-colors hover:bg-[var(--color-surface-elevated)]"
        data-testid="nav-login"
        @click="authStore.login()"
      >
        {{ t('nav.login') }}
      </button>
    </template>

    <RouterView />
  </AppShell>
</template>
