<script setup lang="ts">
import {
  Card,
  FormErrors,
  FormField,
  SubmitButton,
  useFormErrors,
  useMutationState,
  useToast,
} from '@personal-stack/vue-common'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useWorkspacesStore } from '@/features/workspaces'

const store = useWorkspacesStore()
const router = useRouter()
const toast = useToast()
const create = useMutationState<void>()
const formErrors = useFormErrors()
const name = ref('')

const scratchWorkspaces = computed(() => store.workspaces.filter((w) => w.kind === 'SCRATCH'))

onMounted(() => {
  void store.loadAll()
})

async function onCreate(): Promise<void> {
  const label = name.value.trim() || `scratch-${new Date().toISOString().substring(11, 16).replace(':', '')}`
  formErrors.clear()
  try {
    await create.run(async () => {
      const created = await store.create({ name: label, kind: 'SCRATCH' })
      toast.success('Scratch workspace created', 'No git clone — just a Pod with a shell.')
      await router.push(`/sessions/workspace/${created.id}`)
    })
    name.value = ''
  } catch (e) {
    formErrors.captureFromCatch(e)
  }
}
</script>

<template>
  <div class="space-y-6" data-testid="scratch-tab">
    <Card>
      <template #header>
        <h2 class="text-lg font-semibold">Spin up a scratch workspace</h2>
      </template>
      <p class="text-sm text-gray-400">
        A scratch workspace is a fresh Pod with a shell and the agent CLIs (Claude + Codex) — no git repo cloned. Useful
        for ad-hoc commands, package experiments, or pre-writing scripts before pulling them into a real project.
      </p>
      <form class="mt-4 space-y-3" @submit.prevent="onCreate">
        <FormErrors :error="formErrors.general.value" />
        <div class="flex items-end gap-3">
          <FormField
            label="Name (optional)"
            :error="formErrors.fieldErrorFor('name')"
            hint="Defaults to a timestamped label."
          >
            <template #default="{ id }">
              <input
                :id="id"
                v-model="name"
                type="text"
                maxlength="80"
                class="w-72 rounded border border-[var(--color-surface-border)] bg-[var(--color-surface-card)] px-3 py-2 text-sm"
                placeholder="ad-hoc"
                data-testid="scratch-name"
              />
            </template>
          </FormField>
          <SubmitButton label="Start scratch" :status="create.status.value" data-testid="scratch-create-submit" />
        </div>
      </form>
    </Card>

    <section>
      <h3 class="mb-3 text-sm font-semibold text-gray-300">Existing scratch workspaces</h3>
      <p v-if="scratchWorkspaces.length === 0" class="text-sm text-gray-500 italic">
        No scratch workspaces. The one you create above will land here.
      </p>
      <ul v-else class="space-y-2" data-testid="scratch-list">
        <li v-for="w in scratchWorkspaces" :key="w.id">
          <Card :to="`/sessions/workspace/${w.id}`" :data-testid="`scratch-${w.id}`">
            <template #header>
              <div class="flex items-baseline justify-between">
                <span class="font-semibold">{{ w.name }}</span>
                <span class="text-xs text-gray-500">{{ w.status }}</span>
              </div>
            </template>
            <p class="text-xs text-gray-500">{{ new Date(w.updatedAt).toLocaleString() }}</p>
          </Card>
        </li>
      </ul>
    </section>
  </div>
</template>
