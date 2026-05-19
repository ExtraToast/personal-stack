export interface Repository {
  id: string
  name: string
  repoUrl: string
  defaultBranch: string
  vaultKeyPath: string
  deployKeyFingerprint: string | null
  deployKeyAddedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface RepositoryDetail {
  repository: Repository
  attachedProjects: AttachedProject[]
}

export interface AttachedProject {
  id: string
  name: string
  slug: string
}

export interface CreateRepositoryInput {
  name: string
  repoUrl: string
  defaultBranch?: string
}

export interface AttachDeployKeyInput {
  privateKeyOpenssh: string
  publicKeyOpenssh: string
  knownHosts?: string
}
