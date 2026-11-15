import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { TOKEN_KEY } from '@/api'
import type { LoginUser } from '@/types'

const USER_KEY = 'guardian_admin_user'

function readStoredUser(): LoginUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as LoginUser) : null
  } catch {
    localStorage.removeItem(USER_KEY)
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref<LoginUser | null>(readStoredUser())
  const isLoggedIn = computed(() => Boolean(token.value))
  const isAdmin = computed(() => Boolean(token.value && user.value?.isAdmin))
  const displayName = computed(() => user.value?.name || '管理员')

  function setSession(session: { token: string; user: LoginUser }) {
    token.value = session.token
    user.value = session.user
    localStorage.setItem(TOKEN_KEY, session.token)
    localStorage.setItem(USER_KEY, JSON.stringify(session.user))
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return {
    token,
    user,
    isLoggedIn,
    isAdmin,
    displayName,
    setSession,
    logout,
  }
})
