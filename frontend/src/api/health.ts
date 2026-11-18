import request from './request'
import type { ApiResponse } from '@/types/api'

/** 健康检查接口返回的业务数据 */
export interface HealthData {
  application: string
  status: string
  phase: string
}

/** 验证前后端链路是否打通：GET /api/v1/health */
export function getHealth() {
  return request.get<ApiResponse<HealthData>>('/v1/health')
}
