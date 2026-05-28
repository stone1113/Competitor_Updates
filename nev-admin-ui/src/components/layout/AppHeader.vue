<template>
  <header class="header">
    <div class="header-left">
      <button class="collapse-btn" @click="appStore.toggleSidebar" :aria-label="appStore.sidebarCollapsed ? '展开侧栏' : '收起侧栏'">
        <span class="collapse-bar" :class="{ collapsed: appStore.sidebarCollapsed }"></span>
        <span class="collapse-bar" :class="{ collapsed: appStore.sidebarCollapsed }"></span>
        <span class="collapse-bar" :class="{ collapsed: appStore.sidebarCollapsed }"></span>
      </button>
      <span class="crumb-title">{{ pageTitle }}</span>
    </div>
    <div class="header-right">
      <span class="header-meta tabular">{{ now }}</span>
    </div>
  </header>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '../../stores/app'

const route = useRoute()
const appStore = useAppStore()

const pageTitle = computed(() => route.meta.title || '')

const now = ref(formatNow())
let timer = null

function formatNow() {
  const d = new Date()
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => { timer = setInterval(() => { now.value = formatNow() }, 60_000) })
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--header-height);
  padding: 0 var(--space-6);
  background: var(--bg);
  border-bottom: 1px solid var(--hairline);
}
.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}
.collapse-btn {
  width: 28px;
  height: 28px;
  display: inline-flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  padding: 4px 6px;
  transition: background var(--duration-fast) var(--ease-out);
}
.collapse-btn:hover { background: rgba(0,0,0,0.04); }
.collapse-bar {
  display: block;
  height: 1.5px;
  background: var(--ink);
  transition: transform var(--duration-base) var(--ease-out);
  transform-origin: left center;
}
.collapse-bar.collapsed:nth-child(2) { transform: scaleX(0.6); }
.collapse-bar.collapsed:nth-child(3) { transform: scaleX(0.3); }
.crumb-title {
  font-family: var(--font-display);
  font-weight: 500;
  font-size: var(--text-md);
  color: var(--ink);
  letter-spacing: var(--tracking-tight);
}
.header-meta {
  font-size: var(--text-xs);
  color: var(--ink-muted);
}
</style>
