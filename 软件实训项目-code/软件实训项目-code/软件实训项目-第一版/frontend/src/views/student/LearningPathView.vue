<template>
  <div>
    <h2 class="page-title">学习路径</h2>
    <div class="page-card">
      <el-button type="primary" @click="generate" :loading="loading">生成/更新学习路径</el-button>
    </div>
    <!-- 四阶段展示 -->
    <div v-for="stage in stages" :key="stage" class="page-card" style="margin-top:12px;">
      <h3>{{ stageLabels[stage] }}</h3>
      <el-table :data="tasksByStage[stage] || []" stripe>
        <el-table-column prop="title" label="任务" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="scope">
            <el-select v-model="scope.row.status" @change="(v: string) => updateTask(scope.row.id, v)" size="small">
              <el-option label="待开始" value="PENDING" /><el-option label="进行中" value="IN_PROGRESS" /><el-option label="已完成" value="COMPLETED" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="dueDate" label="截止日" width="120" />
      </el-table>
    </div>
    <div v-if="allTasks.length === 0" class="page-card" style="text-align:center;padding:40px;color:#909399;">
      还没有学习任务，请先生成学习路径
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { generateLearningPath, getLearningTasks, updateTaskStatus } from '@/api/student'

const loading = ref(false)
const allTasks = ref<any[]>([])
const stages = ['BASIC', 'FRAMEWORK', 'PROJECT', 'INTERVIEW']
const stageLabels: Record<string, string> = { BASIC: '基础入门', FRAMEWORK: '框架进阶', PROJECT: '项目实战', INTERVIEW: '面试冲刺' }

const tasksByStage = computed(() => {
  const map: Record<string, any[]> = {}
  stages.forEach(s => map[s] = allTasks.value.filter(t => t.stage === s))
  return map
})

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
