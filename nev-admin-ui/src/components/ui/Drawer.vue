<template>
  <Teleport to="body">
    <Transition name="drawer-fade">
      <div v-if="model" class="ui-drawer-mask" @click.self="onMaskClick">
        <Transition name="drawer-slide" appear>
          <aside
            v-if="model"
            class="ui-drawer"
            :class="`d-${direction}`"
            :style="sizeStyle"
            role="dialog"
            aria-modal="true"
          >
            <header class="ui-drawer-head" v-if="title || $slots.header">
              <slot name="header">
                <span class="eyebrow">{{ eyebrow }}</span>
                <h3 class="ui-drawer-title display">{{ title }}</h3>
              </slot>
              <button class="ui-drawer-close" @click="close" aria-label="关闭">×</button>
            </header>
            <div class="ui-drawer-body">
              <slot />
            </div>
            <footer v-if="$slots.footer" class="ui-drawer-foot">
              <slot name="footer" />
            </footer>
          </aside>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed, watch } from 'vue'

const model = defineModel({ type: Boolean, default: false })
const props = defineProps({
  title: String,
  eyebrow: String,
  direction: { type: String, default: 'rtl' }, // rtl | ltr
  size: { type: String, default: '50%' },
  maskClosable: { type: Boolean, default: true }
})

const sizeStyle = computed(() => ({ width: props.size }))

watch(model, (open) => {
  if (open) document.body.style.overflow = 'hidden'
  else document.body.style.overflow = ''
})

function close() { model.value = false }
function onMaskClick() { if (props.maskClosable) close() }
</script>

<style scoped>
.ui-drawer-mask {
  position: fixed;
  inset: 0;
  background: rgba(23,22,20,0.35);
  z-index: var(--z-drawer);
  display: flex;
  justify-content: flex-end;
}
.ui-drawer {
  background: var(--surface);
  height: 100vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-lg);
  max-width: 100vw;
}
.d-ltr { margin-right: auto; }
.d-rtl { margin-left: auto; }

.ui-drawer-head {
  padding: var(--space-6) var(--space-8) var(--space-4);
  border-bottom: 1px solid var(--hairline);
  position: relative;
}
.ui-drawer-title {
  font-size: var(--text-xl);
  font-weight: 500;
  margin-top: var(--space-2);
  letter-spacing: var(--tracking-tight);
}
.ui-drawer-close {
  position: absolute;
  top: var(--space-4);
  right: var(--space-5);
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  font-size: 20px;
  color: var(--ink-muted);
  cursor: pointer;
  line-height: 1;
}
.ui-drawer-close:hover { color: var(--ink); }
.ui-drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-6) var(--space-8);
}
.ui-drawer-foot {
  padding: var(--space-4) var(--space-8);
  border-top: 1px solid var(--hairline);
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
}

/* Transitions */
.drawer-fade-enter-active, .drawer-fade-leave-active {
  transition: opacity var(--duration-base) var(--ease-out);
}
.drawer-fade-enter-from, .drawer-fade-leave-to { opacity: 0; }

.drawer-slide-enter-active, .drawer-slide-leave-active {
  transition: transform var(--duration-slow) var(--ease-out);
}
.d-rtl.drawer-slide-enter-from, .d-rtl.drawer-slide-leave-to { transform: translateX(100%); }
.d-ltr.drawer-slide-enter-from, .d-ltr.drawer-slide-leave-to { transform: translateX(-100%); }
</style>
