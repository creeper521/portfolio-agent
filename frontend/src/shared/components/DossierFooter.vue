<script setup lang="ts">
defineProps<{
  contentVersion: string
}>()
</script>

<template>
  <footer class="dossier-footer" data-dossier-footer>
    <div class="page-shell">
      <div class="dossier-footer__grid">
        <section>
          <h2>ABOUT THIS DOSSIER</h2>
          <p>内容来自经过公开审查的个人工程记录。Agent 负责查询与表达，不修改事实。</p>
        </section>
        <section>
          <h2>PUBLIC BOUNDARY</h2>
          <p>只展示已批准的项目、摘要和脱敏证据索引，不公开原始日报与内部路径。</p>
        </section>
        <section>
          <h2>PRIVACY</h2>
          <p>无需登录；会话只保存在当前浏览器，并在 7 天后自动过期。</p>
        </section>
      </div>
      <div class="dossier-footer__base">
        <span>VOLUME 01 · {{ contentVersion }}</span>
        <span>PUBLIC · DESENSITIZED · HUMAN REVIEWED</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.dossier-footer {
  padding: 54px 0 45px;
  border-top: 1px solid var(--rule);
  background: var(--paper);
}

.dossier-footer__grid {
  display: grid;
  grid-template-columns: 1.3fr 1fr 1fr;
  gap: 55px;
}

h2 {
  margin: 0 0 16px;
  color: var(--ink-2);
  font: 11px var(--mono);
  letter-spacing: 0.13em;
}

p {
  margin: 0;
  color: var(--ink-2);
  font-size: 13px;
  line-height: 1.75;
}

.dossier-footer__base {
  display: flex;
  margin-top: 35px;
  padding-top: 18px;
  justify-content: space-between;
  color: var(--muted);
  border-top: 1px solid var(--rule);
  font: 10px var(--mono);
  letter-spacing: 0.1em;
}

@media (max-width: 700px) {
  .dossier-footer__grid {
    grid-template-columns: 1fr;
    gap: 30px;
  }

  .dossier-footer__base {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
