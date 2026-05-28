<template>
  <div class="ui-tabs">
    <div class="ui-tabs-bar" role="tablist">
      <button
        v-for="t in tabs"
        :key="t.name"
        type="button"
        role="tab"
        :aria-selected="t.name === active"
        class="ui-tab"
        :class="{ active: t.name === active }"
        @click="setActive(t.name)"
      >
        {{ t.label }}
      </button>
      <span class="ui-tabs-rail"></span>
    </div>
    <div class="ui-tabs-pane">
      <slot />
    </div>
  </div>
</template>

<script setup>
import { provide, reactive, computed } from 'vue'

const active = defineModel({ type: String, default: '' })
const emit = defineEmits(['change'])

const tabs = reactive([])

function register(tab) {
  if (!tabs.find(t => t.name === tab.name)) tabs.push(tab)
  if (!active.value) active.value = tab.name
}
function unregister(name) {
  const i = tabs.findIndex(t => t.name === name)
  if (i >= 0) tabs.splice(i, 1)
}
function setActive(name) {
  if (active.value === name) return
  active.value = name
  emit('change', name)
}

provide('uiTabs', {
  active: computed(() => active.value),
  register,
  unregister
})
</script>

<style scoped>
.ui-tabs-bar {
  display: flex;
  gap: var(--space-6);
  border-bottom: 1px solid var(--hairline);
  position: relative;
  padding: 0 0 0 var(--space-1);
}
.ui-tab {
  background: transparent;
  border: none;
  padding: var(--space-3) 0 var(--space-3);
  font-family: var(--font-display);
  font-size: var(--text-md);
  letter-spacing: var(--tracking-tight);
  color: var(--ink-muted);
  cursor: pointer;
  position: relative;
  transition: color var(--duration-fast) var(--ease-out);
  font-weight: 500;
}
.ui-tab:hover { color: var(--ink); }
.ui-tab.active {
  color: var(--ink);
}
.ui-tab.active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--accent);
}
.ui-tabs-pane {
  padding-top: var(--space-5);
}
</style>
