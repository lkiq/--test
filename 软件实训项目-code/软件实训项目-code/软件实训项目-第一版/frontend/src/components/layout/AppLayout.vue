<template>
  <div class="app-layout" v-if="isLoggedIn">
    <Sidebar />
    <div class="main-area">
      <HeaderBar />
      <div class="content">
        <router-view />
      </div>
    </div>
  </div>
  <router-view v-else />
</template>

<script setup lang="ts">
import { useUserStore } from '@/stores/user'
import { computed } from 'vue'
import Sidebar from './Sidebar.vue'
import HeaderBar from './HeaderBar.vue'

const userStore = useUserStore()
const isLoggedIn = computed(() => userStore.isLoggedIn)
</script>
