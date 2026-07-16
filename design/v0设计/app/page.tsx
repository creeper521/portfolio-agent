import Navbar from '@/components/navbar'
import HeroSection from '@/components/hero-section'
import ProjectsSection from '@/components/projects-section'
import TimelineSection from '@/components/timeline-section'
import SkillsSection from '@/components/skills-section'
import Footer from '@/components/footer'

export default function Page() {
  return (
    <main className="min-h-screen bg-background">
      <Navbar />
      <HeroSection />
      <ProjectsSection />
      <TimelineSection />
      <SkillsSection />
      <Footer />
    </main>
  )
}
