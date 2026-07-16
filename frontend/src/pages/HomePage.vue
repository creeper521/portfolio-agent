<script setup lang="ts">
import { onMounted, ref } from 'vue'

import AudienceDialogue from '../features/audience/components/AudienceDialogue.vue'
import CredibilitySummary from '../features/home/components/CredibilitySummary.vue'
import ExploreIndex from '../features/home/components/ExploreIndex.vue'
import PortfolioHero from '../features/home/components/PortfolioHero.vue'
import type { PublicPortfolio } from '../features/public-content/model/publicContentTypes'
import { publicContentRepository } from '../features/public-content/repository/publicContentRepository'
import DossierFooter from '../shared/components/DossierFooter.vue'

const portfolio = ref<PublicPortfolio | null>(null)

onMounted(async () => {
  portfolio.value = await publicContentRepository.getPortfolio()
})
</script>

<template>
  <main v-if="portfolio" class="home-page">
    <PortfolioHero
      :owner="portfolio.owner"
      :version="portfolio.contentVersion"
    />
    <CredibilitySummary :portfolio="portfolio" />
    <AudienceDialogue :portfolio="portfolio" />
    <ExploreIndex />
    <DossierFooter :content-version="portfolio.contentVersion" />
  </main>
  <main v-else class="home-preview-loading" aria-busy="true">
    <span>正在装订公开档案…</span>
  </main>
</template>

<style scoped>
.home-preview-loading {
  display: grid;
  min-height: calc(100vh - var(--header-height));
  place-items: center;
  color: var(--muted);
  background: var(--paper);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.12em;
}
</style>
