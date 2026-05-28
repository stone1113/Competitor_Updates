<template>
  <div class="ui-input" :class="[{ disabled, focused, 'is-textarea': type === 'textarea' }, `s-${size}`]">
    <textarea
      v-if="type === 'textarea'"
      :value="model"
      :placeholder="placeholder"
      :disabled="disabled"
      :rows="rows"
      class="ui-input-control"
      @input="onInput"
      @focus="focused = true"
      @blur="focused = false"
    />
    <input
      v-else
      :type="type"
      :value="model"
      :placeholder="placeholder"
      :disabled="disabled"
      class="ui-input-control"
      @input="onInput"
      @focus="focused = true"
      @blur="focused = false"
    />
    <button
      v-if="clearable && model && !disabled"
      class="ui-input-clear"
      type="button"
      @click="clear"
      aria-label="清空"
    >×</button>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const model = defineModel({ type: [String, Number], default: '' })
const props = defineProps({
  type: { type: String, default: 'text' }, // text | password | textarea | number
  placeholder: String,
  disabled: Boolean,
  clearable: Boolean,
  rows: { type: Number, default: 3 },
  size: { type: String, default: 'md' } // sm | md | lg
})

const focused = ref(false)
function onInput(e) { model.value = e.target.value }
function clear() { model.value = '' }
</script>

<style scoped>
.ui-input {
  display: inline-flex;
  align-items: center;
  background: var(--surface);
  border: 1px solid var(--hairline);
  transition: border-color var(--duration-fast) var(--ease-out);
  width: 100%;
  position: relative;
}
.ui-input.focused { border-color: var(--ink); }
.ui-input.disabled { background: var(--surface-sunk); }

.s-sm .ui-input-control { padding: 5px 10px; font-size: var(--text-xs); }
.s-md .ui-input-control { padding: 8px 12px; font-size: var(--text-sm); }
.s-lg .ui-input-control { padding: 11px 14px; font-size: var(--text-base); }

.ui-input-control {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: var(--ink);
  font-family: var(--font-body);
  width: 100%;
  min-width: 0;
}
.ui-input-control::placeholder { color: var(--ink-subtle); }

.is-textarea .ui-input-control {
  resize: vertical;
  line-height: var(--leading-normal);
}

.ui-input-clear {
  border: none;
  background: transparent;
  color: var(--ink-subtle);
  cursor: pointer;
  font-size: 16px;
  padding: 0 8px;
  line-height: 1;
}
.ui-input-clear:hover { color: var(--ink); }
</style>
