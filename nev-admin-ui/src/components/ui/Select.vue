<template>
  <div class="ui-select-wrap" ref="wrap">
    <button
      type="button"
      class="ui-select"
      :class="{ open, disabled, multiple }"
      :disabled="disabled"
      @click="toggle"
    >
      <span class="ui-select-display">
        <template v-if="multiple && Array.isArray(model) && model.length">
          <span v-for="v in model" :key="v" class="ui-select-tag">
            {{ labelOf(v) }}
            <span class="ui-select-tag-x" @click.stop="removeOne(v)">×</span>
          </span>
        </template>
        <template v-else-if="!multiple && model !== '' && model !== null && model !== undefined">
          <span class="ui-select-text">{{ labelOf(model) }}</span>
        </template>
        <span v-else class="ui-select-placeholder">{{ placeholder }}</span>
      </span>
      <span class="ui-select-actions">
        <span
          v-if="clearable && hasValue"
          class="ui-select-clear"
          @click.stop="clear"
        >×</span>
        <span class="ui-select-arrow" :class="{ open }">▾</span>
      </span>
    </button>

    <Teleport to="body">
      <Transition name="ui-pop">
        <div
          v-if="open"
          class="ui-select-pop"
          :style="popStyle"
          @click.stop
        >
          <ul class="ui-select-list" role="listbox">
            <li
              v-for="opt in options"
              :key="opt.value"
              class="ui-select-opt"
              :class="{ active: isSelected(opt.value) }"
              @click="pick(opt.value)"
            >
              <span class="ui-select-mark" v-if="multiple">{{ isSelected(opt.value) ? '■' : '□' }}</span>
              <span>{{ opt.label }}</span>
            </li>
            <li v-if="!options.length" class="ui-select-empty">无选项</li>
          </ul>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted, onBeforeUnmount } from 'vue'

const model = defineModel({ default: '' })
const props = defineProps({
  options: { type: Array, default: () => [] },     // [{ label, value }]
  placeholder: { type: String, default: '请选择' },
  multiple: Boolean,
  clearable: Boolean,
  disabled: Boolean
})

const wrap = ref(null)
const open = ref(false)
const popStyle = reactive({ top: '0px', left: '0px', width: '0px' })

const hasValue = computed(() => {
  if (props.multiple) return Array.isArray(model.value) && model.value.length > 0
  return model.value !== '' && model.value !== null && model.value !== undefined
})

function labelOf(v) {
  const o = props.options.find(o => o.value === v)
  return o ? o.label : v
}

function toggle() {
  if (props.disabled) return
  if (open.value) { open.value = false; return }
  positionPop()
  open.value = true
}
function positionPop() {
  const r = wrap.value.getBoundingClientRect()
  popStyle.top = `${r.bottom + window.scrollY + 4}px`
  popStyle.left = `${r.left + window.scrollX}px`
  popStyle.width = `${r.width}px`
}
function isSelected(v) {
  if (props.multiple) return Array.isArray(model.value) && model.value.includes(v)
  return model.value === v
}
function pick(v) {
  if (props.multiple) {
    const arr = Array.isArray(model.value) ? [...model.value] : []
    const i = arr.indexOf(v)
    if (i >= 0) arr.splice(i, 1); else arr.push(v)
    model.value = arr
  } else {
    model.value = v
    open.value = false
  }
}
function removeOne(v) {
  if (!props.multiple) return
  model.value = model.value.filter(x => x !== v)
}
function clear() {
  model.value = props.multiple ? [] : ''
}

function onDoc(e) {
  if (!open.value) return
  if (wrap.value && !wrap.value.contains(e.target) && !e.target.closest('.ui-select-pop')) {
    open.value = false
  }
}
onMounted(() => document.addEventListener('click', onDoc))
onBeforeUnmount(() => document.removeEventListener('click', onDoc))
</script>

<style scoped>
.ui-select-wrap { display: inline-block; width: 100%; position: relative; }
.ui-select {
  width: 100%;
  background: var(--surface);
  border: 1px solid var(--hairline);
  padding: 6px 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  cursor: pointer;
  font-family: var(--font-body);
  font-size: var(--text-sm);
  color: var(--ink);
  text-align: left;
  min-height: 34px;
}
.ui-select.open { border-color: var(--ink); }
.ui-select.disabled { background: var(--surface-sunk); cursor: not-allowed; }

.ui-select-display {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
  min-width: 0;
}
.ui-select-text { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ui-select-placeholder { color: var(--ink-subtle); }

.ui-select-tag {
  background: var(--surface-sunk);
  font-size: var(--text-xs);
  padding: 2px 6px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.ui-select-tag-x {
  cursor: pointer;
  color: var(--ink-subtle);
  line-height: 1;
}
.ui-select-tag-x:hover { color: var(--accent); }

.ui-select-actions {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}
.ui-select-clear {
  color: var(--ink-subtle);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  padding: 0 4px;
}
.ui-select-clear:hover { color: var(--ink); }
.ui-select-arrow {
  color: var(--ink-subtle);
  font-size: 10px;
  transition: transform var(--duration-base) var(--ease-out);
}
.ui-select-arrow.open { transform: rotate(180deg); }

.ui-select-pop {
  position: absolute;
  z-index: var(--z-dropdown);
  background: var(--surface);
  border: 1px solid var(--hairline);
  box-shadow: var(--shadow-md);
  max-height: 280px;
  overflow-y: auto;
}
.ui-select-list { list-style: none; margin: 0; padding: 4px 0; }
.ui-select-opt {
  padding: 8px 12px;
  font-size: var(--text-sm);
  color: var(--ink);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-family: var(--font-body);
}
.ui-select-opt:hover { background: var(--surface-hover); }
.ui-select-opt.active { color: var(--accent); font-weight: var(--weight-semibold); }
.ui-select-mark { color: var(--ink-subtle); font-size: 10px; }
.ui-select-opt.active .ui-select-mark { color: var(--accent); }
.ui-select-empty {
  padding: 8px 12px;
  font-size: var(--text-sm);
  color: var(--ink-subtle);
  text-align: center;
}

.ui-pop-enter-active, .ui-pop-leave-active {
  transition: opacity var(--duration-fast) var(--ease-out),
              transform var(--duration-fast) var(--ease-out);
}
.ui-pop-enter-from, .ui-pop-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
