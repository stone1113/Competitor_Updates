import { reactive } from 'vue'

export const toasts = reactive([])
let seq = 0

export function dismiss(id) {
  const i = toasts.findIndex(t => t.id === id)
  if (i >= 0) toasts.splice(i, 1)
}

function show(message, type = 'info', duration = 3000) {
  const id = ++seq
  toasts.push({ id, message, type })
  if (duration > 0) {
    setTimeout(() => dismiss(id), duration)
  }
  return id
}

export const toast = {
  info:    (msg, d) => show(msg, 'info', d),
  success: (msg, d) => show(msg, 'success', d),
  error:   (msg, d) => show(msg, 'error', d),
  warning: (msg, d) => show(msg, 'warning', d)
}
