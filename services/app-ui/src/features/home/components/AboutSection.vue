<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t, tm } = useI18n()
</script>

<template>
  <section id="about" class="bg-gray-50 px-6 py-24 dark:bg-gray-900">
    <div class="mx-auto max-w-4xl">
      <h2 class="text-3xl font-bold tracking-tight">
        {{ t('about.title') }}
      </h2>

      <div class="mt-8 grid gap-10 md:grid-cols-2">
        <div>
          <p class="text-lg leading-relaxed text-gray-600 dark:text-gray-300">
            {{ t('about.intro') }}
          </p>
          <p class="mt-4 leading-relaxed text-gray-500 dark:text-gray-400">
            {{ t('about.body') }}
          </p>
          <dl class="mt-6 space-y-2 text-sm">
            <div class="flex gap-2">
              <dt class="font-medium">&#128205;</dt>
              <dd class="text-gray-600 dark:text-gray-400">
                {{ t('about.location') }}
              </dd>
            </div>
            <div class="flex gap-2">
              <dt class="font-medium">&#127760;</dt>
              <dd class="text-gray-600 dark:text-gray-400">
                {{ t('about.languages') }}
              </dd>
            </div>
          </dl>
        </div>

        <div>
          <h3 class="text-lg font-semibold">
            {{ t('about.education') }}
          </h3>
          <ol class="mt-4 space-y-4">
            <li
              v-for="(entry, i) in tm('about.educationEntries') as Array<{
                period: string
                institution: string
                degree: string
              }>"
              :key="i"
              class="border-l-2 border-indigo-500/30 pl-4"
            >
              <p
                class="text-xs font-semibold uppercase tracking-wider text-indigo-600 dark:text-indigo-400"
              >
                {{ entry.period }}
              </p>
              <p class="mt-1 font-medium">
                {{ entry.degree }}
              </p>
              <p class="text-sm text-gray-500 dark:text-gray-400">
                {{ entry.institution }}
              </p>
            </li>
          </ol>
        </div>
      </div>
    </div>
  </section>
</template>
