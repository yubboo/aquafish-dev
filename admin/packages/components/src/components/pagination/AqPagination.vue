<script setup lang="ts">
import { computed } from 'vue'

import AqButton from '../button/AqButton.vue'
import { getPageCount, normalizePage } from './pagination'

const props = withDefaults(defineProps<{
  page: number
  total: number
  pageSize?: number
  disabled?: boolean
}>(), {
  pageSize: 20,
  disabled: false,
})

const emit = defineEmits<{
  'update:page': [page: number]
  change: [page: number]
}>()

const pageCount = computed(() => getPageCount(props.total, props.pageSize))
const currentPage = computed(() => normalizePage(props.page, pageCount.value))

function goTo(page: number): void {
  const nextPage = normalizePage(page, pageCount.value)
  if (props.disabled || nextPage === currentPage.value) {
    return
  }
  emit('update:page', nextPage)
  emit('change', nextPage)
}
</script>

<template>
  <nav class="flex items-center gap-3" aria-label="分页">
    <AqButton
      variant="secondary"
      size="small"
      :disabled="disabled || currentPage <= 1"
      aria-label="上一页"
      @click="goTo(currentPage - 1)"
    >
      上一页
    </AqButton>
    <span class="text-sm text-[var(--aq-color-muted)]">
      第 {{ currentPage }} / {{ pageCount }} 页
    </span>
    <AqButton
      variant="secondary"
      size="small"
      :disabled="disabled || currentPage >= pageCount"
      aria-label="下一页"
      @click="goTo(currentPage + 1)"
    >
      下一页
    </AqButton>
  </nav>
</template>
