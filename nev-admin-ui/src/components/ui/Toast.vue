<template>
  <Teleport to="body">
    <ul class="ui-toast-stack" aria-live="polite">
      <TransitionGroup name="toast">
        <li
          v-for="t in toasts"
          :key="t.id"
          class="ui-toast"
          :class="`v-${t.type}`"
        >
          <span class="ui-toast-bar" aria-hidden="true"></span>
          <div class="ui-toast-body">
            <span class="eyebrow ui-toast-kicker">{{ kickerOf(t.type) }}</span>
            <p class="ui-toast-msg">{{ t.message }}</p>
          </div>
          <button class="ui-toast-close" @click="dismiss(t.id)" aria-label="关闭">×</button>
        </li>
      </TransitionGroup>
    </ul>
  </Teleport>
</template>

<script setup>
import { toasts, dismiss } from './toast-store'

function kickerOf(type) {
  return ({ success: 'OK', error: 'ERROR', warning: 'WARN', info: 'INFO' }[type] || 'INFO')
}
</script>

<style scoped>
.ui-toast-stack {
  position: fixed;
  top: var(--space-6);
  right: var(--space-6);
  z-index: var(--z-toast);
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  pointer-events: none;
  max-width: 360px;
}
.ui-toast {
  background: var(--surface);
  border: 1px solid var(--hairline);
  display: flex;
  align-items: stretch;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  pointer-events: auto;
  box-shadow: var(--shadow-md);
}
.ui-toast-bar {
  width: 2px;
  background: var(--ink);
  flex-shrink: 0;
}
.v-success .ui-toast-bar { background: var(--positive); }
.v-error .ui-toast-bar { background: var(--accent); }
.v-warning .ui-toast-bar { background: var(--alert); }

.ui-toast-body { flex: 1; }
.ui-toast-kicker {
  display: block;
  margin-bottom: 2px;
}
.v-success .ui-toast-kicker { color: var(--positive); }
.v-error .ui-toast-kicker { color: var(--accent); }
.v-warning .ui-toast-kicker { color: var(--alert); }

.ui-toast-msg {
  font-size: var(--text-sm);
  color: var(--ink);
  line-height: var(--leading-snug);
}
.ui-toast-close {
  background: transparent;
  border: none;
  font-size: 16px;
  color: var(--ink-subtle);
  cursor: pointer;
  align-self: flex-start;
  padding: 0 4px;
  line-height: 1;
}
.ui-toast-close:hover { color: var(--ink); }

.toast-enter-active, .toast-leave-active {
  transition: all var(--duration-base) var(--ease-out);
}
.toast-enter-from { opacity: 0; transform: translateX(20px); }
.toast-leave-to   { opacity: 0; transform: translateX(20px); }
</style>
