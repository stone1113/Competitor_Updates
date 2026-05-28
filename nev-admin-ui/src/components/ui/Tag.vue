<template>
  <span class="ui-tag" :class="`v-${variant}`">
    <slot />
  </span>
</template>

<script setup>
defineProps({
  variant: { type: String, default: 'default' } // default | accent | positive | alert | neutral | ghost
})
</script>

<style scoped>
.ui-tag {
  display: inline-flex;
  align-items: center;
  font-family: var(--font-body);
  font-size: var(--text-xs);
  font-weight: var(--weight-medium);
  padding: 2px 8px;
  letter-spacing: 0.02em;
  line-height: 1.5;
  white-space: nowrap;
}
.v-default {
  background: var(--surface-sunk);
  color: var(--ink);
}
.v-accent {
  background: var(--accent-soft);
  color: var(--accent);
}
.v-positive {
  background: var(--positive-soft);
  color: var(--positive);
}
.v-alert {
  background: var(--alert-soft);
  color: var(--alert);
}
.v-neutral {
  background: var(--neutral-soft);
  color: var(--neutral);
}
.v-ghost {
  background: transparent;
  color: var(--ink-muted);
  border: 1px solid var(--hairline);
}
</style>
