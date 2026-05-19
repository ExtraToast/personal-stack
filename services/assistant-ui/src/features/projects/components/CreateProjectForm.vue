<script setup lang="ts">
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

function onSubmit(): void {
  if (!name.value.trim() || !slug.value) return
  emit('submit', { name: name.value.trim(), slug: slug.value, description: description.value.trim() })
}
</script>

<template>
  <form class="space-y-3" data-testid="create-project-form" @submit.prevent="onSubmit">
    <div>
      <label class="block text-sm font-medium mb-1" for="proj-name">Name</label>
      <input
        id="proj-name"
        v-model="name"
        type="text"
        required
        maxlength="80"
        class="w-full rounded border border-gray-700 bg-surface-darker px-3 py-2"
        placeholder="Personal Stack"
      />
    </div>
    <div>
      <label class="block text-sm font-medium mb-1" for="proj-slug">Slug</label>
      <input
        id="proj-slug"
        v-model="slug"
        type="text"
        required
        pattern="^[a-z0-9][a-z0-9-]{0,62}$"
        class="w-full rounded border border-gray-700 bg-surface-darker px-3 py-2 font-mono"
        placeholder="personal-stack"
        @input="onSlugInput"
      />
      <p class="text-xs text-gray-500 mt-1">Lowercase, hyphens only — used in KB scope `project:&lt;slug&gt;`.</p>
    </div>
    <div>
      <label class="block text-sm font-medium mb-1" for="proj-desc">Description (optional)</label>
      <textarea
        id="proj-desc"
        v-model="description"
        rows="2"
        maxlength="1000"
        class="w-full rounded border border-gray-700 bg-surface-darker px-3 py-2"
      />
    </div>
    <div class="flex justify-end gap-2">
      <button type="button" class="rounded px-4 py-2 text-sm text-gray-300 hover:bg-gray-800" @click="emit('cancel')">
        Cancel
      </button>
      <button type="submit" class="rounded bg-blue-600 hover:bg-blue-700 px-4 py-2 text-sm text-white">
        Create project
      </button>
    </div>
  </form>
</template>
