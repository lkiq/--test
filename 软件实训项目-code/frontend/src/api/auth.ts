import request from './request'

/** 认证相关 API */

/** 用户注册 */
export const register = (data: { username: string; password: string; phone?: string; email?: string; role: string }) =>
  request.post('/auth/register', data)

/** 用户登录 */
export const login = (data: { username: string; password: string }) =>
  request.post('/auth/login', data)
