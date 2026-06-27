<template>
  <div class="career-page">
    <!-- Hero Banner -->
    <div class="career-hero">
      <div class="hero-decor">
        <div class="decor-circle c1"></div>
        <div class="decor-circle c2"></div>
        <div class="decor-circle c3"></div>
      </div>
      <div class="hero-inner">
        <div class="hero-icon-wrap">
          <span class="hero-icon">🧭</span>
        </div>
        <h1 class="hero-title">AI 职业方向探索</h1>
        <p class="hero-subtitle">告诉AI你的兴趣和偏好，智能推荐最适合你的职业道路</p>
        <div class="hero-tags">
          <span class="hint-tag">💡 试试说："我对后端开发感兴趣，喜欢用Java和Python"</span>
          <span class="hint-tag">🎯 "我喜欢数据分析，对机器学习也有涉猎"</span>
          <span class="hint-tag">🚀 "我想做全栈开发，前端React后端Spring"</span>
        </div>
      </div>
    </div>

    <!-- 主内容区：左右分栏 -->
    <el-row :gutter="20" class="career-main">
      <!-- 左侧：对话 -->
      <el-col :lg="10" :md="12" :sm="24">
        <div class="chat-panel">
          <div class="chat-panel-header">
            <span class="cp-avatar">🤖</span>
            <div>
              <div class="cp-name">AI 职业规划师</div>
              <div class="cp-status">
                <span class="status-dot" :class="{ thinking: loading }"></span>
                {{ loading ? '分析中...' : '在线' }}
              </div>
            </div>
          </div>
          <ChatWindow
            :messages="messages"
            :loading="loading"
            placeholder="描述你的职业兴趣和偏好..."
            @send="handleSend"
          />
        </div>
      </el-col>

      <!-- 右侧：推荐结果 -->
      <el-col :lg="14" :md="12" :sm="24">
        <div class="results-panel" v-if="results.length">
          <div class="results-header">
            <span class="rh-icon">✨</span>
            <div>
              <h3>推荐方向</h3>
              <p>共 {{ results.length }} 个匹配结果</p>
            </div>
          </div>

          <div class="result-list">
            <div
              v-for="(item, idx) in results"
              :key="item.jobTitle"
              class="result-item"
              :style="{ animationDelay: (idx * 0.1) + 's' }"
            >
              <div class="ri-rank" :class="'rank-' + (idx + 1)">
                {{ idx + 1 }}
              </div>
              <div class="ri-body">
                <div class="ri-top">
                  <div class="ri-title-wrap">
                    <span class="ri-icon">{{ getRoleIcon(item.jobTitle) }}</span>
                    <div>
                      <h4 class="ri-title">{{ item.jobTitle }}</h4>
                      <span class="ri-priority" :class="'prio-' + item.learningPriority">
                        {{ item.learningPriority }}
                      </span>
                    </div>
                  </div>
                  <div class="ri-match">
                    <svg class="ri-ring" viewBox="0 0 60 60">
                      <circle cx="30" cy="30" r="25" fill="none" stroke="#e5e7eb" stroke-width="4" />
                      <circle cx="30" cy="30" r="25" fill="none"
                        :stroke="getMatchColor(item.matchScore)"
                        stroke-width="4" stroke-linecap="round"
                        :stroke-dasharray="(item.matchScore * 1.57) + ' ' + (157 - item.matchScore * 1.57)"
                        transform="rotate(-90 30 30)" />
                    </svg>
                    <span class="ri-match-text" :style="{ color: getMatchColor(item.matchScore) }">
                      {{ item.matchScore }}%
                    </span>
                  </div>
                </div>
                <p class="ri-reason">{{ item.reason }}</p>
                <div class="ri-footer">
                  <span class="ri-path">
                    <span class="path-icon">🛤️</span> {{ item.growthPath }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else class="results-empty">
          <div class="empty-illustration">
            <div class="empty-icon-bg">🎯</div>
            <div class="empty-pulse-ring"></div>
          </div>
          <h3>探索你的职业方向</h3>
          <p>在左侧与AI对话，它会根据你的兴趣和技能<br/>智能推荐最适合你的职业道路</p>
          <div class="empty-steps">
            <div class="empty-step">
              <span class="step-num">1</span>
              <span>描述兴趣偏好</span>
            </div>
            <div class="step-arrow">→</div>
            <div class="empty-step">
              <span class="step-num">2</span>
              <span>AI智能分析</span>
            </div>
            <div class="step-arrow">→</div>
            <div class="empty-step">
              <span class="step-num">3</span>
              <span>获取推荐方向</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ChatWindow from '@/components/common/ChatWindow.vue'
import { exploreCareer } from '@/api/student'

const messages = ref<any[]>([
  { role: 'assistant', content: '你好！我是AI职业规划助手。请告诉我你的职业兴趣和技术偏好，我会为你推荐最合适的职业方向。' }
])
const loading = ref(false)
const results = ref<any[]>([])

function getRoleIcon(title: string): string {
  const map: Record<string, string> = {
    '前端': '🎨', '后端': '⚙️', '全栈': '🚀', '数据': '📊', '移动': '📱',
    'AI': '🤖', '算法': '🧮', '测试': '🔍', '运维': '🖥️', '安全': '🔒',
    '产品': '💡', '设计': '🖌️', 'Java': '☕', 'Python': '🐍', 'Go': '🔷'
  }
  for (const [k, v] of Object.entries(map)) {
    if (title.includes(k)) return v
  }
  return '💼'
}

function getMatchColor(score: number): string {
  if (score >= 85) return '#10b981'
  if (score >= 70) return '#3b82f6'
  if (score >= 55) return '#f59e0b'
  return '#ef4444'
}

async function handleSend(text: string) {
  messages.value.push({ role: 'user', content: text })
  loading.value = true
  try {
    // 发送偏好文本 + 完整对话历史
    const history = messages.value.slice(0, -1).map(m => ({
      role: m.role,
      content: m.content
    }))
    const res: any = await exploreCareer({ preferences: text, history })
    const data = res.data
    results.value = data.directions || []
    messages.value.push({
      role: 'assistant',
      content: data.overallAnalysis || '分析完成！我已经根据你的偏好匹配了以下职业方向，请在右侧查看详细推荐结果。'
    })
  } catch {
    messages.value.push({ role: 'system', content: '分析失败，请稍后重试' })
  } finally { loading.value = false }
}
</script>

<style scoped lang="scss">
// ======== Hero Banner ========
.career-hero {
  position: relative;
  background: linear-gradient(135deg, #1e1b4b 0%, #312e81 30%, #3730a3 60%, #4338ca 100%);
  border-radius: 20px;
  padding: 40px;
  margin-bottom: 20px;
  overflow: hidden;
}

.hero-decor {
  position: absolute;
  inset: 0;
  pointer-events: none;
  .decor-circle {
    position: absolute;
    border-radius: 50%;
    opacity: 0.08;
    background: #fff;
    &.c1 { width: 200px; height: 200px; top: -40px; right: -60px; }
    &.c2 { width: 140px; height: 140px; bottom: -20px; left: 15%; }
    &.c3 { width: 100px; height: 100px; top: 30%; right: 25%; }
  }
}

.hero-inner {
  position: relative;
  z-index: 1;
  text-align: center;
}

.hero-icon-wrap {
  width: 70px; height: 70px;
  border-radius: 20px;
  background: rgba(255,255,255,0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  .hero-icon { font-size: 36px; }
}

.hero-title {
  font-size: 32px;
  font-weight: 800;
  color: #fff;
  margin-bottom: 8px;
}

.hero-subtitle {
  color: #c7d2fe;
  font-size: 15px;
  margin-bottom: 20px;
}

.hero-tags {
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
  .hint-tag {
    padding: 6px 16px;
    border-radius: 20px;
    background: rgba(255,255,255,0.1);
    color: #a5b4fc;
    font-size: 13px;
    border: 1px solid rgba(255,255,255,0.08);
    cursor: default;
  }
}

// ======== 主内容区 ========
.career-main {
  align-items: flex-start;
}

.chat-panel {
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
}

.chat-panel-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid #f3f4f6;
  .cp-avatar {
    width: 42px; height: 42px;
    border-radius: 12px;
    background: linear-gradient(135deg, #6366f1, #8b5cf6);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
  }
  .cp-name { font-size: 15px; font-weight: 700; color: #1f2937; }
  .cp-status {
    font-size: 12px; color: #6b7280;
    display: flex; align-items: center; gap: 6px;
  }
  .status-dot {
    width: 8px; height: 8px;
    border-radius: 50%;
    background: #10b981;
    &.thinking {
      background: #f59e0b;
      animation: gentlePulse 1s ease-in-out infinite;
    }
  }
}

// ======== 推荐结果面板 ========
.results-panel {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
  min-height: 200px;
}

.results-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 2px solid #f3f4f6;
  .rh-icon { font-size: 28px; }
  h3 { font-size: 18px; font-weight: 700; color: #1f2937; margin: 0; }
  p { font-size: 13px; color: #9ca3af; margin: 2px 0 0 0; }
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.result-item {
  display: flex;
  gap: 16px;
  padding: 20px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  transition: all 0.3s;
  animation: cardFadeIn 0.4s ease-out both;
  &:hover {
    border-color: #c7d2fe;
    box-shadow: 0 4px 20px rgba(99, 102, 241, 0.1);
    transform: translateX(4px);
  }
}

.ri-rank {
  flex-shrink: 0;
  width: 36px; height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 800;
  color: #fff;
  &.rank-1 { background: linear-gradient(135deg, #f59e0b, #fbbf24); }
  &.rank-2 { background: linear-gradient(135deg, #94a3b8, #cbd5e1); }
  &.rank-3 { background: linear-gradient(135deg, #d97706, #f59e0b); }
  &.rank-4, &.rank-5 { background: #e5e7eb; color: #6b7280; }
}

.ri-body { flex: 1; }

.ri-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}

.ri-title-wrap {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  .ri-icon { font-size: 24px; flex-shrink: 0; margin-top: 2px; }
  .ri-title { font-size: 16px; font-weight: 700; color: #1f2937; margin: 0; }
  .ri-priority {
    display: inline-block;
    padding: 2px 10px;
    border-radius: 6px;
    font-size: 11px;
    font-weight: 600;
    margin-top: 4px;
    &.prio-高 { background: #d1fae5; color: #065f46; }
    &.prio-中 { background: #fef3c7; color: #92400e; }
    &.prio-低 { background: #f3f4f6; color: #6b7280; }
  }
}

.ri-match {
  flex-shrink: 0;
  position: relative;
  width: 60px; height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  .ri-ring {
    position: absolute;
    width: 60px; height: 60px;
  }
  .ri-match-text {
    font-size: 14px;
    font-weight: 800;
    z-index: 1;
  }
}

.ri-reason {
  font-size: 14px;
  color: #6b7280;
  line-height: 1.6;
  margin-bottom: 10px;
}

.ri-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  .ri-path {
    font-size: 13px;
    color: #6366f1;
    display: flex;
    align-items: center;
    gap: 4px;
    .path-icon { font-size: 14px; }
  }
}

// ======== 空状态 ========
.results-empty {
  text-align: center;
  padding: 60px 20px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
}

.empty-illustration {
  position: relative;
  display: inline-block;
  margin-bottom: 20px;
  .empty-icon-bg {
    width: 90px; height: 90px;
    border-radius: 24px;
    background: linear-gradient(135deg, #eef2ff, #e0e7ff);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 44px;
  }
  .empty-pulse-ring {
    position: absolute;
    inset: -8px;
    border-radius: 32px;
    border: 2px solid #c7d2fe;
    animation: ringPulse 2s ease-out infinite;
  }
}

@keyframes ringPulse {
  0% { transform: scale(1); opacity: 1; }
  100% { transform: scale(1.15); opacity: 0; }
}

.results-empty h3 {
  font-size: 18px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 8px;
}

.results-empty p {
  color: #9ca3af;
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 24px;
}

.empty-steps {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  .empty-step {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 18px;
    background: #f9fafb;
    border-radius: 10px;
    border: 1px solid #e5e7eb;
    font-size: 13px;
    color: #374151;
    .step-num {
      width: 22px; height: 22px;
      border-radius: 50%;
      background: #6366f1;
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 11px;
      font-weight: 700;
    }
  }
  .step-arrow { color: #d1d5db; font-size: 18px; }
}

@keyframes cardFadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
