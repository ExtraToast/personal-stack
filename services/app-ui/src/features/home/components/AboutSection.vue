<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t, tm } = useI18n()
</script>

<template>
  <section id="about" class="border-t border-surface-border/40 bg-surface-card px-4 py-12 sm:px-6 sm:py-24">
    <div class="mx-auto max-w-4xl">
      <h2 class="font-mono text-sm font-semibold uppercase tracking-widest text-terminal-green">
        <span class="text-gray-600">01.</span>
        {{ t('about.title') }}
      </h2>

      <div class="mt-6 grid gap-8 sm:mt-10 md:grid-cols-2">
        <div>
          <p class="text-lg leading-relaxed text-gray-300">
            {{ t('about.intro') }}
          </p>
          <p class="mt-4 leading-relaxed text-gray-500">
            {{ t('about.body') }}
          </p>
          <dl class="mt-6 space-y-3 font-mono text-sm">
            <div class="flex items-center gap-3">
              <dt class="text-terminal-cyan">&#9656;</dt>
              <dd class="text-gray-400">
                {{ t('about.location') }}
              </dd>
            </div>
            <div class="flex items-center gap-3">
              <dt class="text-terminal-cyan">&#9656;</dt>
              <dd class="text-gray-400">
                {{ t('about.languages') }}
              </dd>
            </div>
          </dl>
        </div>

        <div>
          <h3 class="mb-4 font-mono text-sm font-semibold uppercase tracking-widest text-terminal-purple">
            {{ t('about.education') }}
          </h3>
          <ol class="space-y-4">
            <li
              v-for="(entry, i) in tm('about.educationEntries') as Array<{
                period: string
                institution: string
                degree: string
              }>"
              :key="i"
              class="rounded-lg border border-surface-border/50 bg-surface-elevated/50 p-4 transition-colors hover:border-accent/30"
            >
              <p class="font-mono text-xs text-accent-light">
                {{ entry.period }}
              </p>
              <p class="mt-1 font-medium text-gray-200">
                {{ entry.degree }}
              </p>
              <p class="text-sm text-gray-500">
                {{ entry.institution }}
              </p>
            </li>
          </ol>
        </div>
      </div>
    </div>
  </section>
</template>
