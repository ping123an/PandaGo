import request from './request'
import type { ApiResponse } from '@/types/api'
import type { LoginPayload, LoginResult, ProfileUpdatePayload, RegisterPayload, UserInfo, AvatarUploadResult } from '@/types/auth'

/** 用户注册，返回新用户 id */
export function register(data: RegisterPayload) {
  return request.post<ApiResponse<number>>('/v1/auth/register', data)
}

/** 账号密码登录，返回 JWT 与用户信息 */
export function login(data: LoginPayload) {
  return request.post<ApiResponse<LoginResult>>('/v1/auth/login', data)
}

/** 获取当前登录用户信息（需携带 Token） */
export function getProfile() {
  return request.get<ApiResponse<UserInfo>>('/v1/auth/me')
}

/** 修改当前登录用户个人资料（仅昵称 / 手机号），返回更新后的资料 */
export function updateProfile(data: ProfileUpdatePayload) {
  return request.put<ApiResponse<UserInfo>>('/v1/users/me/profile', data)
}

/** 上传当前用户头像；字段名固定为 file，后端会校验类型、大小与文件内容 */
export function uploadAvatar(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<ApiResponse<AvatarUploadResult>>('/v1/users/me/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
