<script setup lang="ts">
import { ToastHost, useTheme } from '@personal-stack/vue-common'
import { RouterView } from 'vue-router'
import AppShell from './layouts/AppShell.vue'

useTheme()
</script>

<template>
  <div class="min-h-screen bg-[var(--color-surface-dark)] text-gray-200">
    <AppShell>
      <RouterView />
    </AppShell>
    <ToastHost />
  </div>
</template>
