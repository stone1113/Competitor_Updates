<template>
  <div class="ui-pagination">
    <span class="ui-pagination-total tabular">共 {{ total }} 条</span>

    <div class="ui-pagination-size">
      <span class="ui-pagination-label">每页</span>
      <select v-model.number="size" class="ui-pagination-select" @change="onSize">
        <option v-for="s in pageSizes" :key="s" :value="s">{{ s }}</option>
      </select>
    </div>

    <button
      class="ui-page-btn"
      :disabled="page <= 1"
      @click="go(page - 1)"
    >‹</button>

    <button
      v-for="p in pages"
      :key="p.key"
      class="ui-page-btn"
      :class="{ active: p.value === page, ellipsis: p.ellipsis }"
      :disabled="p.ellipsis"
      @click="!p.ellipsis && go(p.value)"
    >{{ p.label }}</button>

    <button
      class="ui-page-btn"
      :disabled="page >= totalPages"
      @click="go(page + 1)"
    >›</button>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const page = defineModel('currentPage', { type: Number, default: 1 })
const size = defineModel('pageSize', { type: Number, default: 20 })
const props = defineProps({
  total: { type: Number, default: 0 },
  pageSizes: { type: Array, default: () => [20, 50, 100] }
})
const emit = defineEmits(['current-change', 'size-change'])

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / size.value)))

function go(p) {
  if (p < 1 || p > totalPages.value) return
  page.value = p
  emit('current-change', p)
}
function onSize() {
  page.value = 1
  emit('size-change', size.value)
  emit('current-change', 1)
}

const pages = computed(() => {
  const t = totalPages.value
  const c = page.value
  const out = []
  const push = (v, label = v, ellipsis = false) => out.push({ key: ellipsis ? `e${v}` : v, value: v, label, ellipsis })
  if (t <= 7) {
    for (let i = 1; i <= t; i++) push(i)
  } else {
    push(1)
    if (c > 3) push(c, '…', true)
    const start = Math.max(2, c - 1)
    const end = Math.min(t - 1, c + 1)
    for (let i = start; i <= end; i++) push(i)
    if (c < t - 2) push(c + 999, '…', true)
    push(t)
  }
  return out
})
</script>

<style scoped>
.ui-pagination {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  color: var(--ink);
}
.ui-pagination-total {
  font-size: var(--text-xs);
  color: var(--ink-muted);
  margin-right: var(--space-3);
}
.ui-pagination-size {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-right: var(--space-3);
}
.ui-pagination-label {
  font-size: var(--text-xs);
  color: var(--ink-muted);
}
.ui-pagination-select {
  font-family: var(--font-body);
  font-size: var(--text-xs);
  background: var(--surface);
  border: 1px solid var(--hairline);
  padding: 4px 8px;
  color: var(--ink);
}

.ui-page-btn {
  min-width: 28px;
  height: 28px;
  padding: 0 8px;
  background: transparent;
  border: 1px solid transparent;
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--ink);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
}
.ui-page-btn:hover:not(:disabled):not(.ellipsis) {
  border-color: var(--hairline-strong);
}
.ui-page-btn.active {
  background: var(--ink);
  color: var(--ink-inverse);
}
.ui-page-btn:disabled {
  color: var(--ink-subtle);
  cursor: not-allowed;
}
.ui-page-btn.ellipsis {
  cursor: default;
  color: var(--ink-subtle);
}
</style>
