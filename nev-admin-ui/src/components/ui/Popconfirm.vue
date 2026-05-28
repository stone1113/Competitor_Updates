<template>
  <span class="ui-popconfirm-wrap" ref="wrap">
    <span class="ui-popconfirm-trigger" @click.stop="toggle">
      <slot />
    </span>
    <Teleport to="body">
      <Transition name="pop-fade">
        <div v-if="open" class="ui-popconfirm" :style="popStyle" @click.stop>
          <p class="ui-popconfirm-msg">{{ message }}</p>
          <div class="ui-popconfirm-actions">
            <button class="ui-pc-btn quiet" @click="cancel">{{ cancelText }}</button>
            <button class="ui-pc-btn danger" @click="confirm">{{ okText }}</button>
          </div>
          <span class="ui-popconfirm-arrow" :style="arrowStyle"></span>
        </div>
      </Transition>
    </Teleport>
  </span>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  message: { type: String, default: '确认操作？' },
  okText: { type: String, default: '确认' },
  cancelText: { type: String, default: '取消' }
})
const emit = defineEmits(['confirm', 'cancel'])

const wrap = ref(null)
const open = ref(false)
const popStyle = reactive({ top: '0px', left: '0px' })
const arrowStyle = reactive({ left: '16px' })

const POP_WIDTH = 240   // min-width 220 + 左右 padding
const MARGIN = 8

function toggle() {
  if (open.value) { open.value = false; return }
  const rect = wrap.value.getBoundingClientRect()
  const viewportRight = window.scrollX + window.innerWidth - MARGIN
  const triggerLeft = rect.left + window.scrollX
  // 默认左对齐；若溢出右边则右移使其贴右边
  let left = Math.min(triggerLeft, viewportRight - POP_WIDTH)
  left = Math.max(left, window.scrollX + MARGIN)
  popStyle.top = `${rect.bottom + window.scrollY + 8}px`
  popStyle.left = `${left}px`
  // 箭头指向 trigger 中心
  const triggerCenter = rect.left + rect.width / 2 + window.scrollX
  const arrowOffset = Math.max(10, Math.min(POP_WIDTH - 22, triggerCenter - left - 6))
  arrowStyle.left = `${arrowOffset}px`
  open.value = true
}
function confirm() { open.value = false; emit('confirm') }
function cancel() { open.value = false; emit('cancel') }

function onDocClick(e) {
  if (!open.value) return
  if (wrap.value && !wrap.value.contains(e.target)) open.value = false
}
onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))
</script>

<style scoped>
.ui-popconfirm-wrap { display: inline-block; }
.ui-popconfirm-trigger { display: inline-block; cursor: pointer; }

.ui-popconfirm {
  position: absolute;
  z-index: var(--z-dropdown);
  background: var(--surface);
  border: 1px solid var(--hairline);
  padding: var(--space-4) var(--space-5);
  min-width: 220px;
  box-shadow: var(--shadow-md);
}
.ui-popconfirm-arrow {
  position: absolute;
  top: -6px;
  width: 12px;
  height: 12px;
  background: var(--surface);
  border-top: 1px solid var(--hairline);
  border-left: 1px solid var(--hairline);
  transform: rotate(45deg);
}
.ui-popconfirm-msg {
  font-size: var(--text-sm);
  color: var(--ink);
  margin-bottom: var(--space-3);
  line-height: var(--leading-normal);
}
.ui-popconfirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}
.ui-pc-btn {
  font-family: var(--font-body);
  font-size: var(--text-xs);
  padding: 4px 12px;
  border: 1px solid transparent;
  cursor: pointer;
  background: transparent;
}
.ui-pc-btn.quiet { color: var(--ink-muted); }
.ui-pc-btn.quiet:hover { color: var(--ink); }
.ui-pc-btn.danger { background: var(--accent); color: var(--ink-inverse); }
.ui-pc-btn.danger:hover { background: var(--accent-hover); }

.pop-fade-enter-active, .pop-fade-leave-active {
  transition: opacity var(--duration-fast) var(--ease-out),
              transform var(--duration-fast) var(--ease-out);
}
.pop-fade-enter-from, .pop-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
