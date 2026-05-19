import type { InjectionKey, Ref } from 'vue'

export interface TabsContext {
  active: Readonly<Ref<string>>
  register: (value: string) => void
  activate: (value: string) => void
}

export const tabsInjectionKey: InjectionKey<TabsContext> = Symbol('vue-common.tabs')
