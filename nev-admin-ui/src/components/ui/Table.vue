<template>
  <div class="ui-table-wrap">
    <!-- collect column metadata via slot -->
    <div style="display:none"><slot /></div>

    <table class="ui-table" :class="{ stripe }">
      <thead>
        <tr>
          <th
            v-for="col in columns"
            :key="col.key"
            :style="thStyle(col)"
            class="ui-th"
          >
            <span class="ui-th-text">{{ col.label }}</span>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!data.length">
          <td :colspan="columns.length" class="ui-table-empty">{{ emptyText }}</td>
        </tr>
        <tr
          v-for="(row, ri) in data"
          :key="rowKey ? row[rowKey] : ri"
          class="ui-tr"
          :class="{ clickable: rowClickable }"
          @click="onRowClick(row, ri, $event)"
        >
          <td
            v-for="col in columns"
            :key="col.key"
            :style="tdStyle(col)"
            class="ui-td"
          >
            <component
              v-if="col.slotFn"
              :is="renderSlot(col.slotFn, row, ri)"
            />
            <template v-else>
              {{ formatCell(row, col) }}
            </template>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { provide, reactive, h } from 'vue'

const props = defineProps({
  data: { type: Array, default: () => [] },
  rowKey: String,
  rowClickable: Boolean,
  stripe: Boolean,
  emptyText: { type: String, default: '— 暂无数据 —' }
})
const emit = defineEmits(['row-click'])

const columns = reactive([])

function register(col) {
  if (!columns.find(c => c.key === col.key)) columns.push(col)
}
function unregister(key) {
  const i = columns.findIndex(c => c.key === key)
  if (i >= 0) columns.splice(i, 1)
}
provide('uiTable', { register, unregister })

function thStyle(col) {
  const s = {}
  if (col.width) s.width = typeof col.width === 'number' ? `${col.width}px` : col.width
  if (col.minWidth) s.minWidth = typeof col.minWidth === 'number' ? `${col.minWidth}px` : col.minWidth
  if (col.align) s.textAlign = col.align
  return s
}
function tdStyle(col) {
  const s = {}
  if (col.align) s.textAlign = col.align
  return s
}
function formatCell(row, col) {
  if (!col.prop) return ''
  const v = row[col.prop]
  return v == null ? '' : v
}
function renderSlot(slotFn, row, index) {
  return { render: () => slotFn({ row, index }) }
}
function onRowClick(row, index, e) {
  emit('row-click', row, index, e)
}
</script>

<style scoped>
.ui-table-wrap {
  overflow-x: auto;
  background: var(--surface);
}
.ui-table {
  width: 100%;
  border-collapse: collapse;
  font-family: var(--font-body);
  font-size: var(--text-sm);
  color: var(--ink);
}
.ui-th {
  text-align: left;
  padding: var(--space-3) var(--space-4);
  font-weight: var(--weight-semibold);
  font-size: var(--text-xs);
  letter-spacing: var(--tracking-wider);
  text-transform: uppercase;
  color: var(--ink-muted);
  border-bottom: 1px solid var(--ink);
  background: var(--bg);
  white-space: nowrap;
}
.ui-td {
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--hairline);
  vertical-align: middle;
}
.ui-tr.clickable { cursor: pointer; }
.ui-tr:hover .ui-td { background: var(--surface-hover); }
.ui-table.stripe tbody tr:nth-child(even) .ui-td { background: rgba(0,0,0,0.012); }
.ui-table.stripe tbody tr:nth-child(even):hover .ui-td { background: var(--surface-hover); }
.ui-table-empty {
  text-align: center;
  padding: var(--space-12) var(--space-6);
  color: var(--ink-subtle);
  font-size: var(--text-sm);
  font-family: var(--font-display);
  font-style: italic;
}
</style>
