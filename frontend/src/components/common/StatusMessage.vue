<script setup>
import { AlertCircle, CheckCircle, Info } from '@lucide/vue'

const props = defineProps({
  type: {
    type: String,
    default: 'info',
    validator: (v) => ['info', 'success', 'error'].includes(v),
  },
  message: { type: String, default: '' },
})

const iconMap = { info: Info, success: CheckCircle, error: AlertCircle }
</script>

<template>
  <div v-if="message" class="msg" :class="props.type">
    <component :is="iconMap[props.type]" :size="14" :stroke-width="1.5" />
    <span>{{ message }}</span>
  </div>
</template>

<style scoped>
.msg {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 14px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.5;
}

.info {
  background: #eff6ff;
  color: #1e40af;
}

.success {
  background: #f0fdf4;
  color: #166534;
}

.error {
  background: #fef2f2;
  color: #991b1b;
}
</style>
