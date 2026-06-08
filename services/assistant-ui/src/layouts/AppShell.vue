<script setup lang="ts">
import type { AppShellNavItem } from '@/lib/vueWebCommons'
import { AppShell as CommonsAppShell, personalStackThemeOptions } from '@/lib/vueWebCommons'

const navItems: AppShellNavItem[] = [
  { label: 'Sessions', to: '/sessions', testid: 'nav-sessions' },
  { label: 'Projects', to: '/projects', testid: 'nav-projects' },
  { label: 'Repositories', to: '/repositories', testid: 'nav-repositories' },
]
</script>

<template>
  <CommonsAppShell brand-main="assistant" :nav-items="navItems" :theme-options="personalStackThemeOptions">
    <slot />
  </CommonsAppShell>
</template>
