<script setup>
import { Loader } from '@lucide/vue'

defineProps({
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  label: { type: String, default: 'Submit' },
  loadingLabel: { type: String, default: 'Processing...' },
})
</script>

<template>
  <button class="btn" :disabled="disabled || loading" :class="{ loading }">
    <Loader v-if="loading" :size="14" :stroke-width="2" class="spin" />
    <span>{{ loading ? loadingLabel : label }}</span>
  </button>
</template>

<style scoped>
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 24px;
  border-radius: 8px;
  background: #2563eb;
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  transition: background 0.15s, transform 0.1s;
}

.btn:hover:not(:disabled) {
  background: #1d4ed8;
}

.btn:active:not(:disabled) {
  transform: scale(0.98);
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.spin {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
