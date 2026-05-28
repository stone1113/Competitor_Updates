<template>
  <div class="ui-dr-wrap" ref="wrap">
    <button type="button" class="ui-dr-input" :class="{ open, disabled }" :disabled="disabled" @click="toggle">
      <span v-if="hasValue" class="ui-dr-value tabular">{{ model[0] }} → {{ model[1] }}</span>
      <span v-else class="ui-dr-placeholder">{{ placeholder }}</span>
      <span v-if="clearable && hasValue" class="ui-dr-clear" @click.stop="clear">×</span>
      <span v-else class="ui-dr-icon">▾</span>
    </button>

    <Teleport to="body">
      <Transition name="ui-pop">
        <div v-if="open" class="ui-dr-pop" :style="popStyle" @click.stop>
          <div class="ui-dr-cal">
            <header class="ui-dr-head">
              <button class="ui-dp-nav" @click="prev">‹</button>
              <span class="ui-dr-title display">{{ monthLabel(viewYear, viewMonth) }}</span>
              <button class="ui-dp-nav" @click="next">›</button>
            </header>
            <div class="ui-dp-week">
              <span v-for="w in WEEK_LABELS_CN" :key="w">{{ w }}</span>
            </div>
            <div class="ui-dp-grid">
              <button
                v-for="(c, i) in cells"
                :key="i"
                type="button"
                class="ui-dp-cell"
                :class="cellClass(c)"
                @click="pick(c.date)"
                @mouseenter="hover = c.date"
              >{{ c.date.getDate() }}</button>
            </div>
          </div>
          <footer class="ui-dr-foot">
            <span class="eyebrow">{{ statusText }}</span>
            <button class="ui-dr-cancel" @click="cancel">取消</button>
          </footer>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { ymd, parseYmd, isSameDay, monthMatrix, WEEK_LABELS_CN, monthLabel } from './calendar-utils'

const model = defineModel({ type: Array, default: () => [] }) // ['YYYY-MM-DD','YYYY-MM-DD']
const props = defineProps({
  placeholder: { type: String, default: '选择日期范围' },
  disabled: Boolean,
  clearable: { type: Boolean, default: true }
})

const wrap = ref(null)
const open = ref(false)
const today = new Date()

const viewYear = ref(today.getFullYear())
const viewMonth = ref(today.getMonth())

const popStyle = reactive({ top: '0px', left: '0px' })

const cells = computed(() => monthMatrix(viewYear.value, viewMonth.value).flat())
const hover = ref(null)

const start = ref(null) // Date
const end = ref(null)   // Date

const hasValue = computed(() => Array.isArray(model.value) && model.value.length === 2 && model.value[0] && model.value[1])

watch(model, (v) => {
  if (Array.isArray(v) && v.length === 2) {
    start.value = parseYmd(v[0])
    end.value = parseYmd(v[1])
  } else {
    start.value = null
    end.value = null
  }
}, { immediate: true })

function toggle() {
  if (props.disabled) return
  if (open.value) { open.value = false; return }
  if (start.value) { viewYear.value = start.value.getFullYear(); viewMonth.value = start.value.getMonth() }
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
function pick(d) {
  if (!start.value || (start.value && end.value)) {
    start.value = d
    end.value = null
  } else {
    if (d.getTime() < start.value.getTime()) {
      end.value = start.value
      start.value = d
    } else {
      end.value = d
    }
    model.value = [ymd(start.value), ymd(end.value)]
    open.value = false
  }
}
function cancel() { open.value = false }
function clear() { model.value = []; start.value = null; end.value = null }

const statusText = computed(() => {
  if (start.value && !end.value) return `已选 ${ymd(start.value)} — 选择终点`
  if (start.value && end.value) return `${ymd(start.value)} → ${ymd(end.value)}`
  return '选择起点'
})

function cellClass(c) {
  const d = c.date
  const sel = isSameDay(d, start.value) || isSameDay(d, end.value)
  let inSel = false, inHover = false
  if (start.value && end.value) {
    const t = d.getTime()
    if (t > start.value.getTime() && t < end.value.getTime()) inSel = true
  } else if (start.value && hover.value) {
    const a = Math.min(start.value.getTime(), hover.value.getTime())
    const b = Math.max(start.value.getTime(), hover.value.getTime())
    const t = d.getTime()
    if (t > a && t < b) inHover = true
  }
  return {
    outside: !c.currentMonth,
    selected: sel,
    'in-range': inSel,
    'in-hover': inHover,
    today: isSameDay(d, today)
  }
}

function onDoc(e) {
  if (!open.value) return
  if (wrap.value && !wrap.value.contains(e.target) && !e.target.closest('.ui-dr-pop')) open.value = false
}
onMounted(() => document.addEventListener('click', onDoc))
onBeforeUnmount(() => document.removeEventListener('click', onDoc))
</script>

<style scoped>
.ui-dr-wrap { display: inline-block; width: 100%; position: relative; }
.ui-dr-input {
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
.ui-dr-input.open { border-color: var(--ink); }
.ui-dr-input.disabled { background: var(--surface-sunk); cursor: not-allowed; }
.ui-dr-value { font-family: var(--font-mono); font-size: var(--text-xs); }
.ui-dr-placeholder { color: var(--ink-subtle); }
.ui-dr-clear, .ui-dr-icon { color: var(--ink-subtle); font-size: 10px; }
.ui-dr-clear { font-size: 14px; cursor: pointer; }
.ui-dr-clear:hover { color: var(--ink); }

.ui-dr-pop {
  position: absolute;
  z-index: var(--z-dropdown);
  background: var(--surface);
  border: 1px solid var(--hairline);
  box-shadow: var(--shadow-md);
  width: 280px;
}
.ui-dr-cal { padding: var(--space-3); }
.ui-dr-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-3);
}
.ui-dr-title {
  font-size: var(--text-md);
  font-weight: 500;
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
.ui-dp-cell.today { font-weight: var(--weight-semibold); color: var(--accent); }
.ui-dp-cell.in-range, .ui-dp-cell.in-hover {
  background: var(--accent-soft);
  color: var(--accent);
}
.ui-dp-cell.selected {
  background: var(--ink);
  color: var(--ink-inverse);
}

.ui-dr-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-2) var(--space-3);
  border-top: 1px solid var(--hairline);
  background: var(--bg-elevated);
}
.ui-dr-cancel {
  background: none; border: none; cursor: pointer;
  font-size: var(--text-xs); color: var(--ink-muted);
}
.ui-dr-cancel:hover { color: var(--ink); }

.ui-pop-enter-active, .ui-pop-leave-active {
  transition: opacity var(--duration-fast) var(--ease-out), transform var(--duration-fast) var(--ease-out);
}
.ui-pop-enter-from, .ui-pop-leave-to { opacity: 0; transform: translateY(-4px); }
</style>
