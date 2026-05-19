<script setup lang="ts">
import { computed, reactive } from 'vue'

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
function asProperties(value: unknown): Record<string, FieldSpec> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
  const result: Record<string, FieldSpec> = {}
  // FieldSpec / Record narrowing beyond "non-array object" lives
  // behind a future shared schema; until then the casts here are
  // intentional bridges from JSON-Schema's `unknown` to the inputs
  // this component knows how to render.
  /* eslint-disable ts/consistent-type-assertions */
  for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
    if (v && typeof v === 'object') {
      result[k] = v as FieldSpec
    }
  }
  /* eslint-enable ts/consistent-type-assertions */
  return result
}

function asStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return value.filter((v): v is string => typeof v === 'string')
}

const properties = computed<Record<string, FieldSpec>>(() => asProperties(props.schema.properties))
const required = computed<string[]>(() => asStringArray(props.schema.required))

const values = reactive<Record<string, unknown>>(
  Object.fromEntries(Object.entries(properties.value).map(([k, spec]) => [k, spec.default ?? defaultFor(spec)])),
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
        <span v-if="required.includes(String(name))" class="text-red-400">*</span>
      </label>
      <p v-if="spec.description" class="text-xs text-gray-400">{{ spec.description }}</p>
      <select
        v-if="spec.enum"
        v-model="values[String(name)]"
        class="w-full rounded border border-gray-700 bg-black/30 px-2 py-1 text-sm"
      >
        <option v-for="opt in spec.enum" :key="String(opt)" :value="opt">{{ opt }}</option>
      </select>
      <input v-else-if="spec.type === 'boolean'" v-model="values[String(name)]" type="checkbox" class="rounded" />
      <input
        v-else-if="spec.type === 'integer' || spec.type === 'number'"
        v-model.number="values[String(name)]"
        type="number"
        :step="spec.type === 'integer' ? 1 : 'any'"
        class="w-full rounded border border-gray-700 bg-black/30 px-2 py-1 text-sm"
      />
      <input
        v-else
        v-model="values[String(name)]"
        type="text"
        class="w-full rounded border border-gray-700 bg-black/30 px-2 py-1 text-sm"
      />
    </div>
    <button type="submit" class="rounded bg-purple-600 hover:bg-purple-700 px-3 py-1.5 text-sm text-white">
      {{ submitLabel ?? 'Submit' }}
    </button>
  </form>
</template>
