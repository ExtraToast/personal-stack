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

    <p
      v-if="props.users.length === 0"
      class="rounded-md border border-surface-border/50 bg-surface-elevated p-6 text-center font-mono text-xs text-gray-500"
    >
      No users match this filter.
    </p>

    <div class="space-y-3">
      <div
        v-for="user in props.users"
        :key="user.id"
        class="overflow-hidden rounded-lg border border-surface-border bg-surface-elevated transition-colors hover:border-surface-border/80"
      >
        <!-- Card header: identity + role + delete. Divided from the body so
             the per-user editing affordances don't blur into the ambient
             page chrome. -->
        <div class="flex items-center justify-between border-b border-surface-border/50 bg-surface-dark/40 px-4 py-3">
          <div class="min-w-0">
            <div class="flex items-center gap-2">
              <span class="truncate font-mono text-sm font-bold text-gray-200">{{ user.username }}</span>
              <span
                class="rounded-sm border px-1.5 py-0.5 font-mono text-[10px] uppercase tracking-wider"
                :class="
                  user.role === 'ADMIN'
                    ? 'border-terminal-green/40 bg-terminal-green/10 text-terminal-green'
                    : 'border-surface-border text-gray-500'
                "
              >
                {{ user.role }}
              </span>
              <span
                v-if="!user.emailConfirmed"
                class="rounded-sm border border-terminal-amber/40 bg-terminal-amber/10 px-1.5 py-0.5 font-mono text-[10px] uppercase tracking-wider text-terminal-amber"
              >
                unconfirmed
              </span>
            </div>
            <div class="truncate font-mono text-xs text-gray-500">{{ user.email }}</div>
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

        <!-- Card body: the service-permissions editor. Given its own
             padding + label so it's obviously a sub-section rather than
             a continuation of the header controls. -->
        <div class="px-4 py-3">
          <div class="mb-2 font-mono text-[10px] uppercase tracking-wider text-gray-600">Service permissions</div>
          <ServicePermissionsEditor
            :model-value="user.servicePermissions"
            :disabled="saving === user.id"
            @update:model-value="onServicesChange(user, $event)"
          />
        </div>
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
