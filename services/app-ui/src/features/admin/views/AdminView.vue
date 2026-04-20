<script setup lang="ts">
import type { AdminUser } from '../services/adminService'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/features/auth'
import UserTable from '../components/UserTable.vue'
import { fetchUsers } from '../services/adminService'

const authStore = useAuthStore()
const router = useRouter()
const users = ref<AdminUser[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const query = ref('')

async function loadUsers(): Promise<void> {
  if (!authStore.isAuthenticated) return
  loading.value = true
  error.value = null
  try {
    users.value = await fetchUsers()
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

const totalUsers = computed(() => users.value.length)
const adminCount = computed(() => users.value.filter((u) => u.role === 'ADMIN').length)
const confirmedCount = computed(() => users.value.filter((u) => u.emailConfirmed).length)
const mostRecent = computed<string>(() => {
  if (users.value.length === 0) return '—'
  const newest = users.value.reduce((a, b) => (a.createdAt > b.createdAt ? a : b))
  return newest.username
})

const filtered = computed<AdminUser[]>(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return users.value
  return users.value.filter((u) => u.username.toLowerCase().includes(q) || u.email.toLowerCase().includes(q))
})
</script>

<template>
  <div class="mx-auto max-w-6xl px-6 py-24">
    <!-- Header band: page title + quick stats. All values computed from the
         already-fetched user list, so no extra API calls — the admin sees a
         population overview the instant the page lands. -->
    <header class="mb-8 rounded-lg border border-surface-border/50 bg-surface-elevated p-6">
      <div class="mb-4 flex items-center gap-2">
        <div class="flex gap-1.5">
          <div class="h-2 w-2 rounded-full bg-red-500/60" />
          <div class="h-2 w-2 rounded-full bg-terminal-amber/60" />
          <div class="h-2 w-2 rounded-full bg-terminal-green/60" />
        </div>
        <span class="font-mono text-xs text-gray-600">~/admin/users</span>
      </div>
      <h1 class="mb-4 font-mono text-xl font-bold text-terminal-green">User Management</h1>
      <div class="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <div>
          <div class="font-mono text-xs text-gray-500">Total</div>
          <div class="font-mono text-lg text-gray-200" data-testid="admin-stat-total">{{ totalUsers }}</div>
        </div>
        <div>
          <div class="font-mono text-xs text-gray-500">Admins</div>
          <div class="font-mono text-lg text-gray-200" data-testid="admin-stat-admins">{{ adminCount }}</div>
        </div>
        <div>
          <div class="font-mono text-xs text-gray-500">Confirmed</div>
          <div class="font-mono text-lg text-gray-200" data-testid="admin-stat-confirmed">{{ confirmedCount }}</div>
        </div>
        <div>
          <div class="font-mono text-xs text-gray-500">Newest</div>
          <div class="truncate font-mono text-lg text-gray-200" :title="mostRecent" data-testid="admin-stat-newest">
            {{ mostRecent }}
          </div>
        </div>
      </div>
    </header>

    <!-- Filter bar. Pure client-side: tiny user-table, the population never
         gets big enough to justify a server-side search. -->
    <div class="mb-4 flex items-center gap-2">
      <span class="font-mono text-xs text-gray-600">&gt;</span>
      <input
        v-model="query"
        class="w-full rounded-md border border-surface-border bg-surface-dark px-3 py-2 font-mono text-sm text-gray-200 placeholder-gray-600 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent sm:w-80"
        placeholder="Filter by username or email"
        type="search"
        data-testid="admin-filter"
      />
      <span v-if="query && filtered.length !== users.length" class="font-mono text-xs text-gray-500">
        {{ filtered.length }} / {{ users.length }}
      </span>
    </div>

    <div v-if="loading" class="font-mono text-xs text-gray-500">Loading…</div>
    <div v-else-if="error" class="font-mono text-xs text-red-400">{{ error }}</div>
    <UserTable v-else :users="filtered" @refresh="loadUsers" />
  </div>
</template>
