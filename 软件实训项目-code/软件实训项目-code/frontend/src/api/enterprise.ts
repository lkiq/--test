import request from './request'

/** 企业端 API */
export const recommend = (data: { projectDescription: string; filters?: string }) =>
  request.post('/enterprise/recommend', data)
export const getRecommendHistory = () => request.get('/enterprise/recommend/history')
