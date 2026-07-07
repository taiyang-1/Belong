<script setup>
defineProps({
  title: { type: String, default: '' },
})
</script>

<template>
  <section class="result-section">
    <h4 v-if="title" class="section-title">{{ title }}</h4>
    <div class="section-body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.result-section {
  margin-bottom: 20px;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid #f1f5f9;
}

.section-body {
  font-size: 14px;
  color: #374151;
  line-height: 1.7;
}
</style>
