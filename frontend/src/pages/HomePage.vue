<script setup lang="ts">
import { computed } from 'vue'

import AudienceDialogue from '../features/audience/components/AudienceDialogue.vue'
import CredibilitySummary from '../features/home/components/CredibilitySummary.vue'
import ExploreIndex from '../features/home/components/ExploreIndex.vue'
import PortfolioHero from '../features/home/components/PortfolioHero.vue'
import { heroMarginalia } from '../features/portfolio/model/heroMarginalia'
import { usePublicContent } from '../features/public-content/composables/usePublicContent'
import DossierFooter from '../shared/components/DossierFooter.vue'
import PublicContentFeedback from '../shared/components/PublicContentFeedback.vue'

const { portfolio, status, error, action, retryAfterSeconds, retry } = usePublicContent()

const marginalia = computed(() =>
  portfolio.value ? heroMarginalia(portfolio.value) : null,
)
</script>

<template>
  <main v-if="status === 'ready' && portfolio" class="home-page">
    <PortfolioHero
      :owner="portfolio.owner"
      :version="portfolio.contentVersion"
      :marginalia="marginalia"
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
    :action="action"
    :retry-after-seconds="retryAfterSeconds"
    @retry="retry"
  />
</template>
