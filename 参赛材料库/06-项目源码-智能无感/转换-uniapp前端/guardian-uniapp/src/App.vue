<script setup lang="ts">
/**
 * 应用入口
 *
 * 挂载时检查登录态；未登录且不在白名单页面则跳登录。
 */
import { onLaunch, onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import { useSettingsStore } from '@/stores/settings'

/** 无需登录即可访问的页面 */
const PUBLIC_PAGES = [
  'pages/login/index',
  'pages/register/index',
  'pages/forgot-password/index',
  'pages/user-agreement/index',
  'pages/privacy-policy/index',
  'pages/voiceprint-agreement/index',
  'pages/about/index'
]

onLaunch(() => {
  const userStore = useUserStore()
  const settingsStore = useSettingsStore()

  // 长辈默认开启大字模式（首次启动时）
  if (uni.getStorageSync('guardian_first_launch') === '') {
    uni.setStorageSync('guardian_first_launch', 'done')
    if (userStore.isElder) {
      settingsStore.set('elderMode', true)
    }
  }

  const pages = getCurrentPages()
  const current = pages.length > 0 ? pages[pages.length - 1].route || '' : ''

  if (!userStore.isLoggedIn && !PUBLIC_PAGES.includes(current)) {
    uni.redirectTo({ url: '/pages/login/index' })
  }
})

onShow(() => {
  // 每次回到前台刷新用户信息（信用分等可能在别处变化）
  const userStore = useUserStore()
  if (userStore.isLoggedIn) {
    userStore.refreshProfile()
  }
})
</script>

<style lang="scss">
@import '@/styles/index.scss';
</style>
