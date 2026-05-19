import type { AttachDeployKeyInput, CreateRepositoryInput, Repository, RepositoryDetail } from '../types'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  attachDeployKey as attachKeyApi,
  createRepository as createApi,
  deleteRepository as deleteApi,
  getRepository as getApi,
  listRepositories as listApi,
} from '../services/repositoriesService'

export const useRepositoriesStore = defineStore('repositories', () => {
  const items = ref<Repository[]>([])
  const detailById = ref<Record<string, RepositoryDetail>>({})
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  const byId = computed(() => Object.fromEntries(items.value.map((r) => [r.id, r] as const)))

  async function loadAll(): Promise<void> {
    isLoading.value = true
    error.value = null
    try {
      items.value = await listApi()
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e)
      throw e
    } finally {
      isLoading.value = false
    }
  }

  async function loadDetail(id: string): Promise<RepositoryDetail> {
    const detail = await getApi(id)
    detailById.value[id] = detail
    // Keep the list in sync if the repository row drifted.
    const idx = items.value.findIndex((r) => r.id === id)
    if (idx >= 0) items.value[idx] = detail.repository
    return detail
  }

  async function create(input: CreateRepositoryInput): Promise<Repository> {
    const created = await createApi(input)
    items.value = [created, ...items.value]
    return created
  }

  async function attachKey(id: string, input: AttachDeployKeyInput): Promise<void> {
    await attachKeyApi(id, input)
    // The key write is async (returns 202); poll once via the detail
    // endpoint so the fingerprint shows in the UI as soon as Vault has it.
    await loadDetail(id)
  }

  async function destroy(id: string): Promise<void> {
    await deleteApi(id)
    items.value = items.value.filter((r) => r.id !== id)
    delete detailById.value[id]
  }

  return {
    items,
    detailById,
    isLoading,
    error,
    byId,
    loadAll,
    loadDetail,
    create,
    attachKey,
    destroy,
  }
})
