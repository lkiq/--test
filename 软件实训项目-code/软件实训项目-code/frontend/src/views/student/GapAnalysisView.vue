<template>
  <div class="gap-page">
    <!-- 有数据时 -->
    <template v-if="report">
      <!-- 顶部匹配概览 -->
      <div class="gap-hero" :style="{ '--match-color': gapColor(report.overallMatch || 0) }">
        <div class="gap-hero-bg">
          <div class="gh-circle c1"></div>
          <div class="gh-circle c2"></div>
        </div>
        <div class="gap-hero-content">
          <div class="gh-left">
            <el-tag effect="dark" class="gh-badge">🎯 目标岗位</el-tag>
            <h1 class="gh-title">{{ report.jobTitle }}</h1>
            <p class="gh-desc">以下是你的能力与该岗位要求的差距分析</p>
            <el-button type="primary" size="large" class="gh-action" @click="$router.push('/student/learning-path')">
              📚 生成学习路径 <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
          <div class="gh-right">
            <div class="match-gauge">
              <svg viewBox="0 0 140 140">
                <circle cx="70" cy="70" r="60" fill="none" stroke="#e5e7eb" stroke-width="10" />
                <circle cx="70" cy="70" r="60" fill="none" :stroke="gapColor(report.overallMatch || 0)"
                  stroke-width="10" stroke-linecap="round"
                  :stroke-dasharray="(report.overallMatch || 0) * 3.77 + ' ' + (377 - (report.overallMatch || 0) * 3.77)"
                  transform="rotate(-90 70 70)"
                  class="gauge-animate" />
              </svg>
              <div class="gauge-center">
                <span class="gauge-value">{{ report.overallMatch || 0 }}</span>
                <span class="gauge-label">综合匹配度</span>
                <span class="gauge-sub" :style="{ color: gapColor(report.overallMatch || 0) }">
                  {{ matchLevel(report.overallMatch || 0) }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 技能差距矩阵 -->
      <div class="gap-matrix-section">
        <h3 class="section-title-big">
          <span class="st-icon">📋</span> 技能差距矩阵
          <span class="st-sub">紫色=岗位要求 · 蓝色=你的水平</span>
        </h3>

        <div class="gap-cards">
          <div
            v-for="gap in report.gaps || []"
            :key="gap.skillName"
            class="gap-card"
            :class="'gap-' + gapClass(gap.gapDegree)"
          >
            <div class="gc-header">
              <span class="gc-skill">{{ gap.skillName }}</span>
              <el-tag
                :type="gap.gapDegree === '严重不足' ? 'danger' : gap.gapDegree === '需要提升' ? 'warning' : 'success'"
                size="small"
                effect="dark"
              >
                {{ gap.gapDegree }}
              </el-tag>
            </div>

            <div class="gc-compare">
              <div class="gc-bar-row">
                <span class="gc-bar-label">你的水平</span>
                <div class="gc-bar-wrap">
                  <div class="gc-bar gc-bar-user" :style="{ width: levelPercent(gap.userLevel) + '%' }">
                    <span class="gc-bar-text">{{ gap.userLevel }}</span>
                  </div>
                </div>
              </div>
              <div class="gc-bar-row">
                <span class="gc-bar-label">岗位要求</span>
                <div class="gc-bar-wrap">
                  <div class="gc-bar gc-bar-req" :style="{ width: levelPercent(gap.requiredLevel) + '%' }">
                    <span class="gc-bar-text">{{ gap.requiredLevel }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 雷达图 -->
      <div v-if="report.radarChart" class="gap-radar-section">
        <h3 class="section-title-big">
          <span class="st-icon">📊</span> 能力雷达图对比
        </h3>
        <div class="radar-card">
          <RadarChart :data="report.radarChart" />
        </div>
      </div>

      <!-- 底部操作 -->
      <div class="gap-actions">
        <el-button size="large" @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon> 返回岗位列表
        </el-button>
        <el-button type="primary" size="large" @click="$router.push('/student/learning-path')">
          📚 生成学习路径 <el-icon><ArrowRight /></el-icon>
        </el-button>
        <el-button type="warning" size="large" @click="$router.push('/student/interview')">
          🎤 模拟面试练习
        </el-button>
      </div>
    </template>

    <!-- 空状态 -->
    <div v-else class="gap-empty">
      <div class="empty-visual">
        <div class="empty-icon-bg">🔍</div>
        <div class="empty-ripple r1"></div>
        <div class="empty-ripple r2"></div>
      </div>
      <h2>还没有差距分析数据</h2>
      <p>在岗位匹配页面选择一个心仪的岗位，AI将为你生成详细的能力差距分析报告</p>
      <div class="empty-features">
        <div class="ef-item">
          <span class="ef-icon">📊</span>
          <span>技能雷达图</span>
        </div>
        <div class="ef-item">
          <span class="ef-icon">🎯</span>
          <span>差距精准定位</span>
        </div>
        <div class="ef-item">
          <span class="ef-icon">📚</span>
          <span>学习路径推荐</span>
        </div>
      </div>
      <el-button type="primary" size="large" @click="$router.push('/student/job-matching')">
        前往岗位匹配
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowRight, ArrowLeft } from '@element-plus/icons-vue'
import { analyzeGap } from '@/api/student'
import RadarChart from '@/components/charts/RadarChart.vue'

const route = useRoute()
const report = ref<any>(null)

onMounted(async () => {
  const jobId = route.query.jobId
  if (jobId) {
    const res: any = await analyzeGap(Number(jobId))
    report.value = res.data
  }
})

function gapColor(percentage: number) {
  if (percentage >= 80) return '#10b981'
  if (percentage >= 60) return '#3b82f6'
  if (percentage >= 40) return '#f59e0b'
  return '#ef4444'
}

function matchLevel(pct: number): string {
  if (pct >= 80) return '高度匹配'
  if (pct >= 60) return '基本匹配'
  if (pct >= 40) return '差距较大'
  return '差距显著'
}

function gapClass(degree: string): string {
  if (degree === '严重不足') return 'critical'
  if (degree === '需要提升') return 'warning'
  return 'ok'
}

function levelPercent(level: string): number {
  const map: Record<string, number> = { '精通': 95, '熟练': 80, '掌握': 55, '了解': 30, '入门': 15 }
  return map[level] || 40
}
</script>

<style scoped lang="scss">
// ======== Hero ========
.gap-hero {
  position: relative;
  background: linear-gradient(135deg, #0f172a, #1e293b);
  border-radius: 20px;
  overflow: hidden;
  margin-bottom: 20px;
}

.gap-hero-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  .gh-circle {
    position: absolute;
    border-radius: 50%;
    border: 1px solid rgba(255,255,255,0.06);
    &.c1 { width: 260px; height: 260px; top: -80px; right: -40px; }
    &.c2 { width: 180px; height: 180px; bottom: -50px; left: 20%; }
  }
}

.gap-hero-content {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 36px 40px;
  gap: 40px;
}

.gh-left {
  flex: 1;
  .gh-badge { margin-bottom: 12px; font-weight: 600; }
  .gh-title { font-size: 30px; font-weight: 800; color: #fff; margin: 0 0 8px; }
  .gh-desc { color: #94a3b8; font-size: 14px; margin-bottom: 20px; }
  .gh-action {
    height: 44px;
    border-radius: 12px;
    font-weight: 600;
    background: linear-gradient(135deg, #6366f1, #8b5cf6);
    border: none;
    box-shadow: 0 4px 20px rgba(99, 102, 241, 0.4);
    &:hover { transform: translateY(-2px); }
  }
}

.gh-right {
  flex-shrink: 0;
}

.match-gauge {
  position: relative;
  width: 140px; height: 140px;
  .gauge-animate {
    animation: gaugeFill 1.5s ease-out forwards;
    stroke-dasharray: 0 377;
  }
  .gauge-center {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    .gauge-value { font-size: 36px; font-weight: 800; color: #fff; line-height: 1; }
    .gauge-label { font-size: 11px; color: #94a3b8; margin-top: 2px; }
    .gauge-sub { font-size: 12px; font-weight: 600; margin-top: 2px; }
  }
}

@keyframes gaugeFill {
  to { stroke-dasharray: var(--target-dash) 377; }
}

// ======== 矩阵 ========
.gap-matrix-section {
  margin-bottom: 20px;
}

.section-title-big {
  font-size: 18px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 10px;
  .st-icon { font-size: 22px; }
  .st-sub {
    font-size: 12px;
    color: #9ca3af;
    font-weight: 400;
    margin-left: auto;
  }
}

.gap-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 14px;
}

.gap-card {
  background: #fff;
  border-radius: 14px;
  padding: 20px;
  border-left: 5px solid #e5e7eb;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  transition: all 0.3s;
  &:hover { box-shadow: 0 4px 20px rgba(0,0,0,0.08); transform: translateY(-2px); }
  &.gap-critical { border-left-color: #ef4444; }
  &.gap-warning { border-left-color: #f59e0b; }
  &.gap-ok { border-left-color: #10b981; }
}

.gc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  .gc-skill { font-size: 15px; font-weight: 700; color: #1f2937; }
}

.gc-compare {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.gc-bar-row {
  display: flex;
  align-items: center;
  gap: 12px;
  .gc-bar-label {
    width: 60px;
    font-size: 12px;
    color: #6b7280;
    text-align: right;
    flex-shrink: 0;
  }
  .gc-bar-wrap {
    flex: 1;
    height: 24px;
    background: #f3f4f6;
    border-radius: 12px;
    overflow: hidden;
  }
  .gc-bar {
    height: 100%;
    border-radius: 12px;
    display: flex;
    align-items: center;
    padding-left: 12px;
    transition: width 1s ease-out;
    .gc-bar-text {
      font-size: 12px;
      font-weight: 600;
      color: #fff;
    }
  }
  .gc-bar-user {
    background: linear-gradient(90deg, #3b82f6, #60a5fa);
  }
  .gc-bar-req {
    background: linear-gradient(90deg, #8b5cf6, #a78bfa);
  }
}

// ======== 雷达图 ========
.gap-radar-section {
  margin-bottom: 20px;
  .radar-card {
    background: #fff;
    border-radius: 16px;
    padding: 24px;
    box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  }
}

// ======== 操作 ========
.gap-actions {
  display: flex;
  justify-content: center;
  gap: 14px;
  flex-wrap: wrap;
}

// ======== 空状态 ========
.gap-empty {
  text-align: center;
  padding: 80px 20px;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
}

.empty-visual {
  position: relative;
  display: inline-block;
  margin-bottom: 24px;
  .empty-icon-bg {
    width: 100px; height: 100px;
    border-radius: 28px;
    background: linear-gradient(135deg, #fef2f2, #fef3c7, #ecfdf5);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 46px;
  }
  .empty-ripple {
    position: absolute;
    border-radius: 50%;
    border: 2px solid #e5e7eb;
    &.r1 {
      inset: -12px;
      animation: ripple 2.5s ease-out infinite;
    }
    &.r2 {
      inset: -24px;
      animation: ripple 2.5s ease-out 0.5s infinite;
    }
  }
}

@keyframes ripple {
  0% { transform: scale(1); opacity: 0.6; }
  100% { transform: scale(1.5); opacity: 0; }
}

.gap-empty h2 {
  font-size: 22px; font-weight: 700; color: #1f2937; margin-bottom: 8px;
}
.gap-empty p {
  color: #6b7280; font-size: 14px; max-width: 400px; margin: 0 auto 28px;
}

.empty-features {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-bottom: 28px;
  .ef-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 20px;
    background: #f9fafb;
    border-radius: 10px;
    border: 1px solid #e5e7eb;
    font-size: 14px;
    color: #374151;
    .ef-icon { font-size: 18px; }
  }
}
</style>
