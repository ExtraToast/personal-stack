<script setup lang="ts">
import { reactive, computed } from 'vue'

interface FieldSpec {
  type: 'string' | 'integer' | 'number' | 'boolean'
  title?: string
  description?: string
  default?: unknown
  enum?: unknown[]
}

const props = defineProps<{ schema: Record<string, unknown>; submitLabel?: string }>()
const emit = defineEmits<{ submit: [value: Record<string, unknown>] }>()

/**
 * Lightweight JSON-Schema renderer. Only the slice the agents
 * actually need today: top-level `properties` map with primitive
 * types, optional `enum`, optional `default`, optional `required`.
 * No nested objects, no allOf/oneOf — agents who need richer
 * surfaces should fall back to a Choice block or a Diff block.
 */
const properties = computed<Record<string, FieldSpec>>(() => {
  const props_ = props.schema['properties']
  return (props_ && typeof props_ === 'object' ? props_ : {}) as Record<string, FieldSpec>
})

const required = computed<string[]>(() => {
  const r = props.schema['required']
  return Array.isArray(r) ? (r as string[]) : []
})

const values = reactive<Record<string, unknown>>(
  Object.fromEntries(
    Object.entries(properties.value).map(([k, spec]) => [k, spec.default ?? defaultFor(spec)]),
  ),
)

function defaultFor(spec: FieldSpec): unknown {
  if (spec.type === 'boolean') return false
  if (spec.type === 'integer' || spec.type === 'number') return 0
  return ''
}

function isMissingRequired(): boolean {
  return required.value.some((k) => {
    const v = values[k]
    return v === '' || v === null || v === undefined
  })
}

function onSubmit(): void {
  if (isMissingRequired()) return
  emit('submit', { ...values })
}
</script>

<template>
  <form
    class="rounded-lg border border-purple-500/30 bg-purple-500/5 p-4 my-2 space-y-3"
    data-testid="form-block"
    @submit.prevent="onSubmit"
  >
    <div v-for="(spec, name) in properties" :key="name" class="space-y-1">
      <label class="block text-sm font-medium">
        {{ spec.title ?? name }}
        <span v-if="required.includes(name as string)" class="text-red-400">*</span>
      </label>
      <p v-if="spec.description" class="text-xs text-gray-400">{{ spec.description }}</p>
      <select
        v-if="spec.enum"
        v-model="values[name as string]"
        class="w-full rounded border border-gray-700 bg-black/30 px-2 py-1 text-sm"
      >
        <option v-for="opt in spec.enum" :key="String(opt)" :value="opt">{{ opt }}</option>
      </select>
      <input
        v-else-if="spec.type === 'boolean'"
        v-model="values[name as string]"
        type="checkbox"
        class="rounded"
      >
      <input
        v-else-if="spec.type === 'integer' || spec.type === 'number'"
        v-model.number="values[name as string]"
        type="number"
        :step="spec.type === 'integer' ? 1 : 'any'"
        class="w-full rounded border border-gray-700 bg-black/30 px-2 py-1 text-sm"
      >
      <input
        v-else
        v-model="values[name as string]"
        type="text"
        class="w-full rounded border border-gray-700 bg-black/30 px-2 py-1 text-sm"
      >
    </div>
    <button
      type="submit"
      class="rounded bg-purple-600 hover:bg-purple-700 px-3 py-1.5 text-sm text-white"
    >{{ submitLabel ?? 'Submit' }}</button>
  </form>
</template>
