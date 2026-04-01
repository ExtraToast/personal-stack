<script setup lang="ts">
import type { ProfileData } from '../services/accountService'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/features/auth'
import { changePassword, fetchProfile, updateProfile } from '../services/accountService'

const router = useRouter()
const authStore = useAuthStore()

const profile = ref<ProfileData | null>(null)
const firstName = ref('')
const lastName = ref('')
const profileMsg = ref('')
const profileErr = ref('')

const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const passwordMsg = ref('')
const passwordErr = ref('')

onMounted(async () => {
  if (!authStore.isAuthenticated) {
    router.push('/')
    return
  }
  try {
    profile.value = await fetchProfile()
    firstName.value = profile.value.firstName
    lastName.value = profile.value.lastName
  } catch {
    profileErr.value = 'Failed to load profile'
  }
})

async function saveProfile(): Promise<void> {
  profileMsg.value = ''
  profileErr.value = ''
  try {
    profile.value = await updateProfile({
      firstName: firstName.value,
      lastName: lastName.value,
    })
    profileMsg.value = 'Profile updated'
  } catch {
    profileErr.value = 'Failed to update profile'
  }
}

async function submitPasswordChange(): Promise<void> {
  passwordMsg.value = ''
  passwordErr.value = ''

  if (newPassword.value !== confirmPassword.value) {
    passwordErr.value = 'Passwords do not match'
    return
  }
  if (newPassword.value.length < 8) {
    passwordErr.value = 'Password must be at least 8 characters'
    return
  }

  try {
    await changePassword({
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
    })
    passwordMsg.value = 'Password changed successfully'
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
  } catch {
    passwordErr.value = 'Failed to change password. Check your current password.'
  }
}
</script>

<template>
  <div class="mx-auto max-w-2xl px-4 py-12">
    <h1 class="mb-8 font-mono text-2xl font-bold text-gray-100">Account</h1>

    <!-- Profile Section -->
    <section class="mb-8 rounded-lg border border-surface-border/50 bg-surface-elevated p-6">
      <div class="mb-4 flex items-center gap-2">
        <div class="flex gap-1.5">
          <div class="h-2 w-2 rounded-full bg-red-500/60" />
          <div class="h-2 w-2 rounded-full bg-terminal-amber/60" />
          <div class="h-2 w-2 rounded-full bg-terminal-green/60" />
        </div>
        <span class="font-mono text-xs text-gray-600">~/account/profile</span>
      </div>

      <h2 class="mb-4 font-mono text-lg font-semibold text-gray-200">Profile</h2>

      <div class="space-y-4">
        <div>
          <label class="block font-mono text-xs font-medium text-gray-500">Username</label>
          <div
            class="mt-1 rounded-md border border-surface-border/30 bg-surface-dark px-3 py-2 font-mono text-sm text-gray-400"
          >
            {{ profile?.username ?? '...' }}
          </div>
        </div>

        <div>
          <label class="block font-mono text-xs font-medium text-gray-500">Email</label>
          <div
            class="mt-1 rounded-md border border-surface-border/30 bg-surface-dark px-3 py-2 font-mono text-sm text-gray-400"
          >
            {{ profile?.email ?? '...' }}
          </div>
        </div>

        <div class="flex gap-4">
          <div class="flex-1">
            <label class="block font-mono text-xs font-medium text-gray-400" for="firstName">First name</label>
            <input
              id="firstName"
              v-model="firstName"
              class="mt-1 block w-full rounded-md border border-surface-border bg-surface-dark px-3 py-2 font-mono text-sm text-gray-200 placeholder-gray-600 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
              type="text"
            />
          </div>
          <div class="flex-1">
            <label class="block font-mono text-xs font-medium text-gray-400" for="lastName">Last name</label>
            <input
              id="lastName"
              v-model="lastName"
              class="mt-1 block w-full rounded-md border border-surface-border bg-surface-dark px-3 py-2 font-mono text-sm text-gray-200 placeholder-gray-600 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
              type="text"
            />
          </div>
        </div>

        <p
          v-if="profileMsg"
          class="rounded-md border border-terminal-green/20 bg-terminal-green/10 px-3 py-2 text-sm text-terminal-green"
        >
          {{ profileMsg }}
        </p>
        <p v-if="profileErr" class="rounded-md border border-red-500/20 bg-red-500/10 px-3 py-2 text-sm text-red-400">
          {{ profileErr }}
        </p>

        <button
          class="rounded-md bg-accent px-4 py-2 font-mono text-xs font-semibold text-white transition-colors hover:bg-accent-light"
          @click="saveProfile"
        >
          Save
        </button>
      </div>
    </section>

    <!-- Change Password Section -->
    <section class="rounded-lg border border-surface-border/50 bg-surface-elevated p-6">
      <div class="mb-4 flex items-center gap-2">
        <div class="flex gap-1.5">
          <div class="h-2 w-2 rounded-full bg-red-500/60" />
          <div class="h-2 w-2 rounded-full bg-terminal-amber/60" />
          <div class="h-2 w-2 rounded-full bg-terminal-green/60" />
        </div>
        <span class="font-mono text-xs text-gray-600">~/account/password</span>
      </div>

      <h2 class="mb-4 font-mono text-lg font-semibold text-gray-200">Change password</h2>

      <div class="space-y-4">
        <div>
          <label class="block font-mono text-xs font-medium text-gray-400" for="currentPassword"
            >Current password</label
          >
          <input
            id="currentPassword"
            v-model="currentPassword"
            autocomplete="current-password"
            class="mt-1 block w-full rounded-md border border-surface-border bg-surface-dark px-3 py-2 font-mono text-sm text-gray-200 placeholder-gray-600 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
            type="password"
          />
        </div>

        <div>
          <label class="block font-mono text-xs font-medium text-gray-400" for="newPassword">New password</label>
          <input
            id="newPassword"
            v-model="newPassword"
            autocomplete="new-password"
            class="mt-1 block w-full rounded-md border border-surface-border bg-surface-dark px-3 py-2 font-mono text-sm text-gray-200 placeholder-gray-600 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
            placeholder="Min. 8 characters"
            type="password"
          />
        </div>

        <div>
          <label class="block font-mono text-xs font-medium text-gray-400" for="confirmPassword"
            >Confirm new password</label
          >
          <input
            id="confirmPassword"
            v-model="confirmPassword"
            autocomplete="new-password"
            class="mt-1 block w-full rounded-md border border-surface-border bg-surface-dark px-3 py-2 font-mono text-sm text-gray-200 placeholder-gray-600 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
            type="password"
          />
        </div>

        <p
          v-if="passwordMsg"
          class="rounded-md border border-terminal-green/20 bg-terminal-green/10 px-3 py-2 text-sm text-terminal-green"
        >
          {{ passwordMsg }}
        </p>
        <p v-if="passwordErr" class="rounded-md border border-red-500/20 bg-red-500/10 px-3 py-2 text-sm text-red-400">
          {{ passwordErr }}
        </p>

        <button
          class="rounded-md bg-accent px-4 py-2 font-mono text-xs font-semibold text-white transition-colors hover:bg-accent-light"
          @click="submitPasswordChange"
        >
          Change password
        </button>
      </div>
    </section>
  </div>
</template>
