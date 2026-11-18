import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types/api'
import { getToken, removeToken } from '@/utils/token'

/**
 * 统一 HTTP 客户端。
 * - baseURL 为 /api，Vite 开发代理会把 /api 转发到后端 8080。
 * - 请求拦截器自动携带 Bearer Token。
 * - 响应拦截器：识别业务码、统一处理 401，具体提示文案由页面决定。
 */

/** 允许单个请求声明跳过 401 自动跳转（如登录接口的密码错误） */
export interface RequestConfig extends InternalAxiosRequestConfig {
  skipAuthRedirect?: boolean
}

const request: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body && typeof body === 'object' && 'code' in body && body.code !== 200) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return response
  },
  (error) => {
    const status = error.response?.status
    const config = error.config as RequestConfig | undefined

    // 401：Token 缺失 / 无效 / 过期 → 清理本地凭据并跳转登录页
    // 登录页自身的 401（密码错误）由页面处理，跳过自动跳转，避免循环
    if (status === 401 && config && !config.skipAuthRedirect) {
      removeToken()
      const currentPath = window.location.pathname + window.location.search
      const loginUrl = `/login?redirect=${encodeURIComponent(currentPath)}`
      // 避免在登录页反复跳转
      if (window.location.pathname !== '/login') {
        window.location.href = loginUrl
      }
    }

    // 把业务错误信息挂到 Error 上，方便页面展示后端提示
    const body = error.response?.data as ApiResponse<unknown> | undefined
    const message = body?.message || error.message || '请求失败'
    return Promise.reject(new Error(message))
  },
)

export default request
