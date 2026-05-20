<script setup lang="ts">
import type { AppShellNavItem } from './appShellTypes'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useTheme } from '../composables/useTheme'

interface Props {
  /**
   * Primary nav entries, rendered as the horizontal links between
   * the brand and the right-cluster (theme toggle + account menu).
   */
  navItems: AppShellNavItem[]
  /**
   * Brand displayed on the far left. The `~/` prefix + suffix
   * dot-domain decoration come from a shared template — pass the
   * middle segment as `brandMain` (e.g. "assistant", "joris", "auth").
   */
  brandMain?: string
  /** Suffix dot-domain. Default ".dev". */
  brandSuffix?: string
  /** Optional href / route for the brand link. Default "/". */
  brandTo?: string | object
  /** Account-menu target. Default "/account". */
  accountHref?: string
  /** Render the theme cycler. Default true. */
  showThemeToggle?: boolean
}

withDefaults(defineProps<Props>(), {
  brandMain: 'app',
  brandSuffix: '.dev',
  brandTo: '/',
  accountHref: '/account',
  showThemeToggle: true,
})

const route = useRoute()
const { mode, setTheme } = useTheme()

const activeBase = computed(() => `/${(route.path.split('/')[1] ?? '').toLowerCase()}`)

function isActive(item: AppShellNavItem): boolean {
  if (item.active) return item.active(route.path)
  const to = typeof item.to === 'string' ? item.to : item.to.path
  return activeBase.value === to
}

function cycleTheme(): void {
  const modes = ['light', 'dark', 'system'] as const
  const idx = modes.indexOf(mode.value)
  setTheme(modes[(idx + 1) % modes.length]!)
}

const menuOpen = ref(false)
const menuRoot = ref<HTMLElement | null>(null)

function toggleMenu(): void {
  menuOpen.value = !menuOpen.value
}

function closeMenu(): void {
  menuOpen.value = false
}

function onClickOutside(event: MouseEvent): void {
  if (!menuOpen.value || !menuRoot.value) return
  if (event.target instanceof Node && !menuRoot.value.contains(event.target)) {
    menuOpen.value = false
  }
}

function onKeyDown(event: KeyboardEvent): void {
  if (event.key === 'Escape') menuOpen.value = false
}

onMounted(() => {
  document.addEventListener('click', onClickOutside)
  document.addEventListener('keydown', onKeyDown)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onClickOutside)
  document.removeEventListener('keydown', onKeyDown)
})
</script>

<template>
  <div class="min-h-screen bg-[var(--color-surface-dark)] text-[var(--color-text-primary)]">
    <nav
      class="fixed top-0 z-40 w-full border-b border-[var(--color-surface-border)]/50 bg-[var(--color-surface-dark)]/90 backdrop-blur-md"
      data-testid="app-nav"
    >
      <div class="mx-auto flex max-w-6xl items-center justify-between px-4 py-3 sm:px-6">
        <RouterLink
          :to="brandTo"
          class="font-mono text-sm font-bold tracking-tight text-[var(--color-terminal-green)]"
          data-testid="app-home-link"
        >
          <span class="text-[var(--color-text-muted)]">~/</span>{{ brandMain
          }}<span class="text-[var(--color-accent-light)]">{{ brandSuffix }}</span>
        </RouterLink>

        <!-- Desktop nav. Hidden on `<sm` so the mobile fold-out is the only chrome. -->
        <div class="hidden items-center gap-1 font-mono text-xs sm:flex" data-testid="app-nav-desktop">
          <RouterLink
            v-for="item in navItems"
            :key="`desk-${item.testid ?? item.label}`"
            :to="item.to"
            :data-testid="item.testid"
            class="rounded-md px-3 py-1.5 transition-colors"
            :class="[
              isActive(item)
                ? 'bg-[var(--color-surface-elevated)] text-[var(--color-terminal-green)]'
                : 'text-[var(--color-text-muted)] hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-terminal-green)]',
            ]"
          >
            {{ item.label }}
          </RouterLink>
        </div>

        <!-- Right cluster: theme toggle + always-visible menu trigger that
             doubles as the mobile nav drawer on small screens. -->
        <div ref="menuRoot" class="relative flex items-center gap-1">
          <button
            v-if="showThemeToggle"
            type="button"
            class="rounded-md px-2 py-1 font-mono text-xs text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-terminal-amber)]"
            :title="`Theme: ${mode}`"
            data-testid="nav-theme-toggle"
            @click="cycleTheme"
          >
            <span v-if="mode === 'light'">☀</span>
            <span v-else-if="mode === 'dark'">☾</span>
            <span v-else>⟳</span>
          </button>

          <button
            type="button"
            class="rounded-md p-1.5 text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-terminal-green)]"
            :aria-label="menuOpen ? 'Close menu' : 'Open menu'"
            :aria-expanded="menuOpen"
            data-testid="nav-menu-trigger"
            @click="toggleMenu"
          >
            <svg
              v-if="!menuOpen"
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              class="h-4 w-4"
              aria-hidden="true"
            >
              <line x1="3" y1="6" x2="21" y2="6" />
              <line x1="3" y1="12" x2="21" y2="12" />
              <line x1="3" y1="18" x2="21" y2="18" />
            </svg>
            <svg
              v-else
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              class="h-4 w-4"
              aria-hidden="true"
            >
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>

          <div
            v-if="menuOpen"
            class="absolute right-0 top-full z-50 mt-2 min-w-[12rem] rounded-md border border-[var(--color-surface-border)]/60 bg-[var(--color-surface-elevated)] py-1 shadow-lg"
            role="menu"
            data-testid="nav-menu"
          >
            <RouterLink
              v-for="item in navItems"
              :key="`menu-${item.testid ?? item.label}`"
              :to="item.to"
              class="block px-4 py-2 font-mono text-xs text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-surface-card)] hover:text-[var(--color-terminal-green)] sm:hidden"
              :data-testid="`menu-${item.testid ?? item.label.toLowerCase()}`"
              @click="closeMenu"
            >
              {{ item.label }}
            </RouterLink>
            <div class="border-t border-[var(--color-surface-border)]/30 sm:hidden" />
            <button
              v-if="showThemeToggle"
              type="button"
              class="block w-full px-4 py-2 text-left font-mono text-xs text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-surface-card)] hover:text-[var(--color-terminal-green)]"
              data-testid="menu-theme"
              @click="cycleTheme"
            >
              Theme: {{ mode }}
            </button>
            <a
              :href="accountHref"
              class="block w-full px-4 py-2 text-left font-mono text-xs text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-surface-card)] hover:text-[var(--color-terminal-green)]"
              data-testid="menu-account"
              @click="closeMenu"
            >
              Account
            </a>
          </div>
        </div>
      </div>
    </nav>

    <main class="pt-14">
      <slot />
    </main>
  </div>
</template>
