import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getProfile, login as loginApi, register as registerApi, uploadAvatar as uploadAvatarApi } from '@/api/auth'
import { getToken, removeToken, setToken } from '@/utils/token'
import type { LoginPayload, RegisterPayload, UserInfo } from '@/types/auth'

/** 用户端统一默认头像：个人中心与右上角共用，加载失败也回退到它 */
export const DEFAULT_USER_AVATAR = '/images/default-avatar.svg'

/**
 * 全局用户登录态。
 *
 * <p>注意：这里的 isLoggedIn / hasRole 只用于前端体验（菜单显示、路由守卫），
 * <strong>绝不能替代后端鉴权</strong>。真正的权限判断在后端 Spring Security 完成，
 * 前端隐藏菜单很容易被绕过。</p>
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(getToken())
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => Boolean(token.value))
  const roles = computed<string[]>(() => userInfo.value?.roles ?? [])
  const username = computed(() => userInfo.value?.username ?? '')
  const nickname = computed(() => userInfo.value?.nickname ?? username.value)

  /** 当前用户头像 URL（单一数据源）：未设置时回退统一默认头像 */
  const avatarUrl = computed(() => userInfo.value?.avatar || DEFAULT_USER_AVATAR)

  /**
   * 统一更新当前用户信息：头像上传/资料修改后调用，一次写入全局 Store，
   * 所有读取 avatarUrl 的组件（右上角、个人中心等）立即响应式更新。
   */
  function updateUserInfo(patch: Partial<UserInfo>): void {
    if (!userInfo.value) return
    userInfo.value = { ...userInfo.value, ...patch }
  }

  /** 上传头像：调接口 → 用返回 URL 更新全局 Store（后端已同步写库） */
  async function uploadAvatar(file: File): Promise<void> {
    const { data } = await uploadAvatarApi(file)
    // 以返回的 avatarUrl 更新统一字段 avatar；随后刷新确保与后端库一致
    updateUserInfo({ avatar: data.data.avatarUrl })
    await fetchProfile()
  }

  function saveToken(value: string): void {
    token.value = value
    setToken(value)
  }

  /** 登录：调用接口 → 保存 Token → 记录后端返回的用户信息 */
  async function login(payload: LoginPayload): Promise<void> {
    const { data } = await loginApi(payload)
    saveToken(data.data.token)
    userInfo.value = {
      userId: data.data.userId,
      username: data.data.username,
      nickname: data.data.nickname,
      avatar: data.data.avatar,
      phone: null,
      roles: data.data.roles,
    }
  }

  /** 注册：仅提交注册，不自动登录 */
  async function register(payload: RegisterPayload): Promise<number> {
    const { data } = await registerApi(payload)
    return data.data
  }

  /** 从后端拉取当前用户信息，用于刷新页面后恢复登录态 */
  async function fetchProfile(): Promise<void> {
    if (!token.value) return
    try {
      const { data } = await getProfile()
      userInfo.value = data.data
    } catch {
      // Token 无效时由 Axios 拦截器处理跳转，这里只清理本地状态
      logout()
    }
  }

  function hasRole(role: string): boolean {
    return roles.value.includes(role)
  }

  function logout(): void {
    token.value = null
    userInfo.value = null
    removeToken()
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    roles,
    username,
    nickname,
    avatarUrl,
    hasRole,
    login,
    register,
    fetchProfile,
    updateUserInfo,
    uploadAvatar,
    saveToken,
    logout,
  }
})
