<script setup lang="ts">
import { computed } from 'vue'

import type { AqStatusTone } from './status'

const props = withDefaults(defineProps<{
  tone?: AqStatusTone
}>(), {
  tone: 'neutral',
})

const toneClass = computed(() => ({
  neutral: 'border-[var(--aq-color-border)] text-[var(--aq-color-muted)]',
  success: 'border-[var(--aq-color-success)] text-[var(--aq-color-success)]',
  warning: 'border-[var(--aq-color-warning)] text-[var(--aq-color-warning)]',
  danger: 'border-[var(--aq-color-danger)] text-[var(--aq-color-danger)]',
  info: 'border-[var(--aq-color-info)] text-[var(--aq-color-info)]',
}[props.tone]))

const dotClass = computed(() => ({
  neutral: 'bg-[var(--aq-color-muted)]',
  success: 'bg-[var(--aq-color-success)]',
  warning: 'bg-[var(--aq-color-warning)]',
  danger: 'bg-[var(--aq-color-danger)]',
  info: 'bg-[var(--aq-color-info)]',
}[props.tone]))
</script>

<template>
  <span
    class="inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium"
    :class="toneClass"
  >
    <span class="size-1.5 rounded-full" :class="dotClass" aria-hidden="true" />
    <slot />
  </span>
</template>
