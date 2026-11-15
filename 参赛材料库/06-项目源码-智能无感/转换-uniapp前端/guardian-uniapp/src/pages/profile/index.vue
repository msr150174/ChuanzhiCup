<template>
  <view class="page profile-page tabbar-gap" :class="{ 'elder-mode': settingsStore.settings.elderMode }">
    <!-- ① 用户信息卡片 -->
    <view class="user-section">
      <view class="user-card">
        <view class="user-header" @click="showAccountActions">
          <view class="user-avatar">
            <text>{{ displayName.slice(0, 1) }}</text>
          </view>
          <view class="user-main">
            <text class="user-name">{{ displayName }}</text>
            <view class="user-tags">
              <text class="user-badge">{{ roleText }}</text>
              <text class="user-badge light">💓 {{ healthLevel }}</text>
            </view>
          </view>
          <view class="user-settings-btn" @click.stop="goPage('/pages/settings/index')">
            <text>⚙️</text>
          </view>
        </view>

        <!-- 统计三宫格 -->
        <view class="stats-grid">
          <view class="stat-item">
            <text class="stat-value">{{ stats.totalDays }}</text>
            <text class="stat-label">累计打卡</text>
          </view>
          <view class="stat-divider"></view>
          <view class="stat-item">
            <text class="stat-value">{{ stats.continuousDays }}</text>
            <text class="stat-label">连续打卡</text>
          </view>
          <view class="stat-divider"></view>
          <view class="stat-item">
            <text class="stat-value primary">{{ healthScore }}</text>
            <text class="stat-label">健康评分</text>
          </view>
        </view>
      </view>
    </view>

    <!-- ② 10 个功能入口 -->
    <view class="menu-section">
      <view class="menu-card">
        <view
          v-for="item in MENU_ITEMS"
          :key="item.id"
          class="menu-item"
          @click="handleMenuClick(item)"
        >
          <view class="menu-icon-wrapper" :style="{ background: item.bg }">
            <text>{{ item.icon }}</text>
          </view>
          <view class="menu-text">
            <text class="menu-title">{{ item.label }}</text>
            <text v-if="item.value" class="menu-value">{{ item.value }}</text>
          </view>

          <!-- 右侧三选一：开关 / 徽章 / 箭头 -->
          <view
            v-if="item.hasSwitch"
            class="menu-switch"
            :class="{ on: settingsStore.settings.elderMode }"
            @click.stop="toggleElderMode"
          >
            <view class="menu-switch-knob"></view>
          </view>
          <text v-else-if="item.badge" class="menu-badge">{{ item.badge }}</text>
          <text v-else class="menu-arrow">›</text>
        </view>
      </view>
    </view>

    <!-- ③ 版本号 -->
    <text class="version-text">安居守护 v1.0.0 · 让家人安心每一天</text>

    <!-- 账号操作面板 -->
    <view v-if="showActions" class="modal-overlay" @click.self="showActions = false">
      <view class="action-sheet">
        <view class="sheet-item" @click="editProfile">
          <text>✏️ 修改个人资料</text>
        </view>
        <view class="sheet-item" @click="goPage('/pages/health-report/index')">
          <text>📈 查看健康报告</text>
        </view>
        <view class="sheet-item danger" @click="handleLogout">
          <text>🚪 退出登录</text>
        </view>
        <view class="sheet-item cancel" @click="showActions = false">
          <text>取消</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 个人中心页
 * ----------------------------------------------------------------------------
 * 用户卡（头像 + 昵称 + 角色标签）→ 统计三宫格 → 10 个功能入口 → 版本号
 */
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { checkinApi, statisticsApi } from '@/services/api'
import { useUserStore } from '@/stores/user'
import { useSettingsStore } from '@/stores/settings'

const userStore = useUserStore()
const settingsStore = useSettingsStore()

interface MenuItem {
  id: string
  icon: string
  bg: string
  label: string
  value?: string
  badge?: string
  hasSwitch?: boolean
  path?: string
}

/** 统计兜底 */
const FALLBACK_STATS = { totalDays: 86, continuousDays: 12, weekDays: 5, weekTarget: 7 }
const stats = ref({ ...FALLBACK_STATS })
const healthScore = ref(92)
const healthLevel = ref('状态良好')
const showActions = ref(false)

// ============================================================================
// 派生用户信息
// ============================================================================
const displayName = computed(() => userStore.userInfo?.name || '未登录用户')

const roleText = computed(() => {
  const role = userStore.userInfo?.role
  if (role === 'elder') return '👴 被守护者'
  if (role === 'family') return '👨‍👩‍👧 家属'
  return '👤 未设置身份'
})

/** 10 个功能入口 */
const MENU_ITEMS: MenuItem[] = [
  {
    id: 'userRole',
    icon: '👤',
    bg: 'rgba(24,144,255,0.12)',
    label: '我的身份',
    value: '长辈 / 家属',
    badge: '点击切换'
  },
  {
    id: 'userMode',
    icon: '👥',
    bg: 'rgba(91,154,122,0.15)',
    label: '当前模式',
    value: '标准模式',
    badge: '点击切换'
  },
  {
    id: 'members',
    icon: '🔗',
    bg: 'rgba(91,154,122,0.15)',
    label: '成员绑定管理',
    value: '管理家属绑定关系',
    path: '/pages/member-bind/index'
  },
  {
    id: 'elderMode',
    icon: '☀️',
    bg: 'rgba(212,165,116,0.15)',
    label: '长辈极简模式',
    value: '放大字号与按钮',
    hasSwitch: true
  },
  {
    id: 'admin',
    icon: '🛡️',
    bg: 'rgba(229,90,90,0.12)',
    label: '管理员系统',
    badge: '需验证',
    path: '/pages/guardian-panel/index'
  },
  {
    id: 'privacy',
    icon: '🔒',
    bg: 'rgba(93,173,226,0.15)',
    label: '隐私安全设置',
    path: '/pages/privacy-security/index'
  },
  {
    id: 'settings',
    icon: '⚙️',
    bg: 'rgba(125,116,107,0.12)',
    label: '系统设置',
    path: '/pages/settings/index'
  },
  {
    id: 'about',
    icon: '📄',
    bg: 'rgba(125,116,107,0.12)',
    label: '关于我们',
    path: '/pages/about/index'
  },
  {
    id: 'report',
    icon: '📈',
    bg: 'rgba(24,144,255,0.12)',
    label: '查看详细报告',
    path: '/pages/health-report/index'
  },
  {
    id: 'developer',
    icon: '🧠',
    bg: 'rgba(114,46,209,0.12)',
    label: '开发者管理',
    path: '/pages/debug-log/index'
  }
]

function toast(msg: string) {
  uni.showToast({ title: msg, icon: 'none' })
}

function goPage(path: string) {
  uni.navigateTo({
    url: path,
    fail: () => {
      toast('页面开发中')
    }
  })
}

// ============================================================================
// 数据加载
// ============================================================================
async function loadStats() {
  try {
    const data = await checkinApi.stats()
    stats.value = {
      totalDays: Number(data?.totalDays ?? FALLBACK_STATS.totalDays),
      continuousDays: Number(data?.continuousDays ?? FALLBACK_STATS.continuousDays),
      weekDays: Number(data?.weekDays ?? FALLBACK_STATS.weekDays),
      weekTarget: Number(data?.weekTarget ?? FALLBACK_STATS.weekTarget)
    }
  } catch {
    stats.value = { ...FALLBACK_STATS }
  }

  try {
    const score = await checkinApi.healthScore()
    healthScore.value = Math.round(Number(score?.totalScore ?? 92))
    healthLevel.value = score?.level || '状态良好'
  } catch {
    healthScore.value = 92
    healthLevel.value = '状态良好'
  }
}

onShow(() => {
  userStore.refreshProfile()
  loadStats()
})

// ============================================================================
// 交互
// ============================================================================
/** 切换长辈极简模式（持久化） */
function toggleElderMode() {
  settingsStore.set('elderMode', !settingsStore.settings.elderMode)
  toast(settingsStore.settings.elderMode ? '已开启长辈模式' : '已关闭长辈模式')
}

/** 点击头像：弹出账号操作面板 */
function showAccountActions() {
  showActions.value = true
}

/** 修改资料：切换身份 */
async function editProfile() {
  showActions.value = false
  const nextRole = userStore.userInfo?.role === 'elder' ? 'family' : 'elder'
  try {
    await userStore.updateProfile({ role: nextRole })
    toast(nextRole === 'elder' ? '已设置为被守护者' : '已设置为家属')
  } catch {
    toast('修改失败，请检查网络')
  }
}

/** 退出登录 */
function handleLogout() {
  showActions.value = false
  uni.showModal({
    title: '退出登录',
    content: '确定要退出当前账号吗？',
    success: async (res) => {
      if (!res.confirm) return
      await userStore.logout()
      uni.redirectTo({ url: '/pages/login/index' })
    }
  })
}

/** 菜单点击分发（带开关的项不触发跳转） */
async function handleMenuClick(item: MenuItem) {
  if (item.hasSwitch) return

  switch (item.id) {
    case 'userRole': {
      const nextRole = userStore.userInfo?.role === 'elder' ? 'family' : 'elder'
      try {
        await userStore.updateProfile({ role: nextRole })
        toast(nextRole === 'elder' ? '已设置为被守护者' : '已设置为家属')
      } catch {
        toast('切换失败，请检查网络')
      }
      break
    }
    case 'userMode': {
      const next = !settingsStore.settings.elderMode
      settingsStore.set('elderMode', next)
      toast(next ? '已切换到长辈极简模式' : '已切换到标准模式')
      break
    }
    default:
      if (item.path) goPage(item.path)
      break
  }
}
</script>

<style lang="scss" scoped>
.profile-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f4efe6 0%, #f5f0e6 100%);
}

/* ---------- ① 用户卡 ---------- */
.user-section {
  padding: 32rpx;
}

.user-card {
  padding: 36rpx;
  background: linear-gradient(135deg, #8fa68e 0%, #a8c4a7 100%);
  border-radius: var(--radius-lg);
  box-shadow: 0 12rpx 40rpx rgba(143, 166, 142, 0.25);
}

.user-header {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.user-avatar {
  width: 120rpx;
  height: 120rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 52rpx;
  font-weight: 700;
  color: var(--primary-d);
  background: rgba(255, 255, 255, 0.9);
  border-radius: 50%;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.08);
}

.user-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.user-name {
  font-size: 40rpx;
  font-weight: 700;
  color: #ffffff;
}

.user-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.user-badge {
  padding: 6rpx 20rpx;
  font-size: 22rpx;
  color: #ffffff;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 999rpx;
}

.user-badge.light {
  background: rgba(255, 255, 255, 0.18);
}

.user-settings-btn {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 36rpx;
}

/* 统计三宫格 */
.stats-grid {
  display: flex;
  align-items: center;
  margin-top: 36rpx;
  padding-top: 28rpx;
  border-top: 2rpx solid rgba(255, 255, 255, 0.25);
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}

.stat-value {
  font-size: 44rpx;
  font-weight: 700;
  color: #ffffff;
}

.stat-value.primary {
  color: #fff3d6;
}

.stat-label {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.85);
}

.stat-divider {
  width: 2rpx;
  height: 56rpx;
  background: rgba(255, 255, 255, 0.25);
}

/* ---------- ② 功能入口 ---------- */
.menu-section {
  padding: 0 32rpx;
}

.menu-card {
  background: var(--surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow);
  padding: 0 32rpx;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 30rpx 0;
  border-bottom: 2rpx solid var(--divider);
}

.menu-item:last-child {
  border-bottom: none;
}

.menu-item:active {
  opacity: 0.7;
}

.menu-icon-wrapper {
  width: 76rpx;
  height: 76rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  border-radius: 24rpx;
}

.menu-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.menu-title {
  font-size: 32rpx;
  font-weight: 500;
  color: var(--text-1);
}

.menu-value {
  font-size: 24rpx;
  color: var(--text-2);
}

.menu-arrow {
  font-size: 40rpx;
  color: var(--text-3);
  line-height: 1;
}

.menu-badge {
  padding: 6rpx 18rpx;
  font-size: 22rpx;
  color: var(--primary-d);
  background: var(--primary-l);
  border-radius: 999rpx;
}

/* 开关 */
.menu-switch {
  width: 88rpx;
  height: 48rpx;
  padding: 4rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  background: rgba(125, 116, 107, 0.2);
  border-radius: 24rpx;
  transition: background 0.2s ease;
}

.menu-switch.on {
  background: var(--primary);
}

.menu-switch-knob {
  width: 40rpx;
  height: 40rpx;
  background: #ffffff;
  border-radius: 50%;
  transition: transform 0.2s ease;
}

.menu-switch.on .menu-switch-knob {
  transform: translateX(40rpx);
}

/* ---------- ③ 版本号 ---------- */
.version-text {
  display: block;
  margin-top: 48rpx;
  text-align: center;
  font-size: 24rpx;
  color: var(--text-3);
}

/* ---------- 账号操作面板 ---------- */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  align-items: flex-end;
  background: rgba(0, 0, 0, 0.45);
}

.action-sheet {
  width: 100%;
  padding: 24rpx 24rpx calc(24rpx + env(safe-area-inset-bottom));
  background: var(--surface);
  border-radius: 32rpx 32rpx 0 0;
}

.sheet-item {
  padding: 32rpx 0;
  text-align: center;
  font-size: 32rpx;
  color: var(--text-1);
  border-bottom: 2rpx solid var(--divider);
}

.sheet-item.danger {
  color: var(--danger);
}

.sheet-item.cancel {
  margin-top: 16rpx;
  border-bottom: none;
  color: var(--text-2);
}

/* ---------- 大字模式增强 ---------- */
.elder-mode .menu-title {
  font-size: 44rpx;
}

.elder-mode .menu-value {
  font-size: 32rpx;
}

.elder-mode .user-name {
  font-size: 52rpx;
}

.elder-mode .stat-value {
  font-size: 56rpx;
}
</style>
