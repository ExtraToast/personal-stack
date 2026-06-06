<script setup lang="ts">
import { Card } from '@personal-stack/vue-common'
import { computed } from 'vue'
import { githubAppLinks } from '../services/githubAppLinks'

const appSlug = computed(() => githubAppLinks.resolveGitHubAppSlug())
const encodedAppSlug = computed(() => encodeURIComponent(appSlug.value))
const publicAppHref = computed(() => `https://github.com/apps/${encodedAppSlug.value}`)
const installHref = computed(() => `${publicAppHref.value}/installations/new`)
const ownerPermissionsHref = computed(() => `https://github.com/settings/apps/${encodedAppSlug.value}/permissions`)
const ownerInstallationsHref = computed(() => `https://github.com/settings/apps/${encodedAppSlug.value}/installations`)
</script>

<template>
  <section data-testid="github-app-reference">
    <Card :interactive="false">
      <template #header>
        <div class="flex flex-col gap-2 sm:flex-row sm:items-baseline sm:justify-between">
          <div>
            <h2 class="text-lg font-semibold">GitHub App</h2>
            <p class="mt-1 text-sm text-[var(--color-text-muted)]">
              Use the App installation for repository automation before attaching deploy keys.
            </p>
          </div>
          <code class="text-xs text-[var(--color-terminal-cyan)]">{{ appSlug }}</code>
        </div>
      </template>

      <div class="grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(16rem,20rem)]">
        <div>
          <div class="flex flex-wrap gap-3 text-sm">
            <a
              :href="publicAppHref"
              target="_blank"
              rel="noopener noreferrer"
              class="text-[var(--color-accent-light)] underline"
              data-testid="github-app-public-link"
            >
              Public App page
            </a>
            <a
              :href="installHref"
              target="_blank"
              rel="noopener noreferrer"
              class="text-[var(--color-accent-light)] underline"
              data-testid="github-app-install-link"
            >
              New installation
            </a>
            <a
              :href="ownerPermissionsHref"
              target="_blank"
              rel="noopener noreferrer"
              class="text-[var(--color-accent-light)] underline"
              data-testid="github-app-owner-permissions-link"
            >
              App owner permissions
            </a>
            <a
              :href="ownerInstallationsHref"
              target="_blank"
              rel="noopener noreferrer"
              class="text-[var(--color-accent-light)] underline"
              data-testid="github-app-owner-installations-link"
            >
              App owner installations
            </a>
          </div>

          <p class="mt-3 text-sm text-[var(--color-text-muted)]" data-testid="github-app-permission-approval-note">
            Permission changes must be approved on each existing installation before installation tokens receive the
            updated grants.
          </p>
        </div>

        <div>
          <h3 class="text-sm font-semibold">Requested repository permissions</h3>
          <ul class="mt-2 space-y-1 text-sm text-[var(--color-text-muted)]">
            <li
              v-for="permission in githubAppLinks.requestedPermissions"
              :key="permission.key"
              :data-testid="`github-app-permission-${permission.key}`"
            >
              {{ permission.label }}: {{ permission.access }}
            </li>
          </ul>
        </div>
      </div>
    </Card>
  </section>
</template>
