<template>
  <div class="header-bar">
    <div style="font-size:14px;color:#666;">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item v-for="item in breadcrumbs" :key="item">{{ item }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    <el-dropdown @command="handleCommand">
      <span style="cursor:pointer;display:flex;align-items:center;gap:8px;">
        <el-avatar :size="32" :icon="UserFilled" />
        <span>{{ userStore.username }}</span>
        <el-icon><ArrowDown /></el-icon>
      </span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="logout">退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { UserFilled, ArrowDown } from '@element-plus/icons-vue'

const route = useRoute()
const userStore = useUserStore()
const breadcrumbs = computed(() => {
  const name = route.name as string
  const map: Record<string, string> = {
    'StudentHome': '学生首页', 'Profile': '求职画像', 'Assessment': '能力测评',
    'CareerExploration': '方向探索', 'JobMatching': '岗位匹配', 'GapAnalysis': '差距分析',
    'LearningPath': '学习路径', 'LearningProgress': '学习进度', 'ResumeOptimize': '简历优化',
    'Interview': '模拟面试', 'EnterpriseHome': '企业首页', 'Recommend': '项目推荐',
    'AdminHome': '管理首页', 'UserManagement': '用户管理', 'SkillDictionary': '技能词典',
    'CustomerService': '智能客服'
  }
  return [map[name] || name || '']
})

function handleCommand(cmd: string) {
  if (cmd === 'logout') userStore.logout()
}
</script>
