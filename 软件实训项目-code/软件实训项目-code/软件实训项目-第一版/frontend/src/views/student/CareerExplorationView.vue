<template>
  <div>
    <h2 class="page-title">AI 职业方向探索</h2>
    <div class="page-card"><ChatWindow :messages="messages" :loading="loading" placeholder="描述你的职业兴趣和偏好..." @send="handleSend" /></div>
    <div v-if="results.length" class="page-card" style="margin-top:16px;">
      <h3>推荐方向</h3>
      <el-row :gutter="16">
        <el-col :span="8" v-for="item in results" :key="item.jobTitle">
          <el-card shadow="hover" style="margin-top:12px;">
            <template #header><b>{{ item.jobTitle }}</b><el-tag style="float:right;">匹配 {{ item.matchScore }}%</el-tag></template>
            <p>{{ item.reason }}</p>
            <p style="color:#909399;">优先级：{{ item.learningPriority }} | {{ item.growthPath }}</p>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ChatWindow from '@/components/common/ChatWindow.vue'
import { exploreCareer } from '@/api/student'

const messages = ref<any[]>([{ role: 'assistant', content: '你好！我是职业规划助手。请告诉我你的职业兴趣和偏好，我会为你推荐合适的职业方向。' }])
const loading = ref(false)
const results = ref<any[]>([])

async function handleSend(text: string) {
  messages.value.push({ role: 'user', content: text })
  loading.value = true
  try {
    const res: any = await exploreCareer({ preferences: text })
    const data = res.data
    results.value = data.directions || []
    messages.value.push({ role: 'assistant', content: data.overallAnalysis || '分析完成，请在右侧查看推荐结果' })
  } catch {
    messages.value.push({ role: 'system', content: '分析失败，请稍后重试' })
  } finally { loading.value = false }
}
</script>
