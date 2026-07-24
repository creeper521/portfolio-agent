<script setup lang="ts">
import type { PublicOwner } from '../../public-content/model/publicContentTypes'

defineProps<{
  owner: PublicOwner
  version: string
}>()
</script>

<template>
  <section class="portfolio-hero paper-surface" data-home-layer="hero">
    <div class="portfolio-hero__paper">
      <div class="portfolio-hero__index" data-hero-index>
        <span>VOL. 01 · {{ version }}</span>
        <span>FACTS BEFORE WORDS</span>
      </div>

      <div class="portfolio-hero__copy reveal">
        <p class="portfolio-hero__chapter">ENGINEERING DOSSIER / 00</p>
        <h1>
          <span v-if="owner.name">{{ owner.name }}</span>
          <span v-else>{{ owner.role }}</span>
          <em>工程实践档案</em>
        </h1>
        <p class="portfolio-hero__lead">{{ owner.summary }}</p>
        <div class="portfolio-hero__actions">
          <a data-hero-primary-action href="#credibility">开始浏览 ↓</a>
          <a data-hero-question-action href="#dialogue">直接提问 →</a>
        </div>
      </div>

      <div class="portfolio-hero__scroll" aria-hidden="true">
        <i></i>
        <span>SCROLL TO INSPECT</span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.portfolio-hero {
  height: clamp(680px, 76svh, 780px);
  overflow: hidden;
  border-bottom: 1px solid var(--rule);
}

.portfolio-hero__paper {
  position: relative;
  height: 100%;
  padding: 104px max(48px, calc((100vw - var(--content-width)) / 2)) 48px;
  overflow: hidden;
}

.portfolio-hero__paper::after {
  position: absolute;
  z-index: 0;
  inset: 0;
  content: '';
  pointer-events: none;
  opacity: 0.48;
  background:
    radial-gradient(circle at 20% 20%, rgba(122, 46, 42, 0.045), transparent 32%),
    repeating-linear-gradient(
      90deg,
      transparent 0,
      transparent calc(20% - 1px),
      rgba(111, 93, 70, 0.055) 20%
    );
}

.portfolio-hero__index,
.portfolio-hero__copy,
.portfolio-hero__scroll {
  position: relative;
  z-index: 1;
}

.portfolio-hero__index {
  display: flex;
  padding-bottom: 14px;
  justify-content: space-between;
  color: var(--ink-2);
  border-bottom: 1px solid var(--rule);
  font: 11.5px var(--mono);
  letter-spacing: 0.12em;
}

.portfolio-hero__copy {
  width: min(800px, calc(100% - 28px));
  margin-top: 54px;
}

.portfolio-hero__chapter {
  display: flex;
  margin: 0;
  align-items: center;
  gap: 17px;
  color: var(--red);
  font: 12.5px var(--mono);
  letter-spacing: 0.13em;
}

.portfolio-hero__chapter::after {
  width: 82px;
  height: 1px;
  content: '';
  background: var(--red);
}

h1 {
  max-width: 10ch;
  margin: 22px 0 23px;
  font: 700 clamp(58px, 6.5vw, 92px) / 0.94 var(--serif);
  letter-spacing: -0.045em;
}

h1 span,
h1 em {
  display: block;
}

h1 em {
  color: var(--red);
  font-weight: 400;
  font-style: normal;
  opacity: 0;
  animation: hero-shift 1.1s 0.35s var(--ease) forwards;
}

.portfolio-hero__lead {
  max-width: 620px;
  margin: 0;
  color: var(--ink-2);
  font: 20px/1.68 var(--serif);
}

.portfolio-hero__actions {
  display: flex;
  margin-top: 30px;
  gap: 20px;
}

.portfolio-hero__actions a {
  padding: 9px 2px;
  border-bottom: 1px solid var(--ink);
  font: 13px var(--mono);
  letter-spacing: 0.06em;
}

.portfolio-hero__actions a:last-child {
  color: var(--red);
  border-color: var(--red);
}

.portfolio-hero__scroll {
  position: absolute;
  bottom: 25px;
  left: max(48px, calc((100vw - var(--content-width)) / 2));
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--ink-2);
  font: 11px var(--mono);
  letter-spacing: 0.12em;
}

.portfolio-hero__scroll i {
  display: block;
  width: 56px;
  height: 1px;
  overflow: hidden;
  background: var(--rule);
}

.portfolio-hero__scroll i::after {
  display: block;
  width: 45%;
  height: 100%;
  content: '';
  background: var(--red);
  animation: dossier-scan 2.4s ease-in-out infinite;
}

@media (max-width: 980px) {
  .portfolio-hero {
    height: auto;
  }

  .portfolio-hero__paper {
    min-height: 650px;
    padding: 96px 30px 48px;
  }

  .portfolio-hero__copy {
    margin-top: 70px;
  }
}

@media (max-width: 620px) {
  .portfolio-hero__paper {
    padding: 96px 22px 50px;
  }

  .portfolio-hero__index {
    font-size: 10px;
  }

  .portfolio-hero__copy {
    margin-top: 58px;
  }

  h1 {
    font-size: clamp(48px, 15vw, 62px);
  }

  h1 em {
    transform: none;
  }

  .portfolio-hero__lead {
    font-size: 17px;
  }

  .portfolio-hero__scroll {
    left: 22px;
  }
}
</style>
