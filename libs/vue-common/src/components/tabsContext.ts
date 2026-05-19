import type { ComputedRef, InjectionKey } from 'vue'

export interface TabsContext {
  /**
   * Read-only view of the currently-active tab value. `WritableComputedRef`
   * upcasts cleanly to `ComputedRef` so callers can only read; the parent
   * `<Tabs>` owns the writes.
   */
  active: ComputedRef<string>
  register: (value: string) => void
  activate: (value: string) => void
}

export const tabsInjectionKey: InjectionKey<TabsContext> = Symbol('vue-common.tabs')
