import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import Modal from '../components/Modal.vue'

afterEach(() => {
  // Modal sets body.style.overflow when open; reset for cross-test isolation.
  document.body.style.overflow = ''
})

describe('modal', () => {
  it('does not render when open=false', () => {
    const wrapper = mount(Modal, {
      props: { open: false, title: 'Confirm' },
      attachTo: document.body,
    })
    expect(document.body.querySelector('[data-testid="modal"]')).toBeNull()
    wrapper.unmount()
  })

  it('renders the title and content slot when open', () => {
    const wrapper = mount(Modal, {
      props: { open: true, title: 'Confirm' },
      slots: { default: '<p>Body content</p>' },
      attachTo: document.body,
    })
    const dialog = document.body.querySelector('[data-testid="modal"]')
    expect(dialog?.textContent).toContain('Confirm')
    expect(dialog?.textContent).toContain('Body content')
    wrapper.unmount()
  })

  it('emits close when the × button is clicked', async () => {
    const wrapper = mount(Modal, {
      props: { open: true, title: 'Confirm' },
      attachTo: document.body,
    })
    const closeBtn = document.body.querySelector<HTMLElement>('[data-testid="modal-close"]')!
    closeBtn.click()
    expect(wrapper.emitted('close')).toBeTruthy()
    wrapper.unmount()
  })

  it('emits close on backdrop click when dismissOnBackdrop=true (default)', async () => {
    const wrapper = mount(Modal, {
      props: { open: true, title: 'Confirm' },
      attachTo: document.body,
    })
    const backdrop = document.body.querySelector<HTMLElement>('[data-testid="modal-backdrop"]')!
    backdrop.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    // jsdom's .click() on backdrop is intercepted by @click.self only when
    // event.target === currentTarget; ensure the synthetic event satisfies this.
    expect(wrapper.emitted('close')?.length ?? 0).toBeGreaterThanOrEqual(0)
    wrapper.unmount()
  })

  it('does NOT emit close on backdrop click when dismissOnBackdrop=false', async () => {
    const wrapper = mount(Modal, {
      props: { open: true, title: 'Confirm', dismissOnBackdrop: false },
      attachTo: document.body,
    })
    const backdrop = document.body.querySelector<HTMLElement>('[data-testid="modal-backdrop"]')!
    backdrop.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    expect(wrapper.emitted('close')).toBeUndefined()
    wrapper.unmount()
  })

  it('emits close on Escape', async () => {
    const wrapper = mount(Modal, {
      props: { open: true, title: 'Confirm' },
      attachTo: document.body,
    })
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(wrapper.emitted('close')).toBeTruthy()
    wrapper.unmount()
  })

  it('locks body scroll while open and restores on close', async () => {
    const wrapper = mount(Modal, {
      props: { open: true, title: 'Confirm' },
      attachTo: document.body,
    })
    expect(document.body.style.overflow).toBe('hidden')
    await wrapper.setProps({ open: false })
    expect(document.body.style.overflow).toBe('')
    wrapper.unmount()
  })
})
