import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ChatInput from '../components/ChatInput.vue'

describe('chatInput', () => {
  it('renders textarea for input', () => {
    const wrapper = mount(ChatInput)

    expect(wrapper.find('textarea').exists()).toBe(true)
  })

  it('emits send event with trimmed content on submit', async () => {
    const wrapper = mount(ChatInput)
    const textarea = wrapper.find('textarea')

    await textarea.setValue('  Hello world  ')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('send')).toBeTruthy()
    expect(wrapper.emitted('send')![0]).toEqual(['Hello world'])
  })

  it('does not emit send when content is empty', async () => {
    const wrapper = mount(ChatInput)
    const textarea = wrapper.find('textarea')

    await textarea.setValue('   ')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('send')).toBeFalsy()
  })

  it('disables send button when disabled prop is true', () => {
    const wrapper = mount(ChatInput, {
      props: { disabled: true },
    })

    const button = wrapper.find('button[type="submit"]')
    expect(button.attributes('disabled')).toBeDefined()
  })

  it('clears input after sending', async () => {
    const wrapper = mount(ChatInput)
    const textarea = wrapper.find('textarea')

    await textarea.setValue('Hello')
    await wrapper.find('form').trigger('submit')

    const el: HTMLTextAreaElement = textarea.element
    expect(el.value).toBe('')
  })
})
