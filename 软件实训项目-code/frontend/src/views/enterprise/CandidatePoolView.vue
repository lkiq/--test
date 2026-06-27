<template>
  <div>
    <h2 class="page-title">候选人库</h2>
    <div class="page-card">
      <div class="action-bar" style="margin-bottom: 16px;">
        <el-input v-model="keyword" placeholder="搜索候选人" style="width: 240px;" clearable />
        <el-select v-model="filterStatus" placeholder="全部状态" style="width: 140px; margin-left: 12px;">
          <el-option label="全部" value="" />
          <el-option label="待筛选" value="pending" />
          <el-option label="邀约面试" value="interview" />
          <el-option label="不合适" value="reject" />
        </el-select>
      </div>
      <el-table :data="filteredCandidates" stripe>
        <el-table-column prop="name" label="候选人" />
        <el-table-column prop="position" label="应聘职位" />
        <el-table-column prop="matchScore" label="匹配度" />
        <el-table-column prop="status" label="状态">
          <template #default="scope">
            <el-tag :type="statusType(scope.row.status)">{{ scope.row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default>
            <el-button link type="primary">查看简历</el-button>
            <el-button link type="success">邀约</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const keyword = ref('')
const filterStatus = ref('')
const candidates = ref([
  { name: '张三', position: 'Java后端开发工程师', matchScore: '92%', status: '待筛选' },
  { name: '李四', position: '前端开发工程师', matchScore: '88%', status: '邀约面试' },
  { name: '王五', position: 'Python数据工程师', matchScore: '85%', status: '不合适' }
])

const filteredCandidates = computed(() => {
  return candidates.value.filter(c => {
    if (keyword.value && !c.name.includes(keyword.value) && !c.position.includes(keyword.value)) return false
    if (filterStatus.value && c.status !== filterStatus.value) return false
    return true
  })
})

function statusType(status: string) {
  if (status === '待筛选') return 'info'
  if (status === '邀约面试') return 'success'
  if (status === '不合适') return 'danger'
  return ''
}
</script>
