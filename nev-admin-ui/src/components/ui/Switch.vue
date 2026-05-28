<template>
  <button
    type="button"
    role="switch"
    :aria-checked="model"
    class="ui-switch"
    :class="{ on: model, disabled }"
    :disabled="disabled"
    @click="toggle"
  >
    <span class="ui-switch-track"></span>
    <span class="ui-switch-thumb"></span>
  </button>
</template>

<script setup>
const model = defineModel({ type: Boolean, default: false })
const props = defineProps({
  disabled: Boolean
})
const emit = defineEmits(['change'])
function toggle() {
  if (props.disabled) return
  model.value = !model.value
  emit('change', model.value)
}
</script>

<style scoped>
.ui-switch {
  position: relative;
  width: 36px;
  height: 20px;
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  display: inline-block;
}
.ui-switch.disabled { opacity: 0.4; cursor: not-allowed; }

.ui-switch-track {
  position: absolute;
  inset: 0;
  background: var(--hairline-strong);
  transition: background var(--duration-base) var(--ease-out);
}
.ui-switch.on .ui-switch-track { background: var(--ink); }

.ui-switch-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  background: var(--surface);
  transition: transform var(--duration-base) var(--ease-out);
  box-shadow: 0 1px 2px rgba(0,0,0,0.15);
}
.ui-switch.on .ui-switch-thumb { transform: translateX(16px); }

.ui-switch:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
</style>
