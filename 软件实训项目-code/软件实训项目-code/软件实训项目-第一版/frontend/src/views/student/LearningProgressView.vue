<template>
  <div>
    <h2 class="page-title">学习进度</h2>
    <el-row :gutter="20">
      <el-col :span="6" v-for="stat in stats" :key="stat.label">
        <el-card shadow="hover" style="text-align:center;">
          <el-statistic :title="stat.label" :value="stat.value" />
        </el-card>
      </el-col>
    </el-row>
    <!-- 技能掌握度 -->
    <div class="page-card" style="margin-top:16px;">
      <h3>技能掌握度</h3>
      <div v-for="(v, k) in skillData?.skillScores || {}" :key="k" style="margin:8px 0;">
        <span style="width:100px;display:inline-block;">技能 {{ k }}</span>
        <el-progress :percentage="v" :stroke-width="12" style="width:400px;" />
        <el-tag size="small" style="margin-left:8px;">{{ skillData?.skillLevels?.[k] }}</el-tag>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getProgressOverview, getSkillProgress } from '@/api/student'

const stats = ref<any[]>([
  { label: '任务完成率', value: '0%' }, { label: '测评次数', value: 0 },
  { label: '面试次数', value: 0 }, { label: '简历分析', value: 0 }
])
const skillData = ref<any>(null)

onMounted(async () => {
  try {
    const overview: any = await getProgressOverview()
    const d = overview.data
    stats.value = [
      { label: '任务完成率', value: `${d.completionRate || 0}%` },
      { label: '测评次数', value: d.totalAssessmentCount || 0 },
      { label: '面试次数', value: d.totalInterviewCount || 0 },
      { label: '简历分析', value: d.resumeAnalysisCount || 0 }
    ]
    const skill: any = await getSkillProgress()
    skillData.value = skill.data
  } catch {/* ignored */}
})
</script>
