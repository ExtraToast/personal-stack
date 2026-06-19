<script setup lang="ts">
import { RouterView } from 'vue-router'
import { useTheme } from '@/lib/vueWebCommons'

useTheme()
</script>

<template>
  <div class="min-h-dvh bg-surface-dark text-[var(--color-text-primary)]">
    <RouterView />
  </div>
</template>
