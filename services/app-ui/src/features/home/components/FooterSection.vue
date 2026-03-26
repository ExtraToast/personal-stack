<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const year = new Date().getFullYear()
</script>

<template>
  <footer class="border-t border-gray-200 px-6 py-8 dark:border-gray-800">
    <div
      class="mx-auto flex max-w-5xl flex-col items-center gap-2 text-center text-sm text-gray-400 dark:text-gray-500"
    >
      <p>
        &copy; {{ year }} Joris Jonkers &middot;
        {{ t('footer.built') }}
      </p>
      <a
        class="underline-offset-2 hover:underline"
        href="https://github.com/ExtraToast"
        rel="noopener noreferrer"
        target="_blank"
      >
        {{ t('footer.source') }}
      </a>
    </div>
  </footer>
</template>
