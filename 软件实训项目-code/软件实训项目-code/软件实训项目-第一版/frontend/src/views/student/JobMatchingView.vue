<template>
  <div>
    <h2 class="page-title">岗位匹配推荐</h2>
    <div class="page-card">
      <el-row :gutter="12">
        <el-col :span="8"><el-input v-model="searchKeyword" placeholder="搜索岗位" /></el-col>
        <el-col :span="8"><el-select v-model="searchCity" placeholder="筛选城市" clearable style="width:100%"><el-option v-for="c in cities" :key="c" :label="c" :value="c" /></el-select></el-col>
        <el-col :span="8"><el-button type="primary" @click="search">搜索</el-button> <el-button @click="fetchRecommend">智能推荐</el-button></el-col>
      </el-row>
    </div>
    <el-row :gutter="16">
      <el-col :span="8" v-for="job in jobs" :key="job.jobId || job.id">
        <el-card shadow="hover" style="margin-top:16px;">
          <template #header>
            <b>{{ job.title }}</b>
            <el-tag size="small" type="success" style="float:right;" v-if="job.matchScore">匹配 {{ job.matchScore }}%</el-tag>
          </template>
          <p><el-tag size="small">{{ job.city || job.city }}</el-tag> {{ job.salaryRange }}</p>
          <p style="color:#909399;margin-top:8px;font-size:13px;">{{ (job.jd || '').substring(0, 80) }}...</p>
          <div style="margin-top:8px;">
            <SkillTag v-for="t in job.skillTags || []" :key="t.skillName" :name="t.skillName" :status="t.status as any" />
          </div>
          <div style="margin-top:12px;">
            <el-button size="small" type="primary" @click="$router.push(`/student/gap-analysis?jobId=${job.jobId || job.id}`)">差距分析</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { recommendJobs, searchJobs as searchJobsApi } from '@/api/student'
import SkillTag from '@/components/common/SkillTag.vue'

const jobs = ref<any[]>([])
const searchKeyword = ref('')
const searchCity = ref('')
const cities = ['北京', '上海', '深圳', '广州', '杭州', '成都', '武汉']

async function fetchRecommend() {
  const res: any = await recommendJobs()
  jobs.value = res.data || []
}

async function search() {
  const res: any = await searchJobsApi(searchKeyword.value || undefined, searchCity.value || undefined)
  jobs.value = res.data || []
}

onMounted(fetchRecommend)
</script>
