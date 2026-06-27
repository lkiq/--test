<template>
  <div>
    <h2 class="page-title">我的投递</h2>
    <div class="page-card">
      <el-table v-if="applications.length" :data="applications" stripe>
        <el-table-column prop="title" label="岗位">
          <template #default="scope">
            <el-link type="primary" @click="$router.push(`/student/job/${scope.row.jobId}`)">{{ scope.row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="companyName" label="公司" />
        <el-table-column prop="time" label="投递时间">
          <template #default="scope">{{ formatTime(scope.row.time) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态">
          <template #default="scope">
            <el-tag type="primary">{{ scope.row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default="scope">
            <el-button link type="primary" @click="$router.push(`/student/job/${scope.row.jobId}`)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <EmptyState v-else scene="document" title="暂无投递记录" description="去岗位匹配看看心仪的职位吧" action-text="去找工作" @action="$router.push('/student/job-matching')" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useJobStore } from '@/stores/job'
import EmptyState from '@/components/common/EmptyState.vue'

const jobStore = useJobStore()

const applications = computed(() => {
  return jobStore.applications.map(a => {
    const job = jobStore.jobs.find(j => j.id === a.jobId)
    return {
      ...a,
      title: job?.title || '未知岗位',
      companyName: job?.companyName || '未知公司'
    }
  })
})

function formatTime(iso: string) {
  return new Date(iso).toLocaleString()
}
</script>
