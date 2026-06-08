<script setup lang="ts">
import { RouterView } from 'vue-router'
import { ToastHost, useTheme } from '@/lib/vueWebCommons'
import AppShell from './layouts/AppShell.vue'

useTheme()
</script>

<template>
  <div class="min-h-screen bg-[var(--color-surface-dark)] text-[var(--color-text-primary)]">
    <AppShell>
      <RouterView />
    </AppShell>
    <ToastHost />
  </div>
</template>
