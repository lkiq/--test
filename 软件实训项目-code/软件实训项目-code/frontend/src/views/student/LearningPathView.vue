<template>
  <div class="learning-page">
    <!-- Hero -->
    <div class="lp-hero">
      <div class="lph-left">
        <div class="lph-badge">
          <span class="badge-pulse"></span>AI 个性化规划
        </div>
        <h1 class="lph-title">学习路径</h1>
        <p class="lph-desc">基于你的能力差距，AI为你生成四阶段学习路径，助你高效提升</p>
      </div>
      <div class="lph-right">
        <el-button type="primary" size="large" class="generate-btn" @click="generate" :loading="loading">
          <el-icon><Refresh /></el-icon> {{ allTasks.length ? '更新学习路径' : '生成学习路径' }}
        </el-button>
      </div>
    </div>

    <!-- 四阶段时间线 -->
    <div v-if="allTasks.length" class="lp-timeline">
      <div
        v-for="(stage, idx) in stages"
        :key="stage"
        class="stage-block"
        :class="'stage-' + stage.toLowerCase()"
      >
        <!-- 阶段头部 -->
        <div class="stage-header">
          <div class="stage-marker" :style="{ background: stageColors[stage] }">
            <span class="stage-num">{{ idx + 1 }}</span>
          </div>
          <div class="stage-info">
            <div class="stage-badge" :style="{ background: stageColors[stage] + '20', color: stageColors[stage] }">
              {{ stageIcons[stage] }} {{ stageLabels[stage] }}
            </div>
            <div class="stage-meta">
              {{ stageDescs[stage] }} · {{ tasksByStage[stage]?.length || 0 }} 个任务
            </div>
          </div>
          <div class="stage-progress">
            <svg viewBox="0 0 50 50" class="sp-ring">
              <circle cx="25" cy="25" r="20" fill="none" stroke="#e5e7eb" stroke-width="4" />
              <circle cx="25" cy="25" r="20" fill="none" :stroke="stageColors[stage]"
                stroke-width="4" stroke-linecap="round"
                :stroke-dasharray="(stageProgress(stage) * 1.256) + ' ' + (125.6 - stageProgress(stage) * 1.256)"
                transform="rotate(-90 25 25)" />
            </svg>
            <span class="sp-text">{{ stageProgress(stage) }}%</span>
          </div>
        </div>

        <!-- 任务列表 -->
        <div class="stage-tasks" v-if="tasksByStage[stage]?.length">
          <div
            v-for="task in tasksByStage[stage]"
            :key="task.id"
            class="task-item"
            :class="'task-' + task.status.toLowerCase()"
          >
            <div class="task-check">
              <span v-if="task.status === 'COMPLETED'" class="check-done">✓</span>
              <span v-else-if="task.status === 'IN_PROGRESS'" class="check-active"></span>
              <span v-else class="check-pending"></span>
            </div>
            <div class="task-content">
              <span class="task-title">{{ task.title }}</span>
              <span class="task-due" v-if="task.dueDate">
                <el-icon><Calendar /></el-icon> {{ task.dueDate }}
              </span>
            </div>
            <div class="task-action">
              <el-select
                v-model="task.status"
                @change="(v) => updateTask(task.id, v)"
                size="small"
                class="task-select"
              >
                <el-option label="待开始" value="PENDING" />
                <el-option label="进行中" value="IN_PROGRESS" />
                <el-option label="已完成" value="COMPLETED" />
              </el-select>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="lp-empty">
      <div class="empty-art">
        <div class="art-stages">
          <div class="art-stage s1">📖</div>
          <div class="art-line"></div>
          <div class="art-stage s2">🔧</div>
          <div class="art-line"></div>
          <div class="art-stage s3">🚀</div>
          <div class="art-line"></div>
          <div class="art-stage s4">🎯</div>
        </div>
      </div>
      <h2>还没有学习路径</h2>
      <p>完成能力测评或差距分析后，AI将为你生成专属的四阶段学习路径<br/>从基础入门到面试冲刺，助你系统化成长</p>
      <div class="empty-actions">
        <el-button type="primary" size="large" @click="generate" :loading="loading">
          🤖 立即生成
        </el-button>
        <el-button size="large" @click="$router.push('/student/assessment')">
          📝 先去测评
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Refresh, Calendar } from '@element-plus/icons-vue'
import { generateLearningPath, getLearningTasks, updateTaskStatus } from '@/api/student'

const loading = ref(false)
const allTasks = ref<any[]>([])
const stages = ['BASIC', 'FRAMEWORK', 'PROJECT', 'INTERVIEW']
const stageLabels: Record<string, string> = {
  BASIC: '基础入门', FRAMEWORK: '框架进阶', PROJECT: '项目实战', INTERVIEW: '面试冲刺'
}
const stageIcons: Record<string, string> = {
  BASIC: '📖', FRAMEWORK: '🔧', PROJECT: '🚀', INTERVIEW: '🎯'
}
const stageDescs: Record<string, string> = {
  BASIC: '夯实基础', FRAMEWORK: '掌握框架', PROJECT: '积累经验', INTERVIEW: '冲刺Offer'
}
const stageColors: Record<string, string> = {
  BASIC: '#3b82f6', FRAMEWORK: '#8b5cf6', PROJECT: '#f59e0b', INTERVIEW: '#10b981'
}

const tasksByStage = computed(() => {
  const map: Record<string, any[]> = {}
  stages.forEach(s => map[s] = allTasks.value.filter(t => t.stage === s))
  return map
})

function stageProgress(stage: string): number {
  const tasks = tasksByStage.value[stage]
  if (!tasks || tasks.length === 0) return 0
  const done = tasks.filter(t => t.status === 'COMPLETED').length
  return Math.round((done / tasks.length) * 100)
}

async function generate() {
  loading.value = true
  try {
    await generateLearningPath()
    await fetchTasks()
  } finally { loading.value = false }
}

async function fetchTasks() {
  const res: any = await getLearningTasks()
  allTasks.value = res.data || []
}

async function updateTask(id: number, status: string) {
  await updateTaskStatus(id, status)
}

onMounted(fetchTasks)
</script>

<style scoped lang="scss">
// ======== Hero ========
.lp-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 20px;
  background: linear-gradient(135deg, #f0fdf4 0%, #ecfeff 50%, #fef3c7 100%);
  border-radius: 20px;
  padding: 32px 36px;
  margin-bottom: 24px;
  border: 1px solid #e5e7eb;
}

.lph-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  border-radius: 20px;
  background: rgba(99, 102, 241, 0.12);
  color: #4f46e5;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 10px;
  .badge-pulse {
    width: 7px; height: 7px;
    border-radius: 50%;
    background: #6366f1;
    animation: gentlePulse 1.5s ease-in-out infinite;
  }
}

.lph-title {
  font-size: 28px;
  font-weight: 800;
  color: #1f2937;
  margin: 0 0 6px;
}

.lph-desc {
  color: #6b7280;
  font-size: 14px;
  max-width: 400px;
}

.generate-btn {
  height: 48px;
  padding: 0 28px;
  border-radius: 14px;
  font-size: 16px;
  font-weight: 600;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border: none;
  box-shadow: 0 4px 20px rgba(99, 102, 241, 0.3);
  &:hover { transform: translateY(-2px); box-shadow: 0 6px 28px rgba(99, 102, 241, 0.45); }
}

// ======== 阶段时间线 ========
.lp-timeline {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.stage-block {
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  transition: all 0.3s;
  &:hover { box-shadow: 0 4px 20px rgba(0,0,0,0.08); }
}

.stage-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  border-bottom: 1px solid #f3f4f6;
}

.stage-marker {
  width: 44px; height: 44px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  .stage-num {
    color: #fff;
    font-size: 18px;
    font-weight: 800;
  }
}

.stage-info {
  flex: 1;
  .stage-badge {
    display: inline-block;
    padding: 3px 14px;
    border-radius: 8px;
    font-size: 14px;
    font-weight: 700;
    margin-bottom: 4px;
  }
  .stage-meta {
    font-size: 12px;
    color: #9ca3af;
  }
}

.stage-progress {
  display: flex;
  align-items: center;
  gap: 8px;
  .sp-ring {
    width: 50px; height: 50px;
  }
  .sp-text {
    font-size: 15px;
    font-weight: 700;
    color: #374151;
  }
}

// 任务列表
.stage-tasks {
  padding: 8px 24px 16px;
}

.task-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 10px;
  transition: all 0.25s;
  &:hover { background: #f9fafb; }
  &.task-completed {
    .task-title { text-decoration: line-through; color: #9ca3af; }
  }
  &.task-in_progress {
    background: #fefce8;
    border: 1px solid #fde68a;
  }
}

.task-check {
  flex-shrink: 0;
  .check-done {
    width: 26px; height: 26px;
    border-radius: 50%;
    background: #10b981;
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    font-weight: 700;
  }
  .check-active {
    width: 26px; height: 26px;
    border-radius: 50%;
    border: 2px solid #f59e0b;
    position: relative;
    &::after {
      content: '';
      position: absolute;
      inset: 4px;
      border-radius: 50%;
      background: #f59e0b;
      animation: gentlePulse 1.2s ease-in-out infinite;
    }
  }
  .check-pending {
    width: 26px; height: 26px;
    border-radius: 50%;
    border: 2px solid #d1d5db;
  }
}

.task-content {
  flex: 1;
  .task-title {
    font-size: 14px;
    color: #374151;
    font-weight: 500;
  }
  .task-due {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: #9ca3af;
    margin-top: 2px;
  }
}

.task-action {
  .task-select {
    width: 110px;
  }
}

// ======== 空状态 ========
.lp-empty {
  text-align: center;
  padding: 80px 20px;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
}

.empty-art {
  margin-bottom: 24px;
  .art-stages {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0;
  }
  .art-stage {
    width: 64px; height: 64px;
    border-radius: 18px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 28px;
    &.s1 { background: #dbeafe; }
    &.s2 { background: #ede9fe; }
    &.s3 { background: #fef3c7; }
    &.s4 { background: #d1fae5; }
  }
  .art-line {
    width: 40px; height: 3px;
    background: linear-gradient(90deg, #d1d5db, #e5e7eb);
    border-radius: 2px;
  }
}

.lp-empty h2 {
  font-size: 22px; font-weight: 700; color: #1f2937; margin-bottom: 8px;
}
.lp-empty p {
  color: #6b7280; font-size: 14px; line-height: 1.7; margin-bottom: 28px;
}

.empty-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
