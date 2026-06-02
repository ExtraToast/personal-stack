import type { Repository, RepositoryDetail } from '../types'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  attachDeployKey,
  createRepository,
  deleteRepository,
  getRepository,
  listRepositories,
  verifyRepositoryAccess,
} from '../services/repositoriesService'
import { useRepositoriesStore } from '../stores/repositories'

vi.mock('../services/repositoriesService', () => ({
  listRepositories: vi.fn(),
  getRepository: vi.fn(),
  createRepository: vi.fn(),
  attachDeployKey: vi.fn(),
  deleteRepository: vi.fn(),
  verifyRepositoryAccess: vi.fn(),
}))

const mocked = {
  listRepositories: vi.mocked(listRepositories),
  getRepository: vi.mocked(getRepository),
  createRepository: vi.mocked(createRepository),
  attachDeployKey: vi.mocked(attachDeployKey),
  deleteRepository: vi.mocked(deleteRepository),
  verifyRepositoryAccess: vi.mocked(verifyRepositoryAccess),
}

function fakeRepo(over: Partial<Repository> = {}): Repository {
  return {
    id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    name: 'demo',
    repoUrl: 'git@github.com:owner/demo.git',
    defaultBranch: 'main',
    vaultKeyPath: 'secret/data/agents/repositories/aaaaaaaa-...',
    deployKeyFingerprint: null,
    deployKeyAddedAt: null,
    createdAt: '2026-05-20T10:00:00Z',
    updatedAt: '2026-05-20T10:00:00Z',
    ...over,
  }
}

describe('repositories store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loadAll populates items', async () => {
    mocked.listRepositories.mockResolvedValue([fakeRepo()])
    const store = useRepositoriesStore()
    await store.loadAll()
    expect(store.items).toHaveLength(1)
    expect(store.isLoading).toBe(false)
    expect(store.error).toBeNull()
  })

  it('loadAll captures and re-throws errors', async () => {
    mocked.listRepositories.mockRejectedValue(new Error('boom'))
    const store = useRepositoriesStore()
    await expect(store.loadAll()).rejects.toThrow('boom')
    expect(store.error).toBe('boom')
    expect(store.isLoading).toBe(false)
  })

  it('loadDetail caches by id and keeps the list row in sync', async () => {
    const list = fakeRepo({ name: 'list-name' })
    const detail: RepositoryDetail = {
      repository: fakeRepo({ name: 'detail-name' }),
      attachedProjects: [],
    }
    mocked.listRepositories.mockResolvedValue([list])
    mocked.getRepository.mockResolvedValue(detail)
    const store = useRepositoriesStore()
    await store.loadAll()
    await store.loadDetail(detail.repository.id)
    expect(store.detailById[detail.repository.id]).toEqual(detail)
    // The row in the list should pick up the detail's name.
    expect(store.items[0]!.name).toBe('detail-name')
  })

  it('create prepends to items', async () => {
    const created = fakeRepo({ id: 'new', name: 'new' })
    mocked.createRepository.mockResolvedValue(created)
    const store = useRepositoriesStore()
    store.items = [fakeRepo({ id: 'old' })]
    const result = await store.create({ name: 'new', repoUrl: 'git@x:y/z.git', defaultBranch: 'main' })
    expect(result).toEqual(created)
    expect(store.items[0]!.id).toBe('new')
  })

  it('attachKey re-fetches the detail so the fingerprint updates', async () => {
    mocked.attachDeployKey.mockResolvedValue()
    mocked.getRepository.mockResolvedValue({
      repository: fakeRepo({ deployKeyFingerprint: 'SHA256:x' }),
      attachedProjects: [],
    })
    const store = useRepositoriesStore()
    await store.attachKey('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', {
      privateKeyOpenssh: '-----BEGIN OPENSSH PRIVATE KEY-----\nx\n-----END OPENSSH PRIVATE KEY-----',
      publicKeyOpenssh: 'ssh-ed25519 AAAA test@laptop',
    })
    expect(mocked.attachDeployKey).toHaveBeenCalledOnce()
    expect(mocked.getRepository).toHaveBeenCalledOnce()
  })

  it('verify stores the result onto the cached detail', async () => {
    const repo = fakeRepo({ deployKeyFingerprint: 'SHA256:x' })
    mocked.getRepository.mockResolvedValue({ repository: repo, attachedProjects: [] })
    mocked.verifyRepositoryAccess.mockResolvedValue({
      read: true,
      write: false,
      defaultBranchProtected: null,
      checkedAt: '2026-05-21T10:00:00Z',
      messages: ['no GitHub token configured'],
    })
    const store = useRepositoriesStore()
    await store.loadDetail(repo.id)
    const result = await store.verify(repo.id)
    expect(result.read).toBe(true)
    expect(store.detailById[repo.id]!.verify).toEqual(result)
  })

  it('verify is a no-op on the cache when no detail is loaded for the id', async () => {
    mocked.verifyRepositoryAccess.mockResolvedValue({
      read: true,
      write: true,
      defaultBranchProtected: true,
      checkedAt: '2026-05-21T10:00:00Z',
      messages: [],
    })
    const store = useRepositoriesStore()
    const result = await store.verify('missing-id')
    expect(result.write).toBe(true)
    expect(store.detailById['missing-id']).toBeUndefined()
  })

  it('destroy removes from items + detailById', async () => {
    mocked.deleteRepository.mockResolvedValue()
    const store = useRepositoriesStore()
    const a = fakeRepo({ id: 'a' })
    const b = fakeRepo({ id: 'b' })
    store.items = [a, b]
    store.detailById = { a: { repository: a, attachedProjects: [] } }
    await store.destroy('a')
    expect(store.items.map((r) => r.id)).toEqual(['b'])
    expect(store.detailById.a).toBeUndefined()
  })
})
