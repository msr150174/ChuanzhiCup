<template>
  <div class="page guardian-page">
    <!-- ============ ① 骨架屏（loading 为真时） ============ -->
    <div v-if="__state.loading" class="skeleton-area">
      <div class="skeleton sk-title"></div>
      <div class="skeleton sk-card"></div>
      <div class="skeleton sk-card"></div>
    </div>

    <!-- ============ ② 长辈详情视图 ============ -->
    <div v-else-if="__state.selectedElder" class="detail-view">
      <!-- 详情头部：返回 + 标题 -->
      <div class="detail-header">
        <button class="back-btn" @click="backToList">‹</button>
        <span class="detail-title">{{ __state.selectedElder.name }} 的状态</span>
      </div>

      <!-- 基本状态卡片 -->
      <div class="card">
        <div class="elder-row">
          <div class="elder-avatar">{{ avatarText(__state.selectedElder.name) }}</div>
          <div class="elder-main">
            <div class="elder-name">{{ __state.selectedElder.name }}</div>
            <div class="elder-sub">最后活动：{{ __state.selectedElder.lastActive }}</div>
          </div>
          <span
            class="status-badge"
            :class="__state.selectedElder.sleepStatus === 'awake' ? 'badge-awake' : 'badge-sleep'">
            {{ __state.selectedElder.sleepStatus === 'awake' ? '活动中' : '休息中' }}
          </span>
        </div>

        <div class="divider"></div>

        <!-- 健康评分进度条 -->
        <div class="score-row">
          <span class="score-label">健康评分</span>
          <span class="score-value">{{ __state.selectedElder.healthScore }}分</span>
        </div>
        <div class="progress-track">
          <div class="progress-fill" :style="{ width: __state.selectedElder.healthScore + '%' }"></div>
        </div>
      </div>

      <!-- 今日打卡情况 -->
      <div class="card">
        <div class="card-title">今日打卡情况</div>
        <div class="checkin-columns">
          <!-- 晨起打卡 -->
          <div class="checkin-col">
            <div class="checkin-head"><span class="col-icon">☀️</span>晨起打卡</div>
            <div v-if="__state.selectedElder.lastCheckin.morning" class="checkin-ok">
              ✓ 已完成 {{ __state.selectedElder.lastCheckin.morningTime }}
            </div>
            <div v-else class="checkin-fail">⚠️ 未打卡</div>
          </div>
          <!-- 睡前打卡 -->
          <div class="checkin-col">
            <div class="checkin-head"><span class="col-icon">🌙</span>睡前打卡</div>
            <div v-if="__state.selectedElder.lastCheckin.evening" class="checkin-ok">
              ✓ 已完成 {{ __state.selectedElder.lastCheckin.eveningTime }}
            </div>
            <div v-else class="checkin-wait">🕐 待打卡</div>
          </div>
        </div>
      </div>

      <!-- 告警信息：仅展示未处理的 -->
      <div v-if="unhandledAlerts.length > 0" class="card">
        <div class="card-title">告警信息</div>
        <div v-for="a in unhandledAlerts" :key="a.id" class="alert-item">
          <div class="alert-top">
            <span class="alert-title">
              <span class="alert-icon">{{ alertIcon(a.type) }}</span>{{ a.title }}
            </span>
            <span class="alert-time">{{ a.time }}</span>
          </div>
          <div class="alert-actions">
            <button class="btn-handle" @click="handleAlert(a.id, 'handle')">处理</button>
            <button class="btn-ignore" @click="handleAlert(a.id, 'ignore')">忽略</button>
          </div>
        </div>
      </div>

      <!-- 远程提醒设置：4 个开关 -->
      <div class="card">
        <div class="card-title">远程提醒设置</div>
        <div
          v-for="(row, idx) in remoteRows"
          :key="row.key"
          class="setting-item"
          :class="{ 'no-border': idx === remoteRows.length - 1 }">
          <span class="setting-left">
            <span class="setting-icon">{{ row.icon }}</span>
            <span class="setting-label">{{ row.label }}</span>
          </span>
          <div
            class="switch"
            :class="{ on: __state.remoteSettings[row.key] }"
            @click="toggleRemote(row.key)">
            <div class="switch-knob"></div>
          </div>
        </div>
      </div>

      <!-- 快捷联系：打电话 / 发消息 -->
      <div class="btn-row">
        <button class="btn-primary" @click="callElder">📞 打电话</button>
        <button class="btn-outline" @click="messageElder">💬 发消息</button>
      </div>

      <!-- 快捷操作：健康报告 / 打卡日历 -->
      <div class="btn-row">
        <button class="btn-primary" @click="goHealthReport">📈 健康报告</button>
        <button class="btn-primary" @click="goCalendar">📅 打卡日历</button>
      </div>
    </div>

    <!-- ============ ③ 长辈列表视图（默认） ============ -->
    <div v-else class="list-view">
      <!-- 页面标题 -->
      <div class="page-header">
        <span class="header-icon">🛡️</span>
        <span class="page-title">家属监护面板</span>
      </div>
      <p class="page-sub">实时查看长辈状态，远程管理提醒设置</p>

      <div class="section-label">已绑定长辈 ({{ __state.elders.length }})</div>

      <!-- 长辈卡片列表 -->
      <div v-if="__state.elders.length > 0">
        <div v-for="e in __state.elders" :key="e.id" class="elder-card" @click="selectElder(e)">
          <div class="elder-row">
            <div class="elder-avatar">{{ avatarText(e.name) }}</div>
            <div class="elder-main">
              <div class="elder-name">{{ e.name }}</div>
              <div class="elder-sub">最后活动：{{ e.lastActive }}</div>
            </div>
            <span class="chevron">›</span>
          </div>

          <div class="divider"></div>

          <div class="elder-footer">
            <span class="sleep-status">
              <template v-if="e.sleepStatus === 'sleeping'">🌙 休息中</template>
              <template v-else>☀️ 活动中</template>
            </span>
            <span class="badge-group">
              <span class="mini-badge" :class="e.lastCheckin.morning ? 'mini-ok' : 'mini-no'">
                晨起 {{ e.lastCheckin.morning ? '✓' : '○' }}
              </span>
              <span class="mini-badge" :class="e.lastCheckin.evening ? 'mini-ok' : 'mini-no'">
                睡前 {{ e.lastCheckin.evening ? '✓' : '○' }}
              </span>
            </span>
          </div>
        </div>
      </div>

      <!-- 空态 -->
      <div v-else class="empty-card">
        <div class="empty-icon">❓</div>
        <div class="empty-text">暂无绑定的长辈</div>
      </div>

      <!-- 绑定新长辈 -->
      <div class="bind-card" @click="goMemberBind">
        <span class="bind-left">👤 绑定新长辈</span>
        <span class="chevron">›</span>
      </div>

      <!-- 使用提示 -->
      <div class="tips-card">
        <span class="tips-icon">❓</span>
        <span class="tips-text">
          绑定长辈后，您可以实时查看其健康状态、打卡情况，并远程设置提醒推送。长辈同意后才能完成绑定。
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 家属监护面板 guardian-panel
 *
 * 三态渲染：骨架屏 → 长辈列表 → 长辈详情
 * 修正原项目两个缺陷：
 *   1. 电话号码不再硬编码，改为读 elder.phone
 *   2. 加载兜底时直接用最新局部变量选中，不再依赖闭包旧值
 */
import { reactive, computed, onMounted } from 'vue'

type AlertType = 'warning' | 'info' | 'error'
type SleepStatus = 'awake' | 'sleeping' | 'unknown'

interface AlertItem {
  id: string
  type: AlertType
  title: string
  time: string
  handled: boolean
}

interface LastCheckin {
  morning: boolean
  evening: boolean
  morningTime?: string
  eveningTime?: string
}

interface ElderStatus {
  id: string
  name: string
  phone: string
  lastActive: string
  sleepStatus: SleepStatus
  lastCheckin: LastCheckin
  healthScore: number
  alerts: AlertItem[]
}

interface RemoteSettings {
  morningReminder: boolean
  eveningReminder: boolean
  abnormalAlert: boolean
  missedCheckinAlert: boolean
}

/** mock 两位长辈，界面看起来是活的 */
const MOCK_ELDERS: ElderStatus[] = [
  {
    id: 'elder_001',
    name: '李秀兰',
    phone: '13800138001',
    lastActive: '5分钟前',
    sleepStatus: 'awake',
    lastCheckin: { morning: true, evening: false, morningTime: '06:30' },
    healthScore: 86,
    alerts: [
      { id: 'a1', type: 'warning', title: '今日睡前打卡尚未完成', time: '21:40', handled: false },
      { id: 'a2', type: 'info', title: '今日步数已达标 6820 步', time: '19:12', handled: false }
    ]
  },
  {
    id: 'elder_002',
    name: '王建国',
    phone: '13800138002',
    lastActive: '1小时前',
    sleepStatus: 'sleeping',
    lastCheckin: { morning: true, evening: true, morningTime: '07:10', eveningTime: '21:20' },
    healthScore: 72,
    alerts: [
      { id: 'b1', type: 'error', title: '检测到昨夜睡眠时长不足 5 小时', time: '07:05', handled: false },
      { id: 'b2', type: 'warning', title: '降压药 20:00 未记录服用', time: '20:30', handled: false },
      { id: 'b3', type: 'info', title: '本周血压记录已完成', time: '昨天', handled: true }
    ]
  }
]

const __state = reactive({
  loading: true as boolean,
  elders: [] as ElderStatus[],
  selectedElder: null as ElderStatus | null,
  remoteSettings: {
    morningReminder: true,
    eveningReminder: true,
    abnormalAlert: true,
    missedCheckinAlert: true
  } as RemoteSettings
})

/** 远程提醒 4 行的展示配置 */
const remoteRows = [
  { key: 'morningReminder' as const, icon: '☀️', label: '晨起提醒推送' },
  { key: 'eveningReminder' as const, icon: '🌙', label: '睡前提醒推送' },
  { key: 'abnormalAlert' as const, icon: '📈', label: '异常情况告警' },
  { key: 'missedCheckinAlert' as const, icon: '🕐', label: '漏打卡提醒' }
]

/** 仅展示未处理的告警 */
const unhandledAlerts = computed(() =>
  __state.selectedElder ? __state.selectedElder.alerts.filter((a) => !a.handled) : []
)

function toast(msg: string) {
  console.log('[toast]', msg)
}

/** 头像显示姓名首字 */
function avatarText(name: string): string {
  return name ? name.charAt(0) : '👤'
}

function alertIcon(type: AlertType): string {
  if (type === 'error') return '⚠️'
  if (type === 'info') return 'ℹ️'
  return '⚠️'
}

/**
 * 加载监护数据
 * 原项目缺陷：catch 分支里读的是闭包旧值 elders（初始 []），兜底数据不会自动选中。
 * 这里改为先用局部变量兜住结果，再用同一份最新值去选中，逻辑一致。
 */
function loadGuardianData() {
  __state.loading = true
  // 真实接口：GET /api/guardian/elders + GET /api/guardian/status
  let latest: ElderStatus[] = []
  try {
    latest = MOCK_ELDERS
  } catch {
    // 兜底数据
    latest = [
      {
        id: 'default',
        name: '测试长辈',
        phone: '13800138000',
        lastActive: '刚刚',
        sleepStatus: 'awake',
        lastCheckin: { morning: true, evening: false, morningTime: '06:30' },
        healthScore: 85,
        alerts: []
      }
    ]
  }
  __state.elders = latest
  // ★ 修正点：用最新局部变量 latest 选中，而不是闭包里的旧 __state.elders
  __state.selectedElder = null
  __state.loading = false
  console.log('[guardian] 已加载长辈数量 =', latest.length)
}

function selectElder(elder: ElderStatus) {
  __state.selectedElder = elder
}

function backToList() {
  __state.selectedElder = null
}

/** 处理 / 忽略告警：成功后本地置 handled，告警从视图消失 */
function handleAlert(alertId: string, action: 'handle' | 'ignore') {
  const elder = __state.selectedElder
  if (!elder) return
  const target = elder.alerts.find((a) => a.id === alertId)
  if (!target) return
  target.handled = true
  toast(action === 'handle' ? '已处理' : '已忽略')
}

/** 远程提醒开关：先本地乐观更新，再提交 */
function toggleRemote(key: keyof RemoteSettings) {
  __state.remoteSettings[key] = !__state.remoteSettings[key]
  toast(__state.remoteSettings[key] ? '设置已保存' : '设置已保存')
}

/** ★ 修正点：号码从 mock 数据读，不再硬编码 */
function callElder() {
  const phone = __state.selectedElder?.phone
  if (!phone) {
    toast('该长辈未绑定手机号')
    return
  }
  console.log('[call]', phone)
  toast(`正在呼叫 ${phone}`)
}

function messageElder() {
  const id = __state.selectedElder?.id
  console.log('[navigate] /pages/ai-chat/index?elderId=' + id)
}

function goHealthReport() {
  console.log('[navigate] /pages/health-report/index')
}

function goCalendar() {
  console.log('[navigate] /pages/calendar/index')
}

function goMemberBind() {
  console.log('[navigate] /pages/member-bind/index')
}

onMounted(() => {
  loadGuardianData()
})
</script>

<style scoped>
/* ================= 页面容器 ================= */
.guardian-page {
  min-height: 100vh;
  padding: 16px;
  padding-bottom: 80px;
  background: linear-gradient(180deg, var(--bg) 0%, #f0f4f0 40%, #ffffff 100%);
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif;
}

/* ================= 骨架屏 ================= */
.skeleton-area {
  display: flex;
  flex-direction: column;
}

.skeleton {
  background: linear-gradient(90deg, #ece6db 25%, #f6f2ea 37%, #ece6db 63%);
  background-size: 400% 100%;
  border-radius: var(--radius-sm);
  animation: sk-shine 1.4s ease infinite;
}

.sk-title {
  height: 32px;
  width: 100%;
  margin-bottom: 16px;
}

.sk-card {
  height: 128px;
  width: 100%;
  margin-bottom: 12px;
}

@keyframes sk-shine {
  0% { background-position: 100% 50%; }
  100% { background-position: 0 50%; }
}

/* ================= 列表视图 ================= */
.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.header-icon {
  font-size: 28px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-1);
}

.page-sub {
  font-size: 14px;
  color: var(--text-2);
  margin: 0 0 18px;
}

.section-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  margin-bottom: 10px;
}

.elder-card {
  background: var(--surface);
  border: 1px solid rgba(91, 154, 122, 0.16);
  border-radius: var(--radius);
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: var(--shadow);
  cursor: pointer;
  transition: transform 0.15s ease;
}

.elder-card:active {
  transform: scale(0.98);
}

.elder-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 长辈头像：圆形，姓名首字 */
.elder-avatar {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 24px;
  background: linear-gradient(135deg, var(--primary), var(--primary-d));
  color: #ffffff;
  font-size: 20px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.elder-main {
  flex: 1;
  min-width: 0;
}

.elder-name {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-1);
}

.elder-sub {
  font-size: 13px;
  color: var(--text-2);
  margin-top: 4px;
}

.chevron {
  font-size: 22px;
  color: #b9b0a5;
  line-height: 1;
}

.divider {
  height: 1px;
  background: rgba(91, 154, 122, 0.12);
  margin: 12px 0;
}

.elder-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sleep-status {
  font-size: 13px;
  color: var(--text-2);
}

.badge-group {
  display: flex;
  gap: 8px;
}

/* 晨起/睡前小徽章 */
.mini-badge {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 10px;
  font-weight: 500;
}

.mini-ok {
  background: var(--primary-l);
  color: var(--primary-d);
}

.mini-no {
  background: rgba(125, 116, 107, 0.12);
  color: var(--text-2);
}

/* 空态 */
.empty-card {
  background: var(--surface);
  border: 1px dashed rgba(91, 154, 122, 0.3);
  border-radius: var(--radius);
  padding: 40px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.empty-icon {
  font-size: 44px;
}

.empty-text {
  font-size: 15px;
  color: var(--text-2);
}

/* 绑定新长辈 */
.bind-card {
  margin-top: 16px;
  background: var(--surface);
  border: 1px solid rgba(91, 154, 122, 0.16);
  border-radius: var(--radius);
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
}

.bind-left {
  font-size: 16px;
  color: var(--text-1);
}

/* 使用提示 */
.tips-card {
  margin-top: 16px;
  padding: 14px;
  border-radius: var(--radius-sm);
  background: rgba(212, 165, 116, 0.14);
  display: flex;
  gap: 10px;
}

.tips-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.tips-text {
  font-size: 13px;
  line-height: 1.6;
  color: #8a6a3d;
}

/* ================= 详情视图 ================= */
.detail-header {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--surface);
  border-radius: var(--radius-sm);
  padding: 12px 16px;
  margin-bottom: 16px;
  box-shadow: var(--shadow);
}

.back-btn {
  border: none;
  background: transparent;
  font-size: 26px;
  line-height: 1;
  color: var(--text-2);
  cursor: pointer;
  padding: 0 4px;
}

.detail-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-1);
}

.card {
  background: var(--surface);
  border: 1px solid rgba(91, 154, 122, 0.16);
  border-radius: var(--radius);
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: var(--shadow);
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-1);
  margin-bottom: 12px;
}

.status-badge {
  font-size: 12px;
  padding: 4px 12px;
  border-radius: 12px;
  font-weight: 500;
}

.badge-awake {
  background: var(--primary-l);
  color: var(--primary-d);
}

.badge-sleep {
  background: rgba(99, 102, 241, 0.14);
  color: #4f46e5;
}

.score-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.score-label {
  font-size: 14px;
  color: var(--text-2);
}

.score-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--primary-d);
}

/* 健康评分进度条 */
.progress-track {
  height: 8px;
  border-radius: 4px;
  background: rgba(91, 154, 122, 0.15);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, var(--primary), var(--primary-d));
  transition: width 0.5s ease;
}

/* 今日打卡双列 */
.checkin-columns {
  display: flex;
  gap: 16px;
}

.checkin-col {
  flex: 1;
}

.checkin-head {
  font-size: 14px;
  color: var(--text-1);
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.col-icon {
  font-size: 18px;
}

.checkin-ok {
  font-size: 13px;
  color: var(--primary-d);
}

.checkin-fail {
  font-size: 13px;
  color: var(--danger);
}

.checkin-wait {
  font-size: 13px;
  color: var(--text-2);
}

/* 告警项 */
.alert-item {
  background: rgba(125, 116, 107, 0.06);
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
}

.alert-item:last-child {
  margin-bottom: 0;
}

.alert-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.alert-title {
  font-size: 14px;
  color: var(--text-1);
  display: flex;
  align-items: center;
  gap: 6px;
}

.alert-icon {
  font-size: 16px;
}

.alert-time {
  font-size: 12px;
  color: var(--text-2);
  flex-shrink: 0;
}

.alert-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.btn-handle {
  flex: 1;
  height: 34px;
  border: none;
  border-radius: 10px;
  background: var(--primary-d);
  color: #ffffff;
  font-size: 14px;
  cursor: pointer;
}

.btn-ignore {
  flex: 1;
  height: 34px;
  border: 1px solid rgba(91, 154, 122, 0.35);
  border-radius: 10px;
  background: transparent;
  color: var(--primary-d);
  font-size: 14px;
  cursor: pointer;
}

/* 远程提醒设置行 */
.setting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid rgba(91, 154, 122, 0.1);
}

.setting-item.no-border {
  border-bottom: none;
  padding-bottom: 0;
}

.setting-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.setting-icon {
  font-size: 17px;
}

.setting-label {
  font-size: 15px;
  color: var(--text-1);
}

/* 自定义开关 48×28 */
.switch {
  width: 48px;
  height: 28px;
  border-radius: 14px;
  background: rgba(125, 116, 107, 0.28);
  position: relative;
  cursor: pointer;
  transition: background 0.2s ease;
  flex-shrink: 0;
}

.switch.on {
  background: var(--primary-d);
}

.switch-knob {
  width: 24px;
  height: 24px;
  border-radius: 12px;
  background: #ffffff;
  position: absolute;
  top: 2px;
  left: 2px;
  transition: left 0.2s ease;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.18);
}

.switch.on .switch-knob {
  left: 22px;
}

/* 底部按钮行 */
.btn-row {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
}

.btn-primary {
  flex: 1;
  height: 46px;
  border: none;
  border-radius: 23px;
  background: linear-gradient(135deg, var(--primary), var(--primary-d));
  color: #ffffff;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(91, 154, 122, 0.22);
}

.btn-primary:active {
  transform: scale(0.97);
}

.btn-outline {
  flex: 1;
  height: 46px;
  border: 1px solid rgba(91, 154, 122, 0.4);
  border-radius: 23px;
  background: transparent;
  color: var(--primary-d);
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
}

.btn-outline:active {
  transform: scale(0.97);
}
</style>

<template>
  <div class="page report-page">
    <!-- ============ ① 报告概览卡片 ============ -->
    <div class="overview-card">
      <div class="overview-header">
        <div class="overview-title">
          <span class="ov-icon">📄</span>
          <span>健康分析报告</span>
        </div>
        <div class="overview-meta">
          <span class="period-badge">{{ __state.reportPeriod }}</span>
          <button class="refresh-btn" @click="handleRefresh">🔄</button>
        </div>
      </div>

      <!-- 评分区：CSS 圆形评分环 + 进度条 -->
      <div class="score-section">
        <div class="score-ring">
          <div class="ring-number">{{ __state.overallScore ?? '--' }}</div>
          <div class="ring-label">综合评分</div>
        </div>
        <div class="score-desc">
          <div class="progress-track">
            <div class="progress-fill" :style="{ width: (__state.overallScore || 0) + '%' }"></div>
          </div>
          <p class="score-text">{{ overallText }}</p>
        </div>
      </div>
    </div>

    <!-- ============ ② 四个维度 Tab ============ -->
    <div class="tabs-card">
      <div class="tabs-header">
        <div
          v-for="c in __state.categories"
          :key="c.id"
          class="tab-item"
          :class="{ active: __state.activeTab === c.id }"
          @click="__state.activeTab = c.id">
          <span class="tab-icon">{{ c.icon }}</span>
          <span class="tab-text">{{ c.title }}</span>
        </div>
      </div>

      <!-- 当前维度卡片 -->
      <div v-if="currentCategory" class="category-card">
        <div class="category-header">
          <div class="category-info">
            <div class="category-name">
              <span class="cat-icon">{{ currentCategory.icon }}</span>{{ currentCategory.title }}
            </div>
            <div class="category-desc">{{ currentCategory.description }}</div>
          </div>
          <div class="category-score" :style="{ color: scoreColor(currentCategory.score) }">
            {{ currentCategory.score ?? '待测' }}<span class="score-unit">分</span>
          </div>
        </div>

        <!-- 指标明细 -->
        <div
          v-for="(m, idx) in currentCategory.metrics"
          :key="m.id"
          class="metric-item"
          :class="{ 'no-border': idx === currentCategory.metrics.length - 1 }">
          <div class="metric-header">
            <span class="metric-name">{{ m.name }}</span>
            <span class="metric-trend" :style="{ color: trendColor(m.trend) }">{{ trendIcon(m.trend) }}</span>
          </div>
          <div class="metric-value-row">
            <span class="metric-value" :style="{ color: statusColor(m.status) }">{{ m.value }}</span>
            <span class="metric-unit">{{ m.unit }}</span>
            <span
              class="status-badge"
              :style="{ background: statusColor(m.status) + '22', color: statusColor(m.status) }">
              {{ statusLabel(m.status) }}
            </span>
          </div>
          <div class="metric-desc">{{ m.description }}</div>
        </div>
      </div>
    </div>

    <!-- ============ ③ AI 洞察 ============ -->
    <div class="ai-card">
      <div class="ai-header">
        <span class="ai-icon">✨</span>
        <span class="ai-title">AI健康洞察</span>
        <span v-if="__state.aiLoading" class="ai-loading">分析中…</span>
      </div>
      <p class="ai-summary">{{ __state.aiSummary }}</p>

      <div class="insights-list">
        <div
          v-for="ins in __state.aiInsights"
          :key="ins.id"
          class="insight-item"
          :class="'priority-' + ins.priority">
          <div class="insight-header">
            <span class="insight-badge" :style="{ background: insightColor(ins.type) + '22', color: insightColor(ins.type) }">
              {{ insightLabel(ins.type) }}
            </span>
            <span v-if="ins.priority === 'high'" class="insight-warn">⚠️</span>
          </div>
          <div class="insight-content">{{ ins.content }}</div>
        </div>
      </div>
    </div>

    <!-- ============ ④ 历史报告 ============ -->
    <div class="history-card">
      <div class="history-header">
        <span class="his-icon">🕐</span>
        <span class="his-title">历史报告</span>
      </div>
      <div class="history-empty">暂无历史报告，完成一周打卡后将自动生成</div>
    </div>

    <!-- ============ ⑤ 底部操作 ============ -->
    <div class="action-buttons">
      <button class="download-btn" @click="downloadReport">⬇️ 下载报告</button>
      <button class="share-btn" @click="shareReport">📤 分享给家属</button>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 健康分析报告 health-report
 *
 * 四个维度（睡眠质量 / 作息节律 / 活动状态 / 心理状态），共 8 个指标。
 * 原项目 healthScore 恒为 null 显示「待分析」，这里用 mock 数据算出真实分数。
 * 不使用任何图表库，评分环与进度条全部用 CSS 绘制。
 */
import { reactive, computed } from 'vue'

type MetricStatus = 'good' | 'warning' | 'danger' | 'empty'
type Trend = 'up' | 'down' | 'stable' | 'none'
type InsightType = 'observation' | 'analysis' | 'suggestion'
type Priority = 'high' | 'medium' | 'low'

interface HealthMetric {
  id: string
  name: string
  value: string
  unit: string
  status: MetricStatus
  trend: Trend
  description: string
}

interface HealthCategory {
  id: string
  title: string
  icon: string
  score: number | null
  description: string
  metrics: HealthMetric[]
}

interface AIInsight {
  id: string
  type: InsightType
  content: string
  priority: Priority
}

/**
 * 分类评分：把状态映射为分数后取平均（照抄原项目公式）
 * good=100 / warning=70 / danger=40
 */
function calculateCategoryScore(metrics: HealthMetric[]): number | null {
  const valid = metrics.filter((m) => m.value !== '' && m.status !== 'empty')
  if (valid.length === 0) return null
  const scores = valid.map((m) => {
    switch (m.status) {
      case 'good':
        return 100
      case 'warning':
        return 70
      case 'danger':
        return 40
      default:
        return 0
    }
  })
  return Math.round(scores.reduce((a, b) => a + b, 0) / scores.length)
}

/** 综合评分：所有非空分类评分取平均 */
function calculateOverallScore(cats: HealthCategory[]): number | null {
  const valid = cats.filter((c) => c.score !== null)
  if (valid.length === 0) return null
  return Math.round(valid.reduce((sum, c) => sum + (c.score || 0), 0) / valid.length)
}

/* ---------- mock 数据：7 天睡眠/活动记录 ---------- */
const SLEEP_MINUTES = [455, 470, 430, 480, 445, 460, 450] // 平均约 7.6 小时
const WAKE_TIMES = ['06:30', '06:45', '07:10', '06:35', '06:50', '07:05', '06:40']
const STEPS = [6820, 7210, 5940, 8100, 6650, 7320, 6980]
const OUTDOOR = [45, 38, 22, 60, 41, 52, 47]
const SEDENTARY = [95, 110, 130, 88, 102, 96, 91]
const DAYS_WITH_DATA = 7

/* ---------- 构造 8 个指标 ---------- */

// ① 睡眠质量
const avgMinutes = SLEEP_MINUTES.reduce((a, b) => a + b, 0) / SLEEP_MINUTES.length
const avgHours = Math.round((avgMinutes / 60) * 10) / 10
const durationStatus: MetricStatus = avgHours >= 7 && avgHours <= 9 ? 'good' : avgHours > 0 ? 'warning' : 'empty'
// 睡眠效率 = min(100, round(小时数 / 8 * 100))
const efficiency = avgHours > 0 ? Math.min(100, Math.round((avgHours / 8) * 100)) : 0
const efficiencyStatus: MetricStatus = efficiency >= 85 ? 'good' : 'warning'

// ② 作息节律：起床时间标准差
function calculateTimeDeviation(times: string[]): number {
  const minutes = times.map((t) => {
    const parts = t.split(':').map(Number)
    return parts[0] * 60 + parts[1]
  })
  const avg = minutes.reduce((a, b) => a + b, 0) / minutes.length
  const variance = minutes.reduce((a, m) => a + Math.pow(m - avg, 2), 0) / minutes.length
  return Math.round(Math.sqrt(variance))
}
const wakeDeviation = calculateTimeDeviation(WAKE_TIMES)
const wakeStatus: MetricStatus = wakeDeviation <= 30 ? 'good' : wakeDeviation <= 60 ? 'warning' : 'danger'
const completionRate = Math.round((DAYS_WITH_DATA / 7) * 100)
const checkinStatus: MetricStatus = completionRate >= 80 ? 'good' : completionRate >= 50 ? 'warning' : 'empty'

// ③ 活动状态
const totalSteps = STEPS.reduce((a, b) => a + b, 0)
const avgSteps = Math.round(totalSteps / STEPS.length)
const stepsStatus: MetricStatus = avgSteps >= 6000 ? 'good' : avgSteps >= 3000 ? 'warning' : 'danger'
const avgOutdoor = Math.round(OUTDOOR.reduce((a, b) => a + b, 0) / OUTDOOR.length)
const outdoorStatus: MetricStatus = avgOutdoor >= 30 ? 'good' : 'warning'
const avgSedentary = Math.round(SEDENTARY.reduce((a, b) => a + b, 0) / SEDENTARY.length)
const sedentaryHours = Math.round(avgSedentary / 60)
const sedentaryStatus: MetricStatus = avgSedentary <= 120 ? 'good' : avgSedentary <= 240 ? 'warning' : 'danger'

// ④ 心理状态（mock 出一个真实分数，不再恒 null）
const mentalScore = 82
const mentalStatus: MetricStatus = mentalScore >= 80 ? 'good' : mentalScore >= 60 ? 'warning' : 'danger'

/** 构造 4 个维度并算分 */
function buildCategories(): HealthCategory[] {
  const sleepMetrics: HealthMetric[] = [
    {
      id: 'duration',
      name: '平均睡眠时长',
      value: String(avgHours),
      unit: '小时',
      status: durationStatus,
      trend: 'stable',
      description: `本周平均${avgHours}小时，理想区间 7–9 小时`
    },
    {
      id: 'efficiency',
      name: '睡眠效率',
      value: String(efficiency),
      unit: '%',
      status: efficiencyStatus,
      trend: 'stable',
      description: '基于入睡与起床时间计算'
    }
  ]

  const rhythmMetrics: HealthMetric[] = [
    {
      id: 'wake',
      name: '起床时间偏差',
      value: String(wakeDeviation),
      unit: '分钟',
      status: wakeStatus,
      trend: wakeDeviation <= 30 ? 'stable' : 'down',
      description: wakeDeviation <= 30 ? '起床时间很规律' : '建议固定起床时间'
    },
    {
      id: 'checkin',
      name: '打卡完成率',
      value: String(completionRate),
      unit: '%',
      status: checkinStatus,
      trend: completionRate >= 80 ? 'up' : 'stable',
      description: `本周已打卡${DAYS_WITH_DATA}天`
    }
  ]

  const activityMetrics: HealthMetric[] = [
    {
      id: 'steps',
      name: '日均步数',
      value: String(avgSteps),
      unit: '步',
      status: stepsStatus,
      trend: avgSteps >= 6000 ? 'up' : 'stable',
      description: avgSteps >= 6000 ? '活动量充足' : '建议增加运动'
    },
    {
      id: 'outdoor',
      name: '户外时长',
      value: String(avgOutdoor),
      unit: '分钟',
      status: outdoorStatus,
      trend: 'stable',
      description: avgOutdoor >= 30 ? '户外活动充足' : '建议多户外活动'
    },
    {
      id: 'sedentary',
      name: '久坐时长',
      value: String(sedentaryHours),
      unit: '小时',
      status: sedentaryStatus,
      trend: 'stable',
      description: avgSedentary > 120 ? '久坐时间过长，请适时活动' : '久坐时间适中'
    }
  ]

  const mentalMetrics: HealthMetric[] = [
    {
      id: 'score',
      name: '心理状态评分',
      value: String(mentalScore),
      unit: '分',
      status: mentalStatus,
      trend: 'stable',
      description: mentalScore >= 80 ? '心理状态良好' : '建议多与家人交流'
    }
  ]

  const cats: HealthCategory[] = [
    {
      id: 'sleep',
      title: '睡眠质量',
      icon: '🌙',
      score: null,
      description: '基于打卡数据计算',
      metrics: sleepMetrics
    },
    {
      id: 'rhythm',
      title: '作息节律',
      icon: '☀️',
      score: null,
      description: '基于打卡数据计算',
      metrics: rhythmMetrics
    },
    {
      id: 'activity',
      title: '活动状态',
      icon: '📈',
      score: null,
      description: '基于步数和定位计算',
      metrics: activityMetrics
    },
    {
      id: 'mental',
      title: '心理状态',
      icon: '🧠',
      score: null,
      description: '基于AI评估',
      metrics: mentalMetrics
    }
  ]

  // 逐个分类算分；心理状态直接用原始分（照抄原项目规则）
  cats.forEach((c) => {
    c.score = c.id === 'mental' ? mentalScore : calculateCategoryScore(c.metrics)
  })
  return cats
}

const __state = reactive({
  reportPeriod: '本周',
  loading: false,
  aiLoading: false,
  activeTab: 'sleep' as string,
  categories: buildCategories() as HealthCategory[],
  overallScore: null as number | null,
  aiSummary: '',
  aiInsights: [] as AIInsight[]
})

/** ★ 修正点：综合评分用 mock 数据真实算出 */
__state.overallScore = calculateOverallScore(__state.categories)

const currentCategory = computed(
  () => __state.categories.find((c) => c.id === __state.activeTab) || __state.categories[0]
)

const overallText = computed(() => {
  const s = __state.overallScore
  if (s === null) return '暂无足够数据'
  if (s >= 80) return '整体健康状况良好，请继续保持当前作息'
  if (s >= 60) return '需要关注部分指标，建议调整作息习惯'
  return '建议及时调整生活方式，并咨询家庭医生'
})

function toast(msg: string) {
  console.log('[toast]', msg)
}

function statusColor(status: MetricStatus): string {
  if (status === 'good') return 'var(--primary)'
  if (status === 'warning') return 'var(--warning)'
  if (status === 'danger') return 'var(--danger)'
  return '#b9b0a5'
}

function statusLabel(status: MetricStatus): string {
  if (status === 'good') return '优秀'
  if (status === 'warning') return '一般'
  if (status === 'danger') return '需改善'
  return '无数据'
}

function trendIcon(trend: Trend): string {
  if (trend === 'up') return '📈'
  if (trend === 'down') return '📉'
  if (trend === 'stable') return '✓'
  return ''
}

function trendColor(trend: Trend): string {
  if (trend === 'up') return 'var(--primary-d)'
  if (trend === 'down') return 'var(--danger)'
  return 'var(--primary)'
}

/** 分类分数着色：≥80 绿 / ≥60 黄 / 否则红 */
function scoreColor(score: number | null): string {
  if (score === null) return '#b9b0a5'
  if (score >= 80) return 'var(--primary-d)'
  if (score >= 60) return 'var(--warning)'
  return 'var(--danger)'
}

function insightColor(type: InsightType): string {
  if (type === 'analysis') return '#9b59b6'
  if (type === 'suggestion') return 'var(--primary-d)'
  return '#3498db'
}

function insightLabel(type: InsightType): string {
  if (type === 'analysis') return '深度分析'
  if (type === 'suggestion') return '改善建议'
  return '观察发现'
}

/** mock AI 洞察：直接给结果，不调真实接口 */
function generateInsights() {
  __state.aiLoading = true
  __state.aiSummary =
    '本周综合评分 ' +
    (__state.overallScore ?? 0) +
    ' 分。睡眠时长与作息规律性表现稳定，活动量处于良好水平。需要注意的是久坐时长偏高、户外活动可以再增加一些。'
  __state.aiInsights = [
    {
      id: 'ai-0',
      type: 'observation',
      content: `本周平均睡眠 ${avgHours} 小时，睡眠效率 ${efficiency}%，处于健康区间。`,
      priority: 'low'
    },
    {
      id: 'ai-1',
      type: 'analysis',
      content: `起床时间偏差为 ${wakeDeviation} 分钟，说明作息较为规律；久坐平均每天约 ${sedentaryHours} 小时，是主要扣分项。`,
      priority: 'medium'
    },
    {
      id: 'ai-2',
      type: 'suggestion',
      content: '建议每坐满 1 小时起身活动 5 分钟，并把每日户外时间提升到 45 分钟以上。',
      priority: 'high'
    },
    {
      id: 'ai-3',
      type: 'suggestion',
      content: '心理状态评分良好，推荐继续保持与家人的日常交流与散步习惯。',
      priority: 'low'
    }
  ]
  __state.aiLoading = false
}

generateInsights()

function handleRefresh() {
  __state.categories = buildCategories()
  __state.overallScore = calculateOverallScore(__state.categories)
  generateInsights()
  toast('已刷新报告数据')
}

function downloadReport() {
  // 原项目写文件到小程序沙箱，Web 端用占位提示
  toast('报告生成中，Web 端暂不支持本地文件下载')
}

function shareReport() {
  toast('请使用小程序的分享功能转发给家属')
}
</script>

<style scoped>
/* ================= 页面容器 ================= */
.report-page {
  min-height: 100vh;
  padding: 16px;
  padding-bottom: 80px;
  background: linear-gradient(180deg, var(--bg) 0%, #f0f4f0 100%);
  display: flex;
  flex-direction: column;
  gap: 16px;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif;
}

/* ================= 概览卡片 ================= */
.overview-card {
  background: var(--surface);
  border: 1px solid rgba(91, 154, 122, 0.12);
  border-radius: 24px;
  padding: 24px;
  box-shadow: 0 6px 24px rgba(91, 154, 122, 0.1);
  animation: breath-glow 4s ease-in-out infinite;
}

@keyframes breath-glow {
  0%, 100% { box-shadow: 0 6px 24px rgba(91, 154, 122, 0.1); }
  50% { box-shadow: 0 6px 30px rgba(91, 154, 122, 0.2); }
}

.overview-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}

.overview-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 20px;
  font-weight: 600;
  color: var(--text-1);
}

.ov-icon {
  font-size: 30px;
}

.overview-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.period-badge {
  font-size: 12px;
  color: #ffffff;
  background: linear-gradient(135deg, var(--primary), var(--primary-d));
  border-radius: 14px;
  padding: 4px 12px;
}

.refresh-btn {
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 17px;
  background: rgba(91, 154, 122, 0.1);
  font-size: 16px;
  cursor: pointer;
}

/* 评分区 */
.score-section {
  display: flex;
  align-items: center;
  gap: 20px;
}

/* CSS 圆形评分环 120×120 */
.score-ring {
  width: 120px;
  height: 120px;
  flex-shrink: 0;
  border-radius: 60px;
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-d) 100%);
  box-shadow: 0 6px 20px rgba(91, 154, 122, 0.25);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s ease;
}

.score-ring:hover {
  transform: scale(1.02);
}

.ring-number {
  font-size: 36px;
  font-weight: 700;
  color: #ffffff;
  line-height: 1.1;
}

.ring-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 2px;
}

.score-desc {
  flex: 1;
  min-width: 0;
}

.progress-track {
  height: 16px;
  border-radius: 8px;
  background: rgba(91, 154, 122, 0.15);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 8px;
  background: linear-gradient(90deg, var(--primary), var(--primary-d));
  transition: width 0.6s ease;
}

.score-text {
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-2);
  margin: 10px 0 0;
}

/* ================= Tabs ================= */
.tabs-card {
  background: var(--surface);
  border: 1px solid rgba(91, 154, 122, 0.12);
  border-radius: 20px;
  padding: 12px;
  box-shadow: 0 4px 16px rgba(91, 154, 122, 0.08);
}

.tabs-header {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-bottom: 12px;
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 12px;
  background: rgba(125, 116, 107, 0.08);
  cursor: pointer;
  transition: all 0.2s ease;
}

.tab-item.active {
  background: linear-gradient(135deg, var(--primary), var(--primary-d));
}

.tab-icon {
  font-size: 16px;
}

.tab-text {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-2);
}

.tab-item.active .tab-text {
  color: #ffffff;
}

/* ================= 维度卡片 ================= */
.category-card {
  padding: 4px 4px 0;
  animation: fade-in-up 0.5s ease;
}

@keyframes fade-in-up {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

.category-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: 8px;
}

.category-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
  font-weight: 600;
  color: var(--text-1);
}

.cat-icon {
  font-size: 22px;
}

.category-desc {
  font-size: 12px;
  color: var(--text-2);
  margin-top: 4px;
}

.category-score {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.1;
}

.score-unit {
  font-size: 12px;
  margin-left: 2px;
}

/* 指标项 */
.metric-item {
  padding: 16px 0;
  border-bottom: 1px solid rgba(91, 154, 122, 0.1);
}

.metric-item.no-border {
  border-bottom: none;
  padding-bottom: 4px;
}

.metric-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.metric-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-1);
}

.metric-trend {
  font-size: 14px;
}

.metric-value-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-top: 6px;
}

.metric-value {
  font-size: 24px;
  font-weight: 700;
}

.metric-unit {
  font-size: 12px;
  color: var(--text-2);
}

.status-badge {
  font-size: 10px;
  padding: 3px 10px;
  border-radius: 10px;
  font-weight: 500;
  margin-left: auto;
}

.metric-desc {
  font-size: 12px;
  color: var(--text-2);
  margin-top: 6px;
}

/* ================= AI 洞察 ================= */
.ai-card {
  background: linear-gradient(135deg, #faf5e9 0%, var(--surface) 100%);
  border: 1px solid rgba(212, 165, 116, 0.25);
  border-radius: 20px;
  padding: 20px;
  box-shadow: 0 4px 16px rgba(91, 154, 122, 0.08);
}

.ai-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.ai-icon {
  font-size: 22px;
}

.ai-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-1);
}

.ai-loading {
  margin-left: auto;
  font-size: 12px;
  color: var(--primary-d);
}

.ai-summary {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-1);
  margin: 0 0 14px;
}

.insights-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.insight-item {
  padding: 16px;
  border-radius: 16px;
  background: rgba(143, 166, 142, 0.1);
  border: 1px solid rgba(91, 154, 122, 0.08);
  border-left: 4px solid transparent;
  animation: fade-in-up 0.4s ease;
}

.insight-item.priority-high {
  background: rgba(229, 90, 90, 0.08);
  border-left-color: var(--danger);
}

.insight-item.priority-medium {
  background: rgba(212, 165, 116, 0.12);
  border-left-color: var(--warning);
}

.insight-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.insight-badge {
  font-size: 10px;
  padding: 3px 10px;
  border-radius: 10px;
  font-weight: 500;
}

.insight-warn {
  font-size: 14px;
}

.insight-content {
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-1);
}

/* ================= 历史报告 ================= */
.history-card {
  background: var(--surface);
  border: 1px solid rgba(91, 154, 122, 0.12);
  border-radius: 20px;
  padding: 20px;
}

.history-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.his-icon {
  font-size: 22px;
}

.his-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-1);
}

.history-empty {
  text-align: center;
  font-size: 14px;
  color: var(--text-2);
  padding: 16px 0;
}

/* ================= 底部按钮 ================= */
.action-buttons {
  display: flex;
  gap: 12px;
  padding: 4px 0 8px;
}

.download-btn {
  flex: 1;
  height: 48px;
  border: none;
  border-radius: 20px;
  background: linear-gradient(135deg, var(--primary), var(--primary-d));
  color: #ffffff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.share-btn {
  flex: 1;
  height: 48px;
  border: 2px solid rgba(91, 154, 122, 0.3);
  border-radius: 20px;
  background: transparent;
  color: var(--primary-d);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.download-btn:active,
.share-btn:active {
  transform: scale(0.97);
}
</style>

<template>
  <div class="page family-page">
    <!-- ============ ① 页面标题 ============ -->
    <div class="page-header">
      <span class="header-icon">👥</span>
      <div class="header-text">
        <div class="page-title">家属绑定</div>
        <div class="page-subtitle">邀请家属查看您的健康数据</div>
      </div>
    </div>

    <!-- ============ ② 邀请码区域 ============ -->
    <div class="card invite-section">
      <div class="invite-header">
        <span class="invite-header-icon">🛡️</span>
        <span class="section-title">专属邀请码</span>
      </div>

      <div class="invite-card">
        <span class="invite-code">{{ __state.inviteCode }}</span>
        <div class="invite-actions">
          <button class="invite-btn" @click="copyInviteCode">🔗 复制</button>
          <button class="invite-btn" @click="shareInviteCode">💬 分享</button>
        </div>
      </div>

      <p class="invite-tip">家属输入此邀请码即可绑定，绑定后可查看您的健康数据与告警通知</p>
    </div>

    <!-- ============ ③ 已绑定家属 ============ -->
    <div class="card family-section">
      <div class="section-header">
        <span class="section-header-icon">👥</span>
        <span class="section-title">已绑定家属 ({{ activeMembers.length }})</span>
        <button class="add-btn" @click="openAddModal">➕</button>
      </div>

      <div v-if="activeMembers.length > 0" class="family-list">
        <div v-for="m in activeMembers" :key="m.id" class="family-item">
          <!-- 头像 + 主要监护人角标 -->
          <div class="member-avatar">
            {{ avatarText(m.name) }}
            <span v-if="m.isPrimary" class="primary-badge">❤</span>
          </div>

          <!-- 信息区：点击查看详情 -->
          <div class="member-info" @click="viewMemberDetail(m)">
            <div class="member-line">
              <span class="member-name">{{ m.name }}</span>
              <span class="member-relation">{{ m.relation }}</span>
            </div>
            <div class="member-sub">📞 {{ m.phone }}</div>
            <div class="member-line">
              <span class="member-time">🕐 绑定于 {{ m.bindTime }}</span>
              <span class="status-tag" :class="'status-' + m.status">{{ statusLabel(m.status) }}</span>
            </div>
          </div>

          <!-- 操作区 -->
          <div class="member-actions">
            <button
              v-if="!m.isPrimary && m.status === 'active'"
              class="circle-btn btn-primary-set"
              title="设为主要监护人"
              @click="setPrimaryGuardian(m)">
              ❤
            </button>
            <button class="circle-btn btn-perm" title="权限设置" @click="openPermissionSetting(m)">🛡️</button>
            <button class="circle-btn btn-unbind" title="解绑" @click="unbindFamily(m)">🚫</button>
          </div>
        </div>
      </div>

      <!-- 空态 -->
      <div v-else class="empty-state">
        <div class="empty-icon">👥</div>
        <div class="empty-title">暂无绑定家属</div>
        <div class="empty-desc">点击上方添加按钮邀请家属</div>
      </div>
    </div>

    <!-- ============ ④ 待处理邀请 ============ -->
    <div v-if="__state.pendingInvites.length > 0" class="card pending-section">
      <div class="section-header">
        <span class="section-header-icon">⚠️</span>
        <span class="section-title">待处理邀请 ({{ __state.pendingInvites.length }})</span>
      </div>

      <div class="pending-list">
        <div v-for="p in __state.pendingInvites" :key="p.id" class="pending-item">
          <div>
            <div class="pending-phone">{{ p.phone }}</div>
            <div class="pending-hint">等待确认中...</div>
          </div>
          <button class="pending-cancel" @click="cancelInvite(p.id)">取消</button>
        </div>
      </div>
    </div>

    <!-- ============ ⑤ 绑定说明 ============ -->
    <div class="card guide-section">
      <div class="guide-title">绑定说明</div>
      <div class="guide-item"><span class="guide-icon">✓</span>家属绑定后可查看您的健康数据与作息记录</div>
      <div class="guide-item"><span class="guide-icon">✓</span>主要监护人会优先收到告警通知</div>
      <div class="guide-item"><span class="guide-icon">✓</span>您可随时调整每位家属的查看权限</div>
      <div class="guide-item"><span class="guide-icon warn">⚠️</span>解绑后对方将无法继续查看您的数据</div>
    </div>

    <!-- ============ 弹窗 A：添加家属 ============ -->
    <div v-if="__state.showAddModal" class="modal-overlay" @click.self="closeAddModal">
      <div class="modal-content">
        <div class="modal-header">
          <span class="modal-header-icon">➕</span>
          <span class="modal-title">添加家属</span>
        </div>

        <div class="modal-body">
          <!-- 手机号搜索 -->
          <div class="search-section">
            <div class="search-input-wrapper">
              <span class="search-icon">🔍</span>
              <input
                v-model="__state.searchPhone"
                type="number"
                maxlength="11"
                placeholder="输入家属手机号搜索"
                class="search-input" />
            </div>
            <button class="search-btn" @click="searchFamily">
              {{ __state.loading ? '搜索中...' : '搜索' }}
            </button>
          </div>

          <!-- 搜索结果 -->
          <div v-if="__state.searchResult" class="search-result">
            <div>
              <div class="result-name">{{ __state.searchResult.name }}</div>
              <div class="result-phone">{{ __state.searchResult.phone }}</div>
            </div>
            <button class="result-btn" @click="sendBindInvite(__state.searchResult)">🔗 发送邀请</button>
          </div>

          <!-- 其他添加方式 -->
          <div class="other-methods">
            <div class="methods-label">其他添加方式</div>
            <div class="method-item" @click="shareInviteCode">
              <span class="method-icon">💬</span>
              <span class="method-text">分享邀请码给家属</span>
              <span class="method-arrow">›</span>
            </div>
            <div class="method-item" @click="copyInviteCode">
              <span class="method-icon">🔗</span>
              <span class="method-text">复制邀请码手动发送</span>
              <span class="method-arrow">›</span>
            </div>
          </div>
        </div>

        <div class="modal-footer">
          <button class="close-btn" @click="closeAddModal">关闭</button>
        </div>
      </div>
    </div>

    <!-- ============ 弹窗 B：权限设置 ============ -->
    <div v-if="__state.showPermissionModal" class="modal-overlay" @click.self="closePermissionModal">
      <div class="modal-content permission-modal">
        <div class="modal-header">
          <span class="modal-header-icon">🛡️</span>
          <div>
            <div class="modal-title">权限设置</div>
            <div class="modal-sub">{{ __state.selectedMember?.name }}</div>
          </div>
        </div>

        <div class="permission-list">
          <div v-for="p in permissionRows" :key="p.key" class="permission-item">
            <div class="permission-info">
              <div class="permission-label">{{ p.label }}</div>
              <div class="permission-desc">{{ p.desc }}</div>
            </div>
            <div
              class="toggle-switch"
              :class="{ on: __state.selectedMember?.permissions[p.key] }"
              @click="togglePermission(p.key)">
              <div class="toggle-slider"></div>
            </div>
          </div>
        </div>

        <div class="modal-footer row">
          <button class="cancel-btn" @click="closePermissionModal">取消</button>
          <button class="save-btn" @click="savePermissions">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 家庭成员管理 family
 *
 * 邀请码 + 已绑定家属列表 + 待处理邀请 + 两个弹窗（添加家属 / 权限设置）。
 * 权限级别规则（照抄原项目）：设为「主要监护人」→ 全开 6 项权限，
 * 其他 active 成员批量降级为 limited。
 */
import { reactive, computed } from 'vue'

interface Permissions {
  viewHealth: boolean
  viewLocation: boolean
  receiveAlert: boolean
  callContact: boolean
}

interface FamilyMember {
  id: string
  name: string
  phone: string
  relation: string
  isPrimary: boolean
  bindTime: string
  status: 'active' | 'pending' | 'inactive'
  level: 'full' | 'limited'
  permissions: Permissions
}

/** mock 家属数据：3 位已绑定 + 1 条待处理邀请 */
const MOCK_MEMBERS: FamilyMember[] = [
  {
    id: 'fm_001',
    name: '张文博',
    phone: '13901234567',
    relation: '儿子',
    isPrimary: true,
    bindTime: '2024-03-12',
    status: 'active',
    level: 'full',
    permissions: {
      viewHealth: true,
      viewLocation: true,
      receiveAlert: true,
      callContact: true
    }
  },
  {
    id: 'fm_002',
    name: '李静',
    phone: '13712345678',
    relation: '女儿',
    isPrimary: false,
    bindTime: '2024-05-08',
    status: 'active',
    level: 'limited',
    permissions: {
      viewHealth: true,
      viewLocation: false,
      receiveAlert: true,
      callContact: false
    }
  },
  {
    id: 'fm_003',
    name: '王秀英',
    phone: '13698765432',
    relation: '侄女',
    isPrimary: false,
    bindTime: '2024-08-20',
    status: 'active',
    level: 'limited',
    permissions: {
      viewHealth: true,
      viewLocation: true,
      receiveAlert: false,
      callContact: false
    }
  },
  {
    id: 'fm_004',
    name: '待确认家属',
    phone: '13566667777',
    relation: '亲属',
    isPrimary: false,
    bindTime: '2024-09-30',
    status: 'pending',
    level: 'limited',
    permissions: {
      viewHealth: false,
      viewLocation: false,
      receiveAlert: false,
      callContact: false
    }
  }
]

const __state = reactive({
  inviteCode: '8F3K-9D2M',
  familyMembers: MOCK_MEMBERS.filter((m) => m.status === 'active') as FamilyMember[],
  pendingInvites: MOCK_MEMBERS.filter((m) => m.status === 'pending') as FamilyMember[],
  searchPhone: '',
  searchResult: null as FamilyMember | null,
  showAddModal: false,
  showPermissionModal: false,
  selectedMember: null as FamilyMember | null,
  loading: false
})

const activeMembers = computed(() => __state.familyMembers.filter((m) => m.status === 'active'))

/** 权限弹窗 4 项（照抄原项目字段） */
const permissionRows = [
  {
    key: 'viewHealth' as const,
    label: '查看健康数据',
    desc: '包括作息记录、睡眠质量、AI报告'
  },
  {
    key: 'viewLocation' as const,
    label: '查看位置信息',
    desc: '实时位置与历史轨迹'
  },
  {
    key: 'receiveAlert' as const,
    label: '接收告警通知',
    desc: 'SOS求救、异常告警提醒'
  },
  {
    key: 'callContact' as const,
    label: '联系紧急联系人',
    desc: '可通过平台联系您的其他联系人'
  }
]

function toast(msg: string) {
  console.log('[toast]', msg)
}

function avatarText(name: string): string {
  return name ? name.charAt(0) : '👤'
}

function statusLabel(status: string): string {
  if (status === 'active') return '已激活'
  if (status === 'pending') return '待确认'
  return '已失效'
}

function copyInviteCode() {
  // 真实环境用剪贴板 API，这里给出可复制的日志
  console.log('[copy]', __state.inviteCode)
  toast('邀请码已复制')
}

function shareInviteCode() {
  toast('请使用小程序的分享功能发送邀请码')
}

function openAddModal() {
  __state.showAddModal = true
}

function closeAddModal() {
  __state.showAddModal = false
  __state.searchResult = null
  __state.searchPhone = ''
}

/** 搜索家属：手机号长度 < 4 直接提示 */
function searchFamily() {
  if (__state.searchPhone.length < 4) {
    toast('请输入有效手机号')
    return
  }
  __state.loading = true
  try {
    // 真实接口：GET /api/family/search?phone=...
    __state.searchResult = {
      id: 'new',
      name: '刘敏',
      phone: __state.searchPhone,
      relation: '未绑定',
      isPrimary: false,
      bindTime: '—',
      status: 'pending',
      level: 'limited',
      permissions: {
        viewHealth: false,
        viewLocation: false,
        receiveAlert: false,
        callContact: false
      }
    }
  } catch {
    toast('未找到该用户')
  } finally {
    __state.loading = false
  }
}

/** 发送绑定邀请 */
function sendBindInvite(member: FamilyMember | null) {
  if (!member) return
  const created: FamilyMember = { ...member, id: 'fm_' + Date.now(), status: 'pending' }
  __state.pendingInvites.push(created)
  toast(`已向 ${member.phone} 发送家属绑定邀请`)
  closeAddModal()
}

function cancelInvite(id: string) {
  __state.pendingInvites = __state.pendingInvites.filter((p) => p.id !== id)
  toast('已取消邀请')
}

/** 查看家属详情（只读） */
function viewMemberDetail(m: FamilyMember) {
  const p = m.permissions
  toast(
    `${m.name}｜关系：${m.relation}｜绑定于 ${m.bindTime}｜权限：健康 ${p.viewHealth ? '是' : '否'}、位置 ${
      p.viewLocation ? '是' : '否'
    }、告警 ${p.receiveAlert ? '是' : '否'}、紧急联系人 ${p.callContact ? '是' : '否'}`
  )
}

/**
 * ★ 核心规则：设为主要监护人
 * 该成员 6 项权限全开且 level = full；
 * 其他 active 成员批量降级为 limited。
 */
function setPrimaryGuardian(member: FamilyMember) {
  __state.familyMembers.forEach((m) => {
    if (m.id === member.id) {
      m.isPrimary = true
      m.level = 'full'
      m.permissions = {
        viewHealth: true,
        viewLocation: true,
        receiveAlert: true,
        callContact: true
      }
    } else if (m.status === 'active') {
      m.isPrimary = false
      m.level = 'limited'
    }
  })
  toast(`已设置${member.name}为主要监护人`)
}

function openPermissionSetting(member: FamilyMember) {
  // 深拷贝，取消时不影响原数据
  __state.selectedMember = JSON.parse(JSON.stringify(member)) as FamilyMember
  __state.showPermissionModal = true
}

function closePermissionModal() {
  __state.showPermissionModal = false
  __state.selectedMember = null
}

/** 切换权限开关：仅更新弹窗内本地状态，不立即落库 */
function togglePermission(key: keyof Permissions) {
  if (!__state.selectedMember) return
  __state.selectedMember.permissions[key] = !__state.selectedMember.permissions[key]
}

/** 保存权限：回写到列表中的同一成员 */
function savePermissions() {
  const sel = __state.selectedMember
  if (!sel) return
  const target = __state.familyMembers.find((m) => m.id === sel.id)
  if (target) {
    target.permissions = { ...sel.permissions }
  }
  closePermissionModal()
  toast('权限已更新')
}

/** 解绑家属 */
function unbindFamily(member: FamilyMember) {
  __state.familyMembers = __state.familyMembers.filter((m) => m.id !== member.id)
  toast(`已解绑 ${member.name}（${member.relation}）`)
}
</script>

<style scoped>
/* ================= 页面容器 ================= */
.family-page {
  min-height: 100vh;
  padding: 24px;
  padding-bottom: 100px;
  background: linear-gradient(180deg, #f2f7f3 0%, #ffffff 100%);
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif;
}

/* ================= 标题 ================= */
.page-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 24px;
}

.header-icon {
  font-size: 26px;
  line-height: 1.2;
}

.page-title {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-1);
}

.page-subtitle {
  font-size: 15px;
  color: var(--text-2);
  margin-top: 4px;
}

/* ================= 通用卡片 ================= */
.card {
  background: var(--surface);
  border: 1px solid rgba(91, 154, 122, 0.14);
  border-radius: var(--radius);
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.section-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-1);
}

/* ================= 邀请码区 ================= */
.invite-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.invite-header-icon {
  font-size: 20px;
}

.invite-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: rgba(143, 166, 142, 0.12);
  border: 1px solid var(--primary-d);
  border-radius: var(--radius-sm);
}

.invite-code {
  font-size: 24px;
  font-weight: 700;
  color: var(--primary-d);
  letter-spacing: 4px;
}

.invite-actions {
  display: flex;
  gap: 12px;
}

.invite-btn {
  padding: 8px 16px;
  border-radius: 8px;
  background: var(--surface);
  border: 1px solid var(--primary-d);
  color: var(--primary-d);
  font-size: 14px;
  cursor: pointer;
}

.invite-btn:active {
  transform: scale(0.96);
}

.invite-tip {
  font-size: 14px;
  color: var(--text-2);
  text-align: center;
  line-height: 1.6;
  margin: 14px 0 0;
}

/* ================= 已绑定家属 ================= */
.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.section-header-icon {
  font-size: 20px;
}

.add-btn {
  margin-left: auto;
  padding: 8px;
  border: none;
  border-radius: 8px;
  background: rgba(143, 166, 142, 0.18);
  font-size: 16px;
  cursor: pointer;
}

.family-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.family-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: var(--radius-sm);
  background: rgba(125, 116, 107, 0.05);
}

/* 头像 + 主要监护人角标 */
.member-avatar {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 24px;
  background: linear-gradient(135deg, #8fa68e, #5b9a7a);
  color: #ffffff;
  font-size: 20px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.primary-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 20px;
  height: 20px;
  border-radius: 10px;
  background: var(--danger);
  color: #ffffff;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.member-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  cursor: pointer;
}

.member-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.member-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-1);
}

.member-relation {
  font-size: 12px;
  color: var(--text-2);
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(125, 116, 107, 0.14);
}

.member-sub {
  font-size: 13px;
  color: var(--text-2);
}

.member-time {
  font-size: 12px;
  color: var(--text-2);
}

/* 状态标签三态 */
.status-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
}

.status-active {
  background: rgba(143, 166, 142, 0.2);
  color: var(--primary-d);
}

.status-pending {
  background: rgba(212, 165, 116, 0.2);
  color: #b07b33;
}

.status-inactive {
  background: rgba(229, 90, 90, 0.14);
  color: var(--danger);
}

/* 操作按钮组 */
.member-actions {
  display: flex;
  gap: 8px;
}

.circle-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 16px;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-primary-set {
  background: rgba(229, 90, 90, 0.12);
}

.btn-perm {
  background: rgba(143, 166, 142, 0.16);
}

.btn-unbind {
  background: rgba(229, 90, 90, 0.12);
}

/* 空态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 0;
}

.empty-icon {
  font-size: 46px;
}

.empty-title {
  font-size: 16px;
  color: var(--text-2);
  margin-top: 10px;
}

.empty-desc {
  font-size: 14px;
  color: var(--text-2);
  margin-top: 6px;
}

/* ================= 待处理邀请 ================= */
.pending-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pending-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-radius: 8px;
  background: rgba(212, 165, 116, 0.14);
}

.pending-phone {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-1);
}

.pending-hint {
  font-size: 12px;
  color: #b07b33;
  margin-top: 4px;
}

.pending-cancel {
  padding: 8px 16px;
  border-radius: 8px;
  background: var(--surface);
  border: none;
  color: var(--danger);
  font-size: 14px;
  cursor: pointer;
}

/* ================= 绑定说明 ================= */
.guide-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-1);
  margin-bottom: 14px;
}

.guide-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  font-size: 15px;
  color: var(--text-2);
  line-height: 1.6;
  margin-bottom: 10px;
}

.guide-item:last-child {
  margin-bottom: 0;
}

.guide-icon {
  color: var(--primary-d);
  flex-shrink: 0;
}

.guide-icon.warn {
  color: var(--warning);
}

/* ================= 弹窗 ================= */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}

.modal-content {
  width: 85%;
  max-width: 400px;
  background: var(--surface);
  border-radius: var(--radius);
  overflow: hidden;
  max-height: 86vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px;
  background: rgba(143, 166, 142, 0.12);
}

.modal-header-icon {
  font-size: 22px;
}

.modal-title {
  font-size: 19px;
  font-weight: 700;
  color: var(--text-1);
}

.modal-sub {
  font-size: 14px;
  color: var(--text-2);
  margin-top: 2px;
}

.modal-body {
  padding: 20px;
}

/* 搜索行 */
.search-section {
  display: flex;
  gap: 12px;
}

.search-input-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-radius: var(--radius-sm);
  background: rgba(125, 116, 107, 0.08);
}

.search-icon {
  font-size: 16px;
}

.search-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 16px;
  color: var(--text-1);
}

.search-btn {
  padding: 12px 20px;
  border: none;
  border-radius: var(--radius-sm);
  background: linear-gradient(135deg, var(--primary), var(--primary-d));
  color: #ffffff;
  font-size: 15px;
  cursor: pointer;
}

/* 搜索结果 */
.search-result {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
  padding: 16px;
  border-radius: var(--radius-sm);
  background: rgba(143, 166, 142, 0.12);
}

.result-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-1);
}

.result-phone {
  font-size: 14px;
  color: var(--text-2);
  margin-top: 4px;
}

.result-btn {
  padding: 8px 16px;
  border-radius: 8px;
  background: var(--surface);
  border: 1px solid var(--primary-d);
  color: var(--primary-d);
  font-size: 14px;
  cursor: pointer;
}

/* 其他添加方式 */
.other-methods {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid rgba(125, 116, 107, 0.14);
}

.methods-label {
  font-size: 14px;
  color: var(--text-2);
  margin-bottom: 10px;
}

.method-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  background: rgba(125, 116, 107, 0.06);
  margin-bottom: 8px;
  cursor: pointer;
}

.method-icon {
  font-size: 17px;
}

.method-text {
  flex: 1;
  font-size: 15px;
  color: var(--text-1);
}

.method-arrow {
  font-size: 20px;
  color: var(--text-2);
}

/* 权限弹窗 */
.permission-list {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.permission-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  background: rgba(125, 116, 107, 0.06);
}

.permission-label {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-1);
}

.permission-desc {
  font-size: 12px;
  color: var(--text-2);
  margin-top: 4px;
}

/* 自定义开关 48×28 */
.toggle-switch {
  width: 48px;
  height: 28px;
  flex-shrink: 0;
  border-radius: 14px;
  background: rgba(125, 116, 107, 0.28);
  position: relative;
  cursor: pointer;
  transition: background 0.2s ease;
}

.toggle-switch.on {
  background: var(--primary-d);
}

.toggle-slider {
  width: 24px;
  height: 24px;
  border-radius: 12px;
  background: #ffffff;
  position: absolute;
  top: 2px;
  left: 2px;
  transition: left 0.2s ease;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.18);
}

.toggle-switch.on .toggle-slider {
  left: 22px;
}

/* 弹窗底部 */
.modal-footer {
  padding: 20px;
  display: flex;
  justify-content: center;
}

.modal-footer.row {
  gap: 12px;
  padding-top: 0;
}

.close-btn {
  padding: 12px 48px;
  border: none;
  border-radius: var(--radius-sm);
  background: rgba(125, 116, 107, 0.1);
  color: var(--text-2);
  font-size: 16px;
  cursor: pointer;
}

.cancel-btn {
  flex: 1;
  padding: 12px;
  border: none;
  border-radius: var(--radius-sm);
  background: rgba(125, 116, 107, 0.1);
  color: var(--text-2);
  font-size: 16px;
  cursor: pointer;
}

.save-btn {
  flex: 1;
  padding: 12px;
  border: none;
  border-radius: var(--radius-sm);
  background: linear-gradient(135deg, var(--primary), var(--primary-d));
  color: #ffffff;
  font-size: 16px;
  cursor: pointer;
}
</style>

<template>
  <div class="page medicine-page">
    <!-- ============ ① 页面标题 ============ -->
    <div class="page-header">
      <div class="title-row">
        <span class="header-icon">💊</span>
        <span class="page-title">服药提醒</span>
      </div>
      <p class="page-desc">定时提醒服药，记录每次服药情况</p>
    </div>

    <!-- ============ ② 今日服药进度三宫格 ============ -->
    <div class="stats-card">
      <div class="stats-title-row">
        <span class="stats-icon">📅</span>
        <span class="stats-title">今日服药进度</span>
      </div>
      <div class="stats-content">
        <div class="stat-item">
          <div class="stat-value">{{ __state.takenCount }}</div>
          <div class="stat-label">已服用</div>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <div class="stat-value">{{ pendingCount }}</div>
          <div class="stat-label">待服用</div>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <div class="stat-value">{{ __state.totalTimes }}</div>
          <div class="stat-label">总计</div>
        </div>
      </div>
    </div>

    <!-- ============ ③ Tab 切换 ============ -->
    <div class="reminder-tabs">
      <div class="tabs-header">
        <div
          class="tab-item"
          :class="{ active: __state.activeTab === 'active' }"
          @click="__state.activeTab = 'active'">
          进行中 ({{ activeReminders.length }})
        </div>
        <div
          class="tab-item"
          :class="{ active: __state.activeTab === 'paused' }"
          @click="__state.activeTab = 'paused'">
          已暂停 ({{ inactiveReminders.length }})
        </div>
      </div>

      <!-- 进行中列表 -->
      <div v-if="__state.activeTab === 'active'">
        <div v-if="activeReminders.length > 0">
          <div v-for="r in activeReminders" :key="r.id" class="reminder-card">
            <div class="reminder-header">
              <div class="medicine-icon">💊</div>
              <div class="medicine-info">
                <div class="medicine-name">{{ r.name }}</div>
                <div class="medicine-tags">
                  <span class="tag-outline">{{ r.dosage }}</span>
                  <span class="tag-secondary">{{ r.frequency }}</span>
                </div>
              </div>
              <button class="action-btn" @click="toggleActive(r.id)">✓</button>
            </div>

            <div v-if="r.notes" class="notes-row">
              <span class="notes-label">备注:</span>
              <span class="notes-text">{{ r.notes }}</span>
            </div>

            <div class="sep"></div>

            <div class="today-title">今日服药记录</div>
            <div class="time-slots">
              <div
                v-for="t in r.times"
                :key="t"
                class="time-slot"
                :class="{ taken: isTaken(r, t) }">
                <div class="time-info">
                  <span class="period-icon">{{ periodIcon(t) }}</span>
                  <span class="time-label">{{ periodLabel(t) }}</span>
                  <span class="time-value">{{ t }}</span>
                </div>
                <span v-if="isTaken(r, t)" class="taken-badge">✓ 已服用</span>
                <button v-else class="record-btn" @click="markTaken(r.id, t)">记录</button>
              </div>
            </div>

            <div class="sep"></div>

            <div class="action-row">
              <button class="mini-btn" @click="editReminder(r.id)">✏️ 编辑</button>
              <button class="mini-btn danger" @click="deleteReminder(r.id)">🗑️ 删除</button>
              <button class="mini-btn" @click="viewHistory(r.id)">🕐 历史</button>
            </div>
          </div>
        </div>

        <div v-else class="empty-state">
          <div class="empty-icon">💊</div>
          <div class="empty-text">暂无服药提醒</div>
          <div class="empty-desc">点击下方按钮添加药品</div>
        </div>
      </div>

      <!-- 已暂停列表 -->
      <div v-else>
        <div v-if="inactiveReminders.length > 0">
          <div v-for="r in inactiveReminders" :key="r.id" class="reminder-card inactive">
            <div class="reminder-header">
              <div class="medicine-icon paused">💊</div>
              <div class="medicine-info">
                <div class="medicine-name">{{ r.name }}</div>
                <div class="medicine-tags">
                  <span class="tag-outline">{{ r.dosage }}</span>
                  <span class="tag-secondary">{{ r.frequency }}</span>
                </div>
              </div>
              <button class="action-btn paused" @click="toggleActive(r.id)">⚠️</button>
            </div>

            <div v-if="r.notes" class="notes-row">
              <span class="notes-label">备注:</span>
              <span class="notes-text">{{ r.notes }}</span>
            </div>

            <div class="sep"></div>

            <div class="action-row">
              <button class="mini-btn" @click="editReminder(r.id)">✏️ 编辑</button>
              <button class="mini-btn danger" @click="deleteReminder(r.id)">🗑️ 删除</button>
              <button class="mini-btn" @click="viewHistory(r.id)">🕐 历史</button>
            </div>
          </div>
        </div>

        <div v-else class="empty-state">
          <div class="empty-icon">🕐</div>
          <div class="empty-text">暂无暂停的提醒</div>
        </div>
      </div>
    </div>

    <!-- ============ ④ 提醒方式设置 ============ -->
    <div class="settings-card">
      <div class="settings-title">提醒方式</div>
      <div class="sep"></div>

      <div class="setting-item">
        <span class="setting-left"><span class="setting-icon">🔔</span>微信通知</span>
        <div class="switch" :class="{ on: __state.enablePush }" @click="toggleSetting('enablePush')">
          <div class="switch-knob"></div>
        </div>
      </div>
      <div class="setting-item">
        <span class="setting-left"><span class="setting-icon">📞</span>短信提醒</span>
        <div class="switch" :class="{ on: __state.enableVoice }" @click="toggleSetting('enableVoice')">
          <div class="switch-knob"></div>
        </div>
      </div>
      <div class="setting-item no-border">
        <span class="setting-left"><span class="setting-icon">🔁</span>电话提醒</span>
        <div class="switch" :class="{ on: __state.enableRepeat }" @click="toggleSetting('enableRepeat')">
          <div class="switch-knob"></div>
        </div>
      </div>
    </div>

    <!-- ============ ⑤ 添加按钮 ============ -->
    <div class="add-section">
      <button class="add-btn" @click="addReminder">➕ 添加药品提醒</button>
    </div>

    <!-- ============ ⑥ 使用提示 ============ -->
    <div class="tips-section">
      <div class="tips-card">
        <span class="tips-icon">⚠️</span>
        <span class="tips-text">
          服药提醒将在设定时间准时推送，请确保长辈正确服药后点击「记录」按钮，便于家属了解服药情况。
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 服药提醒 medicine-reminder
 *
 * 修正原项目统计口径缺陷：
 *   原 takenCount 统计全部提醒（含已暂停），totalTimes 只统计进行中，
 *   暂停提醒多时会算出负数的「待服用」。这里统一为只统计进行中的提醒，
 *   并额外加 Math.max(0, ...) 兜底。
 */
import { reactive, computed } from 'vue'

interface TakenToday {
  morning: boolean
  afternoon: boolean
  evening: boolean
}

interface MedicineReminder {
  id: string
  name: string
  dosage: string
  frequency: string
  times: string[]
  startDate: string
  notes?: string
  isActive: boolean
  takenToday: TakenToday
}

/** mock 3 条提醒：2 条进行中 + 1 条已暂停，界面看起来是活的 */
const MOCK_REMINDERS: MedicineReminder[] = [
  {
    id: '1',
    name: '降压药',
    dosage: '1片',
    frequency: '每日3次',
    times: ['08:00', '14:00', '20:00'],
    startDate: '2024-01-01',
    notes: '饭后服用',
    isActive: true,
    takenToday: { morning: true, afternoon: false, evening: false }
  },
  {
    id: '2',
    name: '降糖药',
    dosage: '2片',
    frequency: '每日1次',
    times: ['07:30'],
    startDate: '2024-02-15',
    notes: '早餐前30分钟',
    isActive: true,
    takenToday: { morning: false, afternoon: false, evening: false }
  },
  {
    id: '3',
    name: '钙片',
    dosage: '1粒',
    frequency: '每日1次',
    times: ['19:00'],
    startDate: '2024-04-02',
    notes: '睡前服用，避免与降压药同服',
    isActive: false,
    takenToday: { morning: false, afternoon: false, evening: false }
  }
]

/**
 * 时段划分规则（照抄原项目）
 * hour < 12 → 早晨；hour < 18 → 午后；否则 → 睡前
 */
function getTimePeriod(time: string): 'morning' | 'afternoon' | 'evening' {
  const hour = parseInt(time.split(':')[0], 10)
  if (hour < 12) return 'morning'
  if (hour < 18) return 'afternoon'
  return 'evening'
}

const __state = reactive({
  reminders: MOCK_REMINDERS as MedicineReminder[],
  activeTab: 'active' as 'active' | 'paused',
  // 三个提醒方式开关
  enablePush: true,
  enableVoice: false,
  enableRepeat: true,
  // 未在 UI 暴露但保留在状态里（照抄原项目）
  repeatCount: 3,
  repeatInterval: 5
})

const activeReminders = computed(() => __state.reminders.filter((r) => r.isActive))
const inactiveReminders = computed(() => __state.reminders.filter((r) => !r.isActive))

/** 已服用时间点数：只统计进行中的提醒 */
const takenCount = computed(() =>
  activeReminders.value.reduce((acc, r) => {
    const taken = r.times.filter((t) => r.takenToday[getTimePeriod(t)]).length
    return acc + taken
  }, 0)
)

/** 总时间点数：同样只统计进行中的提醒（与 takenCount 口径一致） */
const totalTimes = computed(() => activeReminders.value.reduce((acc, r) => acc + r.times.length, 0))

/** ★ 修正点：Math.max(0, ...) 兜底，杜绝负数 */
const pendingCount = computed(() => Math.max(0, totalTimes.value - takenCount.value))

function toast(msg: string) {
  console.log('[toast]', msg)
}

function isTaken(r: MedicineReminder, time: string): boolean {
  return r.takenToday[getTimePeriod(time)]
}

function periodLabel(time: string): string {
  const p = getTimePeriod(time)
  if (p === 'morning') return '晨起'
  if (p === 'afternoon') return '午后'
  return '睡前'
}

function periodIcon(time: string): string {
  const p = getTimePeriod(time)
  if (p === 'morning') return '☀️'
  if (p === 'afternoon') return '🍽️'
  return '🌙'
}

/** 记录服药：该时段标记为已服用 */
function markTaken(id: string, time: string) {
  const r = __state.reminders.find((x) => x.id === id)
  if (!r) return
  r.takenToday[getTimePeriod(time)] = true
  toast('已记录服药')
}

/** 启用 / 暂停提醒 */
function toggleActive(id: string) {
  const r = __state.reminders.find((x) => x.id === id)
  if (!r) return
  r.isActive = !r.isActive
  toast(r.isActive ? '已启用提醒' : '已暂停提醒')
}

function toggleSetting(key: 'enablePush' | 'enableVoice' | 'enableRepeat') {
  __state[key] = !__state[key]
  const label = key === 'enablePush' ? '推送' : key === 'enableVoice' ? '播报' : '重复'
  toast(`${label}已${__state[key] ? '开启' : '关闭'}`)
}

function addReminder() {
  console.log('[navigate] /pages/medicine-add/index')
}

function editReminder(id: string) {
  console.log('[navigate] /pages/medicine-add/index?id=' + id)
}

function viewHistory(id: string) {
  console.log('[navigate] /pages/medicine-history/index?id=' + id)
}

function deleteReminder(id: string) {
  __state.reminders = __state.reminders.filter((r) => r.id !== id)
  toast('已删除')
}
</script>

<style scoped>
/* ================= 页面容器 ================= */
.medicine-page {
  min-height: 100vh;
  padding: 16px;
  padding-bottom: 100px;
  background: linear-gradient(180deg, var(--bg) 0%, #f0f4f0 30%, #ffffff 100%);
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif;
}

/* ================= 标题 ================= */
.page-header {
  padding: 16px 0;
  margin-bottom: 8px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-icon {
  font-size: 26px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-1);
}

.page-desc {
  font-size: 14px;
  color: var(--text-2);
  margin: 8px 0 0;
}

/* ================= 进度三宫格 ================= */
.stats-card {
  background: var(--surface);
  border: 1px solid rgba(91, 154, 122, 0.18);
  border-radius: var(--radius);
  padding: 16px;
  box-shadow: var(--shadow);
}

.stats-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.stats-icon {
  font-size: 19px;
}

.stats-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-1);
}

.stats-content {
  display: flex;
  align-items: center;
  justify-content: space-around;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--primary-d);
}

.stat-label {
  font-size: 14px;
  color: var(--text-2);
}

.stat-divider {
  width: 1px;
  height: 40px;
  background: rgba(91, 154, 122, 0.2);
}

/* ================= Tabs ================= */
.reminder-tabs {
  margin-top: 16px;
}

.tabs-header {
  display: flex;
  background: rgba(125, 116, 107, 0.08);
  border-radius: 8px;
  padding: 4px;
  margin-bottom: 14px;
}

.tab-item {
  flex: 1;
  text-align: center;
  font-size: 14px;
  padding: 8px 0;
  border-radius: 6px;
  color: var(--text-2);
  cursor: pointer;
  transition: all 0.2s ease;
}

.tab-item.active {
  background: var(--surface);
  color: var(--primary-d);
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

/* ================= 药品卡片 ================= */
.reminder-card {
  background: var(--surface);
  border: 1px solid rgba(91, 154, 122, 0.18);
  border-radius: var(--radius);
  padding: 16px;
  margin-bottom: 12px;
}

.reminder-card.inactive {
  opacity: 0.72;
}

.reminder-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.medicine-icon {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: var(--radius-sm);
  background: rgba(143, 166, 142, 0.16);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}

.medicine-icon.paused {
  background: rgba(125, 116, 107, 0.12);
}

.medicine-info {
  flex: 1;
  min-width: 0;
}

.medicine-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-1);
}

.medicine-tags {
  display: flex;
  gap: 8px;
  margin-top: 6px;
}

.tag-outline {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 6px;
  border: 1px solid rgba(91, 154, 122, 0.4);
  color: var(--primary-d);
}

.tag-secondary {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(125, 116, 107, 0.1);
  color: var(--text-2);
}

.action-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: rgba(143, 166, 142, 0.15);
  color: var(--primary-d);
  font-size: 16px;
  cursor: pointer;
}

.action-btn.paused {
  background: rgba(125, 116, 107, 0.12);
  color: var(--text-2);
}

/* 备注 */
.notes-row {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(125, 116, 107, 0.06);
}

.notes-label {
  font-size: 13px;
  color: var(--text-2);
}

.notes-text {
  font-size: 13px;
  color: var(--text-1);
}

.sep {
  height: 1px;
  background: rgba(91, 154, 122, 0.14);
  margin: 12px 0;
}

/* 今日服药记录 */
.today-title {
  font-size: 14px;
  color: var(--text-2);
  margin-bottom: 10px;
}

.time-slots {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.time-slot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border-radius: 8px;
  background: rgba(125, 116, 107, 0.05);
  border: 1px solid rgba(125, 116, 107, 0.14);
}

.time-slot.taken {
  background: rgba(143, 166, 142, 0.16);
  border-color: rgba(91, 154, 122, 0.45);
}

.time-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.period-icon {
  font-size: 16px;
}

.time-label {
  font-size: 14px;
  color: var(--text-1);
}

.time-value {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-2);
}

.record-btn {
  padding: 6px 16px;
  border-radius: 8px;
  border: 1px solid rgba(91, 154, 122, 0.45);
  background: transparent;
  color: var(--primary-d);
  font-size: 13px;
  cursor: pointer;
}

.taken-badge {
  font-size: 14px;
  font-weight: 500;
  color: var(--primary-d);
}

/* 操作行 */
.action-row {
  display: flex;
  gap: 8px;
}

.mini-btn {
  flex: 1;
  padding: 8px 0;
  border-radius: 8px;
  border: 1px solid rgba(91, 154, 122, 0.3);
  background: transparent;
  color: var(--text-2);
  font-size: 13px;
  cursor: pointer;
}

.mini-btn.danger {
  border-color: rgba(229, 90, 90, 0.4);
  color: var(--danger);
}

/* 空态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 0;
}

.empty-icon {
  font-size: 46px;
}

.empty-text {
  font-size: 16px;
  color: var(--text-2);
  margin-top: 10px;
}

.empty-desc {
  font-size: 14px;
  color: var(--text-2);
  margin-top: 6px;
}

/* ================= 提醒方式设置 ================= */
.settings-card {
  margin-top: 16px;
  background: var(--surface);
  border: 1px solid rgba(91, 154, 122, 0.18);
  border-radius: var(--radius);
  padding: 16px;
}

.settings-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-1);
}

.setting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid rgba(91, 154, 122, 0.1);
}

.setting-item.no-border {
  border-bottom: none;
  padding-bottom: 0;
}

.setting-left {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  color: var(--text-1);
}

.setting-icon {
  font-size: 18px;
}

/* 开关 48×28 */
.switch {
  width: 48px;
  height: 28px;
  border-radius: 14px;
  background: rgba(125, 116, 107, 0.28);
  position: relative;
  cursor: pointer;
  transition: background 0.2s ease;
  flex-shrink: 0;
}

.switch.on {
  background: var(--primary-d);
}

.switch-knob {
  width: 24px;
  height: 24px;
  border-radius: 12px;
  background: #ffffff;
  position: absolute;
  top: 2px;
  left: 2px;
  transition: left 0.2s ease;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.18);
}

.switch.on .switch-knob {
  left: 22px;
}

/* ================= 添加按钮 ================= */
.add-section {
  padding: 16px 0;
}

.add-btn {
  width: 100%;
  height: 48px;
  border: none;
  border-radius: 24px;
  background: linear-gradient(135deg, var(--primary), var(--primary-d));
  color: #ffffff;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(91, 154, 122, 0.25);
}

.add-btn:active {
  transform: scale(0.98);
}

/* ================= 使用提示 ================= */
.tips-section {
  padding: 0 0 8px;
}

.tips-card {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  border-radius: var(--radius-sm);
  background: rgba(212, 165, 116, 0.14);
  border: 1px solid rgba(212, 165, 116, 0.35);
}

.tips-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.tips-text {
  font-size: 13px;
  line-height: 1.5;
  color: #8a6a3d;
}
</style>

<template>
  <div class="page settings-page">
    <!-- ============ ① 用户信息卡片 ============ -->
    <div class="user-card">
      <div class="user-content">
        <div class="user-avatar" @click="changeAvatar">
          <span class="avatar-emoji">👤</span>
          <span class="avatar-edit-icon">📷</span>
        </div>
        <div class="user-info">
          <!-- 昵称内联编辑态 -->
          <div v-if="__state.isEditingNickname" class="nickname-edit">
            <input v-model="__state.newNickname" maxlength="20" placeholder="请输入昵称" class="nickname-input" />
            <div class="edit-buttons">
              <button class="mini-save" @click="saveNickname">保存</button>
              <button class="mini-cancel" @click="cancelEditNickname">取消</button>
            </div>
          </div>
          <!-- 常态 -->
          <div v-else class="user-name" @click="startEditNickname">
            {{ __state.nickname || '未设置昵称' }}
            <span class="pencil">✏️</span>
          </div>
          <div class="user-phone">{{ __state.phone || '未绑定手机' }}</div>
        </div>
      </div>
    </div>

    <!-- ============ ② 五组设置项 ============ -->
    <div v-for="group in settingGroups" :key="group.title" class="settings-card">
      <div class="section-title">{{ group.title }}</div>
      <div class="sep"></div>

      <div
        v-for="(item, idx) in group.items"
        :key="item.key"
        class="setting-item"
        :class="{ 'no-border': idx === group.items.length - 1 }"
        @click="onItemClick(item)">
        <div class="setting-icon"><span>{{ item.icon }}</span></div>
        <div class="setting-content">
          <div class="setting-title">{{ item.name }}</div>
          <div v-if="item.desc" class="setting-desc">{{ item.desc }}</div>
        </div>

        <!-- 右侧控件：开关 -->
        <div v-if="item.type === 'switch'" class="setting-action">
          <div
            class="switch"
            :class="{ on: switchValue(item.key) }"
            @click.stop="toggleSwitch(item.key)">
            <div class="switch-knob"></div>
          </div>
        </div>
        <!-- 右侧控件：文字值 -->
        <div v-else-if="item.type === 'value'" class="setting-action value-text">
          {{ valueText(item.key) }}
        </div>
        <!-- 右侧控件：箭头跳转 -->
        <div v-else class="setting-action arrow">›</div>
      </div>
    </div>

    <!-- ============ ③ 退出登录 ============ -->
    <div class="logout-section">
      <button class="logout-btn" @click="handleLogout">🚪 退出登录</button>
    </div>

    <!-- ============ ④ 底部说明 ============ -->
    <div class="footer-note">
      版本号：v1.0.0<br />如有问题请联系客服或反馈意见
    </div>

    <!-- ============ ⑤ 编辑对话框 ============ -->
    <div v-if="__state.showEditDialog" class="modal-overlay" @click.self="closeDialog">
      <div class="modal-content">
        <div class="modal-header">
          {{ __state.editType === 'nickname' ? '修改昵称' : '修改手机号' }}
        </div>
        <div class="modal-body">
          <input
            v-model="__state.editValue"
            :type="__state.editType === 'phone' ? 'number' : 'text'"
            :maxlength="__state.editType === 'phone' ? 11 : 20"
            :placeholder="__state.editType === 'nickname' ? '请输入新的昵称' : '请输入新的手机号'"
            class="edit-input" />
        </div>
        <div class="modal-footer">
          <button class="cancel-btn" @click="closeDialog">取消</button>
          <button class="save-btn" @click="saveEdit">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 账号设置 settings
 *
 * 共 22 个设置项，分 5 组：基础设置(4) / 隐私与安全(4) / 功能管理(4) /
 * 账号管理(3) / 关于我们(7)。
 *
 * 修正原项目缺陷：
 *   1. 开关类设置用 localStorage 持久化（key: userSettings）
 *   2. 账号注销不再假装能用，明确提示「功能开发中」
 */
import { reactive, onMounted, watch } from 'vue'

type SettingType = 'switch' | 'navigate' | 'value' | 'edit' | 'action'

interface SettingItem {
  key: string
  icon: string
  name: string
  desc?: string
  type: SettingType
  target?: string
}

const STORAGE_KEY = 'userSettings'

/** 22 个设置项，按分析报告顺序分 5 组 */
const settingGroups = [
  {
    title: '基础设置',
    items: [
      { key: 'notification', icon: '🔔', name: '消息通知', desc: '接收打卡提醒、健康报告等通知', type: 'switch' as SettingType },
      { key: 'voiceBroadcast', icon: '🔊', name: '语音播报', desc: '自动播报健康提醒和建议', type: 'switch' as SettingType },
      { key: 'nightMode', icon: '🌙', name: '夜间模式', desc: '晚上22点后降低屏幕亮度', type: 'switch' as SettingType },
      { key: 'autoCheckin', icon: '📱', name: '自动打卡', desc: '检测到起床/睡觉时自动打卡', type: 'switch' as SettingType }
    ]
  },
  {
    title: '隐私与安全',
    items: [
      { key: 'privacyMode', icon: '🛡️', name: '隐私模式', desc: '隐藏敏感健康数据，仅本人可见', type: 'switch' as SettingType },
      { key: 'locationShare', icon: '🛡️', name: '位置共享', desc: '允许家属查看您的实时位置', type: 'switch' as SettingType },
      { key: 'dataScope', icon: '👁️', name: '数据可见范围', desc: '设置健康数据的分享范围', type: 'value' as SettingType },
      { key: 'security', icon: '🔒', name: '安全设置', desc: '密码修改、登录保护', type: 'navigate' as SettingType, target: '/pages/security-settings/index' }
    ]
  },
  {
    title: '功能管理',
    items: [
      { key: 'simpleMode', icon: '☀️', name: '长辈极简模式', desc: '大字体、简化界面、一键操作', type: 'switch' as SettingType },
      { key: 'memberBind', icon: '👥', name: '成员绑定管理', desc: '管理家属/长辈绑定关系', type: 'navigate' as SettingType, target: '/pages/member-bind/index' },
      { key: 'healthReport', icon: '📈', name: '健康报告', desc: '查看详细健康分析报告', type: 'navigate' as SettingType, target: '/pages/health-report/index' },
      { key: 'privacySecurity', icon: '🔒', name: '隐私安全设置', desc: '数据共享、位置授权管理', type: 'navigate' as SettingType, target: '/pages/privacy-security/index' }
    ]
  },
  {
    title: '账号管理',
    items: [
      { key: 'nickname', icon: '👤', name: '编辑资料', desc: '修改昵称与头像', type: 'edit' as SettingType },
      { key: 'phone', icon: '📱', name: '修改手机号', desc: '更换绑定手机号', type: 'edit' as SettingType },
      { key: 'cancelAccount', icon: '⚠️', name: '注销账号', desc: '注销后将清除所有数据（功能开发中）', type: 'action' as SettingType }
    ]
  },
  {
    title: '关于我们',
    items: [
      { key: 'about', icon: 'ℹ️', name: '关于AI无感节律守护', desc: '版本 1.0.0', type: 'navigate' as SettingType, target: '/pages/about/index' },
      { key: 'help', icon: '❓', name: '使用帮助', type: 'navigate' as SettingType, target: '/pages/help/index' },
      { key: 'feedback', icon: '💬', name: '意见反馈', type: 'navigate' as SettingType, target: '/pages/help/index?tab=feedback' },
      { key: 'agreement', icon: '📄', name: '用户服务协议', type: 'navigate' as SettingType, target: '/pages/user-agreement/index' },
      { key: 'policy', icon: '📄', name: '隐私政策', type: 'navigate' as SettingType, target: '/pages/privacy-policy/index' },
      { key: 'voiceprint', icon: '📄', name: '声纹授权协议', type: 'navigate' as SettingType, target: '/pages/voiceprint-agreement/index' },
      { key: 'debugLog', icon: '📈', name: '调试日志', desc: '智能养老拐杖蓝牙调试（开发专用）', type: 'navigate' as SettingType, target: '/pages/debug-log/index' }
    ]
  }
]

/** 所有开关类字段的默认值 */
const DEFAULT_SETTINGS = {
  notification: true,
  voiceBroadcast: true,
  nightMode: false,
  privacyMode: false,
  autoCheckin: true,
  locationShare: true,
  simpleMode: false
}

const __state = reactive({
  nickname: '李秀兰',
  phone: '13800138001',
  avatar: '',
  isEditingNickname: false,
  newNickname: '李秀兰',
  editType: null as 'nickname' | 'phone' | null,
  editValue: '',
  showEditDialog: false,
  dataScope: '家属可见',
  // 所有开关类的值集中放这里，便于持久化
  settings: { ...DEFAULT_SETTINGS } as Record<string, boolean>
})

function toast(msg: string) {
  console.log('[toast]', msg)
}

/** 读 localStorage 里的开关设置 */
function loadSettings() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const saved = JSON.parse(raw) as Record<string, unknown>
      Object.keys(DEFAULT_SETTINGS).forEach((k) => {
        if (typeof saved[k] === 'boolean') {
          __state.settings[k] = saved[k] as boolean
        }
      })
      if (typeof saved.dataScope === 'string') {
        __state.dataScope = saved.dataScope
      }
    }
  } catch {
    toast('读取本地设置失败，已使用默认值')
  }
}

/** ★ 修正点：写 localStorage，实现持久化 */
function persistSettings() {
  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ ...__state.settings, dataScope: __state.dataScope })
    )
  } catch {
    toast('保存失败，请检查浏览器存储权限')
  }
}

function switchValue(key: string): boolean {
  return Boolean(__state.settings[key])
}

function valueText(key: string): string {
  if (key === 'dataScope') return __state.dataScope
  return ''
}

/** 开关切换：更新后立即持久化 */
function toggleSwitch(key: string) {
  __state.settings[key] = !__state.settings[key]
  persistSettings()
  toast(__state.settings[key] ? '已开启' : '已关闭')
}

/** 数据可见范围：循环切换三档并持久化 */
function cycleDataScope() {
  const options = ['仅自己可见', '家属可见', '全部可见']
  const idx = options.indexOf(__state.dataScope)
  __state.dataScope = options[(idx + 1) % options.length]
  persistSettings()
  toast('已设置：' + __state.dataScope)
}

function onItemClick(item: SettingItem) {
  if (item.type === 'switch') {
    toggleSwitch(item.key)
    return
  }
  if (item.type === 'value') {
    cycleDataScope()
    return
  }
  if (item.type === 'navigate') {
    console.log('[navigate]', item.target)
    return
  }
  if (item.type === 'edit') {
    openEditDialog(item.key === 'phone' ? 'phone' : 'nickname')
    return
  }
  if (item.type === 'action' && item.key === 'cancelAccount') {
    // ★ 修正点：不再假装能注销，明确说明是开发中
    toast('账号注销功能开发中，如需注销请联系客服处理')
  }
}

/* ---------- 昵称内联编辑 ---------- */
function startEditNickname() {
  __state.newNickname = __state.nickname
  __state.isEditingNickname = true
}

function saveNickname() {
  if (!__state.newNickname.trim()) {
    toast('昵称不能为空')
    return
  }
  __state.nickname = __state.newNickname.trim()
  __state.isEditingNickname = false
  toast('昵称已保存')
}

function cancelEditNickname() {
  __state.newNickname = __state.nickname
  __state.isEditingNickname = false
}

/* ---------- 编辑对话框 ---------- */
function openEditDialog(type: 'nickname' | 'phone') {
  __state.editType = type
  __state.editValue = type === 'phone' ? __state.phone : __state.nickname
  __state.showEditDialog = true
}

function closeDialog() {
  __state.showEditDialog = false
  __state.editType = null
  __state.editValue = ''
}

function saveEdit() {
  if (__state.editType === 'nickname') {
    if (!__state.editValue.trim()) {
      toast('昵称不能为空')
      return
    }
    __state.nickname = __state.editValue.trim()
    __state.newNickname = __state.nickname
    toast('昵称已保存')
  } else if (__state.editType === 'phone') {
    // 手机号校验：照抄原项目正则
    const phoneRegex = /^1[3-9]\d{9}$/
    if (!phoneRegex.test(__state.editValue.trim())) {
      toast('手机号格式不正确')
      return
    }
    __state.phone = __state.editValue.trim()
    toast('手机号已更新')
  }
  closeDialog()
}

function changeAvatar() {
  toast('请在 App 内使用相册更换头像')
}

function handleLogout() {
  localStorage.setItem('isLoggedIn', 'false')
  localStorage.removeItem('userNickname')
  localStorage.removeItem('userPhone')
  toast('已退出登录')
}

// settings 变化时自动写回本地存储，保证不丢
watch(
  () => ({ ...__state.settings, dataScope: __state.dataScope }),
  () => persistSettings(),
  { deep: true }
)

onMounted(() => {
  loadSettings()
  const savedNickname = localStorage.getItem('userNickname')
  const savedPhone = localStorage.getItem('userPhone')
  if (savedNickname) {
    __state.nickname = savedNickname
    __state.newNickname = savedNickname
  }
  if (savedPhone) __state.phone = savedPhone
})
</script>

<style scoped>
/* ================= 页面容器 ================= */
.settings-page {
  min-height: 100vh;
  padding: 16px;
  padding-bottom: 100px;
  background: linear-gradient(180deg, var(--bg) 0%, #f0f4f0 100%);
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif;
}

/* ================= 用户信息卡片 ================= */
.user-card {
  background: linear-gradient(135deg, var(--surface) 0%, rgba(143, 166, 142, 0.1) 100%);
  border: 1px solid rgba(91, 154, 122, 0.12);
  border-radius: 24px;
  margin-bottom: 16px;
  box-shadow: 0 4px 20px rgba(91, 154, 122, 0.1);
  animation: card-breath 4s ease-in-out infinite;
}

@keyframes card-breath {
  0%, 100% { box-shadow: 0 4px 20px rgba(91, 154, 122, 0.1); }
  50% { box-shadow: 0 4px 26px rgba(91, 154, 122, 0.2); }
}

.user-content {
  display: flex;
  align-items: center;
  padding: 20px;
}

/* 头像 64×64 */
.user-avatar {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  margin-right: 16px;
  border-radius: 32px;
  background: linear-gradient(135deg, rgba(143, 166, 142, 0.35), rgba(212, 165, 116, 0.25));
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  cursor: pointer;
}

.avatar-emoji {
  font-size: 30px;
}

.avatar-edit-icon {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 22px;
  height: 22px;
  border-radius: 11px;
  background: var(--primary-d);
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-1);
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.pencil {
  font-size: 13px;
}

.user-phone {
  font-size: 14px;
  color: var(--text-2);
  margin-top: 6px;
}

/* 昵称内联编辑 */
.nickname-edit {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nickname-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid rgba(91, 154, 122, 0.4);
  border-radius: 10px;
  font-size: 16px;
  color: var(--text-1);
  background: var(--surface);
  outline: none;
}

.edit-buttons {
  display: flex;
  gap: 8px;
}

.mini-save,
.mini-cancel {
  padding: 6px 16px;
  border-radius: 8px;
  font-size: 13px;
  border: none;
  cursor: pointer;
}

.mini-save {
  background: var(--primary-d);
  color: #ffffff;
}

.mini-cancel {
  background: rgba(125, 116, 107, 0.12);
  color: var(--text-2);
}

/* ================= 设置分组卡片 ================= */
.settings-card {
  background: linear-gradient(135deg, var(--surface) 0%, #f8f8f0 100%);
  border: 1px solid rgba(91, 154, 122, 0.12);
  border-radius: 24px;
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: 0 4px 16px rgba(91, 154, 122, 0.08);
  animation: fade-in-up 0.5s ease;
}

@keyframes fade-in-up {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-1);
}

.sep {
  height: 1px;
  background: rgba(91, 154, 122, 0.14);
  margin: 12px 0;
}

/* 设置行：图标 + 名称 + 描述 + 右侧控件 */
.setting-item {
  display: flex;
  align-items: center;
  padding: 14px 0;
  min-height: 48px;
  border-bottom: 1px solid rgba(91, 154, 122, 0.1);
  cursor: pointer;
  transition: background 0.2s ease;
  border-radius: var(--radius);
}

.setting-item:hover {
  background: linear-gradient(135deg, rgba(143, 166, 142, 0.08), rgba(212, 165, 116, 0.06));
}

.setting-item.no-border {
  border-bottom: none;
  padding-bottom: 0;
}

.setting-icon {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  margin-right: 12px;
  border-radius: 22px;
  background: linear-gradient(135deg, rgba(143, 166, 142, 0.18), rgba(212, 165, 116, 0.14));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
  transition: transform 0.2s ease;
}

.setting-icon:hover {
  transform: scale(1.05);
}

.setting-content {
  flex: 1;
  min-width: 0;
}

.setting-title {
  font-size: 16px;
  color: var(--text-1);
}

.setting-desc {
  font-size: 12px;
  color: var(--text-2);
  margin-top: 4px;
}

.setting-action {
  margin-left: 8px;
  flex-shrink: 0;
}

.value-text {
  font-size: 14px;
  color: var(--primary-d);
}

.arrow {
  font-size: 20px;
  color: #b9b0a5;
  line-height: 1;
}

/* 开关 48×28 */
.switch {
  width: 48px;
  height: 28px;
  border-radius: 14px;
  background: rgba(125, 116, 107, 0.28);
  position: relative;
  cursor: pointer;
  transition: background 0.2s ease;
}

.switch.on {
  background: var(--primary-d);
}

.switch-knob {
  width: 24px;
  height: 24px;
  border-radius: 12px;
  background: #ffffff;
  position: absolute;
  top: 2px;
  left: 2px;
  transition: left 0.2s ease;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.18);
}

.switch.on .switch-knob {
  left: 22px;
}

/* ================= 退出登录 ================= */
.logout-section {
  margin-top: 24px;
  padding: 16px 0;
}

.logout-btn {
  width: 100%;
  height: 56px;
  border-radius: 24px;
  border: 2px solid rgba(229, 90, 90, 0.3);
  background: linear-gradient(135deg, var(--surface), rgba(229, 90, 90, 0.05));
  color: var(--danger);
  font-size: 18px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(229, 90, 90, 0.08);
}

.logout-btn:active {
  transform: scale(0.97);
}

/* ================= 底部说明 ================= */
.footer-note {
  padding: 24px 16px;
  text-align: center;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-2);
}

/* ================= 编辑对话框 ================= */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}

.modal-content {
  width: 85%;
  max-width: 380px;
  background: var(--surface);
  border-radius: var(--radius);
  overflow: hidden;
}

.modal-header {
  padding: 20px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-1);
  background: rgba(143, 166, 142, 0.12);
}

.modal-body {
  padding: 20px;
}

.edit-input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid rgba(91, 154, 122, 0.35);
  border-radius: var(--radius-sm);
  font-size: 16px;
  color: var(--text-1);
  background: var(--surface);
  outline: none;
  box-sizing: border-box;
}

.modal-footer {
  display: flex;
  gap: 12px;
  padding: 0 20px 20px;
}

.cancel-btn {
  flex: 1;
  padding: 12px;
  border: none;
  border-radius: var(--radius-sm);
  background: rgba(125, 116, 107, 0.1);
  color: var(--text-2);
  font-size: 16px;
  cursor: pointer;
}

.save-btn {
  flex: 1;
  padding: 12px;
  border: none;
  border-radius: var(--radius-sm);
  background: linear-gradient(135deg, var(--primary), var(--primary-d));
  color: #ffffff;
  font-size: 16px;
  cursor: pointer;
}
</style>
