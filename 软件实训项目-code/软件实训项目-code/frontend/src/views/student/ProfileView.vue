<template>
  <div>
    <h2 class="page-title">求职画像</h2>
    <div class="page-card">
      <el-form :model="form" label-width="100px" size="large">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="学校"><el-input v-model="form.school" placeholder="请输入学校名称" /></el-form-item>
            <el-form-item label="专业"><el-input v-model="form.major" placeholder="请输入专业" /></el-form-item>
            <el-form-item label="学历">
              <el-select v-model="form.education" style="width:100%">
                <el-option label="专科" value="专科" /><el-option label="本科" value="本科" />
                <el-option label="硕士" value="硕士" /><el-option label="博士" value="博士" />
              </el-select>
            </el-form-item>
            <el-form-item label="求职状态">
              <el-select v-model="form.jobStatus" style="width:100%">
                <el-option label="在校" value="在校" /><el-option label="应届" value="应届" /><el-option label="已毕业" value="已毕业" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="期望城市"><el-input v-model="form.expectedCity" placeholder="如：深圳" /></el-form-item>
            <el-form-item label="期望薪资"><el-input v-model="form.expectedSalary" placeholder="如：15K-25K" /></el-form-item>
            <el-form-item label="技能标签">
              <el-input v-model="skillInput" placeholder="输入技能后回车添加" @keyup.enter="addSkill" />
              <div style="margin-top:8px;">
                <el-tag v-for="s in skills" :key="s" closable @close="removeSkill(s)" style="margin:2px;">{{ s }}</el-tag>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="个人总结"><el-input v-model="form.summary" type="textarea" :rows="3" /></el-form-item>
        <el-form-item><el-button type="primary" @click="save" :loading="loading">保存画像</el-button></el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useProfileStore } from '@/stores/profile'
import { ElMessage } from 'element-plus'

const profileStore = useProfileStore()
const loading = ref(false)
const skillInput = ref('')
const skills = ref<string[]>([])
const form = reactive({ school: '', major: '', education: '', grade: '', expectedCity: '', expectedSalary: '', jobStatus: '', summary: '' })

onMounted(async () => {
  await profileStore.fetchProfile()
  if (profileStore.profile) Object.assign(form, profileStore.profile)
  try { skills.value = JSON.parse(profileStore.profile?.skillTags || '[]') } catch {/* ignore */}
})

function addSkill() {
  const s = skillInput.value.trim()
  if (s && !skills.value.includes(s)) skills.value.push(s)
  skillInput.value = ''
}

function removeSkill(s: string) { skills.value = skills.value.filter(v => v !== s) }

async function save() {
  loading.value = true
  try {
    await profileStore.saveProfile({ ...form, skillTags: JSON.stringify(skills.value) })
    ElMessage.success('保存成功')
  } catch {/* handled */}
  finally { loading.value = false }
}
</script>
