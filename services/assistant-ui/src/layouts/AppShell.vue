<script setup lang="ts">
import type { AppShellNavItem } from '@personal-stack/vue-common'
import { AppShell as CommonsAppShell } from '@personal-stack/vue-common'

const navItems: AppShellNavItem[] = [
  { label: 'Sessions', to: '/sessions', testid: 'nav-sessions' },
  { label: 'Projects', to: '/projects', testid: 'nav-projects' },
  { label: 'Repositories', to: '/repositories', testid: 'nav-repositories' },
]
</script>

<template>
  <CommonsAppShell brand-main="assistant" :nav-items="navItems">
    <slot />
  </CommonsAppShell>
</template>
