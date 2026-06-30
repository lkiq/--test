<template>
  <div>
    <h2 class="page-title">用户管理</h2>
    <div class="page-card">
      <el-row :gutter="12" style="margin-bottom:12px;">
        <el-col :span="6"><el-input v-model="keyword" placeholder="搜索用户名" /></el-col>
        <el-col :span="6">
          <el-select v-model="filterRole" placeholder="角色筛选" clearable><el-option label="学生" value="STUDENT" /><el-option label="HR" value="HR" /><el-option label="管理员" value="ADMIN" /></el-select>
        </el-col>
        <el-col :span="6"><el-button type="primary" @click="fetchUsers">搜索</el-button></el-col>
      </el-row>
      <el-table :data="users" stripe>
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="role" label="角色" />
        <el-table-column prop="phone" label="手机" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="status" label="状态">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'danger'">{{ scope.row.status === 'ACTIVE' ? '正常' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button size="small" :type="scope.row.status === 'ACTIVE' ? 'warning' : 'success'" @click="toggleStatus(scope.row)">
              {{ scope.row.status === 'ACTIVE' ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" type="danger" @click="resetPwd(scope.row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top:12px;" :total="total" v-model:current-page="page" :page-size="size" @current-change="fetchUsers" layout="total, prev, pager, next" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getUsers, updateUserStatus, resetUserPassword } from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const users = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const filterRole = ref('')

async function fetchUsers() {
  const res: any = await getUsers({ page: page.value, size: size.value, keyword: keyword.value, role: filterRole.value })
  users.value = res.data?.records || []
  total.value = res.data?.total || 0
}

async function toggleStatus(user: any) {
  const newStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  await updateUserStatus(user.id, newStatus)
  ElMessage.success('操作成功')
  fetchUsers()
}

/**
 * 重置用户密码，弹窗让管理员输入新密码
 */
async function resetPwd(user: any) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新密码', '重置密码', {
      confirmButtonText: '确定',
      inputPattern: /^.{6,}$/,
      inputErrorMessage: '密码长度不能少于6位'
    })
    await resetUserPassword(user.id, value)
    ElMessage.success(`密码已重置`)
  } catch {/* 取消 */}
}

onMounted(fetchUsers)
</script>
