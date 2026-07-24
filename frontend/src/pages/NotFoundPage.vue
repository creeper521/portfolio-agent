<template>
  <main class="not-found">
    <div class="page-shell not-found__inner">
      <p>404 / UNPUBLISHED</p>
      <h1>这里没有<br />公开内容。</h1>
      <div>
        <span>地址可能已失效，或对应材料仍处于私有审核阶段。</span>
        <RouterLink to="/">返回档案概览 →</RouterLink>
      </div>
    </div>
  </main>
</template>

<style scoped>
.not-found {
  display: grid;
  min-height: calc(100vh - var(--header-height));
  color: var(--paper);
  place-items: center;
  background: var(--ink);
}

.not-found__inner {
  display: grid;
  grid-template-columns: 0.3fr 1fr 0.5fr;
  gap: clamp(30px, 6vw, 90px);
  align-items: end;
}

p {
  color: var(--red-hi);
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.15em;
}

h1 {
  margin: 0;
  font-family: var(--serif);
  font-size: clamp(56px, 8vw, 118px);
  font-weight: 400;
  line-height: 0.98;
}

.not-found__inner div {
  display: grid;
  gap: 26px;
  color: #a99f91;
  font-size: 13px;
  line-height: 1.8;
}

a {
  width: fit-content;
  padding-bottom: 7px;
  color: var(--paper);
  border-bottom: 1px solid var(--red-hi);
  font-family: var(--mono);
  font-size: 10px;
}

@media (max-width: 760px) {
  .not-found__inner {
    grid-template-columns: 1fr;
  }
}
</style>
