<script setup lang="ts">
import type { AdminUser } from '../services/adminService'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/features/auth'
import UserTable from '../components/UserTable.vue'
import { fetchUsers } from '../services/adminService'

const authStore = useAuthStore()
const router = useRouter()
const users = ref<AdminUser[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

async function loadUsers(): Promise<void> {
  if (!authStore.accessToken) return
  loading.value = true
  error.value = null
  try {
    users.value = await fetchUsers(authStore.accessToken)
  } catch {
    error.value = 'Failed to load users'
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (!authStore.isAdmin) {
    await router.replace('/')
    return
  }
  await loadUsers()
})
</script>

<template>
  <div class="mx-auto max-w-6xl px-6 py-24">
    <h1 class="mb-6 font-mono text-lg font-bold text-terminal-green">User Management</h1>

    <div v-if="loading" class="font-mono text-xs text-gray-500">Loading…</div>
    <div v-else-if="error" class="font-mono text-xs text-red-400">{{ error }}</div>
    <UserTable v-else :users="users" @refresh="loadUsers" />
  </div>
</template>
