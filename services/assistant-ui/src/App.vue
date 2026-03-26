<script setup lang="ts">
import { useTheme } from '@private-stack/vue-common'
import { RouterView } from 'vue-router'

useTheme()
</script>

<template>
  <div class="min-h-screen bg-surface-dark text-gray-200">
    <RouterView />
  </div>
</template>
