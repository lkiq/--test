<template>
  <div>
    <h2 class="page-title">管理首页 - 数据看板</h2>
    <el-row :gutter="20">
      <el-col :span="4" v-for="item in dashboardCards" :key="item.label">
        <el-card shadow="hover" style="text-align:center;">
          <el-statistic :title="item.label" :value="item.value" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getDashboard } from '@/api/admin'

const dashboardCards = ref([
  { label: '用户总数', value: 0 }, { label: '测评次数', value: 0 },
  { label: '匹配次数', value: 0 }, { label: '简历分析', value: 0 },
  { label: '面试次数', value: 0 }, { label: '企业推荐', value: 0 }
])

onMounted(async () => {
  const res: any = await getDashboard()
  const d = res.data || {}
  dashboardCards.value = [
    { label: '用户总数', value: d.totalUsers || 0 },
    { label: '测评次数', value: d.totalAssessments || 0 },
    { label: '匹配次数', value: d.totalMatches || 0 },
    { label: '简历分析', value: d.totalResumeAnalysis || 0 },
    { label: '面试次数', value: d.totalInterviews || 0 },
    { label: '企业推荐', value: d.totalEnterpriseRecommendations || 0 }
  ]
})
</script>
