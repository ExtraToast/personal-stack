import type { Repository } from '../../repositories/types'
import type { GithubLink, Project, ProjectDetail } from '../types'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  addLink,
  attachKey,
  createProject,
  getProject,
  linkRepository,
  listProjects,
  removeLink,
  unlinkRepository,
} from '../services/projectsService'
import { useProjectsStore } from '../stores/projects'

vi.mock('../services/projectsService', () => ({
  listProjects: vi.fn(),
  getProject: vi.fn(),
  createProject: vi.fn(),
  addLink: vi.fn(),
  removeLink: vi.fn(),
  attachKey: vi.fn(),
  linkRepository: vi.fn(),
  unlinkRepository: vi.fn(),
}))

const mocked = {
  listProjects: vi.mocked(listProjects),
  getProject: vi.mocked(getProject),
  createProject: vi.mocked(createProject),
  addLink: vi.mocked(addLink),
  removeLink: vi.mocked(removeLink),
  attachKey: vi.mocked(attachKey),
  linkRepository: vi.mocked(linkRepository),
  unlinkRepository: vi.mocked(unlinkRepository),
}

function fakeRepo(over: Partial<Repository> = {}): Repository {
  return {
    id: 'rrrrrrrr-rrrr-rrrr-rrrr-rrrrrrrrrrrr',
    name: 'demo-repo',
    repoUrl: 'git@github.com:owner/demo.git',
    defaultBranch: 'main',
    vaultKeyPath: 'secret/data/agents/repositories/rrrrrrrr...',
    deployKeyFingerprint: null,
    deployKeyAddedAt: null,
    createdAt: '2026-05-20T10:00:00Z',
    updatedAt: '2026-05-20T10:00:00Z',
    ...over,
  }
}

function fakeProject(over: Partial<Project> = {}): Project {
  return {
    id: '11111111-1111-1111-1111-111111111111',
    name: 'demo',
    slug: 'demo',
    description: '',
    createdAt: '2026-05-19T10:00:00Z',
    updatedAt: '2026-05-19T10:00:00Z',
    ...over,
  }
}

function fakeLink(over: Partial<GithubLink> = {}): GithubLink {
  return {
    id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    projectId: '11111111-1111-1111-1111-111111111111',
    name: 'repo',
    repoUrl: 'git@github.com:owner/repo.git',
    defaultBranch: 'main',
    vaultKeyPath: 'secret/data/agents/projects/p/repos/l',
    deployKeyFingerprint: null,
    deployKeyAddedAt: null,
    createdAt: '2026-05-19T10:00:00Z',
    updatedAt: '2026-05-19T10:00:00Z',
    ...over,
  }
}

describe('useProjectsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    Object.values(mocked).forEach((m) => m.mockReset())
  })

  it('loadAll populates projects', async () => {
    mocked.listProjects.mockResolvedValue([fakeProject()])
    const store = useProjectsStore()
    await store.loadAll()
    expect(store.projects).toHaveLength(1)
    expect(store.error).toBeNull()
  })

  it('loadAll surfaces the Error message and re-throws on failure', async () => {
    mocked.listProjects.mockRejectedValue(new Error('boom'))
    const store = useProjectsStore()
    await expect(store.loadAll()).rejects.toThrow('boom')
    expect(store.error).toBe('boom')
  })

  it('open loads detail + links + repositories', async () => {
    const detail: ProjectDetail = {
      project: fakeProject(),
      links: [fakeLink()],
      repositories: [],
    }
    mocked.getProject.mockResolvedValue(detail)
    const store = useProjectsStore()
    await store.open('11111111-1111-1111-1111-111111111111')
    expect(store.activeProject?.id).toBe(detail.project.id)
    expect(store.links).toHaveLength(1)
    expect(store.repositories).toHaveLength(0)
  })

  it('create unshifts the new project', async () => {
    const p = fakeProject({ id: 'new', name: 'fresh' })
    mocked.createProject.mockResolvedValue(p)
    const store = useProjectsStore()
    store.projects = [fakeProject({ id: 'old' })]
    const result = await store.create({ name: 'fresh', slug: 'fresh' })
    expect(result).toEqual(p)
    expect(store.projects[0]!.id).toBe('new')
  })

  it('addNewLink no-ops when no project is open', async () => {
    const store = useProjectsStore()
    const result = await store.addNewLink({ name: 'repo', repoUrl: 'git@x:y/z.git' })
    expect(result).toBeNull()
    expect(mocked.addLink).not.toHaveBeenCalled()
  })

  it('addNewLink appends and returns the new link when a project is open', async () => {
    const link = fakeLink({ id: 'new-link' })
    mocked.addLink.mockResolvedValue(link)
    const store = useProjectsStore()
    store.activeProject = fakeProject()
    const result = await store.addNewLink({ name: 'repo', repoUrl: 'git@x:y/z.git' })
    expect(result).toEqual(link)
    expect(store.links.map((l) => l.id)).toContain('new-link')
  })

  it('dropLink removes from the active project link list', async () => {
    mocked.removeLink.mockResolvedValue()
    const store = useProjectsStore()
    store.activeProject = fakeProject()
    store.links = [fakeLink({ id: 'a' }), fakeLink({ id: 'b' })]
    await store.dropLink('a')
    expect(store.links.map((l) => l.id)).toEqual(['b'])
  })

  it('attach delegates and refreshes the project', async () => {
    mocked.attachKey.mockResolvedValue()
    mocked.getProject.mockResolvedValue({ project: fakeProject(), links: [], repositories: [] })
    const store = useProjectsStore()
    store.activeProject = fakeProject()
    await store.attach('link-id', {
      privateKeyOpenssh: '-----BEGIN OPENSSH PRIVATE KEY-----\nx\n-----END OPENSSH PRIVATE KEY-----',
      publicKeyOpenssh: 'ssh-ed25519 AAAA test@laptop',
    })
    expect(mocked.attachKey).toHaveBeenCalled()
    expect(mocked.getProject).toHaveBeenCalled()
  })

  it('linkRepo no-ops when no project is open', async () => {
    const store = useProjectsStore()
    await store.linkRepo('any-repo-id')
    expect(mocked.linkRepository).not.toHaveBeenCalled()
  })

  it('linkRepo replaces the repositories array with the API response', async () => {
    const repo = fakeRepo({ id: 'new-repo' })
    mocked.linkRepository.mockResolvedValue([repo])
    const store = useProjectsStore()
    store.activeProject = fakeProject()
    store.repositories = []
    await store.linkRepo('new-repo')
    expect(mocked.linkRepository).toHaveBeenCalledWith(store.activeProject!.id, 'new-repo')
    expect(store.repositories.map((r) => r.id)).toEqual(['new-repo'])
  })

  it('unlinkRepo drops the row from the active project list', async () => {
    mocked.unlinkRepository.mockResolvedValue()
    const a = fakeRepo({ id: 'a' })
    const b = fakeRepo({ id: 'b' })
    const store = useProjectsStore()
    store.activeProject = fakeProject()
    store.repositories = [a, b]
    await store.unlinkRepo('a')
    expect(mocked.unlinkRepository).toHaveBeenCalledWith(store.activeProject!.id, 'a')
    expect(store.repositories.map((r) => r.id)).toEqual(['b'])
  })

  it('unlinkRepo no-ops when no project is open', async () => {
    const store = useProjectsStore()
    await store.unlinkRepo('any')
    expect(mocked.unlinkRepository).not.toHaveBeenCalled()
  })
})
