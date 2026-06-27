import request from './request'

/** 学生端 API */

export const getProfile = () => request.get('/student/profile')
export const saveProfile = (data: any) => request.post('/student/profile', data)

export const getAssessmentQuestions = (type = 'COMPREHENSIVE') =>
  request.get('/student/assessment/questions', { params: { type } })
export const submitAssessment = (data: any) => request.post('/student/assessment/submit', data)
export const getAssessmentResult = (id: number) => request.get(`/student/assessment/result/${id}`)
export const getAssessmentHistory = () => request.get('/student/assessment/history')

export const exploreCareer = (data: any) => request.post('/student/career/explore', data)
export const getExploreHistory = () => request.get('/student/career/explore/history')

export const recommendJobs = () => request.post('/student/jobs/recommend')
export const getJobDetail = (id: number) => request.get(`/student/jobs/${id}`)
export const searchJobs = (keyword?: string, city?: string) =>
  request.get('/student/jobs/search', { params: { keyword, city } })

export const analyzeGap = (jobId: number) => request.post(`/student/gap/analyze/${jobId}`)
export const getGapReport = (id: number) => request.get(`/student/gap/report/${id}`)

export const generateLearningPath = () => request.post('/student/learning/generate')
export const getLearningPath = () => request.get('/student/learning/path')
export const updateTaskStatus = (id: number, status: string) =>
  request.put(`/student/learning/tasks/${id}`, { status })
export const getLearningTasks = () => request.get('/student/learning/tasks')
export const getLearningResources = (skill?: number, stage?: string) =>
  request.get('/student/learning/resources', { params: { skill, stage } })

export const uploadResume = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/student/resume/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
export const analyzeResume = (data: any) => request.post('/student/resume/analyze', data)
export const getResumeAnalysis = (id: number) => request.get(`/student/resume/analysis/${id}`)
export const getResumeHistory = () => request.get('/student/resume/history')

export const startInterview = (data: any) => request.post('/student/interview/start', data)
export const submitAnswer = (sid: string, answer: string) =>
  request.post(`/student/interview/${sid}/answer`, { answer })
export const endInterview = (sid: string) => request.post(`/student/interview/${sid}/end`)
export const getInterviewReport = (id: number) => request.get(`/student/interview/report/${id}`)
export const getInterviewHistory = () => request.get('/student/interview/history')

export const getProgressOverview = () => request.get('/student/progress/overview')
export const getSkillProgress = () => request.get('/student/progress/skills')
export const getGrowthReport = () => request.get('/student/progress/report')
