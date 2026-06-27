import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/student/home'
  },
  // 登录/注册
  { path: '/login', name: 'Login', component: () => import('@/views/login/LoginView.vue'), meta: { noAuth: true } },
  { path: '/register', name: 'Register', component: () => import('@/views/register/RegisterView.vue'), meta: { noAuth: true } },

  // 学生端
  { path: '/student/home', name: 'StudentHome', component: () => import('@/views/student/StudentHome.vue') },
  { path: '/student/profile', name: 'Profile', component: () => import('@/views/student/ProfileView.vue') },
  { path: '/student/assessment', name: 'Assessment', component: () => import('@/views/student/AssessmentView.vue') },
  { path: '/student/career-exploration', name: 'CareerExploration', component: () => import('@/views/student/CareerExplorationView.vue') },
  { path: '/student/job-matching', name: 'JobMatching', component: () => import('@/views/student/JobMatchingView.vue') },
  { path: '/student/gap-analysis', name: 'GapAnalysis', component: () => import('@/views/student/GapAnalysisView.vue') },
  { path: '/student/learning-path', name: 'LearningPath', component: () => import('@/views/student/LearningPathView.vue') },
  { path: '/student/learning-progress', name: 'LearningProgress', component: () => import('@/views/student/LearningProgressView.vue') },
  { path: '/student/resume-optimize', name: 'ResumeOptimize', component: () => import('@/views/student/ResumeOptimizeView.vue') },
  { path: '/student/interview', name: 'Interview', component: () => import('@/views/student/InterviewView.vue') },

  // 企业端
  { path: '/enterprise/home', name: 'EnterpriseHome', component: () => import('@/views/enterprise/EnterpriseHome.vue') },
  { path: '/enterprise/recommend', name: 'Recommend', component: () => import('@/views/enterprise/RecommendView.vue') },

  // 管理端
  { path: '/admin/home', name: 'AdminHome', component: () => import('@/views/admin/AdminHome.vue'), meta: { role: 'ADMIN' } },
  { path: '/admin/users', name: 'UserManagement', component: () => import('@/views/admin/UserManagement.vue'), meta: { role: 'ADMIN' } },
  { path: '/admin/skills', name: 'SkillDictionary', component: () => import('@/views/admin/SkillDictionary.vue'), meta: { role: 'ADMIN' } },

  // 智能客服
  { path: '/customer-service', name: 'CustomerService', component: () => import('@/views/customer/CustomerServiceView.vue') },

  // 404
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: () => import('@/views/error/NotFoundView.vue') }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 路由守卫：未登录 → 登录页；角色不符 → 403
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  const role = localStorage.getItem('role')

  // 无需认证的页面
  if (to.meta.noAuth) {
    return next()
  }

  // 未登录跳转登录页
  if (!token) {
    return next('/login')
  }

  // 角色校验
  if (to.meta.role && role !== to.meta.role) {
    return next({ name: role === 'STUDENT' ? 'StudentHome' : role === 'HR' ? 'EnterpriseHome' : 'AdminHome' })
  }

  next()
})

export default router
