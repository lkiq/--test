<template>
  <div>
    <h2 class="page-title">智能客服</h2>
    <el-row :gutter="20">
      <el-col :span="8">
        <div class="page-card" style="height:600px;overflow-y:auto;">
          <h4>常见问题</h4>
          <el-collapse>
            <el-collapse-item v-for="faq in faqs" :key="faq.id" :title="faq.question">
              <p>{{ faq.answer }}</p>
              <el-button size="small" type="primary" @click="askFAQ(faq.question)">问这个问题</el-button>
            </el-collapse-item>
          </el-collapse>
        </div>
      </el-col>
      <el-col :span="16">
        <div class="page-card">
          <ChatWindow :messages="messages" :loading="loading" placeholder="请输入你的问题..." @send="handleSend" />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import ChatWindow from '@/components/common/ChatWindow.vue'
import { chat, getFAQs } from '@/api/customerService'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const faqs = ref<any[]>([])
const messages = ref<any[]>([{ role: 'assistant', content: '你好！我是平台智能客服，有什么可以帮助你的吗？' }])
const loading = ref(false)

onMounted(async () => {
  const res: any = await getFAQs()
  faqs.value = res.data || []
})

function askFAQ(question: string) { handleSend(question) }

async function handleSend(text: string) {
  messages.value.push({ role: 'user', content: text })
  loading.value = true
  try {
    const res: any = await chat({ question: text, userRole: userStore.role })
    const data = res.data
    messages.value.push({ role: 'assistant', content: data.answer })
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || '网络异常，请稍后重试'
    messages.value.push({ role: 'system', content: `抱歉，客服暂时不可用（${msg}）` })
  } finally { loading.value = false }
}
</script>
