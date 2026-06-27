import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { EnrichedJob } from '@/utils/jobEnrich'

export const useJobStore = defineStore('job', () => {
  // 岗位列表与加载状态
  const jobs = ref<EnrichedJob[]>([])
  const loading = ref(false)
  const error = ref('')

  // 筛选条件
  const filters = ref({
    keyword: '',
    city: '',
    direction: [] as string[],
    salaryRange: null as null | [number, number],
    experience: '',
    education: '',
    jobType: '',
    skill: [] as string[]
  })

  // 排序
  const sort = ref('comprehensive')

  // 分页
  const page = ref(1)
  const pageSize = ref(10)

  // 本地收藏与投递（后端暂无接口时先用本地状态模拟）
  const favorites = ref<Set<number>>(new Set())
  const applications = ref<{ jobId: number; time: string; status: string }[]>([])

  const total = computed(() => jobs.value.length)
  const pagedJobs = computed(() => {
    const start = (page.value - 1) * pageSize.value
    return jobs.value.slice(start, start + pageSize.value)
  })

  function setJobs(list: EnrichedJob[]) {
    jobs.value = list
    page.value = 1
  }

  function resetFilters() {
    filters.value = {
      keyword: '',
      city: '',
      direction: [],
      salaryRange: null,
      experience: '',
      education: '',
      jobType: '',
      skill: []
    }
    sort.value = 'comprehensive'
    page.value = 1
  }

  function toggleFavorite(jobId: number) {
    if (favorites.value.has(jobId)) {
      favorites.value.delete(jobId)
    } else {
      favorites.value.add(jobId)
    }
  }

  function isFavorite(jobId: number) {
    return favorites.value.has(jobId)
  }

  function applyJob(jobId: number) {
    if (applications.value.some(a => a.jobId === jobId)) return false
    applications.value.unshift({
      jobId,
      time: new Date().toISOString(),
      status: '已投递'
    })
    return true
  }

  function hasApplied(jobId: number) {
    return applications.value.some(a => a.jobId === jobId)
  }

  return {
    jobs,
    loading,
    error,
    filters,
    sort,
    page,
    pageSize,
    favorites,
    applications,
    total,
    pagedJobs,
    setJobs,
    resetFilters,
    toggleFavorite,
    isFavorite,
    applyJob,
    hasApplied
  }
})
