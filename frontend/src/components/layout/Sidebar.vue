<script setup>
import { MessageSquare, ClipboardList, Calendar } from '@lucide/vue'

defineProps({
  active: { type: String, default: 'chat' },
})

const emit = defineEmits(['navigate'])

const navItems = [
  { key: 'chat', label: 'Chat', icon: MessageSquare },
  { key: 'organize', label: 'Organize', icon: ClipboardList },
  { key: 'plan', label: 'Plan', icon: Calendar },
]
</script>

<template>
  <div class="sidebar">
    <div class="brand">
      <span class="brand-mark">B</span>
      <span class="brand-name">Belong</span>
    </div>

    <nav class="nav">
      <button
        v-for="item in navItems"
        :key="item.key"
        class="nav-item"
        :class="{ active: active === item.key }"
        @click="emit('navigate', item.key)"
      >
        <component :is="item.icon" :size="18" :stroke-width="1.5" />
        <span>{{ item.label }}</span>
      </button>
    </nav>

    <div class="sidebar-footer">
      <span class="version">v0.1</span>
    </div>
  </div>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 22px 14px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 8px 30px;
}

.brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: #ffffff;
  color: #132033;
  font-size: 16px;
  font-weight: 800;
  letter-spacing: 0;
}

.brand-name {
  font-size: 17px;
  font-weight: 700;
  color: #ffffff;
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 42px;
  padding: 0 12px;
  border-radius: 8px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.72);
  transition: background 0.15s, color 0.15s;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.nav-item.active {
  background: rgba(255, 255, 255, 0.13);
  color: #ffffff;
  font-weight: 650;
}

.sidebar-footer {
  padding: 0 8px;
}

.version {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.38);
}
</style>
