<script setup lang="ts">
import type { Block } from '../types/blocks'
import { computed } from 'vue'
import { parseBlocks } from '../types/blocks'
import ApprovalBlock from './blocks/ApprovalBlock.vue'
import ChoiceBlock from './blocks/ChoiceBlock.vue'
import DiffBlock from './blocks/DiffBlock.vue'
import FormBlock from './blocks/FormBlock.vue'
import TextBlock from './blocks/TextBlock.vue'
import ToolCallBlock from './blocks/ToolCallBlock.vue'

const props = defineProps<{ body: string }>()
const emit = defineEmits<{
  pick: [optionId: string]
  decide: [value: { approved: boolean }]
  formSubmit: [value: Record<string, unknown>]
}>()

const blocks = computed<Block[]>(() => parseBlocks(props.body))
</script>

<template>
  <div class="space-y-2">
    <template v-for="(block, i) in blocks" :key="i">
      <TextBlock v-if="block.kind === 'text'" :md="block.md" />
      <ChoiceBlock
        v-else-if="block.kind === 'choice'"
        :prompt="block.prompt"
        :options="block.options"
        @pick="(id: string) => emit('pick', id)"
      />
      <FormBlock
        v-else-if="block.kind === 'form'"
        :schema="block.schema"
        :submit-label="block.submitLabel"
        @submit="(v: Record<string, unknown>) => emit('formSubmit', v)"
      />
      <DiffBlock v-else-if="block.kind === 'diff'" :path="block.path" :patch="block.patch" />
      <ToolCallBlock
        v-else-if="block.kind === 'tool-call'"
        :name="block.name"
        :args="block.args"
        :result="block.result"
      />
      <ApprovalBlock
        v-else-if="block.kind === 'approval'"
        :action="block.action"
        :payload="block.payload"
        @decide="(v: { approved: boolean }) => emit('decide', v)"
      />
    </template>
  </div>
</template>
