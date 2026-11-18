import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import markSrc from '@/assets/images/brand/pandago-mark.svg'
import { getMerchantProfile, updateMerchantProfile } from '@/api/merchant'
import type { MerchantProfile, MerchantProfileUpdatePayload } from '@/types/merchant'

/**
 * 商家资料全局状态。
 *
 * <p>商家店铺头像（logo）以「商家资料」页为唯一数据来源，所有商家页面共用本 Store：
 * 右上角、工作台、商品管理、订单管理、资料页均从 logoUrl 读取，修改后一次更新全局同步。</p>
 *
 * <p>注意：这里只做前端展示态缓存，权限与数据归属仍由后端校验；
 * 普通用户头像（userStore.userInfo.avatar）是独立数据源，不受本 Store 影响。</p>
 */

/** PandaGo 统一品牌图标：商家未设置店铺头像、或图片加载失败时的统一 fallback */
export const DEFAULT_MERCHANT_AVATAR = markSrc

export const useMerchantStore = defineStore('merchant', () => {
  const profile = ref<MerchantProfile | null>(null)
  const loading = ref(false)
  let pending: Promise<void> | null = null

  /** 当前店铺头像 URL：未设置时回退到统一品牌图标 */
  const logoUrl = computed(() => profile.value?.logo || DEFAULT_MERCHANT_AVATAR)

  /** 当前店铺名（用于无资料时的兜底展示） */
  const storeName = computed(() => profile.value?.storeName || '')

  /** 拉取店铺资料；默认已有资料则跳过，force=true 用于刷新页面后的强制同步 */
  async function fetchProfile(force = false): Promise<void> {
    if (!force && profile.value) return
    if (pending) return pending
    loading.value = true
    pending = (async () => {
      try {
        const { data } = await getMerchantProfile()
        profile.value = data.data
      } finally {
        loading.value = false
        pending = null
      }
    })()
    return pending
  }

  /** 更新店铺资料：后端返回最新资料后直接写入全局状态，所有页面头像立即同步 */
  async function updateProfile(payload: MerchantProfileUpdatePayload): Promise<MerchantProfile> {
    const { data } = await updateMerchantProfile(payload)
    profile.value = data.data
    return data.data
  }

  /** 退出登录 / 切换账号时清理，避免下一账号看到上一账号的店铺头像 */
  function reset(): void {
    profile.value = null
    loading.value = false
    pending = null
  }

  return { profile, loading, logoUrl, storeName, fetchProfile, updateProfile, reset }
})
