import type { GithubLink, Project } from '../types'
import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  addLink,
  attachKey,
  createProject,
  getProject,
  listProjects,
  removeLink,
} from '../services/projectsService'

export const useProjectsStore = defineStore('projects', () => {
  const projects = ref<Project[]>([])
  const activeProject = ref<Project | null>(null)
  const links = ref<GithubLink[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  async function loadAll(): Promise<void> {
    isLoading.value = true
    error.value = null
    try {
      projects.value = await listProjects()
    } catch {
      error.value = 'Failed to load projects'
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
    } catch {
      error.value = 'Failed to load project'
    } finally {
      isLoading.value = false
    }
  }

  async function create(input: { name: string; slug: string; description?: string }): Promise<Project> {
    const p = await createProject(input)
    projects.value.unshift(p)
    return p
  }

  async function addNewLink(input: { name: string; repoUrl: string; defaultBranch?: string }): Promise<GithubLink | null> {
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

  async function attach(linkId: string, body: { privateKeyOpenssh: string; publicKeyOpenssh: string; knownHosts?: string }): Promise<void> {
    const project = activeProject.value
    if (!project) return
    await attachKey(project.id, linkId, body)
    await open(project.id)
  }

  return {
    projects,
    activeProject,
    links,
    isLoading,
    error,
    loadAll,
    open,
    create,
    addNewLink,
    dropLink,
    attach,
  }
})
