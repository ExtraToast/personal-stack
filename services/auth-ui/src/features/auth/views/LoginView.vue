<script setup lang="ts">
import LoginForm from '../components/LoginForm.vue'

function handleLogin(credentials: { username: string, password: string }): void {
  // TODO: implement login
  console.log('Login:', credentials.username)
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center">
    <LoginForm @submit="handleLogin" />
  </div>
</template>
