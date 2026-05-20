<script setup lang="ts">
import type { AppShellNavItem } from './appShellTypes'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useTheme } from '../composables/useTheme'

interface Props {
  navItems: AppShellNavItem[]
  brandMain?: string
  brandSuffix?: string
  brandTo?: string | object
  accountHref?: string
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

const themeIcon = computed(() => {
  if (mode.value === 'light') return 'light'
  if (mode.value === 'dark') return 'dark'
  return 'auto'
})

const themeLabel = computed(() => {
  if (mode.value === 'light') return 'light'
  if (mode.value === 'dark') return 'dark'
  return 'auto'
})

// Drawer state — full-height slide-in panel on `< lg` (anything
// narrower than 1024 px). The desktop layout shows the inline nav
// + right cluster only at lg+ so the 5+ portfolio items and the
// extras cluster never get squished into a too-narrow header on
// laptops or tablet portrait. Below lg, every action collapses
// behind the hamburger.
const drawerOpen = ref(false)

function openDrawer(): void {
  drawerOpen.value = true
}

function closeDrawer(): void {
  drawerOpen.value = false
}

function onKeyDown(event: KeyboardEvent): void {
  if (event.key === 'Escape') drawerOpen.value = false
}

watch(drawerOpen, (open) => {
  if (typeof document === 'undefined') return
  document.body.style.overflow = open ? 'hidden' : ''
})

watch(
  () => route.path,
  () => {
    drawerOpen.value = false
  },
)

onMounted(() => {
  document.addEventListener('keydown', onKeyDown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeyDown)
  if (typeof document !== 'undefined') document.body.style.overflow = ''
})
</script>

<template>
  <div class="min-h-screen bg-[var(--color-surface-dark)] text-[var(--color-text-primary)]">
    <nav
      class="fixed top-0 z-40 w-full border-b border-[var(--color-surface-border)]/50 bg-[var(--color-surface-dark)]/90 backdrop-blur-md"
      data-testid="app-nav"
    >
      <div class="mx-auto flex h-14 max-w-6xl items-center justify-between px-4 sm:px-6">
        <RouterLink
          :to="brandTo"
          class="font-mono text-sm font-bold tracking-tight text-[var(--color-terminal-green)]"
          data-testid="app-home-link"
        >
          <span class="text-[var(--color-text-muted)]">~/</span>{{ brandMain
          }}<span class="text-[var(--color-accent-light)]">{{ brandSuffix }}</span>
        </RouterLink>

        <div class="hidden items-center gap-5 font-mono text-xs tracking-tight lg:flex" data-testid="app-nav-desktop">
          <RouterLink
            v-for="item in navItems"
            :key="`desk-${item.testid ?? item.label}`"
            :to="item.to"
            :data-testid="item.testid"
            class="transition-colors"
            :class="[
              isActive(item)
                ? 'text-[var(--color-terminal-green)]'
                : 'text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]',
            ]"
          >
            {{ item.label }}
          </RouterLink>
        </div>

        <div class="hidden items-center gap-4 lg:flex" data-testid="app-nav-right">
          <button
            v-if="showThemeToggle"
            type="button"
            class="font-mono text-xs lowercase tracking-tight text-[var(--color-text-muted)] transition-colors hover:text-[var(--color-terminal-amber)]"
            :title="`Theme: ${mode}`"
            :aria-label="`Theme: ${mode}`"
            data-testid="nav-theme-toggle"
            @click="cycleTheme"
          >
            {{ themeIcon }}
          </button>
          <slot name="extras" :compact="true" />
          <a
            :href="accountHref"
            class="font-mono text-xs text-[var(--color-text-muted)] transition-colors hover:text-[var(--color-terminal-green)]"
            data-testid="nav-account"
          >
            Account
          </a>
        </div>

        <button
          type="button"
          class="inline-flex h-11 w-11 items-center justify-center rounded-md text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-terminal-green)] lg:hidden"
          aria-label="Open menu"
          :aria-expanded="drawerOpen"
          data-testid="nav-menu-trigger"
          @click="openDrawer"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            class="h-5 w-5"
            aria-hidden="true"
          >
            <line x1="3" y1="6" x2="21" y2="6" />
            <line x1="3" y1="12" x2="21" y2="12" />
            <line x1="3" y1="18" x2="21" y2="18" />
          </svg>
        </button>
      </div>
    </nav>

    <Teleport to="body">
      <Transition name="drawer">
        <div
          v-if="drawerOpen"
          class="fixed inset-0 z-50 lg:hidden"
          data-testid="nav-drawer-root"
          aria-modal="true"
          role="dialog"
        >
          <div
            class="absolute inset-0 bg-black/70 backdrop-blur-sm"
            data-testid="nav-drawer-backdrop"
            @click="closeDrawer"
          />
          <aside
            class="absolute inset-y-0 right-0 flex w-[88%] max-w-sm flex-col bg-[var(--color-surface-card)] shadow-2xl"
            data-testid="nav-drawer"
          >
            <header class="flex h-14 items-center justify-between border-b border-[var(--color-surface-border)] px-5">
              <span class="font-mono text-sm font-bold tracking-tight text-[var(--color-terminal-green)]">
                <span class="text-[var(--color-text-muted)]">~/</span>{{ brandMain
                }}<span class="text-[var(--color-accent-light)]">{{ brandSuffix }}</span>
              </span>
              <button
                type="button"
                class="inline-flex h-11 w-11 items-center justify-center rounded-md text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-terminal-green)]"
                aria-label="Close menu"
                data-testid="nav-drawer-close"
                @click="closeDrawer"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  class="h-5 w-5"
                  aria-hidden="true"
                >
                  <line x1="18" y1="6" x2="6" y2="18" />
                  <line x1="6" y1="6" x2="18" y2="18" />
                </svg>
              </button>
            </header>

            <nav class="flex-1 overflow-y-auto px-3 py-4" data-testid="nav-drawer-list">
              <RouterLink
                v-for="item in navItems"
                :key="`drawer-${item.testid ?? item.label}`"
                :to="item.to"
                :data-testid="`drawer-${item.testid ?? item.label.toLowerCase()}`"
                class="flex items-center rounded-md px-4 py-3 font-mono text-sm transition-colors"
                :class="[
                  isActive(item)
                    ? 'bg-[var(--color-surface-elevated)] text-[var(--color-terminal-green)]'
                    : 'text-[var(--color-text-primary)] hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-terminal-green)]',
                ]"
              >
                {{ item.label }}
              </RouterLink>

              <div v-if="$slots.extras" class="mt-4 border-t border-[var(--color-surface-border)] pt-4">
                <slot name="extras" :compact="false" />
              </div>

              <div class="mt-4 border-t border-[var(--color-surface-border)] pt-4">
                <button
                  v-if="showThemeToggle"
                  type="button"
                  class="flex w-full items-center justify-between rounded-md px-4 py-3 text-left font-mono text-sm text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-terminal-amber)]"
                  data-testid="drawer-theme"
                  @click="cycleTheme"
                >
                  <span>Theme</span>
                  <span class="font-mono text-xs">{{ themeLabel }}</span>
                </button>
                <a
                  :href="accountHref"
                  class="flex w-full items-center rounded-md px-4 py-3 font-mono text-sm text-[var(--color-text-muted)] transition-colors hover:bg-[var(--color-surface-elevated)] hover:text-[var(--color-terminal-green)]"
                  data-testid="drawer-account"
                  @click="closeDrawer"
                >
                  Account
                </a>
              </div>
            </nav>
          </aside>
        </div>
      </Transition>
    </Teleport>

    <main class="pt-14">
      <slot />
    </main>
  </div>
</template>

<style scoped>
.drawer-enter-active,
.drawer-leave-active {
  transition: opacity 0.2s ease;
}
.drawer-enter-active aside,
.drawer-leave-active aside {
  transition: transform 0.2s ease;
}
.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}
.drawer-enter-from aside,
.drawer-leave-to aside {
  transform: translateX(100%);
}
</style>
