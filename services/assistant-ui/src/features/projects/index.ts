// Public surface of the projects feature. Other features must
// import via this barrel rather than reaching into types / stores /
// services directly — dependency-cruiser's `no-cross-feature-deep-
// import` rule enforces it.
export { getProject, listProjects } from './services/projectsService'
export { useProjectsStore } from './stores/projects'
export type { GithubLink, Project, ProjectDetail } from './types'
