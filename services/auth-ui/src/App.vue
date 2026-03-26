<script setup lang="ts">
import { useTheme } from '@private-stack/vue-common'
import { RouterView } from 'vue-router'

// Initialize theme (applies dark class to html)
useTheme()
</script>

<template>
  <div class="min-h-screen bg-white text-gray-900 dark:bg-gray-950 dark:text-gray-100">
    <RouterView />
  </div>
</template>
