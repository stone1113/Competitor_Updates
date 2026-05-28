<template>
  <div class="ui-divider" :class="`d-${direction}`" role="separator">
    <span v-if="$slots.default" class="ui-divider-text">
      <slot />
    </span>
  </div>
</template>

<script setup>
defineProps({
  direction: { type: String, default: 'horizontal' } // horizontal | vertical
})
</script>

<style scoped>
.ui-divider.d-horizontal {
  display: flex;
  align-items: center;
  margin: var(--space-5) 0;
  gap: var(--space-3);
}
.ui-divider.d-horizontal::before,
.ui-divider.d-horizontal::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--hairline);
}
.ui-divider.d-vertical {
  display: inline-block;
  width: 1px;
  background: var(--hairline);
  height: 1em;
  vertical-align: middle;
  margin: 0 var(--space-3);
}
.ui-divider-text {
  font-family: var(--font-body);
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-wider);
  text-transform: uppercase;
  color: var(--ink-subtle);
}
</style>
