<script setup lang="ts">
import AudienceDialogue from '../features/audience/components/AudienceDialogue.vue'
import CredibilitySummary from '../features/home/components/CredibilitySummary.vue'
import ExploreIndex from '../features/home/components/ExploreIndex.vue'
import PortfolioHero from '../features/home/components/PortfolioHero.vue'
import { usePublicContent } from '../features/public-content/composables/usePublicContent'
import DossierFooter from '../shared/components/DossierFooter.vue'
import PublicContentFeedback from '../shared/components/PublicContentFeedback.vue'

const { portfolio, status, error, retry } = usePublicContent()
</script>

<template>
  <main v-if="status === 'ready' && portfolio" class="home-page">
    <PortfolioHero
      :owner="portfolio.owner"
      :version="portfolio.contentVersion"
    />
    <CredibilitySummary :portfolio="portfolio" />
    <AudienceDialogue :portfolio="portfolio" />
    <ExploreIndex />
    <DossierFooter :content-version="portfolio.contentVersion" />
  </main>
  <PublicContentFeedback
    v-else-if="status === 'loading' || status === 'error'"
    :status="status"
    :message="error"
    @retry="retry"
  />
</template>
