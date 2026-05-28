<template>
  <section class="ui-card" :class="{ flush, 'has-rule': rule }">
    <header v-if="$slots.header || title" class="ui-card-head">
      <slot name="header">
        <span class="eyebrow" v-if="eyebrow">{{ eyebrow }}</span>
        <h3 class="ui-card-title display" v-if="title">{{ title }}</h3>
      </slot>
      <span v-if="rule" class="accent-rule"></span>
    </header>
    <div class="ui-card-body">
      <slot />
    </div>
    <footer v-if="$slots.footer" class="ui-card-foot">
      <slot name="footer" />
    </footer>
  </section>
</template>

<script setup>
defineProps({
  title: String,
  eyebrow: String,
  flush: Boolean,   // remove padding (for tables that fill the card)
  rule: Boolean     // show accent-rule under header
})
</script>

<style scoped>
.ui-card {
  background: var(--surface);
  border: 1px solid var(--hairline);
}
.ui-card-head {
  padding: var(--space-5) var(--space-6) var(--space-3);
  border-bottom: 1px solid var(--hairline);
}
.ui-card-title {
  font-size: var(--text-lg);
  font-weight: 500;
  margin-top: var(--space-2);
  letter-spacing: var(--tracking-tight);
}
.ui-card.has-rule .ui-card-head { border-bottom: none; padding-bottom: 0; }
.ui-card-body {
  padding: var(--space-6);
}
.ui-card.flush .ui-card-body {
  padding: 0;
}
.ui-card-foot {
  padding: var(--space-4) var(--space-6);
  border-top: 1px solid var(--hairline);
}
</style>
