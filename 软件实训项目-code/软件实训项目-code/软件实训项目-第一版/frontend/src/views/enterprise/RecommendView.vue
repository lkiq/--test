<template>
  <div>
    <h2 class="page-title">项目需求与候选人推荐</h2>
    <div class="page-card">
      <el-form label-width="80px">
        <el-form-item label="项目描述">
          <el-input v-model="projectDesc" type="textarea" :rows="4" placeholder="请描述您的项目需求（不少于20字），AI 将分析并推荐合适候选人" />
        </el-form-item>
        <el-button type="primary" @click="submitRecommend" :loading="loading">分析并推荐</el-button>
      </el-form>
    </div>
    <!-- 推荐结果 -->
    <div v-if="result" class="page-card" style="margin-top:16px;">
      <h3>推荐岗位</h3>
      <el-row :gutter="16">
        <el-col :span="8" v-for="pos in result.positions || []" :key="pos.positionTitle">
          <el-card>
            <template #header><b>{{ pos.positionTitle }}</b>（需求 {{ pos.headcount }} 人）</template>
            <el-tag v-for="r in pos.skillRequirements" :key="r.skillName" size="small" style="margin:2px;">{{ r.skillName }} - {{ r.requiredLevel }}</el-tag>
          </el-card>
        </el-col>
      </el-row>
      <h3 style="margin-top:20px;">候选人推荐</h3>
      <el-table :data="result.candidates || []" stripe>
        <el-table-column prop="username" label="候选人" />
        <el-table-column prop="matchScore" label="综合匹配" />
        <el-table-column prop="skillScore" label="技能匹配" />
        <el-table-column prop="assessmentScore" label="测评适配" />
        <el-table-column prop="learningScore" label="学习进度" />
        <el-table-column prop="recommendReason" label="推荐理由" />
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { recommend } from '@/api/enterprise'

const projectDesc = ref('')
const loading = ref(false)
const result = ref<any>(null)

async function submitRecommend() {
  if (projectDesc.value.length < 20) return
  loading.value = true
  try {
    const res: any = await recommend({ projectDescription: projectDesc.value })
    result.value = res.data
  } finally { loading.value = false }
}
</script>
