export default {
  nav: {
    about: 'Over mij',
    experience: 'Ervaring',
    projects: 'Projecten',
    skills: 'Vaardigheden',
    contact: 'Contact',
  },
  hero: {
    subtitle: 'Jr. Software Ingenieur',
    name: 'Joris Jonkers',
    degree: 'BSc',
    tagline:
      'Ik bouw robuuste en schaalbare software — van zelfgehoste infrastructuur tot gepolijste gebruikersapplicaties. Gepassioneerd over clean architecture, developer experience en het leveren van duurzame oplossingen.',
    cta: {
      github: 'GitHub',
      linkedin: 'LinkedIn',
      email: 'E-mail',
    },
    scroll: 'Scroll om te ontdekken',
  },
  about: {
    title: 'Over mij',
    intro:
      'Een gemotiveerde software-ingenieur en MSc-student die op zoek is naar betekenisvolle uitdagingen om bij te dragen aan de maatschappij en professioneel te groeien.',
    body: 'Sterk gericht op samenwerken in teamverband, waarbij ik aan de universiteit consequent de leiding heb genomen over projecten en projectpartners heb gemotiveerd om de beste resultaten te behalen. Geïnvesteerd in de groei richting projectmanagement door de toepassing van Agile-principes met een voorkeur voor Kanban.',
    location: 'Enschede, Nederland',
    languages: 'Nederlands (moedertaal), Engels (vloeiend)',
    education: 'Opleidingen',
    educationEntries: [
      {
        period: '2024 – Heden',
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
    title: 'Ervaring',
    subtitle: 'Waar ik heb gewerkt en wat ik heb opgeleverd.',
    entries: [
      {
        role: 'Junior Software Developer',
        company: 'Nedap N.V.',
        period: 'Juni 2025 – Heden',
        description:
          'Werkzaam aan de migratie van de ONS Plannen & Roosteren zorglogistiekapplicatie van single-tenant naar multi-tenant architectuur, en van bare-metal infrastructuur naar container-orkestratie met Nomad. Verbetert CI/CD-pipelines, onderhoudt Docker-images en voert reguliere Ruby on Rails-ontwikkeltaken uit.',
        technologies: ['Ruby on Rails', 'Nomad', 'Docker', 'CI/CD'],
      },
      {
        role: 'Junior Software Ingenieur',
        company: 'DynaLynx B.V.',
        period: 'Mei 2022 – Juli 2025',
        description:
          'Software van een transportbedrijf doorontwikkeld en gerefactord, inclusief integraties met overheidsportalen voor wet- en regelgeving. Filtersnelheid verbeterd, testdekking vergroot en dataserialisatie geoptimaliseerd met JSON:API. Een React Native-applicatie door elf versies geüpdatet en bijgedragen aan UI-ontwerp en bedrijfslogica voor een chemo-medicatiemengmachine.',
        technologies: ['Ruby on Rails', 'Angular', 'React Native', '.NET', 'Aurelia'],
      },
      {
        role: 'Secretaris & Commissaris Externe Zaken',
        company: 'ESA Blueshell',
        period: 'September 2024 – Heden',
        description:
          'Sponsorbenaderingsproces vernieuwd met webscraping en gerichte mailcampagnes, wat heeft geleid tot contact met meer dan 100 bedrijven. Verantwoordelijk voor officiële communicatie, notulen en maandelijkse nieuwsbrieven. Sinds april 2024 ook de primaire ontwikkelaar van de full-stack verenigingswebsite, gebouwd met Spring Boot 4 (Kotlin) en Vue.js 3 (TypeScript) en uitgerold als Docker Swarm-stack.',
        technologies: ['Spring Boot', 'Kotlin', 'Vue.js', 'Docker Swarm', 'Traefik'],
      },
    ],
  },
  projects: {
    title: 'Projecten',
    subtitle: 'Dingen die ik heb gebouwd of momenteel aan bouw.',
    entries: [
      {
        title: 'Private Stack',
        description:
          'Een zelfgehost infrastructuurplatform op Docker Swarm met Traefik, Vault en observability tooling. Bevat een auth-service (Spring Authorization Server + TOTP MFA), een AI-assistent en Vue.js-frontends, allemaal gebouwd met hexagonale architectuur en afgedwongen door ArchUnit.',
        technologies: ['Kotlin', 'Spring Boot', 'Vue.js', 'Docker Swarm', 'Traefik', 'Vault', 'PostgreSQL'],
        githubUrl: 'https://github.com/ExtraToast',
      },
      {
        title: 'ESA Blueshell Website',
        description:
          "Full-stack platform voor de grootste studentenesportsvereniging van Nederland. Beheert lidmaatschappen, evenementen, contributies en online inschrijvingen. Uitgerold met Docker Swarm, Traefik, Let's Encrypt TLS en Listmonk voor e-mailbeheer.",
        technologies: ['Spring Boot 4', 'Kotlin', 'Vue.js 3', 'TypeScript', 'Docker Swarm', 'GitHub Actions'],
        liveUrl: 'https://esa-blueshell.nl',
        githubUrl: 'https://github.com/ESA-Blueshell/website',
      },
    ],
  },
  skills: {
    title: 'Vaardigheden',
    subtitle: 'Talen, frameworks en tools waar ik mee werk.',
    categories: {
      languages: 'Programmeertalen',
      frameworks: 'Frameworks & Technologieën',
      tools: 'Tools & Platformen',
      soft: 'Persoonlijke Vaardigheden',
    },
  },
  contact: {
    title: 'Neem contact op',
    subtitle: 'Of het nu gaat om een projectidee, samenwerking of gewoon een gesprek, neem gerust contact op.',
    email: 'info@jorisjonkers.dev',
    cta: 'Stuur een e-mail',
  },
  theme: {
    light: 'Licht',
    dark: 'Donker',
    system: 'Systeem',
  },
  language: {
    en: 'English',
    nl: 'Nederlands',
  },
  footer: {
    built: 'Gebouwd met Vue.js, Spring Boot & Kotlin',
    source: 'Broncode',
  },
}
