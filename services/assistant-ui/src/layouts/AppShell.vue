<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

interface NavItem {
  label: string
  to: string
  testid: string
}

const route = useRoute()

const navItems: NavItem[] = [
  { label: 'Sessions', to: '/sessions', testid: 'nav-sessions' },
  { label: 'Projects', to: '/projects', testid: 'nav-projects' },
  { label: 'Repositories', to: '/repositories', testid: 'nav-repositories' },
]

const activeBase = computed(() => `/${(route.path.split('/')[1] ?? '').toLowerCase()}`)

function isActive(to: string): boolean {
  return activeBase.value === to
}
</script>

<template>
  <div class="min-h-screen flex flex-col">
    <header
      class="flex items-center justify-between border-b border-[var(--color-surface-border)] bg-[var(--color-surface-elevated)] px-6 py-3"
    >
      <RouterLink to="/" class="text-lg font-semibold text-[var(--color-terminal-green)]" data-testid="app-home-link">
        personal-stack
      </RouterLink>
      <nav class="flex items-center gap-1" data-testid="app-nav">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          :data-testid="item.testid"
          class="rounded px-3 py-1.5 text-sm transition-colors"
          :class="[
            isActive(item.to)
              ? 'bg-[var(--color-accent)] text-white'
              : 'text-gray-300 hover:bg-[var(--color-surface-border)]',
          ]"
        >
          {{ item.label }}
        </RouterLink>
      </nav>
    </header>
    <main class="flex-1">
      <slot />
    </main>
  </div>
</template>
