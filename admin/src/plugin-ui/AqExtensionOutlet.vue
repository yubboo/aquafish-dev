<script setup lang="ts">
import { computed } from 'vue'
import type { AqExtensionPointId } from '@aquafish/ui-shared'

import { aqUiExtensions } from './extension-registry'

const props = defineProps<{
  point: AqExtensionPointId
}>()

const resolvedExtensions = computed(() =>
  aqUiExtensions().value.filter(item => item.point === props.point),
)
</script>

<template>
  <div
    v-if="resolvedExtensions.length"
    class="aq-extension-outlet"
    :data-extension-point="point"
  >
    <component
      :is="item.component"
      v-for="item in resolvedExtensions"
      :key="item.key"
      v-bind="item.props"
    />
  </div>
</template>

<style scoped>
.aq-extension-outlet {
  display: contents;
}
</style>
