<script setup lang="ts">
import { TabPanel, Tabs } from '@personal-stack/vue-common'
import { ref } from 'vue'
import ChatTab from '../components/ChatTab.vue'
import ScratchTab from '../components/ScratchTab.vue'
import WorkspaceTab from '../components/WorkspaceTab.vue'

const active = ref<'chat' | 'scratch' | 'workspace'>('chat')
</script>

<template>
  <div class="max-w-6xl mx-auto p-6">
    <header class="mb-6">
      <h1 class="text-2xl font-bold">Sessions</h1>
      <p class="mt-1 text-sm text-[var(--color-text-muted)]">
        Three flavours: <strong>Chat</strong> is pure LLM Q&A (no Pod), <strong>Scratch</strong> spawns a Pod with a
        shell but no git repo, and <strong>Workspace</strong> clones a project's repository so the agent can edit +
        push.
      </p>
    </header>

    <Tabs v-model="active" aria-label="Session flavour">
      <template #tabs="{ active: current, activate }">
        <button
          type="button"
          role="tab"
          :aria-selected="current === 'chat'"
          class="rounded-t px-4 py-2 text-sm transition-colors"
          :class="[
            current === 'chat'
              ? 'bg-[var(--color-surface-elevated)] text-white border-b-2 border-[var(--color-accent)]'
              : 'text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]',
          ]"
          data-testid="sessions-tab-chat"
          @click="activate('chat')"
        >
          Chat
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="current === 'scratch'"
          class="rounded-t px-4 py-2 text-sm transition-colors"
          :class="[
            current === 'scratch'
              ? 'bg-[var(--color-surface-elevated)] text-white border-b-2 border-[var(--color-accent)]'
              : 'text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]',
          ]"
          data-testid="sessions-tab-scratch"
          @click="activate('scratch')"
        >
          Scratch
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="current === 'workspace'"
          class="rounded-t px-4 py-2 text-sm transition-colors"
          :class="[
            current === 'workspace'
              ? 'bg-[var(--color-surface-elevated)] text-white border-b-2 border-[var(--color-accent)]'
              : 'text-[var(--color-text-muted)] hover:text-[var(--color-text-primary)]',
          ]"
          data-testid="sessions-tab-workspace"
          @click="activate('workspace')"
        >
          Workspace
        </button>
      </template>

      <TabPanel value="chat" :keep-alive="true">
        <ChatTab />
      </TabPanel>
      <TabPanel value="scratch">
        <ScratchTab />
      </TabPanel>
      <TabPanel value="workspace">
        <WorkspaceTab />
      </TabPanel>
    </Tabs>
  </div>
</template>
