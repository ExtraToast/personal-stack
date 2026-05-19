<script setup lang="ts">
import { computed, inject, onMounted } from 'vue'
import { tabsInjectionKey } from './tabsContext'

interface Props {
  /**
   * Identifier for this panel. Must match the `value` activated in the
   * parent `<Tabs>` to be visible.
   */
  value: string
  /**
   * Whether to keep the panel mounted when inactive. Defaults to false
   * so heavy panels (transcripts, code editors) don't pay setup costs
   * until first selected. Set true for cheap content that should
   * preserve state on tab switches.
   */
  keepAlive?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  keepAlive: false,
})

const tabs = inject(tabsInjectionKey)
if (!tabs) {
  throw new Error('<TabPanel> must be a descendant of <Tabs>')
}

onMounted(() => tabs.register(props.value))

const isActive = computed(() => tabs.active.value === props.value)
</script>

<template>
  <section
    v-if="keepAlive || isActive"
    v-show="isActive"
    role="tabpanel"
    :data-testid="`tab-panel-${value}`"
    :hidden="!isActive"
  >
    <slot />
  </section>
</template>
