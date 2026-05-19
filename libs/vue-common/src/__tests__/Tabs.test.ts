import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent, h } from 'vue'
import TabPanel from '../components/TabPanel.vue'
import Tabs from '../components/Tabs.vue'

function makeHost(initial?: string) {
  return defineComponent({
    components: { Tabs, TabPanel },
    render() {
      const tabsProps: Record<string, unknown> = { ariaLabel: 'demo' }
      if (initial !== undefined) tabsProps.modelValue = initial
      return h(Tabs, tabsProps, {
        tabs: ({ active, activate }: { active: string; activate: (v: string) => void }) =>
          ['a', 'b', 'c'].map((v) => {
            // Splitting the props object keeps each literal's keys
            // either all-bare or all-quoted, sidestepping prettier
            // (quoteProps=as-needed) and eslint (style/quote-props)
            // disagreeing over the dashed `data-testid` and
            // `aria-selected` keys.
            const dashedAttrs = { 'data-testid': `tab-${v}`, 'aria-selected': active === v }
            return h('button', { key: v, onClick: () => activate(v), ...dashedAttrs }, v)
          }),
        default: () => [
          h(TabPanel, { value: 'a', keepAlive: true }, { default: () => 'Panel A' }),
          h(TabPanel, { value: 'b' }, { default: () => 'Panel B' }),
          h(TabPanel, { value: 'c' }, { default: () => 'Panel C' }),
        ],
      })
    },
  })
}

describe('tabs + TabPanel', () => {
  it('activates the first registered panel by default', () => {
    const wrapper = mount(makeHost())
    expect(wrapper.find('[data-testid="tab-panel-a"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="tab-panel-b"]').exists()).toBe(false)
  })

  it('activates the modelValue panel when explicitly set', () => {
    const wrapper = mount(makeHost('b'))
    expect(wrapper.find('[data-testid="tab-panel-b"]').exists()).toBe(true)
  })

  it('switches to the panel matching the activate(value) call', async () => {
    const wrapper = mount(makeHost())
    await wrapper.get('[data-testid="tab-b"]').trigger('click')
    expect(wrapper.find('[data-testid="tab-panel-b"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="tab-panel-a"]').exists()).toBe(true) // keepAlive
    expect(wrapper.find('[data-testid="tab-panel-a"]').attributes('hidden')).toBe('')
  })

  it('throws when a TabPanel is rendered outside a Tabs host', () => {
    expect(() => mount(TabPanel, { props: { value: 'orphan' } })).toThrow(/<TabPanel>/)
  })
})
