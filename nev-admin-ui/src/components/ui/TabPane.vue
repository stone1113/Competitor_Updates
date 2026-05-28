<template>
  <div v-if="active === name" class="ui-tab-pane" role="tabpanel">
    <slot />
  </div>
</template>

<script setup>
import { inject, onMounted, onBeforeUnmount, computed } from 'vue'

const props = defineProps({
  name: { type: String, required: true },
  label: { type: String, required: true }
})

const ctx = inject('uiTabs')
const active = computed(() => ctx?.active.value)

onMounted(() => ctx?.register({ name: props.name, label: props.label }))
onBeforeUnmount(() => ctx?.unregister(props.name))
</script>
