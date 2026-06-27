<template>
  <div class="assessment-page">
    <!-- ========== 未开始：AI主题开场 ========== -->
    <div v-if="!started" class="assessment-hero">
      <div class="hero-bg-decor">
        <div class="floating-icon i1">🧠</div>
        <div class="floating-icon i2">💡</div>
        <div class="floating-icon i3">🎯</div>
        <div class="floating-icon i4">⚡</div>
        <div class="floating-icon i5">🔬</div>
        <div class="hero-dot-grid"></div>
      </div>
      <div class="hero-content">
        <div class="hero-badge">
          <span class="badge-dot"></span>AI 驱动
        </div>
        <h1 class="hero-title">
          <span class="title-gradient">五维能力测评</span>
        </h1>
        <p class="hero-desc">基于AI算法全面评估你的编程、逻辑、产品思维、技术素养与沟通表达能力</p>
        
        <div class="dimension-cards">
          <div class="dim-card" v-for="dim in dimensions" :key="dim.key">
            <div class="dim-icon" :style="{ background: dim.color }">{{ dim.icon }}</div>
            <div class="dim-name">{{ dim.label }}</div>
            <div class="dim-hint">{{ dim.hint }}</div>
          </div>
        </div>

        <div class="hero-info">
          <div class="info-item"><span class="info-value">25</span><span class="info-label">精选题目</span></div>
          <div class="info-divider"></div>
          <div class="info-item"><span class="info-value">5</span><span class="info-label">能力维度</span></div>
          <div class="info-divider"></div>
          <div class="info-item"><span class="info-value">~15</span><span class="info-label">分钟完成</span></div>
          <div class="info-divider"></div>
          <div class="info-item"><span class="info-value">AI</span><span class="info-label">智能分析</span></div>
        </div>

        <el-button type="primary" size="large" class="start-btn" @click="startAssessment">
          开始测评 <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- ========== 答题中 ========== -->
    <div v-else-if="!finished" class="quiz-section">
      <!-- 顶部进度条 -->
      <div class="quiz-header">
        <div class="quiz-meta">
          <el-tag effect="dark" :color="currentDim?.color" size="large">{{ currentDim?.icon }} {{ currentDim?.label }}</el-tag>
          <span class="quiz-count">第 {{ assessmentStore.currentIndex + 1 }} / {{ assessmentStore.questions.length }} 题</span>
        </div>
        <div class="quiz-progress-wrap">
          <div class="quiz-progress-bar">
            <div class="quiz-progress-fill" :style="{ width: progressPercent + '%' }"></div>
            <div class="quiz-progress-dots">
              <span v-for="i in assessmentStore.questions.length" :key="i"
                class="prog-dot" :class="{ done: i <= assessmentStore.currentIndex, active: i === assessmentStore.currentIndex + 1 }">
                {{ i <= assessmentStore.currentIndex ? '✓' : i }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 题目卡片 -->
      <div v-if="currentQ" class="quiz-card">
        <div class="quiz-question-wrap">
          <span class="quiz-q-num">Q{{ assessmentStore.currentIndex + 1 }}</span>
          <h3 class="quiz-question">{{ currentQ.content }}</h3>
        </div>
        <div class="quiz-options">
          <div
            v-for="(opt, i) in parseOptions(currentQ.options)"
            :key="i"
            class="quiz-option"
            :class="{ selected: selectedAnswer === String.fromCharCode(65 + i) }"
            @click="selectedAnswer = String.fromCharCode(65 + i)"
          >
            <span class="opt-letter">{{ String.fromCharCode(65 + i) }}</span>
            <span class="opt-text">{{ opt }}</span>
            <span class="opt-check" v-if="selectedAnswer === String.fromCharCode(65 + i)">✓</span>
          </div>
        </div>
        <div class="quiz-actions">
          <el-button @click="prev" :disabled="assessmentStore.currentIndex === 0" size="large">
            <el-icon><ArrowLeft /></el-icon> 上一题
          </el-button>
          <el-button type="primary" @click="next" size="large">
            {{ assessmentStore.currentIndex >= assessmentStore.questions.length - 1 ? '提交答卷' : '下一题' }}
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <!-- ========== 结果报告 ========== -->
    <div v-else-if="result" class="result-section">
      <div class="result-hero">
        <div class="result-badge" :class="resultLevelClass">
          <span class="level-icon">{{ resultLevelIcon }}</span>
          <span class="level-text">{{ result.level }}</span>
        </div>
        <h2 class="result-title">测评结果报告</h2>
        <p class="result-subtitle">AI 已全面分析你的能力画像</p>
      </div>

      <!-- 总分仪表盘 -->
      <div class="result-score-card">
        <div class="score-ring-wrap">
          <svg class="score-ring" viewBox="0 0 160 160">
            <circle cx="80" cy="80" r="70" fill="none" stroke="#e5e7eb" stroke-width="12" />
            <circle cx="80" cy="80" r="70" fill="none" :stroke="scoreColor"
              stroke-width="12" stroke-linecap="round"
              :stroke-dasharray="totalScore * 4.4 + ' ' + (440 - totalScore * 4.4)"
              transform="rotate(-90 80 80)"
              class="score-ring-animate" />
          </svg>
          <div class="score-ring-center">
            <span class="score-number">{{ totalScore }}</span>
            <span class="score-unit">分</span>
          </div>
        </div>
        <div class="score-level-tag" :style="{ color: scoreColor }">
          {{ levelLabel }}
        </div>
        <p class="score-hint">继续加油，你的潜力远超想象！</p>
      </div>

      <!-- 维度得分卡片 -->
      <div class="result-dimensions">
        <h3 class="section-label">
          <span class="label-icon">📊</span> 各维度表现
        </h3>
        <div class="dim-scores-grid">
          <div v-for="dim in dimensions" :key="dim.key" class="dim-score-card" :style="{ '--dim-color': dim.color }">
            <div class="ds-header">
              <span class="ds-icon">{{ dim.icon }}</span>
              <span class="ds-name">{{ dim.label }}</span>
            </div>
            <div class="ds-bar-wrap">
              <div class="ds-bar">
                <div class="ds-bar-fill" :style="{ width: (getDimScore(dim.key) || 0) + '%', background: dim.color }"></div>
              </div>
              <span class="ds-value">{{ getDimScore(dim.key) || 0 }}分</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 优劣势分析 -->
      <div class="result-analysis">
        <el-row :gutter="20">
          <el-col :span="12">
            <div class="analysis-card strength">
              <div class="analysis-header">
                <span class="analysis-icon">✅</span>
                <span>优势维度</span>
              </div>
              <p class="analysis-text">{{ result.strengths }}</p>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="analysis-card weakness">
              <div class="analysis-header">
                <span class="analysis-icon">🎯</span>
                <span>薄弱维度</span>
              </div>
              <p class="analysis-text">{{ result.weaknesses }}</p>
            </div>
          </el-col>
        </el-row>
      </div>

      <!-- 行动按钮 -->
      <div class="result-actions">
        <el-button type="primary" size="large" @click="$router.push('/student/gap-analysis')">
          📈 查看差距分析
        </el-button>
        <el-button size="large" @click="$router.push('/student/learning-path')">
          📚 规划学习路径
        </el-button>
        <el-button size="large" @click="reset">🔄 重新测评</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ArrowRight, ArrowLeft } from '@element-plus/icons-vue'
import { useAssessmentStore } from '@/stores/assessment'
import { getAssessmentQuestions, submitAssessment } from '@/api/student'
import { ElMessage } from 'element-plus'

const assessmentStore = useAssessmentStore()
const started = computed(() => assessmentStore.questions.length > 0)
const finished = ref(false)
const result = ref<any>(null)
const selectedAnswer = ref('')

const dimensions = [
  { key: 'programming', label: '编程能力', icon: '💻', hint: '代码功底与算法思维', color: '#3b82f6' },
  { key: 'logic', label: '逻辑推理', icon: '🧩', hint: '分析问题与推理能力', color: '#8b5cf6' },
  { key: 'product', label: '产品思维', icon: '🎨', hint: '用户洞察与产品设计', color: '#ec4899' },
  { key: 'techLiteracy', label: '技术素养', icon: '🔧', hint: '技术广度与深度', color: '#f59e0b' },
  { key: 'communication', label: '沟通表达', icon: '💬', hint: '表达清晰与协作能力', color: '#10b981' }
]

const currentQ = computed(() => assessmentStore.questions[assessmentStore.currentIndex])
const progressPercent = computed(() =>
  Math.round((assessmentStore.currentIndex / assessmentStore.questions.length) * 100)
)

const currentDim = computed(() => {
  const q = currentQ.value
  if (!q) return null
  const idx = assessmentStore.currentIndex
  return dimensions[Math.floor(idx / 5)] || dimensions[0]
})

const totalScore = computed(() => {
  if (!result.value) return 0
  return result.value.dimensionScores?.totalScore || result.value.totalScore || 0
})

const scoreColor = computed(() => {
  const s = totalScore.value
  if (s >= 85) return '#10b981'
  if (s >= 70) return '#3b82f6'
  if (s >= 55) return '#f59e0b'
  return '#ef4444'
})

const levelLabel = computed(() => {
  const s = totalScore.value
  if (s >= 85) return '优秀 · Excellent'
  if (s >= 70) return '良好 · Good'
  if (s >= 55) return '中等 · Average'
  return '需提升 · Needs Improvement'
})

const resultLevelClass = computed(() => {
  const s = totalScore.value
  if (s >= 85) return 'level-excellent'
  if (s >= 70) return 'level-good'
  if (s >= 55) return 'level-average'
  return 'level-need'
})

const resultLevelIcon = computed(() => {
  const s = totalScore.value
  if (s >= 85) return '🏆'
  if (s >= 70) return '🌟'
  if (s >= 55) return '📈'
  return '💪'
})

function getDimScore(key: string) {
  if (!result.value?.dimensionScores) return 0
  return result.value.dimensionScores[key] || 0
}

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

<style scoped lang="scss">
// ======== 未开始：AI主题开场 ========
.assessment-hero {
  position: relative;
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 40%, #1e3a5f 70%, #1a1a2e 100%);
  border-radius: 20px;
  overflow: hidden;
  padding: 60px 40px 50px;
  text-align: center;
}

.hero-bg-decor {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.floating-icon {
  position: absolute;
  font-size: 36px;
  opacity: 0.08;
  animation: floatAround 12s ease-in-out infinite;
  &.i1 { top: 10%; left: 8%; animation-delay: 0s; font-size: 48px; }
  &.i2 { top: 15%; right: 12%; animation-delay: 2s; font-size: 40px; }
  &.i3 { bottom: 20%; left: 10%; animation-delay: 4s; font-size: 44px; }
  &.i4 { top: 40%; right: 6%; animation-delay: 6s; font-size: 38px; }
  &.i5 { bottom: 10%; right: 20%; animation-delay: 3s; font-size: 50px; }
}

.hero-dot-grid {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(circle, rgba(255,255,255,0.06) 1px, transparent 1px);
  background-size: 30px 30px;
}

@keyframes floatAround {
  0%, 100% { transform: translateY(0) rotate(0deg); }
  25% { transform: translateY(-20px) rotate(8deg); }
  50% { transform: translateY(-10px) rotate(-5deg); }
  75% { transform: translateY(-25px) rotate(3deg); }
}

.hero-content {
  position: relative;
  z-index: 1;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 18px;
  border-radius: 20px;
  background: rgba(59, 130, 246, 0.2);
  border: 1px solid rgba(59, 130, 246, 0.3);
  color: #93c5fd;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 20px;
  .badge-dot {
    width: 8px; height: 8px;
    border-radius: 50%;
    background: #60a5fa;
    animation: gentlePulse 2s ease-in-out infinite;
  }
}

.hero-title {
  font-size: 40px;
  font-weight: 800;
  margin-bottom: 14px;
  .title-gradient {
    background: linear-gradient(135deg, #60a5fa, #a78bfa, #f472b6);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }
}

.hero-desc {
  color: #94a3b8;
  font-size: 16px;
  max-width: 500px;
  margin: 0 auto 36px;
  line-height: 1.6;
}

.dimension-cards {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-bottom: 36px;
  flex-wrap: wrap;
}

.dim-card {
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 14px;
  padding: 18px 14px;
  width: 130px;
  transition: all 0.3s;
  &:hover {
    transform: translateY(-4px);
    background: rgba(255,255,255,0.1);
    border-color: rgba(255,255,255,0.15);
  }
  .dim-icon {
    width: 44px; height: 44px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
    margin: 0 auto 8px;
  }
  .dim-name {
    color: #e2e8f0;
    font-size: 14px;
    font-weight: 600;
  }
  .dim-hint {
    color: #64748b;
    font-size: 11px;
    margin-top: 4px;
  }
}

.hero-info {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0;
  margin-bottom: 32px;
  .info-item {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 0 28px;
  }
  .info-value {
    font-size: 24px;
    font-weight: 700;
    color: #e2e8f0;
  }
  .info-label {
    font-size: 12px;
    color: #64748b;
  }
  .info-divider {
    width: 1px; height: 36px;
    background: rgba(255,255,255,0.1);
  }
}

.start-btn {
  height: 52px;
  padding: 0 48px;
  font-size: 18px;
  font-weight: 600;
  border-radius: 14px;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  border: none;
  box-shadow: 0 8px 32px rgba(59, 130, 246, 0.4);
  transition: all 0.3s;
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 12px 40px rgba(59, 130, 246, 0.5);
  }
}

// ======== 答题中 ========
.quiz-header {
  background: #fff;
  border-radius: 16px;
  padding: 20px 28px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
  margin-bottom: 20px;
}

.quiz-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  .quiz-count { font-size: 15px; color: #6b7280; font-weight: 500; }
}

.quiz-progress-wrap {
  position: relative;
}

.quiz-progress-bar {
  height: 6px;
  background: #e5e7eb;
  border-radius: 3px;
  position: relative;
  margin-bottom: 24px;
  .quiz-progress-fill {
    height: 100%;
    background: linear-gradient(90deg, #3b82f6, #8b5cf6);
    border-radius: 3px;
    transition: width 0.4s ease;
  }
}

.quiz-progress-dots {
  display: flex;
  justify-content: space-between;
  position: absolute;
  top: -8px;
  left: 0;
  right: 0;
  .prog-dot {
    width: 22px; height: 22px;
    border-radius: 50%;
    background: #e5e7eb;
    color: #9ca3af;
    font-size: 10px;
    font-weight: 600;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.3s;
    &.done { background: #3b82f6; color: #fff; }
    &.active { background: #fff; color: #3b82f6; border: 2px solid #3b82f6; transform: scale(1.2); }
  }
}

.quiz-card {
  background: #fff;
  border-radius: 16px;
  padding: 36px 32px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
}

.quiz-question-wrap {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 28px;
  .quiz-q-num {
    flex-shrink: 0;
    width: 40px; height: 40px;
    background: linear-gradient(135deg, #3b82f6, #6366f1);
    color: #fff;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    font-weight: 700;
  }
  .quiz-question {
    font-size: 18px;
    color: #1f2937;
    line-height: 1.6;
    font-weight: 600;
  }
}

.quiz-options {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 28px;
}

.quiz-option {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 20px;
  border: 2px solid #e5e7eb;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.25s;
  &:hover { border-color: #93c5fd; background: #f8faff; }
  &.selected {
    border-color: #3b82f6;
    background: #eff6ff;
    .opt-letter { background: #3b82f6; color: #fff; }
  }
  .opt-letter {
    flex-shrink: 0;
    width: 32px; height: 32px;
    border-radius: 8px;
    background: #f3f4f6;
    color: #6b7280;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    font-size: 14px;
    transition: all 0.25s;
  }
  .opt-text { flex: 1; font-size: 15px; color: #374151; }
  .opt-check { color: #3b82f6; font-weight: 700; font-size: 18px; }
}

.quiz-actions {
  display: flex;
  justify-content: space-between;
}

// ======== 结果报告 ========
.result-hero {
  text-align: center;
  padding: 30px 0 20px;
}

.result-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 22px;
  border-radius: 20px;
  font-weight: 600;
  font-size: 15px;
  margin-bottom: 12px;
  &.level-excellent { background: #d1fae5; color: #065f46; border: 1px solid #6ee7b7; }
  &.level-good { background: #dbeafe; color: #1e40af; border: 1px solid #93c5fd; }
  &.level-average { background: #fef3c7; color: #92400e; border: 1px solid #fcd34d; }
  &.level-need { background: #fee2e2; color: #991b1b; border: 1px solid #fca5a5; }
  .level-icon { font-size: 20px; }
}

.result-title {
  font-size: 28px;
  font-weight: 800;
  color: #1f2937;
}

.result-subtitle {
  color: #6b7280;
  font-size: 15px;
  margin-top: 6px;
}

.result-score-card {
  text-align: center;
  padding: 20px 0;
}

.score-ring-wrap {
  position: relative;
  display: inline-block;
  margin: 0 auto;
}

.score-ring {
  width: 180px;
  height: 180px;
  circle { transition: stroke-dasharray 1.2s ease-out; }
}

.score-ring-animate {
  animation: ringFill 1.5s ease-out forwards;
}

@keyframes ringFill {
  from { stroke-dasharray: 0 440; }
}

.score-ring-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  .score-number { font-size: 42px; font-weight: 800; color: #1f2937; line-height: 1; }
  .score-unit { font-size: 14px; color: #6b7280; }
}

.score-level-tag {
  font-size: 16px;
  font-weight: 600;
  margin-top: 8px;
}

.score-hint {
  color: #9ca3af;
  font-size: 13px;
  margin-top: 4px;
}

// 维度得分
.result-dimensions {
  margin-top: 24px;
}

.section-label {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  .label-icon { font-size: 20px; }
}

.dim-scores-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.dim-score-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 18px 20px;
  transition: all 0.3s;
  &:hover {
    border-color: var(--dim-color);
    box-shadow: 0 4px 16px rgba(0,0,0,0.06);
  }
  .ds-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
    .ds-icon { font-size: 20px; }
    .ds-name { font-size: 14px; font-weight: 600; color: #374151; }
  }
  .ds-bar-wrap {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .ds-bar {
    flex: 1;
    height: 10px;
    background: #e5e7eb;
    border-radius: 5px;
    overflow: hidden;
    .ds-bar-fill {
      height: 100%;
      border-radius: 5px;
      transition: width 1s ease-out;
      position: relative;
      &::after {
        content: '';
        position: absolute;
        right: 0; top: 0; bottom: 0;
        width: 4px;
        background: rgba(255,255,255,0.6);
        border-radius: 2px;
      }
    }
  }
  .ds-value {
    font-size: 15px;
    font-weight: 700;
    color: var(--dim-color);
    min-width: 50px;
    text-align: right;
  }
}

// 优劣势
.result-analysis {
  margin-top: 24px;
}

.analysis-card {
  background: #fff;
  border-radius: 14px;
  padding: 24px;
  border: 1px solid #e5e7eb;
  &.strength { border-left: 4px solid #10b981; }
  &.weakness { border-left: 4px solid #f59e0b; }
  .analysis-header {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 16px;
    font-weight: 700;
    color: #1f2937;
    margin-bottom: 12px;
    .analysis-icon { font-size: 20px; }
  }
  .analysis-text {
    color: #6b7280;
    font-size: 14px;
    line-height: 1.7;
  }
}

.result-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 28px;
  flex-wrap: wrap;
}
</style>
