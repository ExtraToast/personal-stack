<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/features/auth'

const authStore = useAuthStore()
const router = useRouter()
const { t } = useI18n()
const open = ref(false)
const dropdownRef = ref<HTMLElement | null>(null)

function toggle(): void {
  open.value = !open.value
}

function navigate(name: string): void {
  open.value = false
  router.push({ name })
}

function logout(): void {
  open.value = false
  authStore.logout()
}

function onClickOutside(event: MouseEvent): void {
  const target = event.target
  if (dropdownRef.value && target instanceof Node && !dropdownRef.value.contains(target)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('click', onClickOutside))
onUnmounted(() => document.removeEventListener('click', onClickOutside))
</script>

<template>
  <div ref="dropdownRef" class="relative">
    <button
      class="rounded-md px-3 py-1 font-mono text-xs text-gray-400 transition-colors hover:bg-surface-elevated hover:text-terminal-green"
      data-testid="nav-profile"
      @click="toggle"
    >
      {{ authStore.user?.username }}
    </button>

    <div
      v-if="open"
      class="absolute right-0 z-50 mt-1 min-w-[140px] rounded-md border border-surface-border/50 bg-surface-elevated shadow-lg"
    >
      <button
        class="block w-full px-4 py-2 text-left font-mono text-xs text-gray-300 transition-colors hover:bg-surface-card hover:text-terminal-green"
        data-testid="dropdown-my-apps"
        @click="navigate('apps')"
      >
        {{ t('nav.myApps') }}
      </button>
      <button
        class="block w-full px-4 py-2 text-left font-mono text-xs text-gray-300 transition-colors hover:bg-surface-card hover:text-terminal-green"
        data-testid="dropdown-account"
        @click="navigate('account')"
      >
        {{ t('nav.account') }}
      </button>
      <button
        v-if="authStore.isAdmin"
        class="block w-full px-4 py-2 text-left font-mono text-xs text-gray-300 transition-colors hover:bg-surface-card hover:text-terminal-green"
        data-testid="dropdown-admin"
        @click="navigate('admin')"
      >
        {{ t('nav.admin') }}
      </button>
      <div class="border-t border-surface-border/30" />
      <button
        class="block w-full px-4 py-2 text-left font-mono text-xs text-gray-400 transition-colors hover:bg-surface-card hover:text-red-400"
        data-testid="dropdown-logout"
        @click="logout"
      >
        {{ t('nav.logout') }}
      </button>
    </div>
  </div>
</template>
