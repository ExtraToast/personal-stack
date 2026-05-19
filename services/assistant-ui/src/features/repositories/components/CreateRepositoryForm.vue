<script setup lang="ts">
import type { CreateRepositoryInput } from '../types'
import { FormField, SubmitButton, useMutationState, useToast } from '@personal-stack/vue-common'
import { computed, ref } from 'vue'

const emit = defineEmits<{
  submit: [value: CreateRepositoryInput]
  cancel: []
}>()

const name = ref('')
const repoUrl = ref('')
const defaultBranch = ref('main')

const submit = useMutationState<void>()
const toast = useToast()

const canSubmit = computed(() => name.value.trim().length > 0 && repoUrl.value.trim().length > 0)

async function onSubmit(): Promise<void> {
  if (!canSubmit.value) return
  try {
    await submit.run(async () => {
      emit('submit', {
        name: name.value.trim(),
        repoUrl: repoUrl.value.trim(),
        defaultBranch: defaultBranch.value.trim() || 'main',
      })
    })
  } catch (e) {
    toast.error('Could not create the repository', e instanceof Error ? e.message : String(e))
  }
}
</script>

<template>
  <form class="space-y-4" data-testid="create-repository-form" @submit.prevent="onSubmit">
    <FormField label="Name" required hint="Short identifier — appears across project pickers.">
      <template #default="{ id, invalid, describedBy }">
        <input
          :id="id"
          v-model="name"
          type="text"
          required
          maxlength="80"
          :aria-describedby="describedBy"
          :aria-invalid="invalid"
          class="w-full rounded border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] px-3 py-2 text-sm"
          placeholder="personal-stack"
          data-testid="repo-name"
        />
      </template>
    </FormField>

    <FormField label="Repository URL" required hint="SSH URL — git@github.com:owner/repo.git">
      <template #default="{ id, invalid, describedBy }">
        <input
          :id="id"
          v-model="repoUrl"
          type="text"
          required
          :aria-describedby="describedBy"
          :aria-invalid="invalid"
          class="w-full rounded border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] px-3 py-2 font-mono text-xs"
          placeholder="git@github.com:ExtraToast/personal-stack.git"
          data-testid="repo-url"
        />
      </template>
    </FormField>

    <FormField label="Default branch" hint="Branch the agent checks out by default.">
      <template #default="{ id }">
        <input
          :id="id"
          v-model="defaultBranch"
          type="text"
          maxlength="80"
          class="w-full rounded border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] px-3 py-2 font-mono text-xs"
          placeholder="main"
          data-testid="repo-default-branch"
        />
      </template>
    </FormField>

    <div class="flex justify-end gap-2">
      <SubmitButton
        type="button"
        variant="secondary"
        label="Cancel"
        :disabled="submit.pending.value"
        @click="emit('cancel')"
      />
      <SubmitButton
        label="Create repository"
        :status="submit.status.value"
        :disabled="!canSubmit"
        data-testid="repo-create-submit"
      />
    </div>
  </form>
</template>
