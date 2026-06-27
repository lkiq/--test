<template>
  <div>
    <h2 class="page-title">简历智能优化</h2>
    <el-row :gutter="20">
      <el-col :span="8">
        <div class="page-card">
          <h3>上传简历</h3>
          <FileUpload @file-change="handleFile" />
          <div v-if="fileUrl" style="margin-top:12px;">
            <p style="color:#67C23A;">文件上传成功</p>
            <el-button type="primary" @click="analyze" :loading="loading">开始分析</el-button>
          </div>
        </div>
      </el-col>
      <el-col :span="16">
        <div v-if="result" class="page-card">
          <h3>分析报告 <el-tag>{{ result.source === 'AI' ? 'AI分析' : '系统分析' }}</el-tag></h3>
          <div style="margin:12px 0;">
            <el-progress type="dashboard" :percentage="result.score || 0" :color="scoreColor" />
            <span>综合评分</span>
          </div>
          <div v-for="(v, k) in result.dimensionScores || {}" :key="k" style="margin:4px 0;">
            <span style="width:80px;display:inline-block;">{{ k }}：</span>
            <el-progress :percentage="v" :stroke-width="8" style="width:300px;" />
          </div>
          <div v-if="result.issues?.length" style="margin-top:16px;">
            <h4>优化建议</h4>
            <el-collapse>
              <el-collapse-item v-for="(issue, i) in result.issues" :key="i" :title="`${issue.severity}: ${issue.description}`">
                <p><b>建议：</b>{{ issue.suggestion }}</p>
                <p v-if="issue.exampleRewrite"><b>示例：</b>{{ issue.exampleRewrite }}</p>
              </el-collapse-item>
            </el-collapse>
          </div>
          <p style="margin-top:12px;color:#909399;">{{ result.summary }}</p>
        </div>
        <div v-else class="page-card" style="text-align:center;padding:40px;color:#909399;">
          请先上传简历文件
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import FileUpload from '@/components/common/FileUpload.vue'
import { uploadResume, analyzeResume as analyzeResumeApi } from '@/api/student'

const fileUrl = ref('')
const loading = ref(false)
const result = ref<any>(null)

const scoreColor = ref('#409EFF')

async function handleFile(file: File) {
  const res: any = await uploadResume(file)
  fileUrl.value = res.data
}

async function analyze() {
  loading.value = true
  try {
    const res: any = await analyzeResumeApi({ fileUrl: fileUrl.value })
    result.value = res.data
  } finally { loading.value = false }
}
</script>
