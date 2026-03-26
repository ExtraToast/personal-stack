<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const year = new Date().getFullYear()
</script>

<template>
  <footer class="border-t border-surface-border/40 px-4 py-6 sm:px-6 sm:py-8">
    <div
      class="mx-auto flex max-w-6xl flex-col items-center gap-2 text-center font-mono text-xs text-gray-600"
    >
      <p>
        &copy; {{ year }} Joris Jonkers &middot;
        {{ t('footer.built') }}
      </p>
      <a
        class="text-gray-500 transition-colors hover:text-terminal-green"
        href="https://github.com/ExtraToast"
        rel="noopener noreferrer"
        target="_blank"
      >
        {{ t('footer.source') }}
      </a>
    </div>
  </footer>
</template>
