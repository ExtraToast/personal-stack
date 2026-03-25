export interface Project {
  id: string
  title: string
  description: string
  techStack: string[]
  githubUrl?: string
  liveUrl?: string
}

export interface Experience {
  id: string
  role: string
  company: string
  period: string
  description: string
  technologies: string[]
}

export interface Skill {
  name: string
  category: 'backend' | 'frontend' | 'infrastructure' | 'database'
}
