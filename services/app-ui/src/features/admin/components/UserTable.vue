<script setup lang="ts">
import type { AdminUser } from '../services/adminService'
import { ref } from 'vue'
import { useAuthStore } from '@/features/auth'
import { deleteUser, updateUserRole, updateUserServices } from '../services/adminService'
import DeleteUserDialog from './DeleteUserDialog.vue'
import RoleSelector from './RoleSelector.vue'
import ServicePermissionsEditor from './ServicePermissionsEditor.vue'

const props = defineProps<{
  users: AdminUser[]
}>()

const emit = defineEmits<{
  refresh: []
}>()

const authStore = useAuthStore()
const pendingDelete = ref<AdminUser | null>(null)
const saving = ref<string | null>(null)
const error = ref<string | null>(null)

async function onRoleChange(user: AdminUser, role: string): Promise<void> {
  if (!authStore.isAuthenticated) return
  saving.value = user.id
  error.value = null
  try {
    await updateUserRole(user.id, role)
    emit('refresh')
  } catch {
    error.value = `Failed to update role for ${user.username}`
  } finally {
    saving.value = null
  }
}

async function onServicesChange(user: AdminUser, services: string[]): Promise<void> {
  if (!authStore.isAuthenticated) return
  saving.value = user.id
  error.value = null
  try {
    await updateUserServices(user.id, services)
    emit('refresh')
  } catch {
    error.value = `Failed to update services for ${user.username}`
  } finally {
    saving.value = null
  }
}

async function confirmDelete(): Promise<void> {
  if (!pendingDelete.value || !authStore.isAuthenticated) return
  const target = pendingDelete.value
  pendingDelete.value = null
  try {
    await deleteUser(target.id)
    emit('refresh')
  } catch {
    error.value = `Failed to delete ${target.username}`
  }
}
</script>

<template>
  <div>
    <p v-if="error" class="mb-3 font-mono text-xs text-red-400">{{ error }}</p>

    <div class="space-y-2">
      <div
        v-for="user in props.users"
        :key="user.id"
        class="rounded-lg border border-surface-border bg-surface-elevated p-4"
      >
        <div class="mb-2 flex items-center justify-between">
          <div>
            <span class="font-mono text-sm font-bold text-gray-200">{{ user.username }}</span>
            <span class="ml-2 font-mono text-xs text-gray-500">{{ user.email }}</span>
          </div>
          <div class="flex items-center gap-3">
            <RoleSelector
              :model-value="user.role"
              :disabled="saving === user.id"
              @update:model-value="onRoleChange(user, $event)"
            />
            <button
              class="font-mono text-xs text-red-500 hover:text-red-400 disabled:opacity-40"
              type="button"
              :disabled="saving === user.id"
              @click="pendingDelete = user"
            >
              Delete
            </button>
          </div>
        </div>
        <ServicePermissionsEditor
          :model-value="user.servicePermissions"
          :disabled="saving === user.id"
          @update:model-value="onServicesChange(user, $event)"
        />
      </div>
    </div>

    <DeleteUserDialog
      :open="pendingDelete !== null"
      :username="pendingDelete?.username ?? ''"
      @confirm="confirmDelete"
      @cancel="pendingDelete = null"
    />
  </div>
</template>
