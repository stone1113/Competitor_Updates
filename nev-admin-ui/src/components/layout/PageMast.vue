<template>
  <header class="pm">
    <div class="pm-row">
      <h1 class="display pm-title">{{ title }}</h1>
      <div class="pm-controls" v-if="$slots.controls">
        <slot name="controls" />
      </div>
    </div>
    <div class="pm-rule"></div>
  </header>
</template>

<script setup>
defineProps({
  title: { type: String, required: true }
})
</script>

<style scoped>
.pm {
  margin-bottom: var(--space-6);
}
.pm-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-6);
  flex-wrap: wrap;
  padding-bottom: var(--space-3);
}
.pm-title {
  font-family: var(--font-display);
  font-weight: 500;
  font-size: var(--text-xl);
  letter-spacing: var(--tracking-tight);
  line-height: 1.2;
}
.pm-controls {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  flex-wrap: wrap;
}
.pm-rule {
  height: 1px;
  background: var(--ink);
}
</style>
