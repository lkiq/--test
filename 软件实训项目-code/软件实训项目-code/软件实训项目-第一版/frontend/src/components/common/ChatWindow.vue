<template>
  <div class="chat-window">
    <div class="chat-messages" ref="msgContainer">
      <div v-for="(msg, i) in messages" :key="i" :class="['message', msg.role]">
        <div class="msg-bubble">{{ msg.content }}</div>
      </div>
    </div>
    <div class="chat-input" v-if="!readonly">
      <el-input v-model="inputText" :placeholder="placeholder" @keyup.enter="send" type="textarea" :rows="2" />
      <el-button type="primary" @click="send" :loading="loading" style="margin-top:8px;">发送</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'

const props = withDefaults(defineProps<{
  messages: { role: string; content: string }[];
  loading?: boolean;
  placeholder?: string;
  readonly?: boolean;
}>(), { loading: false, placeholder: '请输入...', readonly: false })

const emit = defineEmits<{ send: [text: string] }>()
const inputText = ref('')
const msgContainer = ref<HTMLElement>()

watch(() => props.messages.length, () => {
  nextTick(() => {
    if (msgContainer.value) msgContainer.value.scrollTop = msgContainer.value.scrollHeight
  })
})

function send() {
  if (!inputText.value.trim()) return
  emit('send', inputText.value.trim())
  inputText.value = ''
}
</script>

<style scoped>
.chat-window {
  display: flex;
  flex-direction: column;
  height: 500px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
.message {
  margin-bottom: 12px;
  display: flex;
}
.message.user { justify-content: flex-end; }
.msg-bubble {
  max-width: 80%;
  padding: 10px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
}
.message.user .msg-bubble { background: #409EFF; color: #fff; }
.message.assistant .msg-bubble, .message.system .msg-bubble { background: #f0f2f5; color: #303133; }
.chat-input {
  padding: 12px 16px;
  border-top: 1px solid #eee;
}
</style>
