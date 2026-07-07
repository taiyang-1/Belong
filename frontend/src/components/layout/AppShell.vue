<template>
  <div class="shell-page">
    <div class="shell">
      <aside class="sidebar-col">
        <slot name="sidebar" />
      </aside>
      <main class="main-col">
        <slot name="main" />
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell-page {
  min-height: 100dvh;
  padding: 22px;
  background: #eef3f8;
}

.shell {
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr);
  height: calc(100dvh - 44px);
  overflow: hidden;
  border: 1px solid #dce4ee;
  border-radius: 20px;
  background: #ffffff;
  box-shadow: 0 24px 70px rgba(23, 32, 51, 0.08);
}

.sidebar-col {
  background: #132033;
  overflow-y: auto;
}

.main-col {
  background: #ffffff;
  overflow-y: auto;
}

@media (max-width: 1024px) {
  .shell-page {
    padding: 0;
  }

  .shell {
    height: 100dvh;
    border-radius: 0;
    grid-template-columns: 200px minmax(0, 1fr);
  }
}

@media (max-width: 768px) {
  .shell {
    grid-template-columns: 1fr;
  }
  .sidebar-col {
    display: none;
  }
}
</style>
