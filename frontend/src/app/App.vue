<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import DossierHeader from '../shared/components/DossierHeader.vue'

const route = useRoute()
const workspace = computed(() => route.meta.workspace === true)
</script>

<template>
  <div class="site-frame" :class="{ 'site-frame--workspace': workspace }">
    <DossierHeader :compact="workspace" />
    <RouterView />
  </div>
</template>
