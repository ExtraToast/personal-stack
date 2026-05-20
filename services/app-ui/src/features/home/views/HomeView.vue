<script setup lang="ts">
// `AppsGrid` was previously rendered here for authenticated users
// but the dedicated `/apps` route already serves that purpose and
// is reachable via the nav's "My Apps" link. Showing it on the
// home page too duplicated the surface and pushed the portfolio
// content below the fold.
import AboutSection from '../components/AboutSection.vue'
import ContactSection from '../components/ContactSection.vue'
import ExperienceTimeline from '../components/ExperienceTimeline.vue'
import FooterSection from '../components/FooterSection.vue'
import HeroSection from '../components/HeroSection.vue'
import ProjectShowcase from '../components/ProjectShowcase.vue'
import SkillsSection from '../components/SkillsSection.vue'
</script>

<template>
  <div>
    <HeroSection />
    <AboutSection />
    <ExperienceTimeline />
    <ProjectShowcase />
    <SkillsSection />
    <ContactSection />
    <FooterSection />
  </div>
</template>
