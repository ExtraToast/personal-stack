<script setup lang="ts">
import type { Project } from '../types'
import { computed, ref, watch } from 'vue'
import { FormErrors, FormField, SubmitButton, useFormErrors, useMutationState } from '@/lib/vueWebCommons'

interface Props {
  /** Awaited submit handler — same `onSubmit` shape as the other wizards. */
  onSubmit: (input: { name: string; slug: string; description: string }) => Promise<Project>
}

const props = defineProps<Props>()

const emit = defineEmits<{
  success: [created: Project]
  cancel: []
}>()

const name = ref('')
const slug = ref('')
const description = ref('')
const slugManuallyEdited = ref(false)

const autoSlug = computed(() =>
  name.value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60),
)

watch(autoSlug, (value) => {
  if (!slugManuallyEdited.value) slug.value = value
})

function onSlugInput(): void {
  slugManuallyEdited.value = slug.value !== autoSlug.value
}

const submit = useMutationState<void>()
const formErrors = useFormErrors()
const canSubmit = computed(() => name.value.trim().length > 0 && slug.value.length > 0)

async function onSubmit(): Promise<void> {
  if (!canSubmit.value) return
  formErrors.clear()
  try {
    let created: Project | undefined
    await submit.run(async () => {
      created = await props.onSubmit({
        name: name.value.trim(),
        slug: slug.value,
        description: description.value.trim(),
      })
    })
    if (created) emit('success', created)
  } catch (e) {
    formErrors.captureFromCatch(e)
  }
}
</script>

<template>
  <form class="space-y-4" data-testid="create-project-form" @submit.prevent="onSubmit">
    <FormErrors :error="formErrors.general.value" />

    <FormField label="Name" required :error="formErrors.fieldErrorFor('name')">
      <template #default="{ id, invalid }">
        <input
          :id="id"
          v-model="name"
          type="text"
          required
          maxlength="80"
          :aria-invalid="invalid"
          class="w-full rounded border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] px-3 py-2 text-sm"
          placeholder="Personal Stack"
          data-testid="proj-name"
        />
      </template>
    </FormField>

    <FormField
      label="Slug"
      required
      :error="formErrors.fieldErrorFor('slug')"
      hint="Lowercase, hyphens only — used in KB scope project:&lt;slug&gt;."
    >
      <template #default="{ id, invalid }">
        <input
          :id="id"
          v-model="slug"
          type="text"
          required
          pattern="^[a-z0-9][a-z0-9-]{0,62}$"
          :aria-invalid="invalid"
          class="w-full rounded border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] px-3 py-2 font-mono text-xs"
          placeholder="personal-stack"
          data-testid="proj-slug"
          @input="onSlugInput"
        />
      </template>
    </FormField>

    <FormField
      label="Description"
      :error="formErrors.fieldErrorFor('description')"
      hint="Shown on the project's detail page. Optional."
    >
      <template #default="{ id }">
        <textarea
          :id="id"
          v-model="description"
          rows="2"
          maxlength="1000"
          class="w-full rounded border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] px-3 py-2 text-sm"
          placeholder="What this project's about."
          data-testid="proj-desc"
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
        label="Create project"
        :status="submit.status.value"
        :disabled="!canSubmit"
        data-testid="proj-create-submit"
      />
    </div>
  </form>
</template>
