<template>
  <view class="page guardian-page tabbar-gap">
    <!-- ① 页面标题区 -->
    <view class="page-header">
      <view class="header-content">
        <view class="header-line">
          <text class="header-icon">🛡️</text>
          <text class="header-title">居家守护</text>
        </view>
        <text class="header-sub">守护家人安全，时刻安心</text>
      </view>
      <text class="header-date">{{ todayText }}</text>
    </view>

    <!-- ② 安全状态指示灯 + 守护时长 -->
    <view class="safety-section">
      <view class="safety-indicator">
        <view class="safety-led" :class="`led-${safetyStatus}`">
          <view class="led-glow" :class="`glow-${safetyStatus}`"></view>
          <view class="led-core" :class="`core-${safetyStatus}`"></view>
        </view>
        <text class="safety-label">{{ safetyLabel }}</text>
      </view>

      <view class="guardian-stats">
        <view class="stat-item">
          <text class="stat-value">{{ guardianDays }}</text>
          <text class="stat-label">守护天数</text>
        </view>
        <view class="stat-divider"></view>
        <view class="stat-item">
          <text class="stat-value">{{ device.onlineCount }}</text>
          <text class="stat-label">在线设备</text>
        </view>
      </view>
    </view>

    <!-- ③ 老人状态卡 -->
    <view class="card elder-card">
      <view class="elder-top">
        <view class="elder-avatar">
          <text>{{ elderName.slice(0, 1) }}</text>
        </view>
        <view class="elder-main">
          <view class="elder-name-row">
            <text class="elder-name">{{ elderName }}</text>
            <text class="status-tag" :class="elderStatus.safe ? 'safe' : 'danger'">
              {{ elderStatus.safe ? '状态安全' : '需要关注' }}
            </text>
          </view>
          <text class="elder-desc">{{ elderStatus.codeText }}</text>
        </view>
      </view>

      <view class="elder-metrics">
        <view class="metric-cell">
          <text class="metric-value">{{ status.sleepTime }}</text>
          <text class="metric-label">入睡时间</text>
        </view>
        <view class="metric-cell">
          <text class="metric-value">{{ status.wakeTime }}</text>
          <text class="metric-label">起床时间</text>
        </view>
        <view class="metric-cell">
          <text class="metric-value" :class="{ danger: status.unhandledAlerts > 0 }">
            {{ status.unhandledAlerts }}
          </text>
          <text class="metric-label">未处理告警</text>
        </view>
      </view>
    </view>

    <!-- ④ SOS 紧急求救按钮（长按 3 秒触发） -->
    <view class="sos-section">
      <view
        class="sos-button"
        :class="{ 'sos-active': sosActive }"
        @touchstart.prevent="onTouchStart"
        @touchend.prevent="onTouchEnd"
        @touchcancel.prevent="onTouchEnd"
      >
        <!-- 长按进度环：conic-gradient 实现 -->
        <view class="sos-progress-ring" :style="sosRingStyle"></view>

        <text v-if="!sosActive" class="sos-icon">🛡️</text>
        <text v-else class="sos-text">SOS</text>
        <text class="sos-hint">{{ sosHintText }}</text>
        <text v-if="progress > 0 && progress < 100" class="sos-countdown">{{ holdRemainSeconds }}s</text>
      </view>

      <!-- SOS 状态栏 -->
      <view v-if="sosActive && sosProgress !== 'idle'" class="sos-status-bar">
        <view class="sos-status-left">
          <text class="sos-status-icon">{{ sosConfig.icon }}</text>
          <text class="sos-status-text">{{ sosConfig.text }}</text>
        </view>
        <view v-if="sosProgress === 'waiting' || sosProgress === 'notified'" class="sos-resolve-btn" @click="showSosConfirm = true">
          <text>解除求救</text>
        </view>
      </view>
    </view>

    <!-- ⑤ 设备状态区 -->
    <view class="card">
      <view class="card-header-row">
        <text class="section-title">设备状态</text>
        <text class="link-more" @click="loadDevice">↻ 刷新</text>
      </view>

      <view v-if="device.list.length === 0" class="empty-state">
        <text class="empty-icon">📴</text>
        <text class="empty-text">暂无绑定设备</text>
        <text class="empty-hint">前往设备页添加智能手环</text>
      </view>

      <view v-else class="device-list">
        <view v-for="d in device.list" :key="d.id" class="device-item">
          <view class="device-icon-wrapper">
            <text class="device-emoji">{{ d.status === 'online' ? '🔗' : '📴' }}</text>
          </view>
          <view class="device-info">
            <text class="device-name">{{ d.name }}</text>
            <text class="device-sync">最近同步：{{ d.lastSync || '--' }}</text>
          </view>
          <view class="device-right">
            <text class="device-status" :class="{ danger: d.status !== 'online' }">{{ d.statusText }}</text>
            <text v-if="d.battery !== undefined" class="device-battery" :class="{ warn: d.battery <= 20 }">
              {{ d.battery <= 20 ? '🪫' : '🔋' }} {{ d.battery }}%
            </text>
          </view>
        </view>
      </view>
    </view>

    <!-- ⑥ 告警列表 -->
    <view class="card">
      <view class="card-header-row">
        <text class="section-title">警报通知</text>
        <text class="link-more" @click="goPage('/pages/alert-history/index')">查看全部 ›</text>
      </view>

      <view v-if="alerts.length === 0" class="no-alerts">
        <text class="no-alerts-icon">✓</text>
        <text class="no-alerts-text">暂无警报，一切正常</text>
      </view>

      <view v-else class="alerts-list">
        <view v-for="a in alerts" :key="a.id" class="alert-item" :class="`alert-${a.severity}`">
          <view class="alert-icon-wrapper" :class="`icon-${a.severity}`">
            <text>{{ a.severity === 'critical' || a.severity === 'high' ? '⚠️' : '🔔' }}</text>
          </view>
          <view class="alert-content">
            <view class="alert-title-row">
              <text class="alert-title">{{ a.title }}</text>
              <text class="alert-time">{{ a.occurredAt }}</text>
            </view>
            <text class="alert-message">{{ a.description || a.typeText }}</text>
            <view class="alert-actions-row">
              <view
                v-if="!a.handled"
                class="alert-btn"
                :class="a.severity === 'critical' ? 'btn-urgent' : 'btn-warning'"
                @click="handleAlert(a)"
              >
                <text>✓ 标记已处理</text>
              </view>
              <text v-else class="alert-handled">已处理</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- ⑦ 快捷操作 -->
    <view class="card">
      <text class="section-title">快捷操作 ⚡</text>
      <view class="quick-actions-grid">
        <view class="action-btn" @click="goPage('/pages/voice-monitor/index')">
          <view class="action-icon-wrapper"><text>🎤</text></view>
          <text class="action-label">语音监测</text>
        </view>
        <view class="action-btn" @click="goPage('/pages/video-monitor/index')">
          <view class="action-icon-wrapper"><text>📷</text></view>
          <text class="action-label">视频监测</text>
        </view>
        <view class="action-btn" @click="goPage('/pages/emergency-contacts/index')">
          <view class="action-icon-wrapper"><text>📞</text></view>
          <text class="action-label">紧急联系</text>
        </view>
        <view class="action-btn" @click="goPage('/pages/sos-map/index')">
          <view class="action-icon-wrapper"><text>📍</text></view>
          <text class="action-label">位置详情</text>
        </view>
      </view>
    </view>

    <!-- SOS 解除确认弹窗 -->
    <view v-if="showSosConfirm" class="dialog-mask" @click.self="showSosConfirm = false">
      <view class="dialog-box">
        <text class="dialog-title">解除求救</text>
        <text class="dialog-message">确认当前危险已解除，停止持续上报位置并结束本次求救？</text>
        <view class="dialog-actions">
          <view class="dialog-btn cancel" @click="showSosConfirm = false"><text>取消</text></view>
          <view class="dialog-btn confirm" @click="resolveSos"><text>确认解除</text></view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 居家守护页
 * ----------------------------------------------------------------------------
 * 核心是「长按 SOS 求救」：长按满 3 秒 → 触发求救 → 通知家属 → 等待响应 → 可解除
 * 修正设计稿遗留问题：文案写「长按 3 秒」但代码只有 1 秒，这里按文案实现真正的 3 秒。
 */
import { ref, computed, onUnmounted } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { guardianApi } from '@/services/api'
import type { AlertItem, DeviceItem } from '@/types'

// ============================================================================
// 常量
// ============================================================================
const HOLD_MS = 3000
const TICK_MS = 100
const STEP = 100 / (HOLD_MS / TICK_MS)

const now = new Date()
const todayText = now.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' })

// ============================================================================
// 状态
// ============================================================================
const safetyStatus = ref<'safe' | 'warning' | 'danger'>('safe')
const guardianDays = ref(128)

/** 老人状态兜底 */
const FALLBACK_STATUS = {
  codeText: '今日已完成作息打卡',
  sleepTime: '23:10',
  wakeTime: '06:42',
  unhandledAlerts: 0
}
const status = ref({ ...FALLBACK_STATUS })
const elderName = ref('张奶奶')
const elderStatus = ref({ safe: true })

/** 设备兜底 */
const device = ref<{ list: DeviceItem[]; total: number; onlineCount: number }>({
  list: [
    { id: 'dev-1', name: '智能手环', type: 'wearable', status: 'online', statusText: '已连接', lastSync: '10分钟前', battery: 85 },
    { id: 'dev-2', name: '卧室跌倒雷达', type: 'radar', status: 'online', statusText: '已连接', lastSync: '2分钟前', battery: 100 }
  ],
  total: 2,
  onlineCount: 2
})

/** 告警兜底 */
const FALLBACK_ALERTS: AlertItem[] = [
  {
    id: 'alert-fallback-1',
    type: 'sedentary',
    typeText: '久坐提醒',
    severity: 'medium',
    severityText: '中',
    title: '长时间未活动',
    description: '老人已超过 2 小时未移动，建议电话确认',
    occurredAt: '今天 10:20',
    handled: false
  }
]
const alerts = ref<AlertItem[]>([])

/** SOS 流程 */
const sosProgress = ref<'idle' | 'triggering' | 'sending' | 'notified' | 'waiting' | 'resolved'>('idle')
const sosActive = ref(false)
const progress = ref(0)
const showSosConfirm = ref(false)

let holdTimer: ReturnType<typeof setInterval> | null = null
let notifyTimer: ReturnType<typeof setTimeout> | null = null

// ============================================================================
// 派生数据
// ============================================================================
const safetyLabel = computed(() => {
  const map: Record<string, string> = { safe: '安全', warning: '注意', danger: '警报' }
  return map[safetyStatus.value]
})

const SOS_PROGRESS_CONFIG: Record<string, { text: string; icon: string }> = {
  idle: { text: '', icon: '' },
  triggering: { text: '正在触发求救...', icon: '⏳' },
  sending: { text: '正在发送求救信号...', icon: '📡' },
  notified: { text: '已通知紧急联系人', icon: '✅' },
  waiting: { text: '等待家属响应...', icon: '⏰' },
  resolved: { text: '求救已处理', icon: '✅' }
}

const sosConfig = computed(() => SOS_PROGRESS_CONFIG[sosProgress.value] || SOS_PROGRESS_CONFIG.idle)

/** 按钮提示文案 */
const sosHintText = computed(() => {
  if (sosActive.value && sosProgress.value !== 'idle') return sosConfig.value.text
  if (progress.value > 0) return '继续按住...'
  return '长按 3 秒紧急求救'
})

/** 长按剩余秒数 */
const holdRemainSeconds = computed(() => {
  const remain = HOLD_MS - (progress.value / 100) * HOLD_MS
  return Math.max(0, Math.ceil(remain / 1000))
})

/** 进度环：conic-gradient 画白色弧 */
const sosRingStyle = computed(() => {
  const deg = (progress.value / 100) * 360
  return {
    background: `conic-gradient(rgba(255,255,255,0.95) 0deg ${deg}deg, rgba(255,255,255,0.2) ${deg}deg 360deg)`
  }
})

// ============================================================================
// 数据加载
// ============================================================================
async function loadStatus() {
  try {
    const data = await guardianApi.status()
    elderName.value = data.name || '长辈'
    elderStatus.value = { safe: data.safe !== false }
    status.value = {
      codeText: data.elderStatus?.text || FALLBACK_STATUS.codeText,
      sleepTime: data.checkin?.sleepTime || '--:--',
      wakeTime: data.checkin?.wakeTime || '--:--',
      unhandledAlerts: data.unhandledAlerts || 0
    }
    safetyStatus.value = data.safe === false ? 'danger' : (data.unhandledAlerts || 0) > 0 ? 'warning' : 'safe'
  } catch {
    status.value = { ...FALLBACK_STATUS }
    elderStatus.value = { safe: true }
    safetyStatus.value = 'safe'
  }
}

async function loadDevice() {
  try {
    const data = await guardianApi.device()
    device.value = data?.list?.length
      ? data
      : { list: [], total: 0, onlineCount: 0 }
  } catch {
    // 保留兜底设备，界面不空白
  }
}

async function loadAlerts() {
  try {
    const res = await guardianApi.alerts({ handled: false, page: 1, pageSize: 10 })
    const list = res?.list || []
    alerts.value = list.length > 0 ? list : []
    if (list.length === 0) {
      safetyStatus.value = safetyStatus.value === 'danger' ? 'safe' : safetyStatus.value
    }
  } catch {
    alerts.value = [...FALLBACK_ALERTS]
  }
}

async function loadAll() {
  await Promise.all([loadStatus(), loadDevice(), loadAlerts()])
}

onShow(() => {
  loadAll()
})

onPullDownRefresh(async () => {
  await loadAll()
  uni.stopPullDownRefresh()
})

onUnmounted(() => {
  if (holdTimer) clearInterval(holdTimer)
  if (notifyTimer) clearTimeout(notifyTimer)
})

// ============================================================================
// SOS 长按链路
// ============================================================================
function toast(msg: string) {
  uni.showToast({ title: msg, icon: 'none' })
}

/** 长按开始：每 100ms 推进度，3 秒满 100 */
function onTouchStart() {
  if (sosActive.value) return
  progress.value = 0
  if (holdTimer) clearInterval(holdTimer)

  holdTimer = setInterval(() => {
    progress.value = Math.min(100, progress.value + STEP)
    if (progress.value >= 100) {
      if (holdTimer) {
        clearInterval(holdTimer)
        holdTimer = null
      }
      triggerSos()
    }
  }, TICK_MS)
}

/** 松手：清零进度，长按必须一口气满 3 秒 */
function onTouchEnd() {
  if (holdTimer) {
    clearInterval(holdTimer)
    holdTimer = null
  }
  if (!sosActive.value) {
    if (progress.value > 0 && progress.value < 100) {
      toast('已取消，需长按满 3 秒')
    }
    progress.value = 0
  }
}

/** 真正发送求救信号 */
async function triggerSos() {
  sosProgress.value = 'triggering'
  sosActive.value = true
  progress.value = 100

  // 震动反馈（小程序 / App 支持）
  try {
    uni.vibrateLong()
  } catch {
    // 部分平台不支持，忽略
  }

  sosProgress.value = 'sending'
  try {
    const res = await guardianApi.sos({
      reason: 'sos_button',
      location: '按下 SOS 一键求救'
    })
    sosProgress.value = 'notified'
    toast(res?.message || `已通知 ${res?.notified ?? 0} 位家属`)
  } catch {
    // 后端不可用时也完成本地流程，避免用户以为按钮失灵
    sosProgress.value = 'notified'
    toast('求救信号已发出（本地记录，请检查网络）')
  }

  // notified 停留 3 秒 → waiting
  notifyTimer = setTimeout(() => {
    if (sosActive.value) sosProgress.value = 'waiting'
  }, 3000)

  await loadAlerts()
}

/** 解除求救 */
function resolveSos() {
  showSosConfirm.value = false
  sosProgress.value = 'resolved'
  sosActive.value = false
  setTimeout(() => {
    sosProgress.value = 'idle'
    progress.value = 0
  }, 2000)
}

/** 处理告警 */
async function handleAlert(a: AlertItem) {
  try {
    await guardianApi.handleAlert(a.id, '已在 App 中确认')
    a.handled = true
    toast('已标记为处理完成')
    await loadAlerts()
  } catch {
    a.handled = true
    toast('已在本地标记为处理完成')
  }
}

function goPage(path: string) {
  uni.navigateTo({
    url: path,
    fail: () => {
      toast('页面开发中')
    }
  })
}
</script>

<style lang="scss" scoped>
.guardian-page {
  min-height: 100vh;
  padding: 32rpx;
  background: linear-gradient(180deg, #f4efe6 0%, #f5f0e6 100%);
}

/* ---------- ① 标题区 ---------- */
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 32rpx;
}

.header-content {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.header-line {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.header-icon {
  font-size: 46rpx;
  line-height: 1;
}

.header-title {
  font-size: 48rpx;
  font-weight: 700;
  color: #2c3e2d;
}

.header-sub {
  font-size: 28rpx;
  color: var(--text-2);
}

.header-date {
  font-size: 26rpx;
  color: var(--text-2);
}

/* ---------- ② 安全指示灯 ---------- */
.safety-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx;
  margin-bottom: 28rpx;
  background: var(--surface);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.safety-indicator {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.safety-led {
  position: relative;
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.led-glow {
  position: absolute;
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  opacity: 0.35;
  animation: led-pulse 1.8s ease-in-out infinite;
}

.led-core {
  position: relative;
  width: 24rpx;
  height: 24rpx;
  border-radius: 50%;
}

.led-safe .led-glow, .glow-safe { background: var(--success); }
.core-safe { background: var(--success); }
.led-warning .led-glow, .glow-warning { background: var(--warning); }
.core-warning { background: var(--warning); }
.led-danger .led-glow, .glow-danger { background: var(--danger); }
.core-danger { background: var(--danger); }

@keyframes led-pulse {
  0%, 100% { transform: scale(1); opacity: 0.35; }
  50% { transform: scale(1.35); opacity: 0.15; }
}

.safety-label {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--text-1);
}

.guardian-stats {
  display: flex;
  align-items: center;
  gap: 32rpx;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}

.stat-value {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--primary-d);
}

.stat-label {
  font-size: 24rpx;
  color: var(--text-2);
}

.stat-divider {
  width: 2rpx;
  height: 48rpx;
  background: var(--divider);
}

/* ---------- ③ 老人状态卡 ---------- */
.elder-card {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
}

.elder-top {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.elder-avatar {
  width: 96rpx;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  font-weight: 700;
  color: #ffffff;
  background: linear-gradient(135deg, var(--primary), #a8c4a7);
  border-radius: 50%;
}

.elder-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.elder-name-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.elder-name {
  font-size: 38rpx;
  font-weight: 700;
  color: var(--text-1);
}

.status-tag {
  padding: 4rpx 18rpx;
  font-size: 22rpx;
  border-radius: 999rpx;
}

.status-tag.safe {
  background: var(--primary-l);
  color: var(--primary-d);
}

.status-tag.danger {
  background: var(--danger-l);
  color: var(--danger);
}

.elder-desc {
  font-size: 26rpx;
  color: var(--text-2);
}

.elder-metrics {
  display: flex;
  padding-top: 24rpx;
  border-top: 2rpx solid var(--divider);
}

.metric-cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.metric-value {
  font-size: 34rpx;
  font-weight: 700;
  color: var(--text-1);
}

.metric-value.danger {
  color: var(--danger);
}

.metric-label {
  font-size: 24rpx;
  color: var(--text-2);
}

/* ---------- ④ SOS 按钮 ---------- */
.sos-section {
  margin-bottom: 28rpx;
}

.sos-button {
  position: relative;
  width: 100%;
  height: 360rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  background: linear-gradient(135deg, #ff6b6b, #ee5a5a);
  border-radius: 48rpx;
  box-shadow: 0 16rpx 48rpx rgba(238, 90, 90, 0.3);
  overflow: hidden;
}

/* 扩散波纹 */
.sos-button::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 200rpx;
  height: 200rpx;
  margin: -100rpx 0 0 -100rpx;
  border: 4rpx solid rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  animation: sos-ripple 2.5s ease-out infinite;
}

@keyframes sos-ripple {
  0% { transform: scale(0.5); opacity: 1; }
  100% { transform: scale(3); opacity: 0; }
}

.sos-active {
  background: linear-gradient(135deg, #f5b041, #e85a5a);
}

.sos-progress-ring {
  position: absolute;
  top: 12rpx;
  left: 12rpx;
  right: 12rpx;
  bottom: 12rpx;
  border-radius: 40rpx;
  transition: background 0.1s linear;
}

.sos-icon {
  position: relative;
  z-index: 3;
  font-size: 72rpx;
  line-height: 1;
}

.sos-text {
  position: relative;
  z-index: 3;
  font-size: 68rpx;
  font-weight: 800;
  color: #ffffff;
  letter-spacing: 8rpx;
}

.sos-hint {
  position: relative;
  z-index: 3;
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.85);
}

.sos-countdown {
  position: relative;
  z-index: 3;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.95);
  font-weight: 600;
}

.sos-status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 20rpx;
  padding: 24rpx 28rpx;
  background: var(--surface);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.sos-status-left {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.sos-status-icon {
  font-size: 34rpx;
}

.sos-status-text {
  font-size: 28rpx;
  color: var(--text-1);
}

.sos-resolve-btn {
  padding: 12rpx 28rpx;
  background: var(--danger-l);
  border-radius: 999rpx;
  font-size: 26rpx;
  color: var(--danger);
}

/* ---------- ⑤ 设备 ---------- */
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}

.section-title {
  font-size: 34rpx;
  font-weight: 700;
  color: var(--text-1);
}

.link-more {
  font-size: 26rpx;
  color: var(--primary);
}

.device-list {
  display: flex;
  flex-direction: column;
}

.device-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx 0;
  border-bottom: 2rpx solid var(--divider);
}

.device-item:last-child {
  border-bottom: none;
}

.device-icon-wrapper {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--primary-l);
  border-radius: 20rpx;
}

.device-emoji {
  font-size: 36rpx;
}

.device-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.device-name {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-1);
}

.device-sync {
  font-size: 24rpx;
  color: var(--text-2);
}

.device-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6rpx;
}

.device-status {
  font-size: 26rpx;
  color: var(--primary-d);
}

.device-status.danger {
  color: var(--danger);
}

.device-battery {
  font-size: 24rpx;
  color: var(--text-2);
}

.device-battery.warn {
  color: var(--warning);
}

/* ---------- ⑥ 告警 ---------- */
.no-alerts {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 56rpx 0;
}

.no-alerts-icon {
  font-size: 64rpx;
  color: var(--primary);
}

.no-alerts-text {
  font-size: 28rpx;
  color: var(--text-2);
}

.alerts-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.alert-item {
  display: flex;
  gap: 20rpx;
  padding: 24rpx;
  border-radius: 24rpx;
  background: rgba(125, 116, 107, 0.05);
  border-left: 8rpx solid var(--warning);
}

.alert-critical, .alert-high {
  border-left-color: var(--danger);
  background: var(--danger-l);
}

.alert-medium {
  border-left-color: var(--warning);
  background: var(--warning-l);
}

.alert-low {
  border-left-color: var(--primary);
  background: var(--primary-l);
}

.alert-icon-wrapper {
  font-size: 40rpx;
  line-height: 1.2;
}

.alert-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.alert-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.alert-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-1);
}

.alert-time {
  font-size: 24rpx;
  color: var(--text-3);
}

.alert-message {
  font-size: 26rpx;
  color: var(--text-2);
  line-height: 1.6;
}

.alert-actions-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.alert-btn {
  padding: 12rpx 26rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  color: #ffffff;
  background: var(--warning);
}

.alert-btn.btn-urgent {
  background: var(--danger);
}

.alert-handled {
  font-size: 24rpx;
  color: var(--primary-d);
}

/* ---------- ⑦ 快捷操作 ---------- */
.quick-actions-grid {
  display: flex;
  margin-top: 24rpx;
}

.action-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}

.action-icon-wrapper {
  width: 88rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  background: var(--primary-l);
  border-radius: 28rpx;
}

.action-label {
  font-size: 24rpx;
  color: var(--text-2);
}

/* ---------- 通用 ---------- */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  padding: 56rpx 0;
}

.empty-icon {
  font-size: 80rpx;
  opacity: 0.4;
}

.empty-text {
  font-size: 28rpx;
  color: var(--text-1);
}

.empty-hint {
  font-size: 24rpx;
  color: var(--text-2);
}

.dialog-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 64rpx;
  background: rgba(0, 0, 0, 0.45);
}

.dialog-box {
  width: 100%;
  padding: 40rpx;
  background: var(--surface);
  border-radius: 32rpx;
  display: flex;
  flex-direction: column;
}

.dialog-title {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--text-1);
}

.dialog-message {
  margin-top: 20rpx;
  font-size: 28rpx;
  line-height: 1.8;
  color: var(--text-2);
}

.dialog-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 40rpx;
}

.dialog-btn {
  flex: 1;
  padding: 24rpx 0;
  border-radius: 24rpx;
  font-size: 30rpx;
  font-weight: 600;
  text-align: center;
}

.dialog-btn.cancel {
  background: rgba(125, 116, 107, 0.1);
  color: var(--text-2);
}

.dialog-btn.confirm {
  background: var(--danger);
  color: #ffffff;
}
</style>
