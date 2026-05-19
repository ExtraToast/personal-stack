export interface Project {
  id: string
  name: string
  slug: string
  description: string
  createdAt: string
  updatedAt: string
}

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
  links: GithubLink[]
}
