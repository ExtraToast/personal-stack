<script setup lang="ts">
import type { CreateRepositoryInput, Repository } from '../types'
import { FormErrors, FormField, SubmitButton, useFormErrors, useMutationState } from '@personal-stack/vue-common'
import { computed, ref } from 'vue'

interface Props {
  /**
   * Awaited submit handler supplied by the parent. The form's
   * mutation state mirrors this promise so the SubmitButton flips
   * to `failure` on a rejected server response and the inline
   * banner anchors the error next to the failing field.
   */
  onSubmit: (input: CreateRepositoryInput) => Promise<Repository>
}

const props = defineProps<Props>()

const emit = defineEmits<{
  success: [created: Repository]
  cancel: []
}>()

const name = ref('')
const repoUrl = ref('')
const defaultBranch = ref('main')

const submit = useMutationState<void>()
const formErrors = useFormErrors()

const canSubmit = computed(() => name.value.trim().length > 0 && repoUrl.value.trim().length > 0)

async function onSubmit(): Promise<void> {
  if (!canSubmit.value) return
  formErrors.clear()
  try {
    let created: Repository | undefined
    await submit.run(async () => {
      created = await props.onSubmit({
        name: name.value.trim(),
        repoUrl: repoUrl.value.trim(),
        defaultBranch: defaultBranch.value.trim() || 'main',
      })
    })
    if (created) emit('success', created)
  } catch (e) {
    formErrors.captureFromCatch(e)
  }
}
</script>

<template>
  <form class="space-y-4" data-testid="create-repository-form" @submit.prevent="onSubmit">
    <FormErrors :error="formErrors.general.value" />

    <FormField
      label="Name"
      required
      :error="formErrors.fieldErrorFor('name')"
      hint="Short identifier — appears across project pickers."
    >
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

    <FormField
      label="Repository URL"
      required
      :error="formErrors.fieldErrorFor('repoUrl') ?? formErrors.fieldErrorFor('repo_url')"
      hint="SSH URL — git@github.com:owner/repo.git"
    >
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

    <FormField
      label="Default branch"
      :error="formErrors.fieldErrorFor('defaultBranch') ?? formErrors.fieldErrorFor('default_branch')"
      hint="Branch the agent checks out by default."
    >
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
