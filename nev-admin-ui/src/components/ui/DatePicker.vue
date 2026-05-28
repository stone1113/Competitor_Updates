<template>
  <div class="ui-dp-wrap" ref="wrap">
    <button type="button" class="ui-dp-input" :class="{ open, disabled }" :disabled="disabled" @click="toggle">
      <span v-if="model" class="ui-dp-value tabular">{{ model }}</span>
      <span v-else class="ui-dp-placeholder">{{ placeholder }}</span>
      <span class="ui-dp-icon">▾</span>
    </button>

    <Teleport to="body">
      <Transition name="ui-pop">
        <div v-if="open" class="ui-dp-pop" :style="popStyle" @click.stop>
          <header class="ui-dp-head">
            <button class="ui-dp-nav" @click="prev">‹</button>
            <span class="ui-dp-title display">{{ monthLabel(viewYear, viewMonth) }}</span>
            <button class="ui-dp-nav" @click="next">›</button>
          </header>
          <div class="ui-dp-week">
            <span v-for="w in WEEK_LABELS_CN" :key="w">{{ w }}</span>
          </div>
          <div class="ui-dp-grid">
            <button
              v-for="(c, i) in flatCells"
              :key="i"
              type="button"
              class="ui-dp-cell"
              :class="{ outside: !c.currentMonth, selected: isSelected(c.date), today: isToday(c.date) }"
              @click="pick(c.date)"
            >{{ c.date.getDate() }}</button>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { ymd, parseYmd, isSameDay, monthMatrix, WEEK_LABELS_CN, monthLabel } from './calendar-utils'

const model = defineModel({ type: String, default: '' }) // YYYY-MM-DD
const props = defineProps({
  placeholder: { type: String, default: '选择日期' },
  disabled: Boolean
})

const wrap = ref(null)
const open = ref(false)
const today = new Date()

const viewYear = ref(today.getFullYear())
const viewMonth = ref(today.getMonth())

const popStyle = reactive({ top: '0px', left: '0px' })

const flatCells = computed(() => monthMatrix(viewYear.value, viewMonth.value).flat())

watch(model, (v) => {
  const d = parseYmd(v)
  if (d) { viewYear.value = d.getFullYear(); viewMonth.value = d.getMonth() }
})

function toggle() {
  if (props.disabled) return
  if (open.value) { open.value = false; return }
  const d = parseYmd(model.value)
  if (d) { viewYear.value = d.getFullYear(); viewMonth.value = d.getMonth() }
  const r = wrap.value.getBoundingClientRect()
  popStyle.top = `${r.bottom + window.scrollY + 4}px`
  popStyle.left = `${r.left + window.scrollX}px`
  open.value = true
}
function prev() {
  if (viewMonth.value === 0) { viewMonth.value = 11; viewYear.value-- }
  else viewMonth.value--
}
function next() {
  if (viewMonth.value === 11) { viewMonth.value = 0; viewYear.value++ }
  else viewMonth.value++
}
function pick(d) { model.value = ymd(d); open.value = false }
function isSelected(d) { return isSameDay(d, parseYmd(model.value)) }
function isToday(d) { return isSameDay(d, today) }

function onDoc(e) {
  if (!open.value) return
  if (wrap.value && !wrap.value.contains(e.target) && !e.target.closest('.ui-dp-pop')) open.value = false
}
onMounted(() => document.addEventListener('click', onDoc))
onBeforeUnmount(() => document.removeEventListener('click', onDoc))
</script>

<style scoped>
.ui-dp-wrap { display: inline-block; width: 100%; position: relative; }
.ui-dp-input {
  width: 100%;
  background: var(--surface);
  border: 1px solid var(--hairline);
  padding: 6px 10px;
  min-height: 34px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  cursor: pointer;
  font-family: var(--font-body);
  font-size: var(--text-sm);
  color: var(--ink);
  text-align: left;
}
.ui-dp-input.open { border-color: var(--ink); }
.ui-dp-input.disabled { background: var(--surface-sunk); cursor: not-allowed; }
.ui-dp-value { font-family: var(--font-mono); font-size: var(--text-xs); }
.ui-dp-placeholder { color: var(--ink-subtle); }
.ui-dp-icon { color: var(--ink-subtle); font-size: 10px; }

.ui-dp-pop {
  position: absolute;
  z-index: var(--z-dropdown);
  background: var(--surface);
  border: 1px solid var(--hairline);
  box-shadow: var(--shadow-md);
  padding: var(--space-3);
  width: 260px;
}
.ui-dp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-3);
}
.ui-dp-title {
  font-size: var(--text-md);
  font-weight: 500;
  letter-spacing: var(--tracking-tight);
}
.ui-dp-nav {
  background: transparent;
  border: none;
  font-size: 18px;
  cursor: pointer;
  color: var(--ink-muted);
  padding: 0 8px;
}
.ui-dp-nav:hover { color: var(--ink); }
.ui-dp-week {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  text-align: center;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: var(--tracking-wider);
  color: var(--ink-subtle);
  text-transform: uppercase;
  margin-bottom: var(--space-2);
}
.ui-dp-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 2px;
}
.ui-dp-cell {
  aspect-ratio: 1;
  background: transparent;
  border: none;
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--ink);
  cursor: pointer;
  transition: background var(--duration-fast);
}
.ui-dp-cell:hover { background: var(--surface-hover); }
.ui-dp-cell.outside { color: var(--ink-subtle); }
.ui-dp-cell.today {
  font-weight: var(--weight-semibold);
  color: var(--accent);
}
.ui-dp-cell.selected {
  background: var(--ink);
  color: var(--ink-inverse);
}
.ui-dp-cell.selected.today { background: var(--accent); }

.ui-pop-enter-active, .ui-pop-leave-active {
  transition: opacity var(--duration-fast) var(--ease-out), transform var(--duration-fast) var(--ease-out);
}
.ui-pop-enter-from, .ui-pop-leave-to { opacity: 0; transform: translateY(-4px); }
</style>
