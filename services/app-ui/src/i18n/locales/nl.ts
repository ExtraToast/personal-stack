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
      'Ik bouw robuuste en schaalbare software — van zelf-gehoste infrastructuur tot gepolijste gebruikersapplicaties. Gepassioneerd over clean architecture, developer experience, en het leveren van duurzame oplossingen.',
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
      'Een gemotiveerde software-ingenieur en MSc-student die op zoek is naar betekenisvolle uitdagingen die de mogelijkheid bieden om bij te dragen aan de maatschappij en professioneel te groeien.',
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
          'Werkzaam aan de migratie van de ONS Plannen & Roosteren zorglogistiekapplicatie van single-tenant naar multi-tenant architectuur. Bijdragen aan de platformmigratie van bare-metal naar container-orkestratie met Nomad. Verbeteren van CI/CD-pipelines en onderhouden van Docker-images.',
        technologies: ['Ruby on Rails', 'Nomad', 'Docker', 'CI/CD'],
      },
      {
        role: 'Junior Software Ingenieur',
        company: 'DynaLynx B.V.',
        period: 'Mei 2022 – Juli 2025',
        description:
          'Software van een transportbedrijf doorontwikkeld en gerefactord, inclusief integratie met overheidsportalen. Filtersnelheid verbeterd, testdekking vergroot en serialisatie geoptimaliseerd met JSON:API. React Native-applicatie door elf versies geüpdatet. Bijgedragen aan het UI-ontwerp van een chemo-medicatiemengmachine.',
        technologies: ['Ruby on Rails', 'Angular', 'React Native', '.NET', 'Aurelia'],
      },
      {
        role: 'Secretaris & Commissaris Externe Zaken',
        company: 'ESA Blueshell',
        period: 'September 2024 – Heden',
        description:
          'Sponsorbenaderingsproces vernieuwd door webscraping en effectieve mailcampagnes. De full-stack verenigingswebsite volledig herbouwd met Spring Boot 4 (Kotlin) en Vue.js 3 (TypeScript), uitgerold als Docker Swarm-stack met Traefik en GitHub Actions CI/CD. Meer dan 100 bedrijfsrelaties beheerd.',
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
          'Een zelf-gehost infrastructuurplatform op Docker Swarm met Traefik, Vault en observability tooling. Bevat een auth-service (Spring Authorization Server + TOTP MFA), een AI-assistent en Vue.js-frontends — allemaal gebouwd met hexagonale architectuur en afgedwongen door ArchUnit.',
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
          "Full-stack platform voor de grootste studentenesportsvereniging van Nederland. Lidmaatschapsbeheer, evenementenadministratie, contributiebeheer en online inschrijvingen. Uitgerold met Docker Swarm, Traefik, Let's Encrypt TLS en Listmonk voor e-mailbeheer.",
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
    subtitle:
      'Of het nu gaat om een projectidee, samenwerking of gewoon een gesprek — neem gerust contact op.',
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
