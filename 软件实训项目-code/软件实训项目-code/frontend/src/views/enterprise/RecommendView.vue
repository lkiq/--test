<template>
  <div class="recommend-page">
    <!-- Hero -->
    <div class="rm-hero">
      <div class="rmh-glow"></div>
      <div class="rmh-content">
        <div class="rmh-icon">🔮</div>
        <h1>AI 项目需求与候选人推荐</h1>
        <p>输入项目需求，AI 将智能分析并推荐最匹配的岗位方案和候选人</p>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="rm-input-card">
      <div class="rmi-header">
        <span class="rmi-step">STEP 1</span>
        <h3>描述项目需求</h3>
      </div>
      <div class="rmi-body">
        <el-input
          v-model="projectDesc"
          type="textarea"
          :rows="5"
          placeholder="请详细描述您的项目需求（不少于20字），例如：我们需要开发一款面向大学生群体的IT求职平台，包含岗位推荐、能力测评、AI模拟面试等功能，前后端分离架构..."
          class="rmi-textarea"
        />
        <div class="rmi-footer">
          <span class="rmi-count" :class="{ valid: projectDesc.length >= 20 }">
            已输入 {{ projectDesc.length }} 字 {{ projectDesc.length < 20 ? `（还需 ${20 - projectDesc.length} 字）` : '✓' }}
          </span>
          <el-button
            type="primary"
            size="large"
            @click="submitRecommend"
            :loading="loading"
            :disabled="projectDesc.length < 20"
            class="rmi-submit"
          >
            🤖 AI 分析并推荐
          </el-button>
        </div>
      </div>
    </div>

    <!-- 推荐结果 -->
    <template v-if="result">
      <!-- 推荐岗位 -->
      <div class="rm-positions" v-if="result.positions?.length">
        <div class="rm-section-title">
          <span class="rmst-icon">📋</span>
          <div>
            <h3>推荐岗位</h3>
            <p>基于项目需求 AI 智能生成的岗位方案</p>
          </div>
          <span class="rmst-count">{{ result.positions.length }} 个岗位</span>
        </div>

        <div class="rm-pos-grid">
          <div v-for="pos in result.positions" :key="pos.positionTitle" class="rm-pos-card">
            <div class="rmpc-header">
              <h4>{{ pos.positionTitle }}</h4>
              <el-tag type="warning" effect="dark" size="small">需求 {{ pos.headcount }} 人</el-tag>
            </div>
            <div class="rmpc-skills">
              <el-tag
                v-for="r in pos.skillRequirements"
                :key="r.skillName"
                size="small"
                effect="plain"
                :type="reqType(r.requiredLevel)"
              >
                {{ r.skillName }} · {{ r.requiredLevel }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- 候选人推荐 -->
      <div class="rm-candidates" v-if="result.candidates?.length">
        <div class="rm-section-title">
          <span class="rmst-icon">👥</span>
          <div>
            <h3>候选人推荐</h3>
            <p>AI 匹配的最适合候选人</p>
          </div>
          <span class="rmst-count">{{ result.candidates.length }} 人</span>
        </div>

        <div class="rm-cand-list">
          <div v-for="cand in result.candidates" :key="cand.userId || cand.username" class="rm-cand-card">
            <div class="rmcc-left">
              <div class="rmcc-avatar" :style="{ background: avatarColor(cand.username) }">
                {{ (cand.username || '?')[0]?.toUpperCase() }}
              </div>
            </div>
            <div class="rmcc-body">
              <div class="rmcc-name">{{ cand.username }}</div>
              <div class="rmcc-reason">{{ cand.recommendReason }}</div>
              <div class="rmcc-scores">
                <div class="rmcc-score-item">
                  <span class="rmcc-si-label">综合匹配</span>
                  <div class="rmcc-si-bar">
                    <div class="rmcc-si-fill" :style="{ width: (cand.matchScore || 0) + '%', background: matchGradient(cand.matchScore) }"></div>
                  </div>
                  <span class="rmcc-si-val">{{ cand.matchScore || 0 }}%</span>
                </div>
                <div class="rmcc-score-item">
                  <span class="rmcc-si-label">技能匹配</span>
                  <div class="rmcc-si-bar"><div class="rmcc-si-fill" :style="{ width: (cand.skillScore || 0) + '%', background: '#3b82f6' }"></div></div>
                  <span class="rmcc-si-val">{{ cand.skillScore || 0 }}%</span>
                </div>
                <div class="rmcc-score-item">
                  <span class="rmcc-si-label">测评适配</span>
                  <div class="rmcc-si-bar"><div class="rmcc-si-fill" :style="{ width: (cand.assessmentScore || 0) + '%', background: '#8b5cf6' }"></div></div>
                  <span class="rmcc-si-val">{{ cand.assessmentScore || 0 }}%</span>
                </div>
                <div class="rmcc-score-item">
                  <span class="rmcc-si-label">学习进度</span>
                  <div class="rmcc-si-bar"><div class="rmcc-si-fill" :style="{ width: (cand.learningScore || 0) + '%', background: '#10b981' }"></div></div>
                  <span class="rmcc-si-val">{{ cand.learningScore || 0 }}%</span>
                </div>
              </div>
            </div>
            <div class="rmcc-right">
              <el-button type="primary" size="small" plain round>联系候选人</el-button>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 空状态 -->
    <div v-else class="rm-empty">
      <div class="rme-illustration">
        <div class="rme-icon-wrap">🔮</div>
        <div class="rme-rings">
          <div class="rme-ring r1"></div>
          <div class="rme-ring r2"></div>
        </div>
      </div>
      <h3>AI 智能匹配</h3>
      <p>输入项目需求后，AI 将自动分析并推荐最佳岗位方案与候选人</p>
      <div class="rme-features">
        <div class="rme-f-item">
          <span>🧠</span> 智能需求分析
        </div>
        <div class="rme-f-item">
          <span>🎯</span> 岗位自动匹配
        </div>
        <div class="rme-f-item">
          <span>👥</span> 候选人精准推荐
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { recommend } from '@/api/enterprise'

const projectDesc = ref('')
const loading = ref(false)
const result = ref<any>(null)

const avatarColors = ['#3b82f6', '#8b5cf6', '#10b981', '#f59e0b', '#ef4444', '#ec4899']
function avatarColor(name: string): string {
  let hash = 0
  for (let i = 0; i < (name || '').length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash)
  return avatarColors[Math.abs(hash) % avatarColors.length]
}

function reqType(level: string): string {
  if (level === '精通' || level === '熟练') return 'success'
  if (level === '掌握') return 'warning'
  return 'danger'
}

function matchGradient(score: number): string {
  if (score >= 80) return 'linear-gradient(90deg, #10b981, #34d399)'
  if (score >= 60) return 'linear-gradient(90deg, #3b82f6, #60a5fa)'
  if (score >= 40) return 'linear-gradient(90deg, #f59e0b, #fbbf24)'
  return 'linear-gradient(90deg, #ef4444, #f87171)'
}

async function submitRecommend() {
  if (projectDesc.value.length < 20) return
  loading.value = true
  try {
    const res: any = await recommend({ projectDescription: projectDesc.value })
    result.value = res.data
  } finally { loading.value = false }
}
</script>

<style scoped lang="scss">
// ======== Hero ========
.rm-hero {
  position: relative;
  background: linear-gradient(135deg, #0f172a, #1e1b4b, #312e81);
  border-radius: 20px;
  padding: 36px;
  margin-bottom: 24px;
  text-align: center;
  overflow: hidden;
}
.rmh-glow {
  position: absolute;
  top: -30%; left: 50%; transform: translateX(-50%);
  width: 350px; height: 350px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(139,92,246,0.18), transparent);
  pointer-events: none;
}
.rmh-content { position: relative; z-index: 1; }
.rmh-icon {
  width: 60px; height: 60px;
  border-radius: 18px;
  background: rgba(255,255,255,0.12);
  display: flex; align-items: center; justify-content: center;
  font-size: 28px;
  margin: 0 auto 12px;
}
.rm-hero h1 { font-size: 26px; font-weight: 800; color: #fff; margin: 0 0 6px; }
.rm-hero p { color: #c7d2fe; font-size: 14px; }

// ======== 输入区 ========
.rm-input-card {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.04);
  margin-bottom: 24px;
}
.rmi-header {
  display: flex; align-items: center; gap: 12px; margin-bottom: 16px;
  .rmi-step {
    padding: 4px 12px;
    border-radius: 8px;
    background: #eef2ff; color: #4f46e5;
    font-size: 12px; font-weight: 700;
  }
  h3 { font-size: 17px; font-weight: 700; color: #1f2937; margin: 0; }
}
.rmi-body {
  .rmi-textarea :deep(.el-textarea__inner) {
    border-radius: 12px;
    font-size: 14px;
    line-height: 1.7;
  }
  .rmi-footer {
    display: flex; align-items: center; justify-content: space-between;
    margin-top: 16px;
    .rmi-count { font-size: 13px; color: #9ca3af; &.valid { color: #10b981; } }
    .rmi-submit {
      height: 44px;
      border-radius: 12px;
      font-weight: 700;
      background: linear-gradient(135deg, #6366f1, #8b5cf6);
      border: none;
      box-shadow: 0 4px 16px rgba(99,102,241,0.3);
    }
  }
}

// ======== 推荐结果 ========
.rm-section-title {
  display: flex; align-items: center; gap: 14px;
  margin-bottom: 16px;
  .rmst-icon { font-size: 28px; }
  h3 { font-size: 17px; font-weight: 700; color: #1f2937; margin: 0; }
  p { font-size: 12px; color: #9ca3af; margin: 2px 0 0; }
  .rmst-count {
    margin-left: auto;
    padding: 4px 14px;
    border-radius: 20px;
    background: #f3f4f6;
    font-size: 13px; color: #374151; font-weight: 600;
  }
}

.rm-positions { margin-bottom: 24px; }

.rm-pos-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.rm-pos-card {
  background: #fff;
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  border: 1px solid #e5e7eb;
  transition: all 0.3s;
  &:hover { border-color: #c7d2fe; box-shadow: 0 4px 16px rgba(99,102,241,0.08); }
  .rmpc-header {
    display: flex; align-items: center; justify-content: space-between;
    margin-bottom: 12px;
    h4 { font-size: 15px; font-weight: 700; color: #1f2937; margin: 0; }
  }
  .rmpc-skills { display: flex; flex-wrap: wrap; gap: 6px; }
}

// ======== 候选人 ========
.rm-cand-list {
  display: flex; flex-direction: column; gap: 12px;
}

.rm-cand-card {
  background: #fff;
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  display: flex; align-items: flex-start; gap: 16px;
  border: 1px solid #e5e7eb;
  transition: all 0.3s;
  &:hover { border-color: #c7d2fe; box-shadow: 0 4px 16px rgba(0,0,0,0.08); }
}

.rmcc-left {
  flex-shrink: 0;
  .rmcc-avatar {
    width: 44px; height: 44px;
    border-radius: 14px;
    color: #fff;
    display: flex; align-items: center; justify-content: center;
    font-size: 18px; font-weight: 700;
  }
}

.rmcc-body {
  flex: 1;
  .rmcc-name { font-size: 15px; font-weight: 700; color: #1f2937; margin-bottom: 4px; }
  .rmcc-reason { font-size: 13px; color: #6b7280; margin-bottom: 12px; }
}

.rmcc-scores {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px 16px;
  .rmcc-score-item {
    display: flex; align-items: center; gap: 8px;
    .rmcc-si-label { font-size: 11px; color: #9ca3af; width: 56px; flex-shrink: 0; }
    .rmcc-si-bar {
      flex: 1; height: 5px; background: #e5e7eb; border-radius: 3px; overflow: hidden;
      .rmcc-si-fill { height: 100%; border-radius: 3px; transition: width 1s ease-out; }
    }
    .rmcc-si-val { font-size: 12px; font-weight: 700; color: #374151; width: 36px; text-align: right; }
  }
}

.rmcc-right {
  flex-shrink: 0;
  padding-top: 4px;
}

// ======== 空状态 ========
.rm-empty {
  text-align: center;
  padding: 80px 20px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
}

.rme-illustration {
  position: relative; display: inline-block; margin-bottom: 20px;
  .rme-icon-wrap {
    width: 80px; height: 80px;
    border-radius: 22px;
    background: linear-gradient(135deg, #eef2ff, #ede9fe);
    display: flex; align-items: center; justify-content: center;
    font-size: 36px;
  }
  .rme-rings { position: absolute; inset: 0; }
  .rme-ring {
    position: absolute;
    inset: -10px;
    border-radius: 32px;
    border: 2px solid #c7d2fe;
    animation: ringPulse 2.5s ease-out infinite;
    &.r2 { inset: -20px; animation-delay: 0.5s; }
  }
}

@keyframes ringPulse {
  0% { transform: scale(1); opacity: 1; }
  100% { transform: scale(1.2); opacity: 0; }
}

.rm-empty h3 { font-size: 18px; font-weight: 700; color: #1f2937; margin-bottom: 8px; }
.rm-empty p { color: #6b7280; font-size: 14px; margin-bottom: 20px; }

.rme-features {
  display: flex; justify-content: center; gap: 16px; flex-wrap: wrap;
  .rme-f-item {
    display: flex; align-items: center; gap: 6px;
    padding: 10px 18px;
    background: #f9fafb;
    border-radius: 10px;
    border: 1px solid #e5e7eb;
    font-size: 13px; color: #374151;
    span { font-size: 16px; }
  }
}
</style>
