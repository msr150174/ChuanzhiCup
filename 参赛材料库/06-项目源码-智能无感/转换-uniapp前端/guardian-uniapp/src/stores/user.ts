/**
 * 用户状态管理（Pinia）
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/services/api'
import { TOKEN_KEY } from '@/services/request'
import type { UserInfo } from '@/types'

const USER_KEY = 'guardian_user'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(uni.getStorageSync(TOKEN_KEY) || '')
  const userInfo = ref<UserInfo | null>(uni.getStorageSync(USER_KEY) || null)

  /** 是否已登录 */
  const isLoggedIn = computed(() => !!token.value && !!userInfo.value)

  /** 是否长辈（决定默认是否开启大字模式） */
  const isElder = computed(() => userInfo.value?.role === 'elder')

  /** 保存登录态 */
  function setAuth(newToken: string, user: UserInfo) {
    token.value = newToken
    userInfo.value = user
    uni.setStorageSync(TOKEN_KEY, newToken)
    uni.setStorageSync(USER_KEY, user)
  }

  /** 登录 */
  async function login(phone: string, code: string) {
    const res = await authApi.login(phone, code)
    setAuth(res.token, res.user)
    return res
  }

  /** 刷新用户信息 */
  async function refreshProfile(silent = true) {
    if (!token.value) return null
    try {
      const me = await authApi.me()
      userInfo.value = me
      uni.setStorageSync(USER_KEY, me)
      return me
    } catch {
      // 刷新失败不影响使用（token 失效时 request 层会跳登录）
      return null
    }
  }

  /** 更新资料 */
  async function updateProfile(data: Record<string, unknown>) {
    const me = await authApi.updateProfile(data)
    userInfo.value = me
    uni.setStorageSync(USER_KEY, me)
    return me
  }

  /** 登出 */
  async function logout() {
    try {
      await authApi.logout()
    } catch {
      // 接口失败也要清理本地
    }
    token.value = ''
    userInfo.value = null
    uni.removeStorageSync(TOKEN_KEY)
    uni.removeStorageSync(USER_KEY)
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    isElder,
    setAuth,
    login,
    refreshProfile,
    updateProfile,
    logout
  }
})
