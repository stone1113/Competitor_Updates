<template>
  <div class="ui-field" :class="{ 'is-error': error, inline }">
    <label class="ui-field-label" v-if="label">
      <span class="ui-field-label-text">{{ label }}</span>
      <span v-if="required" class="ui-field-required" aria-hidden="true">*</span>
    </label>
    <div class="ui-field-control">
      <slot />
      <div v-if="error" class="ui-field-error">{{ error }}</div>
      <div v-else-if="hint" class="ui-field-hint">{{ hint }}</div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  label: String,
  hint: String,
  error: String,
  required: Boolean,
  inline: Boolean
})
</script>

<style scoped>
.ui-field {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}
.ui-field.inline {
  flex-direction: row;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: 0;
}
.ui-field-label {
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-wider);
  text-transform: uppercase;
  color: var(--ink-muted);
  display: flex;
  align-items: center;
  gap: 4px;
}
.ui-field.inline .ui-field-label {
  text-transform: none;
  letter-spacing: 0;
  font-size: var(--text-sm);
  color: var(--ink);
  font-weight: var(--weight-regular);
}
.ui-field-required { color: var(--accent); }
.ui-field-control { display: flex; flex-direction: column; gap: 4px; }
.ui-field-hint { font-size: var(--text-xs); color: var(--ink-subtle); }
.ui-field-error { font-size: var(--text-xs); color: var(--accent); }
</style>
