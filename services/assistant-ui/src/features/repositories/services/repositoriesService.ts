import type {
  AttachDeployKeyInput,
  CreateRepositoryInput,
  Repository,
  RepositoryDetail,
  RepositoryVerifyResult,
} from '../types'
import { useApiWithAuth } from '@personal-stack/vue-common'

function api(): ReturnType<typeof useApiWithAuth> {
  return useApiWithAuth({ baseUrl: '/api/v1' })
}

export async function listRepositories(): Promise<Repository[]> {
  return api().get<Repository[]>('/repositories')
}

export async function getRepository(id: string): Promise<RepositoryDetail> {
  return api().get<RepositoryDetail>(`/repositories/${id}`)
}

export async function createRepository(input: CreateRepositoryInput): Promise<Repository> {
  return api().post<Repository>('/repositories', input)
}

export async function attachDeployKey(id: string, input: AttachDeployKeyInput): Promise<void> {
  await api().post(`/repositories/${id}/key`, input)
}

export async function deleteRepository(id: string): Promise<void> {
  await api().del(`/repositories/${id}`)
}

export async function verifyRepositoryAccess(id: string): Promise<RepositoryVerifyResult> {
  return api().post<RepositoryVerifyResult>(`/repositories/${id}/verify`, {})
}
