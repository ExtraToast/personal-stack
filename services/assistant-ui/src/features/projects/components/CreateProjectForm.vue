<script setup lang="ts">
import { FormField, SubmitButton, useMutationState, useToast } from '@personal-stack/vue-common'
import { computed, ref, watch } from 'vue'

const emit = defineEmits<{
  submit: [value: { name: string; slug: string; description: string }]
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
const toast = useToast()
const canSubmit = computed(() => name.value.trim().length > 0 && slug.value.length > 0)

async function onSubmit(): Promise<void> {
  if (!canSubmit.value) return
  try {
    await submit.run(async () => {
      emit('submit', { name: name.value.trim(), slug: slug.value, description: description.value.trim() })
    })
  } catch (e) {
    toast.error('Could not create the project', e instanceof Error ? e.message : String(e))
  }
}
</script>

<template>
  <form class="space-y-4" data-testid="create-project-form" @submit.prevent="onSubmit">
    <FormField label="Name" required>
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

    <FormField label="Slug" required hint="Lowercase, hyphens only — used in KB scope project:&lt;slug&gt;.">
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

    <FormField label="Description" hint="Shown on the project's detail page. Optional.">
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
