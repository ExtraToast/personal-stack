export default {
  nav: {
    about: 'About',
    experience: 'Experience',
    projects: 'Projects',
    skills: 'Skills',
    contact: 'Contact',
  },
  hero: {
    subtitle: 'Jr. Software Engineer',
    name: 'Joris Jonkers',
    degree: 'BSc',
    tagline:
      'Building robust, scalable software — from self-hosted infrastructure to polished user-facing applications. Passionate about clean architecture, developer experience, and shipping things that last.',
    cta: {
      github: 'GitHub',
      linkedin: 'LinkedIn',
      email: 'Email',
    },
    scroll: 'Scroll to explore',
  },
  about: {
    title: 'About me',
    intro:
      'A motivated software engineer and MSc student seeking meaningful challenges that allow me to contribute to society and grow as a professional.',
    body: 'Highly focused on working in group settings, consistently leading teams at university and motivating project partners to produce the best possible outcomes. Invested in growing toward project management, having led every university project to excellent results through Agile principles with a preference for Kanban.',
    location: 'Enschede, Netherlands',
    languages: 'Dutch (native), English (fluent)',
    education: 'Education',
    educationEntries: [
      {
        period: '2024 – Present',
        institution: 'University of Twente',
        degree: 'MSc Software Technology',
      },
      {
        period: '2020 – 2024',
        institution: 'University of Twente',
        degree: 'BSc Technical Computer Science',
      },
      {
        period: '2010 – 2016',
        institution: 'Oscar Romero',
        degree: 'VWO NT/NG',
      },
    ],
  },
  experience: {
    title: 'Experience',
    subtitle: 'Where I have worked and what I have shipped.',
    entries: [
      {
        role: 'Junior Software Developer',
        company: 'Nedap N.V.',
        period: 'June 2025 – Present',
        description:
          'Working on the migration of the ONS Plannen & Roosteren healthcare logistics application from single-tenant to multi-tenant architecture. Contributing to the platform migration from bare-metal to Nomad-based container orchestration. Improving CI/CD pipelines and maintaining Docker images.',
        technologies: ['Ruby on Rails', 'Nomad', 'Docker', 'CI/CD'],
      },
      {
        role: 'Junior Software Engineer',
        company: 'DynaLynx B.V.',
        period: 'May 2022 – July 2025',
        description:
          'Developed and refactored software for a transport company, including integrations with government portals. Improved query performance, enhanced test coverage, and optimized data serialization with JSON:API. Updated a React Native application through 11 versions. Contributed to UI design for a chemo drug mixing machine.',
        technologies: ['Ruby on Rails', 'Angular', 'React Native', '.NET', 'Aurelia'],
      },
      {
        role: 'Secretary & Commissioner of External Affairs',
        company: 'ESA Blueshell',
        period: 'September 2024 – Present',
        description:
          "Overhauled sponsor outreach with scraping techniques and email templates. Migrated the association's full-stack website from JS and Java to TS & Kotlin, implemented in Spring Boot 4 (Kotlin) and Vue.js 3 (TypeScript), deployed as a Docker Swarm stack with Traefik and GitHub Actions CI/CD. Managed 100+ company relations.",
        technologies: ['Spring Boot', 'Kotlin', 'Vue.js', 'Docker Swarm', 'Traefik'],
      },
    ],
  },
  projects: {
    title: 'Projects',
    subtitle: 'Things I have built or am building.',
    entries: [
      {
        title: 'Private Stack',
        description:
          'A self-hosted infrastructure platform running on Docker Swarm with Traefik, Vault, and observability tooling. Includes an auth service (Spring Authorization Server + TOTP MFA), an AI assistant service, and Vue.js frontends — all built with hexagonal architecture and enforced by ArchUnit.',
        technologies: [
          'Kotlin',
          'Spring Boot',
          'Vue.js',
          'Docker Swarm',
          'Traefik',
          'Vault',
          'PostgreSQL',
        ],
        githubUrl: 'https://github.com/ExtraToast',
      },
      {
        title: 'ESA Blueshell Website',
        description:
          "Full-stack platform for the Netherlands' largest student Esports association. Membership management, event administration, contribution tracking, and online signups. Deployed with Docker Swarm, Traefik, Let's Encrypt TLS, and Listmonk for email management.",
        technologies: [
          'Spring Boot 4',
          'Kotlin',
          'Vue.js 3',
          'TypeScript',
          'Docker Swarm',
          'GitHub Actions',
        ],
        liveUrl: 'https://esa-blueshell.nl',
        githubUrl: 'https://github.com/ESA-Blueshell/website',
      },
    ],
  },
  skills: {
    title: 'Skills',
    subtitle: 'Languages, frameworks, and tools I work with.',
    categories: {
      languages: 'Programming Languages',
      frameworks: 'Frameworks & Technologies',
      tools: 'Tools & Platforms',
      soft: 'Soft Skills',
    },
  },
  contact: {
    title: 'Get in touch',
    subtitle:
      "Whether it's a project idea, collaboration, or just a chat — feel free to reach out.",
    email: 'info@jorisjonkers.dev',
    cta: 'Send me an email',
  },
  theme: {
    light: 'Light',
    dark: 'Dark',
    system: 'System',
  },
  language: {
    en: 'English',
    nl: 'Nederlands',
  },
  footer: {
    built: 'Built with Vue.js, Spring Boot & Kotlin',
    source: 'Source code',
  },
}
