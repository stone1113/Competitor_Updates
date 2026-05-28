<template>
  <div class="app-shell" :class="{ collapsed: appStore.sidebarCollapsed }">
    <AppSidebar />
    <div class="app-main">
      <AppHeader />
      <main class="app-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'
import { useAppStore } from '../../stores/app'

const appStore = useAppStore()
</script>

<style scoped>
.app-shell {
  display: grid;
  grid-template-columns: var(--sidebar-width) 1fr;
  height: 100vh;
  background: var(--bg);
  transition: grid-template-columns var(--duration-base) var(--ease-out);
}
.app-shell.collapsed {
  grid-template-columns: var(--sidebar-collapsed) 1fr;
}
.app-main {
  display: grid;
  grid-template-rows: var(--header-height) 1fr;
  min-width: 0;
  min-height: 0;
}
.app-content {
  overflow-y: auto;
  background: var(--bg);
}
</style>
