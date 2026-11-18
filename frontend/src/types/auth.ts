/** 认证相关类型，对应后端 auth 模块的 DTO / VO。 */

/** 登录身份：身份选择页传给用户，最终权限由后端数据库角色决定 */
export type LoginAs = 'USER' | 'MERCHANT' | 'ADMIN'

export interface LoginPayload {
  username: string
  password: string
  /** 可选登录意图；后端据此校验该账号是否真实拥有对应角色 */
  loginAs?: LoginAs
}

export interface RegisterPayload {
  username: string
  password: string
  confirmPassword: string
  nickname?: string
  phone?: string
}

export interface LoginResult {
  token: string
  tokenType: string
  expiresIn: number
  userId: number
  username: string
  nickname: string
  avatar: string | null
  roles: string[]
}

export interface UserInfo {
  userId: number
  username: string
  nickname: string
  avatar: string | null
  phone: string | null
  roles: string[]
}

/** 头像上传响应：后端返回相对 URL */
export interface AvatarUploadResult {
  avatarUrl: string
}

/** 个人资料修改请求：仅昵称与手机号可编辑；头像通过上传接口单独更新 */
export interface ProfileUpdatePayload {
  nickname: string
  phone: string | null
}
