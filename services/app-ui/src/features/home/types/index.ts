export interface Project {
  title: string
  description: string
  technologies: string[]
  githubUrl?: string
  liveUrl?: string
}

export interface Experience {
  role: string
  company: string
  period: string
  description: string
  technologies: string[]
}
