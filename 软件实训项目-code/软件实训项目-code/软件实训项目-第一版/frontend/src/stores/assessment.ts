import { defineStore } from 'pinia'
import { ref } from 'vue'

/** 测评状态管理 */
export const useAssessmentStore = defineStore('assessment', () => {
  const questions = ref<any[]>([])
  const currentIndex = ref(0)
  const answers = ref<Map<number, string>>(new Map())
  const result = ref<any>(null)

  function setQuestions(list: any[]) {
    questions.value = list
    currentIndex.value = 0
    answers.value = new Map()
  }

  function setAnswer(questionId: number, answer: string) {
    answers.value.set(questionId, answer)
  }

  function setResult(data: any) {
    result.value = data
  }

  function reset() {
    questions.value = []
    currentIndex.value = 0
    answers.value = new Map()
    result.value = null
  }

  return { questions, currentIndex, answers, result, setQuestions, setAnswer, setResult, reset }
})
