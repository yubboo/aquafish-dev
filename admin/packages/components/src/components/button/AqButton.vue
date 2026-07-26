<script setup lang="ts">
import { computed } from 'vue'

import type { AqButtonSize, AqButtonVariant } from './button'

const props = withDefaults(defineProps<{
  variant?: AqButtonVariant
  size?: AqButtonSize
  loading?: boolean
  disabled?: boolean
  type?: 'button' | 'submit' | 'reset'
}>(), {
  variant: 'primary',
  size: 'medium',
  loading: false,
  disabled: false,
  type: 'button',
})

const emit = defineEmits<{
  click: [event: MouseEvent]
}>()

const variantClass = computed(() => ({
  primary: 'border-transparent bg-[var(--aq-color-primary)] text-[var(--aq-color-on-primary)] hover:bg-[var(--aq-color-primary-hover)]',
  secondary: 'border-[var(--aq-color-border)] bg-[var(--aq-color-paper)] text-[var(--aq-color-text)] hover:bg-black/5',
  danger: 'border-transparent bg-[var(--aq-color-danger)] text-white hover:bg-[var(--aq-color-danger-hover)]',
  ghost: 'border-transparent bg-transparent text-[var(--aq-color-text)] hover:bg-black/5',
}[props.variant]))

const sizeClass = computed(() => ({
  small: 'min-h-8 px-3 text-xs',
  medium: 'min-h-[var(--aq-control-height)] px-4 text-sm',
  large: 'min-h-11 px-5 text-base',
}[props.size]))

/** loading 和 disabled 都必须阻止业务 click，避免写操作被重复提交。 */
function handleClick(event: MouseEvent): void {
  if (props.loading || props.disabled) {
    event.preventDefault()
    return
  }
  emit('click', event)
}
</script>

<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    :aria-busy="loading || undefined"
    class="inline-flex cursor-pointer items-center justify-center gap-2 rounded-[var(--aq-radius-control)] border font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--aq-color-primary)] disabled:cursor-not-allowed disabled:opacity-55"
    :class="[variantClass, sizeClass]"
    @click="handleClick"
  >
    <span
      v-if="loading"
      class="size-4 animate-spin rounded-full border-2 border-current border-r-transparent"
      aria-hidden="true"
    />
    <slot name="icon" />
    <slot />
  </button>
</template>
