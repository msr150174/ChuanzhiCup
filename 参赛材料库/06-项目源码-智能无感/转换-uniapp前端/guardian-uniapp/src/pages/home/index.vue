<template>
  <view class="page home-page tabbar-gap">
    <!-- ① 页面标题区 -->
    <view class="page-header">
      <view class="header-top">
        <view class="header-left">
          <text class="header-title">节律守护</text>
          <text class="header-date">{{ todayText }}</text>
        </view>
        <view class="reminder-btn" :class="{ on: reminderEnabled }" @click="openReminderDialog">
          <text class="reminder-icon">{{ reminderEnabled ? '🔔' : '🔕' }}</text>
          <text class="reminder-text">{{ reminderEnabled ? '提醒已开启' : '提醒已关闭' }}</text>
        </view>
      </view>
      <text class="header-sub">愿您拥有健康的睡眠</text>
    </view>

    <!-- ② 快捷打卡区 -->
    <view class="card-block">
      <view class="card">
        <text class="card-heading">快速打卡</text>
        <view class="checkin-row">
          <!-- 左：睡觉打卡 -->
          <view class="checkin-card" :class="{ sleeping: isSleeping }" @click="handleSleepCheckIn">
            <text class="checkin-emoji" :class="{ active: isSleeping }">🌙</text>
            <text class="checkin-name">{{ isSleeping ? '已睡觉' : '睡觉打卡' }}</text>
            <text class="checkin-desc">{{ isSleeping ? today.sleepTime : '点击打卡' }}</text>
          </view>

          <!-- 右：起床打卡 -->
          <view
            class="checkin-card"
            :class="{ woken: !!today.wakeTime, disabled: !today.sleepTime }"
            @click="handleWakeCheckIn"
          >
            <text class="checkin-emoji" :class="{ warn: !!today.wakeTime }">☀️</text>
            <text class="checkin-name">{{ today.wakeTime ? '已起床' : '起床打卡' }}</text>
            <text class="checkin-desc">
              {{ today.wakeTime ? today.wakeTime : (today.sleepTime ? '点击打卡' : '请先睡觉') }}
            </text>
          </view>
        </view>

        <view v-if="today.wakeTime" class="round-btn" @click="handleNewRound">
          <text>开始新一轮打卡</text>
        </view>
      </view>
    </view>

    <!-- ③ 打卡记录（最近 3 条） -->
    <view class="card-block">
      <view class="card">
        <view class="card-header-row">
          <text class="card-heading">打卡记录</text>
          <text class="link-more" @click="goPage('/pages/health-report/index')">查看全部 ›</text>
        </view>

        <view v-if="recentRecords.length === 0" class="empty-state">
          <text class="empty-icon">🕐</text>
          <text class="empty-text">暂无打卡记录 (´･ω･`)</text>
          <text class="empty-hint">点击上方按钮开始打卡</text>
        </view>

        <view v-else class="record-list">
          <view v-for="record in recentRecords" :key="record.date + (record.sleepTime || '')" class="record-item">
            <view class="record-left">
              <view class="record-avatar"><text>🌙</text></view>
              <view class="record-info">
                <text class="record-date">{{ record.date }}</text>
                <text class="record-time">入睡: {{ record.sleepTime || '--:--' }}</text>
              </view>
            </view>
            <view class="record-right">
              <template v-if="record.wakeTime">
                <text class="record-wake">☀️ {{ record.wakeTime }}</text>
                <text class="pill pill-success">{{ formatDuration(record.duration) }}</text>
              </template>
              <text v-else class="pill pill-warning">进行中</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- ④ 睡眠质量评分环 -->
    <view class="card-block">
      <view class="card score-card">
        <text class="deco deco-moon">🌙</text>
        <text class="deco deco-sparkles">✨</text>

        <view class="card-header-row">
          <text class="card-heading small">🌙 睡眠质量评分</text>
          <text class="score-tag">{{ sleepQualityText }}</text>
        </view>

        <view class="score-body">
          <!-- 纯 CSS 评分环：conic-gradient，270° 量程 -->
          <view class="score-ring" :style="ringStyle">
            <view class="ring-inner">
              <text class="score-num">{{ sleepScore }}</text>
              <text class="score-unit">分</text>
            </view>
          </view>

          <view class="score-detail">
            <view class="detail-row">
              <text class="detail-label">今日睡眠时长</text>
              <text class="detail-value">{{ sleepDurationText }}</text>
            </view>
            <view class="detail-row">
              <text class="detail-label">深度睡眠占比</text>
              <text class="detail-value">{{ deepSleepPercent }}% 💫</text>
            </view>
            <view class="detail-row">
              <text class="detail-label">建议</text>
              <text class="detail-value primary">{{ sleepAdvice }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- ⑤ 天气预报卡片 -->
    <view class="card-block">
      <view class="card weather-card">
        <view class="weather-top">
          <view class="weather-left">
            <text class="weather-emoji">⛅</text>
            <view class="weather-text">
              <text class="weather-name">{{ weather.text }}</text>
              <text class="weather-wind">{{ weather.wind }}</text>
            </view>
          </view>
          <view class="weather-right">
            <text class="weather-temp">{{ weather.temp }}°</text>
            <text class="weather-humidity">💧 {{ weather.humidity }}%</text>
          </view>
        </view>
        <text class="weather-footer">💡 {{ weather.suggestion }}</text>
      </view>
    </view>

    <!-- ⑥ 本周日历（今天前后各 3 天，共 7 天） -->
    <view class="card-block">
      <view class="card">
        <text class="card-heading small">📅 本周睡眠记录</text>
        <view class="week-row">
          <view v-for="day in weekDays" :key="day.dateStr" class="week-day">
            <text class="week-label">{{ day.weekLabel }}</text>
            <view class="week-date" :class="{ today: day.isToday, recorded: day.hasRecord && !day.isToday }">
              <text>{{ day.dayNumber }}</text>
            </view>
            <text v-if="day.hasRecord && !day.isToday" class="week-check">✓</text>
          </view>
        </view>
      </view>
    </view>

    <!-- ⑦ 今日活动 -->
    <view class="card-block">
      <view class="card">
        <text class="card-heading small">📈 今日活动 🏃</text>
        <view class="activity-grid">
          <view class="activity-cell">
            <text class="activity-emoji green">👣</text>
            <text class="activity-value">{{ activity.steps }}</text>
            <text class="activity-unit">步数</text>
          </view>
          <view class="activity-cell">
            <text class="activity-emoji orange">👣</text>
            <text class="activity-value">{{ activity.distance }}</text>
            <text class="activity-unit">公里</text>
          </view>
          <view class="activity-cell">
            <text class="activity-emoji red">💓</text>
            <text class="activity-value">{{ activity.calories }}</text>
            <text class="activity-unit">卡路里</text>
          </view>
          <view class="activity-cell">
            <text class="activity-emoji yellow">☀️</text>
            <text class="activity-value">{{ activity.outdoorMin }}</text>
            <text class="activity-unit">户外分钟</text>
          </view>
        </view>
        <view class="activity-goal">
          <view class="goal-row">
            <text class="goal-label">今日运动目标</text>
            <text class="goal-percent">已完成 {{ activityPercent }}%</text>
          </view>
          <view class="progress-track">
            <view class="progress-fill" :style="{ width: activityPercent + '%' }"></view>
          </view>
        </view>
      </view>
    </view>

    <!-- ⑧ 用药提醒 -->
    <view class="card-block">
      <view class="card">
        <view class="card-header-row">
          <text class="card-heading">用药提醒 💊</text>
          <text class="link-more" @click="goPage('/pages/medicine-reminder/index')">查看全部 ›</text>
        </view>

        <view v-if="medicineList.length === 0" class="empty-state">
          <text class="empty-icon">💊</text>
          <text class="empty-text">暂无用药提醒</text>
          <text class="empty-hint">前往用药提醒页添加</text>
        </view>

        <view v-else class="medicine-list">
          <view v-for="med in medicineList" :key="med.id" class="medicine-item">
            <view class="medicine-info">
              <view class="medicine-name-row">
                <text class="medicine-name">{{ med.name }}</text>
                <text class="pill" :class="med.taken ? 'pill-success' : 'pill-warning'">
                  {{ med.taken ? '已服用' : '待服用' }}
                </text>
              </view>
              <text class="medicine-desc">{{ med.frequency }} · {{ med.dosage }}</text>
              <text class="medicine-time">{{ med.time }}</text>
            </view>
            <view class="medicine-check" :class="{ taken: med.taken }" @click="handleMedicineCheck(med)">
              <text>✓</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- ⑨ AI 健康分析入口 -->
    <view class="card-block">
      <view class="card">
        <view class="card-header-row">
          <text class="card-heading">AI 健康分析</text>
          <text class="link-more" @click="goPage('/pages/ai-sleep-analysis/index')">详细分析 ›</text>
        </view>

        <view class="ai-conclusion">
          <text class="ai-emoji">✨</text>
          <view class="ai-text">
            <text class="ai-main">{{ aiConclusion }}</text>
            <text v-if="sleepDuration !== null" class="ai-sub">{{ aiSubText }}</text>
          </view>
        </view>

        <view class="ai-report-btn" @click="goPage('/pages/ai-sleep-analysis/index')">
          <text>点击获取详细 AI 分析报告</text>
        </view>
      </view>
    </view>

    <!-- ⑩ 打卡确认弹窗 -->
    <view v-if="showCheckDialog" class="dialog-mask" @click.self="showCheckDialog = false">
      <view class="dialog-box">
        <text class="dialog-title">确认打卡</text>
        <text class="dialog-message">{{ dialogMessage }}</text>
        <view class="dialog-actions">
          <view class="dialog-btn cancel" @click="showCheckDialog = false"><text>取消</text></view>
          <view class="dialog-btn confirm" @click="confirmCheckIn"><text>确认</text></view>
        </view>
      </view>
    </view>

    <!-- ⑪ 提醒设置弹窗 -->
    <view v-if="showReminderDialog" class="dialog-mask" @click.self="showReminderDialog = false">
      <view class="dialog-box">
        <view class="dialog-header-row">
          <text class="dialog-title">打卡提醒设置</text>
          <text class="dialog-close" @click="showReminderDialog = false">关闭</text>
        </view>

        <view class="switch-row" @click="toggleReminder">
          <text class="switch-label">开启提醒</text>
          <view class="switch" :class="{ on: reminderEnabled }">
            <view class="switch-knob"></view>
          </view>
        </view>

        <view class="reminder-row">
          <text class="reminder-label">睡觉提醒时间</text>
          <view class="reminder-values">
            <text class="reminder-chip">{{ reminderConfig.sleepTime }}</text>
            <text class="reminder-tip">提前</text>
            <text class="reminder-chip">{{ reminderConfig.sleepReminderMinutes }}分钟</text>
          </view>
        </view>

        <view class="reminder-row">
          <text class="reminder-label">起床提醒时间</text>
          <view class="reminder-values">
            <text class="reminder-chip">{{ reminderConfig.wakeTime }}</text>
            <text class="reminder-tip">提前</text>
            <text class="reminder-chip">{{ reminderConfig.wakeReminderMinutes }}分钟</text>
          </view>
        </view>

        <view class="dialog-test-row">
          <view class="dialog-test-btn" @click="testReminder('睡觉')"><text>测试睡觉提醒</text></view>
          <view class="dialog-test-btn" @click="testReminder('起床')"><text>测试起床提醒</text></view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 首页 —— 节律守护
 * ----------------------------------------------------------------------------
 * 核心是「睡眠打卡闭环」：未睡 → 睡觉打卡 → 睡中 → 起床打卡 → 已完成 → 新一轮
 * 按 VTJ 设计源码的 10 个区块还原，数据全部走真实接口，接口失败用兜底数据保证不空白。
 */
import { ref, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { checkinApi, weatherApi, medicineApi, exerciseApi } from '@/services/api'
import { useSettingsStore } from '@/stores/settings'
import type { TodayCheckin, CheckinRecord } from '@/types'

const settingsStore = useSettingsStore()

// ============================================================================
// 状态
// ============================================================================

/** 今日打卡（兜底：未打卡） */
const FALLBACK_TODAY: TodayCheckin = {
  date: '',
  sleepTime: null,
  wakeTime: null,
  sleepScore: null,
  duration: null,
  status: 'none'
}

const today = ref<TodayCheckin>({ ...FALLBACK_TODAY })

/** 打卡记录兜底（最近 3 天，让界面不空白） */
const FALLBACK_RECORDS: CheckinRecord[] = [
  { date: '昨天', sleepTime: '23:05', wakeTime: '06:30', duration: 7.42, sleepScore: 96, status: 'done' },
  { date: '前天', sleepTime: '22:50', wakeTime: '06:20', duration: 7.5, sleepScore: 96, status: 'done' },
  { date: '大前天', sleepTime: '23:35', wakeTime: '06:55', duration: 7.33, sleepScore: 96, status: 'done' }
]

const records = ref<CheckinRecord[]>([])

/** 天气兜底 */
const FALLBACK_WEATHER = { temp: 25, text: '晴', humidity: 60, wind: '微风', suggestion: '天气不错，适合户外散步 30 分钟' }
const weather = ref({ ...FALLBACK_WEATHER })

/** 今日活动兜底 */
const FALLBACK_ACTIVITY = { steps: 0, distance: 0, calories: 0, outdoorMin: 0 }
const activity = ref({ ...FALLBACK_ACTIVITY })

/** 用药提醒兜底 */
interface MedItem {
  id: string
  name: string
  dosage: string
  frequency: string
  time: string
  taken: boolean
}
const FALLBACK_MEDS: MedItem[] = [
  { id: 'med-1', name: '降压药', dosage: '1片', frequency: '早餐后', time: '08:00', taken: false },
  { id: 'med-2', name: '降糖药', dosage: '半片', frequency: '午餐前', time: '11:30', taken: false },
  { id: 'med-3', name: '钙片', dosage: '2片', frequency: '晚餐后', time: '19:00', taken: false }
]
const medicineList = ref<MedItem[]>([])

/** 提醒配置 */
const reminderEnabled = ref(settingsStore.settings.checkinReminder)
const reminderConfig = ref({
  sleepTime: '22:30',
  sleepReminderMinutes: 30,
  wakeTime: '06:30',
  wakeReminderMinutes: 15
})

/** 弹窗 */
const showCheckDialog = ref(false)
const showReminderDialog = ref(false)
const dialogMessage = ref('')
const pendingAction = ref<'' | 'sleep' | 'wake'>('')
const loading = ref(false)

/** 页面标题日期 */
const now = new Date()
const todayText = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日`

// ============================================================================
// 派生数据
// ============================================================================

/** 是否处于「已睡觉未起床」 */
const isSleeping = computed(() => !!today.value.sleepTime && !today.value.wakeTime)

/** 今日睡眠时长（小时，浮点） */
const sleepDuration = computed<number | null>(() => {
  if (today.value.duration !== null && today.value.duration !== undefined) {
    return today.value.duration
  }
  if (!today.value.sleepTime || !today.value.wakeTime) return null
  const [sh, sm] = today.value.sleepTime.split(':').map(Number)
  const [wh, wm] = today.value.wakeTime.split(':').map(Number)
  let mins = wh * 60 + wm - (sh * 60 + sm)
  if (mins < 0) mins += 24 * 60
  return mins / 60
})

/**
 * 睡眠评分（沿用设计稿分档公式）
 *   7-9 小时最优 → min(95 + (h-7)*2, 100)
 *   6-7 小时一般 → 70 + (h-6)*20
 *   > 9 小时过长 → 80
 *   < 6 小时不足 → max(50, h*10)
 */
const sleepScore = computed(() => {
  if (today.value.sleepScore !== null && today.value.sleepScore !== undefined) {
    return Math.round(today.value.sleepScore)
  }
  const h = sleepDuration.value
  if (!h) return 0
  if (h >= 7 && h <= 9) return Math.round(Math.min(95 + (h - 7) * 2, 100))
  if (h >= 6 && h < 7) return Math.round(70 + (h - 6) * 20)
  if (h > 9) return 80
  return Math.round(Math.max(50, h * 10))
})

/** 睡眠质量文案 */
const sleepQualityText = computed(() => {
  const h = sleepDuration.value
  if (!h) return '待测量'
  if (h >= 7 && h <= 9) return '良好'
  if (h >= 6 && h < 7) return '一般'
  if (h > 9) return '过长'
  return '不足'
})

/** 今日睡眠时长文案 */
const sleepDurationText = computed(() => {
  const h = sleepDuration.value
  if (h === null) return '--'
  const hours = Math.floor(h)
  const minutes = Math.round((h - hours) * 60)
  return `${hours}小时${minutes}分`
})

/** 深度睡眠占比（按时长 + 评分估算，可解释） */
const deepSleepPercent = computed(() => {
  const h = sleepDuration.value
  if (h === null) return 0
  const base = h >= 7 && h <= 9 ? 28 : h >= 6 ? 22 : 16
  const bonus = Math.round((sleepScore.value / 100) * 10)
  return Math.max(10, Math.min(40, base + bonus))
})

/** 建议文案随评分变化 */
const sleepAdvice = computed(() => {
  if (sleepDuration.value === null) return '先完成打卡再评估'
  if (sleepScore.value >= 95) return '保持规律作息'
  if (sleepScore.value >= 80) return '作息基本规律，可再早睡一点'
  return '建议提前入睡，保证 7-8 小时'
})

/** 评分环：conic-gradient 画进度弧，270° 量程 */
const ringStyle = computed(() => {
  const deg = (sleepScore.value / 100) * 270
  return {
    background: `conic-gradient(#8FA68E 0deg ${deg}deg, #e8e3d8 ${deg}deg 270deg, transparent 270deg 360deg)`
  }
})

/** 运动目标完成百分比（按 8000 步目标） */
const activityPercent = computed(() => {
  const target = 8000
  return Math.min(100, Math.round((activity.value.steps / target) * 100))
})

/** 最近 3 条记录，倒序 */
const recentRecords = computed(() => records.value.slice(0, 3))

/** 周历 7 天：今天前后各 3 天 */
const weekDays = computed(() => {
  const labels = ['日', '一', '二', '三', '四', '五', '六']
  const result: { dateStr: string; weekLabel: string; dayNumber: number; isToday: boolean; hasRecord: boolean }[] = []
  const base = new Date()

  for (let i = -3; i <= 3; i++) {
    const d = new Date(base)
    d.setDate(base.getDate() + i)
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    const dateStr = `${d.getFullYear()}-${mm}-${dd}`
    result.push({
      dateStr,
      weekLabel: labels[d.getDay()],
      dayNumber: d.getDate(),
      isToday: i === 0,
      // 今日有打卡，或历史记录里存在该日期
      hasRecord: (i === 0 && !!today.value.sleepTime) || records.value.some((r) => r.date === dateStr)
    })
  }
  return result
})

/** AI 结论文案 */
const aiConclusion = computed(() => {
  if (sleepDuration.value === null) return '完成睡眠打卡后，AI 将为您分析睡眠健康状况'
  return `根据您的睡眠数据分析，今日睡眠时长 ${sleepDuration.value.toFixed(1)} 小时，睡眠质量${sleepQualityText.value}`
})

const aiSubText = computed(() => {
  const h = sleepDuration.value
  if (h === null) return ''
  return h >= 7
    ? '睡眠时长充足，继续保持良好的作息习惯！'
    : '建议适当增加睡眠时间，保持每天 7-8 小时的睡眠时长 📋'
})

// ============================================================================
// 数据加载
// ============================================================================

/** 时长（小时）→ "7.5小时" */
function formatDuration(duration: number | null): string {
  if (duration === null || duration === undefined) return '--'
  return `${Number(duration).toFixed(1)}小时`
}

/** 「HH:mm」→ 今天的分钟数 */
function timeToMinutes(t: string): number {
  const [h, m] = t.split(':').map(Number)
  return h * 60 + m
}

/** 加载打卡数据 */
async function loadCheckin() {
  try {
    const data = await checkinApi.today()
    today.value = data || { ...FALLBACK_TODAY }
  } catch {
    today.value = { ...FALLBACK_TODAY }
  }

  try {
    const res = await checkinApi.records(7)
    records.value = (res?.list || []).slice().reverse()
    if (records.value.length === 0) records.value = [...FALLBACK_RECORDS]
  } catch {
    records.value = [...FALLBACK_RECORDS]
  }
}

/** 加载天气 */
async function loadWeather() {
  try {
    const data = await weatherApi.current()
    const d = data as Record<string, unknown>
    weather.value = {
      temp: Number(d.temp ?? FALLBACK_WEATHER.temp),
      text: String(d.text ?? d.weather ?? FALLBACK_WEATHER.text),
      humidity: Number(d.humidity ?? FALLBACK_WEATHER.humidity),
      wind: String(d.wind ?? FALLBACK_WEATHER.wind),
      suggestion: String(d.suggestion ?? FALLBACK_WEATHER.suggestion)
    }
  } catch {
    weather.value = { ...FALLBACK_WEATHER }
  }
}

/** 加载今日活动 */
async function loadActivity() {
  try {
    const data = await exerciseApi.today()
    activity.value = {
      steps: Number(data?.steps ?? 0),
      distance: Number((data?.list?.[0]?.distance as number) ?? 0),
      calories: Number(data?.calories ?? 0),
      outdoorMin: Number((data?.list || []).reduce((sum, r) => sum + (r.duration || 0), 0))
    }
  } catch {
    activity.value = { ...FALLBACK_ACTIVITY }
  }
}

/** 加载用药提醒（最多展示 3 条） */
async function loadMedicine() {
  try {
    const res = await medicineApi.reminders(true)
    const list = res?.list || []
    medicineList.value = list.slice(0, 3).map((m) => ({
      id: m.id,
      name: m.medicineName,
      dosage: m.dosage,
      frequency: m.frequency,
      time: (m.times && m.times[0]) || '--:--',
      taken: false
    }))
    if (medicineList.value.length === 0) medicineList.value = [...FALLBACK_MEDS]
  } catch {
    medicineList.value = [...FALLBACK_MEDS]
  }
}

/** 刷新全部 */
async function loadAll() {
  loading.value = true
  await Promise.all([loadCheckin(), loadWeather(), loadActivity(), loadMedicine()])
  loading.value = false
}

onShow(() => {
  loadAll()
})

onPullDownRefresh(async () => {
  await loadAll()
  uni.stopPullDownRefresh()
})

// ============================================================================
// 交互逻辑
// ============================================================================

function toast(msg: string) {
  uni.showToast({ title: msg, icon: 'none' })
}

/** 当前 HH:mm */
function nowHHmm(): string {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** 睡觉打卡：睡眠中禁止重复打卡 */
function handleSleepCheckIn() {
  if (isSleeping.value) {
    toast('当前正在睡觉中，请先完成起床打卡')
    return
  }
  pendingAction.value = 'sleep'
  dialogMessage.value = `确认现在开始睡觉打卡？\n时间：${nowHHmm()}`
  showCheckDialog.value = true
}

/** 起床打卡：必须先睡觉，且本轮未起过床 */
function handleWakeCheckIn() {
  if (!today.value.sleepTime) {
    toast('请先完成睡觉打卡')
    return
  }
  if (today.value.wakeTime) {
    toast('本轮已完成起床打卡，请先开始新一轮睡觉')
    return
  }
  const start = timeToMinutes(today.value.sleepTime)
  const end = timeToMinutes(nowHHmm())
  let mins = end - start
  if (mins < 0) mins += 24 * 60
  const duration = mins / 60
  pendingAction.value = 'wake'
  dialogMessage.value = `确认现在起床打卡？\n睡眠时长：${duration.toFixed(1)} 小时`
  showCheckDialog.value = true
}

/** 弹窗确认：调接口打卡，失败时本地先行更新保证界面可用 */
async function confirmCheckIn() {
  const action = pendingAction.value
  showCheckDialog.value = false
  pendingAction.value = ''
  if (!action) return

  const time = nowHHmm()
  try {
    const res = await checkinApi.check(action)
    const serverTime = res?.time || time
    if (action === 'sleep') {
      today.value = { ...today.value, sleepTime: serverTime, wakeTime: null, duration: null, status: 'sleeping' }
      toast('睡觉打卡成功，祝您好梦 🌙')
    } else {
      let mins = timeToMinutes(serverTime) - timeToMinutes(today.value.sleepTime || serverTime)
      if (mins < 0) mins += 24 * 60
      today.value = {
        ...today.value,
        wakeTime: serverTime,
        duration: mins / 60,
        status: 'done'
      }
      toast(`起床打卡成功，共睡眠 ${(mins / 60).toFixed(1)} 小时 ☀️`)
    }
    await loadCheckin()
  } catch {
    // 后端不通时本地兜底，保证「打卡闭环」可演示
    if (action === 'sleep') {
      today.value = { ...today.value, sleepTime: time, wakeTime: null, duration: null, status: 'sleeping' }
      toast('已本地记录睡觉打卡')
    } else {
      let mins = timeToMinutes(time) - timeToMinutes(today.value.sleepTime || time)
      if (mins < 0) mins += 24 * 60
      today.value = { ...today.value, wakeTime: time, duration: mins / 60, status: 'done' }
      toast('已本地记录起床打卡')
    }
  }
}

/** 开始新一轮 */
function handleNewRound() {
  today.value = { ...today.value, sleepTime: null, wakeTime: null, duration: null, sleepScore: null, status: 'none' }
  toast('新的一轮打卡开始啦，祝您今晚好梦～')
}

/** 用药打卡 */
async function handleMedicineCheck(med: MedItem) {
  if (med.taken) return
  med.taken = true
  try {
    await medicineApi.record({ reminderId: med.id, medicineName: med.name, status: 'taken' })
  } catch {
    // 接口失败也保留本地已服用的视觉反馈
  }
  toast('用药打卡成功')
}

/** 切换提醒开关（持久化到 settings store） */
function toggleReminder() {
  reminderEnabled.value = !reminderEnabled.value
  settingsStore.set('checkinReminder', reminderEnabled.value)
}

function openReminderDialog() {
  showReminderDialog.value = true
}

function testReminder(type: string) {
  toast(`已播放${type}提醒`)
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
.home-page {
  min-height: 100vh;
  background: var(--bg);
}

/* ---------- ① 页面标题区 ---------- */
.page-header {
  padding: 32rpx 32rpx 16rpx;
  display: flex;
  flex-direction: column;
}

.header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  flex-direction: column;
}

.header-title {
  font-size: 48rpx;
  font-weight: 700;
  color: var(--text-1);
}

.header-date {
  margin-top: 8rpx;
  font-size: 28rpx;
  color: var(--text-2);
}

.header-sub {
  margin-top: 12rpx;
  font-size: 30rpx;
  color: var(--primary);
}

.reminder-btn {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 14rpx 22rpx;
  border-radius: 24rpx;
  background: rgba(125, 116, 107, 0.1);
}

.reminder-btn.on {
  background: rgba(143, 166, 142, 0.2);
}

.reminder-icon {
  font-size: 34rpx;
  line-height: 1;
}

.reminder-text {
  font-size: 24rpx;
  font-weight: 500;
  color: var(--text-2);
}

.reminder-btn.on .reminder-text {
  color: var(--primary);
}

/* ---------- 卡片通用 ---------- */
.card-block {
  padding: 0 32rpx;
  margin-bottom: 28rpx;
}

.card-heading {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--text-1);
}

.card-heading.small {
  font-size: 32rpx;
}

.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.link-more {
  font-size: 26rpx;
  font-weight: 500;
  color: var(--primary);
}

/* ---------- ② 快捷打卡 ---------- */
.checkin-row {
  display: flex;
  gap: 24rpx;
  margin-top: 24rpx;
}

.checkin-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  padding: 32rpx 16rpx;
  background: rgba(125, 116, 107, 0.06);
  border: 4rpx solid transparent;
  border-radius: 28rpx;
  transition: all 0.25s ease;
}

.checkin-card.sleeping {
  background: rgba(143, 166, 142, 0.2);
  border-color: var(--primary);
}

.checkin-card.woken {
  background: rgba(212, 165, 116, 0.2);
  border-color: var(--warning);
}

.checkin-card.disabled {
  opacity: 0.5;
}

.checkin-card:active {
  transform: scale(0.98);
}

.checkin-emoji {
  font-size: 64rpx;
  line-height: 1.1;
  opacity: 0.6;
}

.checkin-emoji.active,
.checkin-emoji.warn {
  opacity: 1;
}

.checkin-name {
  font-size: 34rpx;
  font-weight: 600;
  color: var(--text-1);
}

.checkin-desc {
  font-size: 26rpx;
  color: var(--text-2);
}

.round-btn {
  width: 100%;
  margin-top: 24rpx;
  padding: 24rpx 0;
  background: var(--primary);
  border-radius: 24rpx;
  font-size: 32rpx;
  font-weight: 600;
  color: #ffffff;
  text-align: center;
}

.round-btn:active {
  opacity: 0.9;
  transform: scale(0.98);
}

/* ---------- ③ 打卡记录 ---------- */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  padding: 64rpx 0;
}

.empty-icon {
  font-size: 96rpx;
  opacity: 0.35;
}

.empty-text {
  font-size: 30rpx;
  color: var(--text-1);
}

.empty-hint {
  font-size: 26rpx;
  color: var(--text-2);
}

.record-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.record-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
  background: rgba(125, 116, 107, 0.05);
  border-radius: 24rpx;
}

.record-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.record-avatar {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  background: rgba(143, 166, 142, 0.2);
  border-radius: 50%;
}

.record-info {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.record-date {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-1);
}

.record-time {
  font-size: 26rpx;
  color: var(--text-2);
}

.record-right {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.record-wake {
  font-size: 26rpx;
  color: var(--text-2);
}

.pill {
  padding: 6rpx 20rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 600;
  white-space: nowrap;
}

.pill-success {
  background: rgba(143, 166, 142, 0.2);
  color: var(--primary-d);
}

.pill-warning {
  background: rgba(212, 165, 116, 0.2);
  color: var(--warning);
}

/* ---------- ④ 睡眠评分环 ---------- */
.score-card {
  position: relative;
  overflow: hidden;
}

.deco {
  position: absolute;
  opacity: 0.1;
}

.deco-moon {
  top: 8rpx;
  right: 24rpx;
  font-size: 140rpx;
}

.deco-sparkles {
  bottom: 8rpx;
  left: 16rpx;
  font-size: 110rpx;
}

.score-tag {
  padding: 8rpx 20rpx;
  font-size: 26rpx;
  color: var(--primary-d);
  background: rgba(143, 166, 142, 0.15);
  border-radius: 999rpx;
}

.score-body {
  display: flex;
  align-items: center;
}

/* 纯 CSS 评分环：外层 conic-gradient 进度弧，内层挖空 */
.score-ring {
  position: relative;
  width: 240rpx;
  height: 240rpx;
  flex-shrink: 0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.4s ease;
}

.ring-inner {
  width: 176rpx;
  height: 176rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #ffffff;
  border-radius: 50%;
  box-shadow: inset 0 4rpx 16rpx rgba(0, 0, 0, 0.06);
}

.score-num {
  font-size: 60rpx;
  font-weight: 700;
  color: var(--primary-d);
  line-height: 1.1;
}

.score-unit {
  font-size: 24rpx;
  color: var(--text-2);
}

.score-detail {
  flex: 1;
  margin-left: 36rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.detail-row {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.detail-label {
  font-size: 24rpx;
  color: var(--text-2);
}

.detail-value {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-1);
}

.detail-value.primary {
  color: var(--primary-d);
}

/* ---------- ⑤ 天气卡片 ---------- */
.weather-card {
  background: linear-gradient(135deg, #eef6ff 0%, #e2f0fb 100%);
}

.weather-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.weather-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.weather-emoji {
  font-size: 68rpx;
  line-height: 1;
}

.weather-text {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.weather-name {
  font-size: 38rpx;
  font-weight: 700;
  color: var(--text-1);
}

.weather-wind {
  font-size: 26rpx;
  color: var(--text-2);
}

.weather-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.weather-temp {
  font-size: 58rpx;
  font-weight: 700;
  color: var(--warning);
  line-height: 1.1;
}

.weather-humidity {
  font-size: 26rpx;
  color: var(--text-2);
}

.weather-footer {
  display: block;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 2rpx solid rgba(125, 116, 107, 0.15);
  font-size: 26rpx;
  color: var(--text-2);
}

/* ---------- ⑥ 本周日历 ---------- */
.week-row {
  display: flex;
  justify-content: space-between;
  margin-top: 24rpx;
}

.week-day {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
}

.week-label {
  font-size: 22rpx;
  color: var(--text-2);
}

.week-date {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text-1);
  background: rgba(125, 116, 107, 0.06);
  border-radius: 50%;
}

.week-date.today {
  background: var(--primary);
  color: #ffffff;
}

.week-date.recorded {
  background: rgba(143, 166, 142, 0.2);
  color: var(--primary-d);
}

.week-check {
  font-size: 22rpx;
  color: var(--primary);
}

/* ---------- ⑦ 今日活动 ---------- */
.activity-grid {
  display: flex;
  margin-top: 24rpx;
  margin-bottom: 28rpx;
}

.activity-cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}

.activity-emoji {
  font-size: 40rpx;
  line-height: 1;
}

.activity-value {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--text-1);
}

.activity-unit {
  font-size: 22rpx;
  color: var(--text-2);
}

.activity-goal {
  padding-top: 20rpx;
  border-top: 2rpx solid var(--divider);
}

.goal-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14rpx;
}

.goal-label {
  font-size: 26rpx;
  color: var(--text-2);
}

.goal-percent {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--primary-d);
}

.progress-track {
  width: 100%;
  height: 16rpx;
  background: rgba(125, 116, 107, 0.12);
  border-radius: 8rpx;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--primary), #9db89c);
  border-radius: 8rpx;
  transition: width 0.4s ease;
}

/* ---------- ⑧ 用药提醒 ---------- */
.medicine-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.medicine-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx;
  background: rgba(125, 116, 107, 0.05);
  border-radius: 24rpx;
}

.medicine-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.medicine-name-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.medicine-name {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-1);
}

.medicine-desc,
.medicine-time {
  font-size: 26rpx;
  color: var(--text-2);
}

.medicine-check {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  color: var(--text-3);
  background: rgba(125, 116, 107, 0.08);
  border-radius: 50%;
}

.medicine-check.taken {
  background: var(--primary);
  color: #ffffff;
}

/* ---------- ⑨ AI 分析 ---------- */
.ai-conclusion {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  padding: 24rpx;
  background: rgba(143, 166, 142, 0.1);
  border-radius: 24rpx;
}

.ai-emoji {
  font-size: 40rpx;
  line-height: 1.2;
}

.ai-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.ai-main {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-1);
  line-height: 1.6;
}

.ai-sub {
  font-size: 26rpx;
  color: var(--text-2);
  line-height: 1.6;
}

.ai-report-btn {
  margin-top: 28rpx;
  padding: 26rpx 0;
  background: linear-gradient(135deg, var(--primary), #9db89c);
  border-radius: 24rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #ffffff;
  text-align: center;
}

.ai-report-btn:active {
  opacity: 0.9;
}

/* ---------- 弹窗 ---------- */
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

.dialog-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}

.dialog-title {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--text-1);
}

.dialog-close {
  font-size: 26rpx;
  color: var(--text-2);
}

.dialog-message {
  margin-top: 20rpx;
  font-size: 28rpx;
  line-height: 1.8;
  color: var(--text-2);
  white-space: pre-wrap;
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
  background: var(--primary);
  color: #ffffff;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 0;
  border-bottom: 2rpx solid var(--divider);
}

.switch-label {
  font-size: 30rpx;
  color: var(--text-1);
}

.switch {
  width: 88rpx;
  height: 48rpx;
  padding: 4rpx;
  display: flex;
  align-items: center;
  background: rgba(125, 116, 107, 0.2);
  border-radius: 24rpx;
  transition: background 0.2s ease;
}

.switch.on {
  background: var(--primary);
}

.switch-knob {
  width: 40rpx;
  height: 40rpx;
  background: #ffffff;
  border-radius: 50%;
  transition: transform 0.2s ease;
}

.switch.on .switch-knob {
  transform: translateX(40rpx);
}

.reminder-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0;
  border-bottom: 2rpx solid var(--divider);
}

.reminder-label {
  font-size: 28rpx;
  color: var(--text-2);
}

.reminder-values {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.reminder-chip {
  padding: 6rpx 18rpx;
  font-size: 24rpx;
  color: var(--primary-d);
  background: var(--primary-l);
  border-radius: 999rpx;
}

.reminder-tip {
  font-size: 24rpx;
  color: var(--text-3);
}

.dialog-test-row {
  display: flex;
  gap: 20rpx;
  margin-top: 32rpx;
}

.dialog-test-btn {
  flex: 1;
  padding: 22rpx 0;
  background: rgba(143, 166, 142, 0.12);
  border-radius: 24rpx;
  font-size: 26rpx;
  color: var(--primary-d);
  text-align: center;
}
</style>
