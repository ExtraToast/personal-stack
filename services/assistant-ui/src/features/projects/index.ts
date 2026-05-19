export { getProject, listProjects } from './services/projectsService'
// Public surface of the projects feature. Other features must
// import via this barrel rather than reaching into types/services
// directly — the dependency-cruiser `no-cross-feature-deep-import`
// rule enforces it.
export type { GithubLink, Project, ProjectDetail } from './types'
