import type { Repository } from '@/features/repositories'

export interface Project {
  id: string
  name: string
  slug: string
  description: string
  createdAt: string
  updatedAt: string
}

/**
 * Legacy per-link record. Kept around for backwards compatibility
 * during the Repository-M:N rollout. Use `Repository` going forward —
 * the project pool is now the authoritative list.
 */
export interface GithubLink {
  id: string
  projectId: string
  name: string
  repoUrl: string
  defaultBranch: string
  vaultKeyPath: string
  deployKeyFingerprint: string | null
  deployKeyAddedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface ProjectDetail {
  project: Project
  /**
   * Deprecated — the legacy per-project link rows. Still emitted by
   * the API for backwards compatibility, but the redesigned UI
   * consumes `repositories` below.
   */
  links: GithubLink[]
  /**
   * The repository pool linked to this project. Many-to-many: a
   * repository can appear under multiple projects without
   * duplicating its deploy key.
   */
  repositories: Repository[]
}
