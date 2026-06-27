<template>
  <div>
    <h2 class="page-title">能力差距分析</h2>
    <div v-if="report" class="page-card">
      <h3>目标岗位：{{ report.jobTitle }}</h3>
      <p style="margin:12px 0;">综合匹配度：<el-progress :percentage="report.overallMatch || 0" :color="gapColor" style="width:200px;" /></p>
      <div style="margin-top:20px;">
        <h4>技能差距矩阵</h4>
        <el-table :data="report.gaps || []" stripe>
          <el-table-column prop="skillName" label="技能" />
          <el-table-column prop="userLevel" label="当前水平">
            <template #default="scope"><el-tag :type="levelType(scope.row.userLevel)">{{ scope.row.userLevel }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="requiredLevel" label="岗位要求" />
          <el-table-column prop="gapDegree" label="差距程度">
            <template #default="scope">
              <el-tag :type="scope.row.gapDegree === '严重不足' ? 'danger' : scope.row.gapDegree === '需要提升' ? 'warning' : 'success'">{{ scope.row.gapDegree }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div v-if="report.radarChart" style="margin-top:20px;">
        <RadarChart :data="report.radarChart" />
      </div>
      <div style="margin-top:20px;">
        <el-button type="primary" @click="$router.push('/student/learning-path')">生成学习路径</el-button>
      </div>
    </div>
    <div v-else class="page-card" style="text-align:center;padding:60px;">
      <p>请先在岗位匹配页面选择目标岗位进行差距分析</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { analyzeGap } from '@/api/student'
import RadarChart from '@/components/charts/RadarChart.vue'

const route = useRoute()
const report = ref<any>(null)

onMounted(async () => {
  const jobId = route.query.jobId
  if (jobId) {
    const res: any = await analyzeGap(Number(jobId))
    report.value = res.data
  }
})

function gapColor(percentage: number) {
  if (percentage >= 80) return '#67C23A'
  if (percentage >= 60) return '#409EFF'
  if (percentage >= 40) return '#E6A23C'
  return '#F56C6C'
}

function levelType(level: string) {
  if (level === '精通' || level === '熟练') return 'success'
  if (level === '掌握') return 'warning'
  return 'danger'
}
</script>
