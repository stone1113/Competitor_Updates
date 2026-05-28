<template>
  <Teleport to="body">
    <Transition name="dialog-fade">
      <div v-if="model" class="ui-dialog-mask" @click.self="onMaskClick">
        <Transition name="dialog-zoom" appear>
          <div
            v-if="model"
            class="ui-dialog"
            :style="{ width }"
            role="dialog"
            aria-modal="true"
          >
            <header class="ui-dialog-head" v-if="title || $slots.header">
              <slot name="header">
                <h3 class="ui-dialog-title display">{{ title }}</h3>
              </slot>
              <button class="ui-dialog-close" @click="close" aria-label="关闭">×</button>
            </header>
            <div class="ui-dialog-body">
              <slot />
            </div>
            <footer v-if="$slots.footer" class="ui-dialog-foot">
              <slot name="footer" />
            </footer>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { watch } from 'vue'

const model = defineModel({ type: Boolean, default: false })
const props = defineProps({
  title: String,
  width: { type: String, default: '520px' },
  maskClosable: { type: Boolean, default: true }
})

watch(model, (open) => {
  if (open) document.body.style.overflow = 'hidden'
  else document.body.style.overflow = ''
})

function close() { model.value = false }
function onMaskClick() { if (props.maskClosable) close() }
</script>

<style scoped>
.ui-dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(23,22,20,0.45);
  z-index: var(--z-dialog);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-6);
}
.ui-dialog {
  background: var(--surface);
  max-height: calc(100vh - var(--space-12));
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-lg);
  max-width: calc(100vw - var(--space-12));
}
.ui-dialog-head {
  padding: var(--space-5) var(--space-6) var(--space-4);
  border-bottom: 1px solid var(--hairline);
  position: relative;
}
.ui-dialog-title {
  font-size: var(--text-lg);
  font-weight: 500;
  letter-spacing: var(--tracking-tight);
  padding-right: var(--space-6);
}
.ui-dialog-close {
  position: absolute;
  top: var(--space-3);
  right: var(--space-4);
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  font-size: 20px;
  color: var(--ink-muted);
  cursor: pointer;
  line-height: 1;
}
.ui-dialog-close:hover { color: var(--ink); }
.ui-dialog-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-6);
}
.ui-dialog-foot {
  padding: var(--space-4) var(--space-6);
  border-top: 1px solid var(--hairline);
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
}

.dialog-fade-enter-active, .dialog-fade-leave-active {
  transition: opacity var(--duration-base) var(--ease-out);
}
.dialog-fade-enter-from, .dialog-fade-leave-to { opacity: 0; }

.dialog-zoom-enter-active, .dialog-zoom-leave-active {
  transition: opacity var(--duration-base) var(--ease-out),
              transform var(--duration-base) var(--ease-out);
}
.dialog-zoom-enter-from, .dialog-zoom-leave-to {
  opacity: 0;
  transform: scale(0.96) translateY(8px);
}
</style>
