<template>
  <div>
    <h2 class="page-title">技能词典管理</h2>
    <div class="page-card">
      <div style="margin-bottom:12px;">
        <el-button type="primary" @click="showAddDialog = true">新增技能</el-button>
        <el-input v-model="searchWord" placeholder="搜索技能" style="width:200px;margin-left:12px;" />
      </div>
      <el-table :data="skills" stripe>
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="category" label="类别" />
        <el-table-column prop="description" label="描述" />
        <el-table-column label="操作" width="160">
          <template #default="scope">
            <el-button size="small" @click="editSkill(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="removeSkill(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="showAddDialog" :title="editingSkill ? '编辑技能' : '新增技能'" width="500px">
      <el-form label-width="80px">
        <el-form-item label="名称"><el-input v-model="skillForm.name" /></el-form-item>
        <el-form-item label="类别">
          <el-select v-model="skillForm.category"><el-option label="编程语言" value="编程语言" /><el-option label="框架" value="框架" /><el-option label="数据库" value="数据库" /><el-option label="工具" value="工具" /><el-option label="软技能" value="软技能" /></el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="skillForm.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="saveSkill">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getSkills, addSkill as addSkillApi, updateSkill as updateSkillApi, deleteSkill } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const skills = ref<any[]>([])
const searchWord = ref('')
const showAddDialog = ref(false)
const editingSkill = ref<any>(null)
const skillForm = reactive({ name: '', category: '编程语言', description: '' })

async function fetchSkills() {
  const res: any = await getSkills({ keyword: searchWord.value, page: 1, size: 100 })
  skills.value = res.data?.records || []
}

function editSkill(skill: any) {
  editingSkill.value = skill
  Object.assign(skillForm, skill)
  showAddDialog.value = true
}

async function saveSkill() {
  if (editingSkill.value) {
    await updateSkillApi(editingSkill.value.id, skillForm)
  } else {
    await addSkillApi(skillForm)
  }
  ElMessage.success('保存成功')
  showAddDialog.value = false
  editingSkill.value = null
  Object.assign(skillForm, { name: '', category: '编程语言', description: '' })
  fetchSkills()
}

async function removeSkill(id: number) {
  try {
    await ElMessageBox.confirm('确定删除此技能？', '确认')
    await deleteSkill(id)
    ElMessage.success('删除成功')
    fetchSkills()
  } catch {/* 取消 */}
}

onMounted(fetchSkills)
</script>
