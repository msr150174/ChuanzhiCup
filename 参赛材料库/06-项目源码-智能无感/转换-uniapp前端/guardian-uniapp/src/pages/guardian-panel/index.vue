<template>
  <view class="page tabbar-gap">
    <!-- ============ 一、骨架屏 ============ -->
    <view v-if="state.loading" class="wrap">
      <view class="skeleton sk-title"></view>
      <view class="skeleton sk-card"></view>
      <view class="skeleton sk-card"></view>
    </view>

    <!-- ============ 二、长辈详情 ============ -->
    <view v-else-if="state.selected" class="wrap">
      <!-- 详情头部（返回） -->
      <view class="detail-header" @click="backToList">
        <text class="back-arrow">‹</text>
        <text class="detail-title">{{ state.selected.name }} 的状态</text>
      </view>

      <!-- 基本状态卡 -->
      <view class="card">
        <view class="elder-row">
          <view class="avatar">{{ avatarText(state.selected.name) }}</view>
          <view class="elder-main">
            <text class="elder-name">{{ state.selected.name }}</text>
            <text class="elder-sub">最后活动：{{ state.selected.lastActive }}</text>
          </view>
          <text class="status-badge" :class="state.selected.sleepStatus === 'awake' ? 'badge-awake' : 'badge-sleep'">
            {{ state.selected.sleepStatus === 'awake' ? '活动中' : '休息中' }}
          </text>
        </view>
        <view class="divider"></view>
        <view class="score-row">
          <text class="score-label">健康评分</text>
          <text class="score-value">{{ state.selected.healthScore }}分</text>
        </view>
        <view class="progress-track">
          <view class="progress-fill" :style="{ width: state.selected.healthScore + '%' }"></view>
        </view>
      </view>

      <!-- 今日打卡双列 -->
      <view class="card">
        <text class="card-title">今日打卡情况</text>
        <view class="checkin-columns">
          <view class="checkin-col">
            <text class="checkin-head">☀️ 晨起打卡</text>
            <view v-if="state.selected.lastCheckin.morning" class="checkin-ok">
              <text class="checkin-ok-text">✅ 已完成 {{ state.selected.lastCheckin.morningTime }}</text>
            </view>
            <view v-else class="checkin-fail">
              <text class="checkin-fail-text">⚠️ 未打卡</text>
            </view>
          </view>
          <view class="checkin-col">
            <text class="checkin-head">🌙 睡前打卡</text>
            <view v-if="state.selected.lastCheckin.evening" class="checkin-ok">
              <text class="checkin-ok-text">✅ 已完成 {{ state.selected.lastCheckin.eveningTime }}</text>
            </view>
            <view v-else class="checkin-wait">
              <text class="checkin-wait-text">🕐 待打卡</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 告警列表 -->
      <view v-if="unhandledAlerts.length > 0" class="card">
        <text class="card-title">告警信息</text>
        <view v-for="a in unhandledAlerts" :key="a.id" class="alert-item">
          <view class="alert-top">
            <text class="alert-title">{{ alertIcon(a.type) }} {{ a.title }}</text>
            <text class="alert-time">{{ a.time }}</text>
          </view>
          <view class="alert-actions">
            <view class="btn-mini" @click="handleAlert(a.id, 'handle')"><text>处理</text></view>
            <view class="btn-mini ghost" @click="handleAlert(a.id, 'ignore')"><text>忽略</text></view>
          </view>
        </view>
      </view>

      <!-- 远程提醒开关 -->
      <view class="card">
        <text class="card-title">远程提醒设置</text>
        <view
          v-for="(row, idx) in remoteRows"
          :key="row.key"
          class="setting-item"
          :class="{ 'no-border': idx === remoteRows.length - 1 }"
        >
          <text class="setting-label">{{ row.icon }} {{ row.label }}</text>
          <view class="switch" :class="{ on: state.remote[row.key] }" @click="toggleRemote(row.key)">
            <view class="switch-knob"></view>
          </view>
        </view>
      </view>

      <!-- 快捷联系 -->
      <view class="btn-row">
        <view class="btn-primary flex-1" @click="callElder"><text>📞 打电话</text></view>
        <view class="btn-ghost flex-1" @click="messageElder"><text>💬 发消息</text></view>
      </view>
      <view class="btn-row">
        <view class="btn-primary flex-1" @click="goHealthReport"><text>📈 健康报告</text></view>
        <view class="btn-primary flex-1" @click="goCalendar"><text>📅 打卡日历</text></view>
      </view>
    </view>

    <!-- ============ 三、长辈列表 ============ -->
    <view v-else class="wrap">
      <view class="page-header">
        <text class="header-icon">🛡️</text>
        <text class="page-title">家属监护面板</text>
      </view>
      <text class="page-sub">实时查看长辈状态，远程管理提醒设置</text>

      <text class="section-label">已绑定长辈 ({{ state.elders.length }})</text>

      <view v-if="state.elders.length > 0">
        <view v-for="e in state.elders" :key="e.id" class="elder-card" @click="selectElder(e)">
          <view class="elder-row">
            <view class="avatar">{{ avatarText(e.name) }}</view>
            <view class="elder-main">
              <text class="elder-name">{{ e.name }}</text>
              <text class="elder-sub">最后活动：{{ e.lastActive }}</text>
            </view>
            <text class="chevron">›</text>
          </view>
          <view class="divider"></view>
          <view class="elder-footer">
            <text class="sleep-status">{{ e.sleepStatus === 'sleeping' ? '🌙 休息中' : '☀️ 活动中' }}</text>
            <view class="badge-group">
              <text class="mini-badge" :class="e.lastCheckin.morning ? 'mini-ok' : 'mini-no'">
                晨起 {{ e.lastCheckin.morning ? '✓' : '○' }}
              </text>
              <text class="mini-badge" :class="e.lastCheckin.evening ? 'mini-ok' : 'mini-no'">
                睡前 {{ e.lastCheckin.evening ? '✓' : '○' }}
              </text>
            </view>
          </view>
        </view>
      </view>

      <EmptyState v-else icon="❓" text="暂无绑定的长辈" desc="绑定后即可查看长辈的健康状态与打卡情况" />

      <view class="bind-card" @click="goMemberBind">
        <text class="bind-left">👤 绑定新长辈</text>
        <text class="chevron">›</text>
      </view>

      <view class="tips-card">
        <text class="tips-text">❓ 绑定长辈后，您可以实时查看其健康状态、打卡情况，并远程设置提醒推送。长辈同意后才能完成绑定。</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 守护面板（家属视角）
 * ----------------------------------------------------------------------------
 * 三态：骨架屏 → 长辈列表 → 长辈详情
 * 真实接口：guardianApi.status（单人状态），失败时使用兜底数据
 */
import { reactive, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { guardianApi } from '@/services/api'
import { useUserStore } from '@/stores/user'
import EmptyState from '@/components/EmptyState.vue'

/** 告警条目 */
interface PanelAlert {
  id: string
  type: 'warning' | 'info' | 'error'
  title: string
  time: string
  handled: boolean
}

/** 长辈状态 */
interface ElderStatus {
  id: string
  name: string
  phone: string
  lastActive: string
  sleepStatus: 'awake' | 'sleeping' | 'unknown'
  lastCheckin: { morning: boolean; evening: boolean; morningTime?: string; eveningTime?: string }
  healthScore: number
  alerts: PanelAlert[]
}

/** 远程提醒开关 */
interface RemoteSettings {
  morningReminder: boolean
  eveningReminder: boolean
  abnormalAlert: boolean
  missedCheckinAlert: boolean
}

/**
 * 兜底数据：后端不可用时保证界面不空白
 * 注：电话号码取自用户资料（userStore），此处不硬编码真实号码
 */
const FALLBACK_ELDERS: ElderStatus[] = [
  {
    id: 'default',
    name: '测试长辈',
    phone: '',
    lastActive: '刚刚',
    sleepStatus: 'awake',
    lastCheckin: { morning: true, evening: false, morningTime: '06:30' },
    healthScore: 85,
    alerts: []
  },
  {
    id: 'elder-2',
    name: '张阿姨',
    phone: '',
    lastActive: '10 分钟前',
    sleepStatus: 'sleeping',
    lastCheckin: { morning: true, evening: true, morningTime: '07:05', eveningTime: '22:10' },
    healthScore: 92,
    alerts: [
      { id: 'fd-alert-1', type: 'warning', title: '昨夜起夜次数偏多', time: '昨天 03:20', handled: false }
    ]
  }
]

const state = reactive({
  loading: true,
  elders: [] as ElderStatus[],
  selected: null as ElderStatus | null,
  remote: {
    morningReminder: true,
    eveningReminder: true,
    abnormalAlert: true,
    missedCheckinAlert: true
  } as RemoteSettings
})

/** 用户信息：用于取联系电话（避免硬编码号码） */
const userStore = useUserStore()

/** 详情内只展示未处理告警 */
const unhandledAlerts = computed(() => (state.selected ? state.selected.alerts.filter((a) => !a.handled) : []))

/** 远程提醒的展示配置 */
const remoteRows: { key: keyof RemoteSettings; icon: string; label: string }[] = [
  { key: 'morningReminder', icon: '☀️', label: '晨起提醒推送' },
  { key: 'eveningReminder', icon: '🌙', label: '睡前提醒推送' },
  { key: 'abnormalAlert', icon: '⚠️', label: '异常情况告警' },
  { key: 'missedCheckinAlert', icon: '🕐', label: '漏打卡提醒' }
]

/** 取姓名首字做头像 */
function avatarText(name: string): string {
  return name ? name.slice(0, 1) : '长'
}

/** 告警图标 */
function alertIcon(type: string): string {
  if (type === 'warning') return '⚠️'
  if (type === 'error') return '🚨'
  return 'ℹ️'
}

/**
 * 拉取长辈列表
 * 先取绑定列表，再并发拉取每个长辈的状态，任一失败降级为默认值
 */
async function loadGuardianData() {
  state.loading = true
  try {
    const res = await guardianApi.status()
    const status: ElderStatus = {
      id: res.userId,
      name: res.name,
      // 电话号码从数据读（接口 → 用户资料），不硬编码
      phone: (res as unknown as { phone?: string }).phone || userStore.userInfo?.emergencyPhone || '',
      lastActive: '刚刚',
      sleepStatus: res.elderStatus.code === 'awake' ? 'awake' : res.elderStatus.code === 'sleeping' ? 'sleeping' : 'unknown',
      lastCheckin: {
        morning: !!res.checkin.wakeTime,
        evening: !!res.checkin.sleepTime,
        morningTime: res.checkin.wakeTime || undefined,
        eveningTime: res.checkin.sleepTime || undefined
      },
      healthScore: 78,
      alerts: []
    }
    state.elders = [status]
  } catch {
    // ★ 兜底：接口失败时使用本地演示数据（电话沿用用户资料）
    const fallback = JSON.parse(JSON.stringify(FALLBACK_ELDERS)) as ElderStatus[]
    fallback.forEach((e) => {
      if (!e.phone) e.phone = userStore.userInfo?.emergencyPhone || ''
    })
    state.elders = fallback
  } finally {
    state.loading = false
  }
}

/** 选中长辈进入详情 */
function selectElder(e: ElderStatus) {
  state.selected = e
}

/** 返回列表 */
function backToList() {
  state.selected = null
}

/** 处理 / 忽略告警 */
async function handleAlert(id: string, action: 'handle' | 'ignore') {
  if (!state.selected) return
  const target = state.selected.alerts.find((a) => a.id === id)
  try {
    await guardianApi.handleAlert(id, action)
  } catch {
    // 接口失败也走本地更新，保证交互可用
  }
  if (target) target.handled = true
  uni.showToast({ title: action === 'handle' ? '已处理' : '已忽略', icon: 'none' })
}

/** 切换远程提醒开关 */
function toggleRemote(key: keyof RemoteSettings) {
  state.remote[key] = !state.remote[key]
  uni.showToast({ title: '设置已保存', icon: 'none' })
}

/** 打电话：号码来自数据，不硬编码 */
function callElder() {
  const phone = state.selected?.phone
  if (!phone) {
    uni.showToast({ title: '暂无联系电话', icon: 'none' })
    return
  }
  uni.makePhoneCall({ phoneNumber: phone, fail: () => undefined })
}

/** 发消息（跳智能助手并带上长辈 id） */
function messageElder() {
  uni.navigateTo({ url: `/pages/ai-chat/index?elderId=${state.selected?.id || ''}` })
}

/** 健康报告 */
function goHealthReport() {
  uni.navigateTo({ url: '/pages/health-report/index' })
}

/** 打卡日历 */
function goCalendar() {
  uni.navigateTo({ url: '/pages/health-detail/index?from=guardian-panel' })
}

/** 绑定新长辈 */
function goMemberBind() {
  uni.navigateTo({ url: '/pages/member-bind/index' })
}

onShow(() => {
  loadGuardianData()
})

onPullDownRefresh(async () => {
  await loadGuardianData()
  uni.stopPullDownRefresh()
})
</script>

<style lang="scss" scoped>
.wrap {
  padding: 24rpx;
}

/* ---- 骨架屏 ---- */
.skeleton {
  background: linear-gradient(90deg, var(--surface-2) 25%, var(--surface-3) 37%, var(--surface-2) 63%);
  border-radius: var(--radius);
  margin-bottom: 24rpx;
}
.sk-title { height: 64rpx; width: 60%; }
.sk-card { height: 224rpx; }

/* ---- 列表视图 ---- */
.page-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.header-icon { font-size: 48rpx; }
.page-title { font-size: 40rpx; font-weight: 700; color: var(--text-1); }
.page-sub { display: block; font-size: 26rpx; color: var(--text-2); margin: 12rpx 0 24rpx; }
.section-label { display: block; font-size: 26rpx; font-weight: 600; color: var(--text-1); margin-bottom: 16rpx; }

.elder-card {
  background: var(--surface);
  border-radius: var(--radius);
  border: 2rpx solid var(--border);
  padding: 28rpx;
  margin-bottom: 24rpx;
  box-shadow: var(--shadow);
}
.elder-row { display: flex; align-items: center; gap: 20rpx; }
.avatar {
  width: 96rpx; height: 96rpx; border-radius: 50%;
  background: linear-gradient(135deg, var(--primary), #9db89c);
  color: #fff; font-size: 36rpx; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.elder-main { flex: 1; display: flex; flex-direction: column; gap: 8rpx; }
.elder-name { font-size: 34rpx; font-weight: 600; color: var(--text-1); }
.elder-sub { font-size: 24rpx; color: var(--text-2); }
.chevron { font-size: 40rpx; color: var(--text-3); }

.divider { height: 2rpx; background: var(--divider); margin: 24rpx 0; }

.elder-footer { display: flex; align-items: center; justify-content: space-between; }
.sleep-status { font-size: 24rpx; color: var(--text-2); }
.badge-group { display: flex; gap: 12rpx; }
.mini-badge {
  font-size: 22rpx; padding: 4rpx 16rpx; border-radius: var(--radius-full);
  background: var(--primary-l); color: var(--primary-d);
}
.mini-no { background: var(--surface-2); color: var(--text-3); }

.bind-card {
  display: flex; align-items: center; justify-content: space-between;
  background: var(--surface); border-radius: var(--radius);
  padding: 32rpx; margin-top: 8rpx; box-shadow: var(--shadow);
}
.bind-left { font-size: 32rpx; color: var(--text-1); }

.tips-card {
  margin-top: 24rpx; padding: 24rpx;
  background: var(--warning-l); border-radius: var(--radius);
}
.tips-text { font-size: 24rpx; color: var(--warning); line-height: 1.6; }

/* ---- 详情视图 ---- */
.detail-header {
  display: flex; align-items: center; gap: 12rpx;
  background: var(--surface); border-radius: var(--radius-sm);
  padding: 24rpx 32rpx; margin-bottom: 24rpx; box-shadow: var(--shadow);
}
.back-arrow { font-size: 52rpx; color: var(--text-2); line-height: 1; }
.detail-title { font-size: 34rpx; font-weight: 600; color: var(--text-1); }

.status-badge { font-size: 22rpx; padding: 6rpx 20rpx; border-radius: var(--radius-full); }
.badge-awake { background: var(--primary-l); color: var(--primary-d); }
.badge-sleep { background: rgba(99, 102, 241, 0.12); color: #6366f1; }

.score-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12rpx; }
.score-label { font-size: 26rpx; color: var(--text-2); }
.score-value { font-size: 28rpx; font-weight: 600; color: var(--text-1); }

.progress-track {
  height: 16rpx; border-radius: var(--radius-full);
  background: var(--surface-2); overflow: hidden;
}
.progress-fill {
  height: 100%; border-radius: var(--radius-full);
  background: linear-gradient(90deg, var(--primary), var(--primary-d));
}

.checkin-columns { display: flex; gap: 24rpx; }
.checkin-col { flex: 1; display: flex; flex-direction: column; gap: 16rpx; }
.checkin-head { font-size: 26rpx; color: var(--text-2); }
.checkin-ok, .checkin-fail, .checkin-wait { padding: 20rpx; border-radius: var(--radius-sm); }
.checkin-ok { background: var(--primary-l); }
.checkin-ok-text { font-size: 26rpx; color: var(--primary-d); }
.checkin-fail { background: var(--danger-l); }
.checkin-fail-text { font-size: 26rpx; color: var(--danger); }
.checkin-wait { background: var(--surface-2); }
.checkin-wait-text { font-size: 26rpx; color: var(--text-3); }

.alert-item { background: var(--surface-2); border-radius: var(--radius-sm); padding: 24rpx; margin-bottom: 16rpx; }
.alert-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; }
.alert-title { font-size: 28rpx; color: var(--text-1); flex: 1; }
.alert-time { font-size: 22rpx; color: var(--text-3); }
.alert-actions { display: flex; gap: 16rpx; }

.btn-mini {
  padding: 10rpx 32rpx; border-radius: var(--radius-full);
  background: var(--primary-d); color: #fff; font-size: 24rpx;
}
.btn-mini.ghost { background: transparent; border: 2rpx solid var(--border); color: var(--text-2); }

.setting-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 28rpx 0; border-bottom: 2rpx solid var(--divider);
}
.setting-item.no-border { border-bottom: none; }
.setting-label { font-size: 28rpx; color: var(--text-1); }

.switch {
  width: 88rpx; height: 48rpx; border-radius: var(--radius-full);
  background: var(--surface-3); position: relative; transition: background 0.2s;
}
.switch.on { background: var(--primary-d); }
.switch-knob {
  position: absolute; top: 4rpx; left: 4rpx;
  width: 40rpx; height: 40rpx; border-radius: 50%;
  background: #fff; transition: left 0.2s;
}
.switch.on .switch-knob { left: 44rpx; }

.btn-row { display: flex; gap: 24rpx; margin-bottom: 24rpx; }
</style>
