<script setup lang="ts">
import type { ProfileData } from '../services/accountService'
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/features/auth'
import { changePassword, fetchProfile, updateProfile } from '../services/accountService'

const router = useRouter()
const authStore = useAuthStore()

const profile = ref<ProfileData | null>(null)
const isLoading = ref(true)
const firstName = ref('')
const lastName = ref('')
const pristineFirstName = ref('')
const pristineLastName = ref('')
const profileMsg = ref('')
const profileErr = ref('')
const isSavingProfile = ref(false)

const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const passwordMsg = ref('')
const passwordErr = ref('')
const isChangingPassword = ref(false)

const isDirty = computed(() => firstName.value !== pristineFirstName.value || lastName.value !== pristineLastName.value)

const passwordStrength = computed<'empty' | 'weak' | 'fair' | 'strong'>(() => {
  const pw = newPassword.value
  if (!pw) return 'empty'
  if (pw.length < 8) return 'weak'
  const variety = Number(/[a-z]/.test(pw)) + Number(/[A-Z]/.test(pw)) + Number(/\d/.test(pw)) + Number(/\W/.test(pw))
  if (variety >= 3 && pw.length >= 12) return 'strong'
  return 'fair'
})

const passwordStrengthLabel = computed(() => {
  switch (passwordStrength.value) {
    case 'strong':
      return 'strong'
    case 'fair':
      return 'fair'
    case 'weak':
      return 'weak — min. 8 characters'
    default:
      return ''
  }
})

const passwordStrengthClass = computed(() => {
  switch (passwordStrength.value) {
    case 'strong':
      return 'text-terminal-green'
    case 'fair':
      return 'text-terminal-amber'
    case 'weak':
      return 'text-red-400'
    default:
      return 'text-gray-500'
  }
})

onMounted(async () => {
  if (!authStore.isAuthenticated) {
    router.push('/')
    return
  }
  try {
    const loaded = await fetchProfile()
    profile.value = loaded
    firstName.value = loaded.firstName
    lastName.value = loaded.lastName
    pristineFirstName.value = loaded.firstName
    pristineLastName.value = loaded.lastName
  } catch {
    profileErr.value = 'Failed to load profile'
  } finally {
    isLoading.value = false
  }
})

async function saveProfile(): Promise<void> {
  if (!isDirty.value || isSavingProfile.value) return
  profileMsg.value = ''
  profileErr.value = ''
  isSavingProfile.value = true
  try {
    const updated = await updateProfile({
      firstName: firstName.value,
      lastName: lastName.value,
    })
    profile.value = updated
    pristineFirstName.value = updated.firstName
    pristineLastName.value = updated.lastName
    profileMsg.value = 'Profile updated'
  } catch {
    profileErr.value = 'Failed to update profile'
  } finally {
    isSavingProfile.value = false
  }
}

async function submitPasswordChange(): Promise<void> {
  if (isChangingPassword.value) return
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

  isChangingPassword.value = true
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
  } finally {
    isChangingPassword.value = false
  }
}

// Auto-dismiss success banners after 3s so the page stops declaring "Profile updated"
// long after the user has moved on. Errors stay sticky — they need action.
watch(profileMsg, (v) => {
  if (v) setTimeout(() => (profileMsg.value = ''), 3000)
})
watch(passwordMsg, (v) => {
  if (v) setTimeout(() => (passwordMsg.value = ''), 3000)
})
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

      <!-- Loading skeleton: shown until fetchProfile resolves, so the first paint
           is a plausible form shape instead of a blank card. -->
      <div v-if="isLoading" class="space-y-4" data-testid="account-loading">
        <div class="h-14 animate-pulse rounded-md bg-surface-dark/60" />
        <div class="h-14 animate-pulse rounded-md bg-surface-dark/60" />
        <div class="flex gap-4">
          <div class="h-14 flex-1 animate-pulse rounded-md bg-surface-dark/60" />
          <div class="h-14 flex-1 animate-pulse rounded-md bg-surface-dark/60" />
        </div>
      </div>

      <div v-else class="space-y-4">
        <div>
          <label class="block font-mono text-xs font-medium text-gray-500">Username</label>
          <div
            class="mt-1 rounded-md border border-surface-border/30 bg-surface-dark px-3 py-2 font-mono text-sm text-gray-400"
            data-testid="account-username"
          >
            {{ profile?.username ?? '...' }}
          </div>
        </div>

        <div>
          <label class="block font-mono text-xs font-medium text-gray-500">Email</label>
          <div
            class="mt-1 rounded-md border border-surface-border/30 bg-surface-dark px-3 py-2 font-mono text-sm text-gray-400"
            data-testid="account-email"
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

        <Transition
          enter-active-class="transition duration-200 ease-out"
          enter-from-class="-translate-y-1 opacity-0"
          enter-to-class="translate-y-0 opacity-100"
          leave-active-class="transition duration-150 ease-in"
          leave-from-class="opacity-100"
          leave-to-class="opacity-0"
        >
          <p
            v-if="profileMsg"
            class="rounded-md border border-terminal-green/20 bg-terminal-green/10 px-3 py-2 text-sm text-terminal-green"
            data-testid="account-profile-success"
          >
            {{ profileMsg }}
          </p>
        </Transition>
        <p
          v-if="profileErr"
          class="rounded-md border border-red-500/20 bg-red-500/10 px-3 py-2 text-sm text-red-400"
          data-testid="account-profile-error"
        >
          {{ profileErr }}
        </p>

        <button
          :disabled="!isDirty || isSavingProfile"
          class="rounded-md bg-accent px-4 py-2 font-mono text-xs font-semibold text-white transition-colors hover:bg-accent-light disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-accent"
          data-testid="account-save-profile"
          @click="saveProfile"
        >
          {{ isSavingProfile ? 'Saving...' : 'Save' }}
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
          <p
            v-if="passwordStrengthLabel"
            class="mt-1 font-mono text-xs"
            :class="passwordStrengthClass"
            data-testid="account-password-strength"
          >
            strength: {{ passwordStrengthLabel }}
          </p>
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

        <Transition
          enter-active-class="transition duration-200 ease-out"
          enter-from-class="-translate-y-1 opacity-0"
          enter-to-class="translate-y-0 opacity-100"
          leave-active-class="transition duration-150 ease-in"
          leave-from-class="opacity-100"
          leave-to-class="opacity-0"
        >
          <p
            v-if="passwordMsg"
            class="rounded-md border border-terminal-green/20 bg-terminal-green/10 px-3 py-2 text-sm text-terminal-green"
            data-testid="account-password-success"
          >
            {{ passwordMsg }}
          </p>
        </Transition>
        <p
          v-if="passwordErr"
          class="rounded-md border border-red-500/20 bg-red-500/10 px-3 py-2 text-sm text-red-400"
          data-testid="account-password-error"
        >
          {{ passwordErr }}
        </p>

        <button
          :disabled="isChangingPassword"
          class="rounded-md bg-accent px-4 py-2 font-mono text-xs font-semibold text-white transition-colors hover:bg-accent-light disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-accent"
          data-testid="account-change-password"
          @click="submitPasswordChange"
        >
          {{ isChangingPassword ? 'Changing...' : 'Change password' }}
        </button>
      </div>
    </section>
  </div>
</template>
