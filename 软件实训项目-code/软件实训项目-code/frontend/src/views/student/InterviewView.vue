<template>
  <div>
    <h2 class="page-title">AI 模拟面试</h2>
    <!-- 未开始 -->
    <div v-if="!session" class="page-card">
      <el-form label-width="100px">
        <el-form-item label="目标岗位">
          <el-input v-model="jobTitle" placeholder="如：Java后端开发" />
        </el-form-item>
        <el-form-item label="面试类型">
          <el-select v-model="interviewType"><el-option label="技术面试" value="TECHNICAL" /><el-option label="HR面试" value="HR" /><el-option label="综合面试" value="COMPREHENSIVE" /></el-select>
        </el-form-item>
        <el-button type="primary" @click="start" :loading="loading">开始面试</el-button>
      </el-form>
    </div>
    <!-- 面试中 -->
    <div v-else-if="!report" class="page-card">
      <ChatWindow :messages="chatMessages" :loading="sending" placeholder="输入你的回答..." @send="handleAnswer" />
      <div style="margin-top:8px;">
        <span>第 {{ session.questionIndex }} / {{ session.totalQuestions }} 题</span>
        <el-button type="warning" @click="endInterview" style="float:right;">结束面试</el-button>
      </div>
    </div>
    <!-- 报告 -->
    <div v-else class="page-card">
      <h3>面试报告</h3>
      <div style="margin:16px 0;">
        <el-progress type="dashboard" :percentage="report.totalScore || 75" :color="'#409EFF'" />
        <span>综合评分</span>
      </div>
      <div style="display:flex;gap:20px;">
        <div v-for="(v, k) in report.dimensionScores || {}" :key="k">
          <el-statistic :title="k" :value="v" suffix="分" />
        </div>
      </div>
      <p style="margin-top:16px;">💡 亮点：{{ (report.highlights || []).join('；') }}</p>
      <p>📝 改进建议：{{ (report.improvements || []).join('；') }}</p>
      <el-button @click="reset" style="margin-top:12px;">重新面试</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ChatWindow from '@/components/common/ChatWindow.vue'
import { startInterview, submitAnswer, endInterview as endInterviewApi } from '@/api/student'

const session = ref<any>(null)
const report = ref<any>(null)
const loading = ref(false)
const sending = ref(false)
const jobTitle = ref('')
const interviewType = ref('COMPREHENSIVE')
const chatMessages = ref<any[]>([])

async function start() {
  loading.value = true
  try {
    const res: any = await startInterview({ interviewType: interviewType.value })
    session.value = res.data
    chatMessages.value = [{ role: 'assistant', content: session.value.question }]
  } finally { loading.value = false }
}

async function handleAnswer(text: string) {
  chatMessages.value.push({ role: 'user', content: text })
  sending.value = true
  try {
    const res: any = await submitAnswer(session.value.sessionId, text)
    const data = res.data
    if (data.finished) {
      chatMessages.value.push({ role: 'assistant', content: '面试结束，正在生成报告...' })
    } else {
      chatMessages.value.push({ role: 'assistant', content: data.question })
    }
    session.value = data
  } finally { sending.value = false }
}

async function endInterview() {
  const res: any = await endInterviewApi(session.value.sessionId)
  report.value = res.data
  session.value = null
}

function reset() { session.value = null; report.value = null; chatMessages.value = [] }
</script>
