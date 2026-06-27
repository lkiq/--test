<template>
  <div>
    <h2 class="page-title">职位管理</h2>
    <div class="page-card">
      <div class="action-bar" style="margin-bottom: 16px;">
        <el-button type="primary" :icon="Plus">发布新职位</el-button>
      </div>
      <el-table :data="jobs" stripe>
        <el-table-column prop="title" label="职位名称" />
        <el-table-column prop="city" label="城市" />
        <el-table-column prop="salaryRange" label="薪资" />
        <el-table-column prop="direction" label="方向" />
        <el-table-column label="状态">
          <template #default><el-tag type="success">招聘中</el-tag></template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default>
            <el-button link type="primary">编辑</el-button>
            <el-button link type="danger">下架</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { searchJobs } from '@/api/student'

const jobs = ref<any[]>([])
onMounted(async () => {
  const res: any = await searchJobs()
  jobs.value = (res.data || []).slice(0, 6)
})
</script>
