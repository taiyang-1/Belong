<script setup>
import { ref } from 'vue'
import AppShell from './components/layout/AppShell.vue'
import Sidebar from './components/layout/Sidebar.vue'
import ChatView from './views/ChatView.vue'
import OrganizeView from './views/OrganizeView.vue'
import PlanView from './views/PlanView.vue'

const activeView = ref('chat')

function switchView(name) {
  activeView.value = name
}
</script>

<template>
  <AppShell>
    <template #sidebar>
      <Sidebar :active="activeView" @navigate="switchView" />
    </template>

    <template #main>
      <ChatView v-if="activeView === 'chat'" />
      <OrganizeView v-else-if="activeView === 'organize'" />
      <PlanView v-else-if="activeView === 'plan'" />
    </template>
  </AppShell>
</template>
