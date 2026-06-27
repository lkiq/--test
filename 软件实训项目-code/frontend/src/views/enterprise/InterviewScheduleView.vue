<template>
  <div>
    <h2 class="page-title">面试日程</h2>
    <div class="page-card">
      <div class="action-bar" style="margin-bottom: 16px;">
        <el-button type="primary" :icon="Plus">安排面试</el-button>
      </div>
      <el-timeline>
        <el-timeline-item
          v-for="item in schedule"
          :key="item.id"
          :timestamp="item.time"
          :type="item.type"
          :icon="item.icon"
        >
          <h4>{{ item.title }}</h4>
          <p style="color: var(--text-secondary); font-size: 14px;">{{ item.candidate }} · {{ item.position }}</p>
        </el-timeline-item>
      </el-timeline>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'

const schedule = ref([
  { id: 1, title: '技术一面', candidate: '张三', position: 'Java后端开发工程师', time: '2026-06-28 14:00', type: 'primary', icon: 'MoreFilled' },
  { id: 2, title: 'HR 面', candidate: '李四', position: '前端开发工程师', time: '2026-06-29 10:00', type: 'success', icon: 'Check' },
  { id: 3, title: '综合面试', candidate: '王五', position: '算法工程师', time: '2026-06-30 15:30', type: 'warning', icon: 'WarningFilled' }
])
</script>
