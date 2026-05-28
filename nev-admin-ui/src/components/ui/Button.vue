<template>
  <button
    :type="nativeType"
    :disabled="disabled || loading"
    class="ui-btn"
    :class="[`v-${variant}`, `s-${size}`, { loading, disabled }]"
    @click="onClick"
  >
    <span v-if="loading" class="ui-btn-spinner" aria-hidden="true"></span>
    <slot />
  </button>
</template>

<script setup>
const props = defineProps({
  variant: { type: String, default: 'primary' }, // primary | ghost | link | danger | quiet
  size: { type: String, default: 'md' },          // sm | md | lg
  nativeType: { type: String, default: 'button' },
  disabled: Boolean,
  loading: Boolean
})
const emit = defineEmits(['click'])
function onClick(e) {
  if (props.disabled || props.loading) return
  emit('click', e)
}
</script>

<style scoped>
.ui-btn {
  font-family: var(--font-body);
  font-weight: var(--weight-medium);
  letter-spacing: var(--tracking-normal);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  border: 1px solid transparent;
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-out),
              color var(--duration-fast) var(--ease-out),
              border-color var(--duration-fast) var(--ease-out);
  white-space: nowrap;
  user-select: none;
}
.ui-btn:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

/* sizes */
.s-sm { font-size: var(--text-xs); padding: 5px 10px; }
.s-md { font-size: var(--text-sm); padding: 7px 16px; }
.s-lg { font-size: var(--text-base); padding: 10px 22px; }

/* primary — filled ink */
.v-primary {
  background: var(--ink);
  color: var(--ink-inverse);
}
.v-primary:hover { background: #2a2925; }

/* ghost — outlined */
.v-ghost {
  background: transparent;
  color: var(--ink);
  border-color: var(--ink);
}
.v-ghost:hover { background: var(--ink); color: var(--ink-inverse); }

/* danger — accent */
.v-danger {
  background: var(--accent);
  color: var(--ink-inverse);
}
.v-danger:hover { background: var(--accent-hover); }

/* quiet — borderless */
.v-quiet {
  background: transparent;
  color: var(--ink-muted);
}
.v-quiet:hover { color: var(--ink); background: rgba(0,0,0,0.04); }

/* link — text-only with underline */
.v-link {
  background: transparent;
  color: var(--ink);
  padding: 2px 0;
  text-decoration: underline;
  text-decoration-color: var(--accent);
  text-underline-offset: 4px;
  text-decoration-thickness: 1px;
}
.v-link:hover { color: var(--accent); }

.disabled, .ui-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.ui-btn-spinner {
  width: 12px;
  height: 12px;
  border: 1.5px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: ui-spin 0.7s linear infinite;
}
@keyframes ui-spin { to { transform: rotate(360deg); } }
</style>
