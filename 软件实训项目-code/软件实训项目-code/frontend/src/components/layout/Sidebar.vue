<template>
  <el-menu
    :default-active="activeMenu"
    class="el-menu-vertical"
    background-color="#304156"
    text-color="#bfcbd9"
    active-text-color="#409EFF"
    router
  >
    <div style="height:60px;display:flex;align-items:center;justify-content:center;color:#fff;font-size:18px;font-weight:bold;">
      AI求职辅导平台
    </div>

    <!-- 学生端菜单 -->
    <template v-if="role === 'STUDENT'">
      <el-menu-item index="/student/home"><el-icon><HomeFilled /></el-icon>学生首页</el-menu-item>
      <el-menu-item index="/student/profile"><el-icon><User /></el-icon>求职画像</el-menu-item>
      <el-menu-item index="/student/assessment"><el-icon><EditPen /></el-icon>能力测评</el-menu-item>
      <el-menu-item index="/student/career-exploration"><el-icon><Compass /></el-icon>方向探索</el-menu-item>
      <el-menu-item index="/student/job-matching"><el-icon><Search /></el-icon>岗位匹配</el-menu-item>
      <el-menu-item index="/student/gap-analysis"><el-icon><DataAnalysis /></el-icon>差距分析</el-menu-item>
      <el-menu-item index="/student/learning-path"><el-icon><List /></el-icon>学习路径</el-menu-item>
      <el-menu-item index="/student/resume-optimize"><el-icon><Document /></el-icon>简历优化</el-menu-item>
      <el-menu-item index="/student/interview"><el-icon><ChatDotRound /></el-icon>模拟面试</el-menu-item>
      <el-menu-item index="/student/learning-progress"><el-icon><TrendCharts /></el-icon>学习进度</el-menu-item>
      <el-menu-item index="/customer-service"><el-icon><Service /></el-icon>智能客服</el-menu-item>
    </template>

    <!-- 企业端菜单 -->
    <template v-if="role === 'HR'">
      <el-menu-item index="/enterprise/home"><el-icon><HomeFilled /></el-icon>企业首页</el-menu-item>
      <el-menu-item index="/enterprise/recommend"><el-icon><Search /></el-icon>项目推荐</el-menu-item>
      <el-menu-item index="/customer-service"><el-icon><Service /></el-icon>智能客服</el-menu-item>
    </template>

    <!-- 管理端菜单 -->
    <template v-if="role === 'ADMIN'">
      <el-menu-item index="/admin/home"><el-icon><HomeFilled /></el-icon>管理首页</el-menu-item>
      <el-menu-item index="/admin/users"><el-icon><UserFilled /></el-icon>用户管理</el-menu-item>
      <el-menu-item index="/admin/skills"><el-icon><Setting /></el-icon>技能词典</el-menu-item>
    </template>
  </el-menu>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const userStore = useUserStore()
const role = computed(() => userStore.role)
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.el-menu-vertical {
  height: 100%;
  border-right: none;
}
</style>
