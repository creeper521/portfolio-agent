<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

defineProps<{ compact?: boolean }>()

const route = useRoute()
const open = ref(false)
const menuButton = ref<HTMLButtonElement | null>(null)

const theme = computed(() => (route.meta.workspace === true ? 'ink' : 'paper'))
const isHome = computed(() => route.name === 'home')

const homeAnchors = [
  { label: '概览', href: '#credibility' },
  { label: '对话', href: '#dialogue' },
  { label: '探索', href: '#explore' },
]

const routeLinks = [
  { label: '概览', to: '/' },
  { label: '项目', to: '/projects' },
  { label: '时间线', to: '/timeline' },
  { label: '证据', to: '/evidence' },
]

function closeMenu(restoreFocus = false) {
  open.value = false
  if (restoreFocus) nextTick(() => menuButton.value?.focus())
}

watch(
  () => route.fullPath,
  () => closeMenu(),
)
</script>

<template>
  <header
    class="dossier-header"
    :data-header-theme="theme"
  >
    <div class="dossier-header__inner">
      <RouterLink class="dossier-mark" to="/" aria-label="返回作品集概览">
        <span class="dossier-mark__name">工程成长档案</span>
        <span class="dossier-mark__meta">/ ENGINEERING DOSSIER</span>
      </RouterLink>

      <button
        ref="menuButton"
        class="menu-trigger"
        type="button"
        :aria-expanded="open"
        aria-controls="primary-navigation"
        @click="open = !open"
      >
        {{ open ? '关闭' : '目录' }}
      </button>

      <nav
        id="primary-navigation"
        class="primary-nav"
        :class="{ 'primary-nav--open': open }"
        aria-label="主导航"
        @keydown.esc="closeMenu(true)"
      >
        <template v-if="isHome">
          <a
            v-for="anchor in homeAnchors"
            :key="anchor.href"
            data-home-anchor
            :href="anchor.href"
            @click="closeMenu()"
          >
            {{ anchor.label }}
          </a>
          <span class="primary-nav__role">技术面试官</span>
        </template>
        <template v-else>
          <RouterLink v-for="link in routeLinks" :key="link.to" :to="link.to">
            {{ link.label }}
          </RouterLink>
        </template>
        <RouterLink class="primary-nav__agent" to="/agent">完整 Agent ↗</RouterLink>
      </nav>
    </div>
  </header>
</template>
