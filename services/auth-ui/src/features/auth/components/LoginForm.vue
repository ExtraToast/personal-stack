<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{
  submit: [credentials: { username: string, password: string }]
}>()

const username = ref('')
const password = ref('')

function handleSubmit(): void {
  emit('submit', { username: username.value, password: password.value })
}
</script>

<template>
  <form class="w-full max-w-md space-y-4 rounded-lg bg-white p-8 shadow-lg" @submit.prevent="handleSubmit">
    <h1 class="text-2xl font-bold">Sign In</h1>
    <div>
      <label for="username" class="block text-sm font-medium">Username</label>
      <input id="username" v-model="username" type="text" required class="mt-1 block w-full rounded border p-2">
    </div>
    <div>
      <label for="password" class="block text-sm font-medium">Password</label>
      <input id="password" v-model="password" type="password" required class="mt-1 block w-full rounded border p-2">
    </div>
    <button type="submit" class="w-full rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700">
      Sign In
    </button>
  </form>
</template>
