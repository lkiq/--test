<template>
  <div>
    <h2 class="page-title">能力测评</h2>
    <!-- 未开始时显示说明 -->
    <div v-if="!started" class="page-card" style="text-align:center;padding:60px;">
      <el-icon :size="60" color="#409EFF"><EditPen /></el-icon>
      <h3 style="margin:16px 0;">五维能力测评</h3>
      <p style="color:#909399;">包含编程能力、逻辑推理、产品思维、技术素养、沟通表达五个维度，共25道题目</p>
      <el-button type="primary" size="large" @click="startAssessment" style="margin-top:20px;">开始测评</el-button>
    </div>
    <!-- 答题中 -->
    <div v-else-if="!finished" class="page-card">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;">
        <span>第 {{ assessmentStore.currentIndex + 1 }} / {{ assessmentStore.questions.length }} 题</span>
        <el-progress :percentage="Math.round((assessmentStore.currentIndex / assessmentStore.questions.length) * 100)" style="width:300px;" />
      </div>
      <div v-if="currentQ" style="padding:20px;">
        <h3 style="margin-bottom:16px;">{{ currentQ.content }}</h3>
        <el-radio-group v-model="selectedAnswer" style="display:flex;flex-direction:column;gap:12px;">
          <el-radio v-for="(opt, i) in parseOptions(currentQ.options)" :key="i" :value="String.fromCharCode(65 + i)">
            {{ String.fromCharCode(65 + i) }}. {{ opt }}
          </el-radio>
        </el-radio-group>
        <div style="margin-top:20px;">
          <el-button @click="prev" :disabled="assessmentStore.currentIndex === 0">上一题</el-button>
          <el-button type="primary" @click="next">
            {{ assessmentStore.currentIndex >= assessmentStore.questions.length - 1 ? '提交' : '下一题' }}
          </el-button>
        </div>
      </div>
    </div>
    <!-- 结果报告 -->
    <div v-else-if="result" class="page-card">
      <h3 style="text-align:center;">测评结果报告</h3>
      <div style="text-align:center;margin:20px 0;">
        <el-progress type="dashboard" :percentage="result.dimensionScores?.totalScore || result.totalScore || 0" :color="scoreColor" />
        <p style="color:#909399;">综合等级：{{ result.level }}</p>
      </div>
      <div style="display:flex;justify-content:space-around;margin-top:20px;">
        <div v-for="(v, k) in result.dimensionScores" :key="k">
          <el-statistic :title="k" :value="v" suffix="分" />
        </div>
      </div>
      <p style="margin-top:16px;">✅ 优势维度：{{ result.strengths }}</p>
      <p>⚠️ 薄弱维度：{{ result.weaknesses }}</p>
      <el-button type="primary" @click="reset" style="margin-top:16px;">重新测评</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useAssessmentStore } from '@/stores/assessment'
import { getAssessmentQuestions, submitAssessment } from '@/api/student'
import { ElMessage } from 'element-plus'

const assessmentStore = useAssessmentStore()
const started = computed(() => assessmentStore.questions.length > 0)
const finished = ref(false)
const result = ref<any>(null)
const selectedAnswer = ref('')

const currentQ = computed(() => assessmentStore.questions[assessmentStore.currentIndex])
const scoreColor = computed(() => {
  const s = result.value?.totalScore || 0
  if (s >= 85) return '#67C23A'
  if (s >= 70) return '#409EFF'
  if (s >= 55) return '#E6A23C'
  return '#F56C6C'
})

function parseOptions(opts: any): string[] {
  try { return typeof opts === 'string' ? JSON.parse(opts) : opts || [] } catch { return [] }
}

async function startAssessment() {
  const res: any = await getAssessmentQuestions()
  assessmentStore.setQuestions(res.data || [])
}

function prev() {
  if (assessmentStore.currentIndex > 0) {
    assessmentStore.currentIndex--
    selectedAnswer.value = assessmentStore.answers.get(currentQ.value?.id) || ''
  }
}

async function next() {
  if (currentQ.value) assessmentStore.setAnswer(currentQ.value.id, selectedAnswer.value)

  if (assessmentStore.currentIndex >= assessmentStore.questions.length - 1) {
    const answers: any[] = []
    assessmentStore.answers.forEach((answer, questionId) => {
      answers.push({ questionId, answer })
    })
    const res: any = await submitAssessment({ answers })
    result.value = res.data
    finished.value = true
  } else {
    assessmentStore.currentIndex++
    selectedAnswer.value = assessmentStore.answers.get(assessmentStore.questions[assessmentStore.currentIndex]?.id) || ''
  }
}

function reset() {
  assessmentStore.reset()
  finished.value = false
  result.value = null
}
</script>
