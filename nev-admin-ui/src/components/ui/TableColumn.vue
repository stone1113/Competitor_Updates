<script>
import { inject, onMounted, onBeforeUnmount, defineComponent, useSlots } from 'vue'

export default defineComponent({
  name: 'UTableColumn',
  props: {
    prop: String,
    label: { type: String, required: true },
    width: [String, Number],
    minWidth: [String, Number],
    align: String
  },
  setup(props) {
    const ctx = inject('uiTable', null)
    const slots = useSlots()
    const key = props.prop || props.label

    onMounted(() => {
      ctx?.register({
        key,
        prop: props.prop,
        label: props.label,
        width: props.width,
        minWidth: props.minWidth,
        align: props.align,
        slotFn: slots.default || null
      })
    })
    onBeforeUnmount(() => ctx?.unregister(key))

    return () => null
  }
})
</script>
