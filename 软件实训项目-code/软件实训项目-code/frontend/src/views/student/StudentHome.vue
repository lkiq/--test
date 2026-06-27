<template>
  <div>
    <h2 class="page-title">学生首页</h2>
    <!-- 步骤条 -->
    <div class="page-card">
      <el-steps :active="activeStep" align-center>
        <el-step title="求职画像" />
        <el-step title="能力测评" />
        <el-step title="方向探索" />
        <el-step title="岗位匹配" />
        <el-step title="差距分析" />
        <el-step title="学习提升" />
        <el-step title="简历面试" />
      </el-steps>
    </div>
    <!-- 快捷入口 -->
    <el-row :gutter="20" style="margin-top:16px;">
      <el-col :span="8">
        <el-card shadow="hover" @click="$router.push('/student/profile')" style="cursor:pointer;">
          <template #header><el-icon><User /></el-icon> 求职画像</template>
          <p style="color:#909399;">完善你的教育背景和技能标签</p>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" @click="$router.push('/student/assessment')" style="cursor:pointer;">
          <template #header><el-icon><EditPen /></el-icon> 能力测评</template>
          <p style="color:#909399;">了解你的职业能力水平</p>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" @click="$router.push('/student/career-exploration')" style="cursor:pointer;">
          <template #header><el-icon><Compass /></el-icon> AI方向探索</template>
          <p style="color:#909399;">智能推荐适合你的职业方向</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getProgressOverview } from '@/api/student'

const activeStep = ref(1)
onMounted(async () => {
  try {
    const res: any = await getProgressOverview()
    const rate = res.data?.completionRate || 0
    if (rate > 80) activeStep.value = 6
    else if (rate > 50) activeStep.value = 4
    else if (rate > 10) activeStep.value = 2
    else activeStep.value = 1
  } catch { activeStep.value = 1 }
})
</script>
