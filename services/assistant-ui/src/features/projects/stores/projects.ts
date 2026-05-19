import type { GithubLink, Project } from '../types'
import type { Repository } from '@/features/repositories'
import { defineStore } from 'pinia'
import { ref } from 'vue'
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

export const useProjectsStore = defineStore('projects', () => {
  const projects = ref<Project[]>([])
  const activeProject = ref<Project | null>(null)
  /**
   * Deprecated legacy per-project link rows. Drains as the API stops
   * emitting them, but kept on the store so existing components don't
   * break mid-rollout.
   */
  const links = ref<GithubLink[]>([])
  /**
   * The repository pool currently linked to the active project. The
   * Repository feature owns the rows; this is just the per-project view.
   */
  const repositories = ref<Repository[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  async function loadAll(): Promise<void> {
    isLoading.value = true
    error.value = null
    try {
      projects.value = await listProjects()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load projects'
      throw e
    } finally {
      isLoading.value = false
    }
  }

  async function open(id: string): Promise<void> {
    isLoading.value = true
    error.value = null
    try {
      const detail = await getProject(id)
      activeProject.value = detail.project
      links.value = detail.links
      repositories.value = detail.repositories
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load project'
      throw e
    } finally {
      isLoading.value = false
    }
  }

  async function create(input: { name: string; slug: string; description?: string }): Promise<Project> {
    const p = await createProject(input)
    projects.value.unshift(p)
    return p
  }

  async function linkRepo(repositoryId: string): Promise<void> {
    const project = activeProject.value
    if (!project) return
    repositories.value = await linkRepository(project.id, repositoryId)
  }

  async function unlinkRepo(repositoryId: string): Promise<void> {
    const project = activeProject.value
    if (!project) return
    await unlinkRepository(project.id, repositoryId)
    repositories.value = repositories.value.filter((r) => r.id !== repositoryId)
  }

  // ─────── Legacy GithubLink flow ──────────────────────────────────
  // Kept so existing components don't break before the full UI
  // migration lands. Net-new code should reach for linkRepo /
  // unlinkRepo above instead.

  async function addNewLink(input: {
    name: string
    repoUrl: string
    defaultBranch?: string
  }): Promise<GithubLink | null> {
    const project = activeProject.value
    if (!project) return null
    const link = await addLink(project.id, input)
    links.value.push(link)
    return link
  }

  async function dropLink(linkId: string): Promise<void> {
    const project = activeProject.value
    if (!project) return
    await removeLink(project.id, linkId)
    links.value = links.value.filter((l) => l.id !== linkId)
  }

  async function attach(
    linkId: string,
    body: { privateKeyOpenssh: string; publicKeyOpenssh: string; knownHosts?: string },
  ): Promise<void> {
    const project = activeProject.value
    if (!project) return
    await attachKey(project.id, linkId, body)
    await open(project.id)
  }

  return {
    projects,
    activeProject,
    links,
    repositories,
    isLoading,
    error,
    loadAll,
    open,
    create,
    linkRepo,
    unlinkRepo,
    addNewLink,
    dropLink,
    attach,
  }
})
