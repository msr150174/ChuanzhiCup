<template>
  <view class="page sleep-page">
    <!-- ① 睡眠评分环 -->
    <view class="hero-card">
      <text class="hero-deco">🌙</text>
      <view class="hero-header">
        <text class="hero-title">AI 睡眠分析</text>
        <text class="hero-level" :class="`level-${levelKey}`">{{ level }}</text>
      </view>

      <view class="hero-body">
        <view class="score-ring" :style="ringStyle">
          <view class="ring-inner">
            <text class="score-num">{{ score }}</text>
            <text class="score-unit">分</text>
          </view>
        </view>

        <view class="hero-detail">
          <view class="detail-row">
            <text class="detail-label">睡眠时长</text>
            <text class="detail-value">{{ sleepHoursText }}</text>
          </view>
          <view class="detail-row">
            <text class="detail-label">入睡时间</text>
            <text class="detail-value">{{ sleepTime || '--:--' }}</text>
          </view>
          <view class="detail-row">
            <text class="detail-label">起床时间</text>
            <text class="detail-value">{{ wakeTime || '--:--' }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- ② 睡眠时长输入 -->
    <view class="card">
      <text class="card-title">📊 睡眠时长</text>
      <view class="slider-box">
        <view class="slider-value-row">
          <text class="slider-value">{{ sleepHours.toFixed(1) }}</text>
          <text class="slider-unit">小时</text>
        </view>
        <slider
          class="slider"
          :value="sleepHours * 10"
          :min="30"
          :max="120"
          :step="1"
          activeColor="#8FA68E"
          backgroundColor="#e8e3d8"
          block-size="24"
          @changing="onSliderChange"
          @change="onSliderChange"
        />
        <view class="slider-labels">
          <text>3h</text>
          <text>7h 最优</text>
          <text>12h</text>
        </view>
      </view>

      <view class="time-row">
        <view class="time-field">
          <text class="time-label">入睡时间</text>
          <picker mode="time" :value="sleepTime" @change="onSleepTimeChange">
            <view class="time-picker"><text>{{ sleepTime || '选择时间' }}</text></view>
          </picker>
        </view>
        <view class="time-field">
          <text class="time-label">起床时间</text>
          <picker mode="time" :value="wakeTime" @change="onWakeTimeChange">
            <view class="time-picker"><text>{{ wakeTime || '选择时间' }}</text></view>
          </picker>
        </view>
      </view>

      <view class="analyze-btn" :class="{ disabled: loading }" @click="handleAnalyze">
        <text>{{ loading ? '分析中...' : '✨ 开始 AI 分析' }}</text>
      </view>
    </view>

    <!-- ③ 睡眠结构 -->
    <view class="card">
      <text class="card-title">🌙 睡眠结构</text>
      <view class="structure-list">
        <view v-for="s in structure" :key="s.key" class="structure-item">
          <view class="structure-top">
            <view class="structure-name-row">
              <text class="structure-dot" :style="{ background: s.color }"></text>
              <text class="structure-name">{{ s.name }}</text>
            </view>
            <text class="structure-percent">{{ s.percent }}%</text>
          </view>
          <view class="structure-track">
            <view
              class="structure-fill"
              :style="{ width: s.percent + '%', background: s.color }"
            ></view>
          </view>
          <text class="structure-desc">{{ s.desc }}</text>
        </view>
      </view>
    </view>

    <!-- ④ 近 7 天睡眠趋势 -->
    <view class="card">
      <text class="card-title">📈 近 7 天睡眠趋势</text>
      <view class="trend-chart">
        <view v-for="item in trend" :key="item.date" class="trend-col">
          <text class="trend-hours">{{ item.hours }}</text>
          <view class="trend-bar-wrapper">
            <view
              class="trend-bar"
              :class="{ good: item.hours >= 7 && item.hours <= 9 }"
              :style="{ height: trendHeight(item.hours) + '%' }"
            ></view>
          </view>
          <text class="trend-label">{{ item.label }}</text>
        </view>
      </view>
    </view>

    <!-- ⑤ AI 分析结论 -->
    <view class="card">
      <text class="card-title">🤖 AI 分析结论</text>
      <view class="conclusion-box">
        <text class="conclusion-icon">✨</text>
        <text class="conclusion-text">{{ analysis }}</text>
      </view>

      <view class="suggestion-list">
        <view v-for="(s, i) in suggestions" :key="i" class="suggestion-item">
          <text class="suggestion-emoji">{{ s.icon }}</text>
          <text class="suggestion-text">{{ s.text }}</text>
        </view>
      </view>
    </view>

    <!-- ⑥ 相关入口 -->
    <view class="card">
      <text class="card-title">🔗 相关功能</text>
      <view class="link-list">
        <view class="link-item" @click="goPage('/pages/health-report/index')">
          <text>📈 查看完整健康报告</text>
          <text class="link-arrow">›</text>
        </view>
        <view class="link-item" @click="goPage('/pages/medicine-reminder/index')">
          <text>💊 用药提醒管理</text>
          <text class="link-arrow">›</text>
        </view>
        <view class="link-item" @click="goPage('/pages/ai-chat/index')">
          <text>🤖 和小护聊聊</text>
          <text class="link-arrow">›</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 睡眠分析页
 * ----------------------------------------------------------------------------
 * 滑块 / 时间选择器输入睡眠数据 → 调 aiApi.sleepAnalysis → 展示评分、结构、趋势、建议
 * 后端不可用时用本地分档公式兜底，保证页面信息完整。
 */
import { ref, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { aiApi, checkinApi } from '@/services/api'

const sleepHours = ref(7.5)
const sleepTime = ref('23:10')
const wakeTime = ref('06:42')
const score = ref(96)
const level = ref('良好')
const analysis = ref('根据您的睡眠数据，整体睡眠质量良好，时长落在 7-9 小时最优区间。')
const loading = ref(false)

/** 兜底建议 */
const FALLBACK_SUGGESTIONS = [
  { icon: '📈', text: '建议：保持规律作息，每天固定时间入睡起床' },
  { icon: '🧠', text: '建议：睡前避免剧烈运动，保持心情放松' },
  { icon: '💓', text: '建议：睡前可以听听轻音乐，帮助入睡' }
]
const suggestions = ref([...FALLBACK_SUGGESTIONS])

/** 近 7 天趋势兜底 */
const FALLBACK_TREND = [
  { date: 'd1', label: '周三', hours: 7.2 },
  { date: 'd2', label: '周四', hours: 6.8 },
  { date: 'd3', label: '周五', hours: 7.6 },
  { date: 'd4', label: '周六', hours: 8.4 },
  { date: 'd5', label: '周日', hours: 8.1 },
  { date: 'd6', label: '周一', hours: 7.4 },
  { date: 'd7', label: '今天', hours: 7.5 }
]
const trend = ref([...FALLBACK_TREND])

// ============================================================================
// 派生
// ============================================================================
const sleepHoursText = computed(() => {
  const h = sleepHours.value
  const hours = Math.floor(h)
  const mins = Math.round((h - hours) * 60)
  return `${hours}小时${mins}分`
})

const levelKey = computed(() => {
  const s = score.value
  if (s >= 90) return 'good'
  if (s >= 75) return 'normal'
  return 'poor'
})

/** 评分环（270° 量程，conic-gradient） */
const ringStyle = computed(() => {
  const deg = (score.value / 100) * 270
  return {
    background: `conic-gradient(#8FA68E 0deg ${deg}deg, #e8e3d8 ${deg}deg 270deg, transparent 270deg 360deg)`
  }
})

/** 睡眠结构（按总分与时长估算，可解释） */
const structure = computed(() => {
  const h = sleepHours.value
  const deep = Math.max(14, Math.min(38, Math.round(20 + (score.value / 100) * 14)))
  const light = Math.max(35, 70 - deep)
  const rem = Math.max(15, 100 - deep - light)
  const wake = Math.max(2, 100 - deep - light - rem)
  const total = deep + light + rem + wake
  return [
    { key: 'deep', name: '深睡', percent: Math.round((deep / total) * 100), color: '#5b9a7a', desc: `约 ${(h * deep / total).toFixed(1)} 小时，身体修复的关键阶段` },
    { key: 'light', name: '浅睡', percent: Math.round((light / total) * 100), color: '#8fa68e', desc: `约 ${(h * light / total).toFixed(1)} 小时，占比最高属正常` },
    { key: 'rem', name: '快速眼动', percent: Math.round((rem / total) * 100), color: '#d4a574', desc: `约 ${(h * rem / total).toFixed(1)} 小时，与记忆巩固相关` },
    { key: 'wake', name: '清醒', percent: Math.round((wake / total) * 100), color: '#e8e3d8', desc: '夜间短暂觉醒，5% 以内属正常' }
  ]
})

/** 趋势柱高度（12 小时为满格） */
function trendHeight(hours: number): number {
  return Math.max(8, Math.min(100, Math.round((hours / 12) * 100)))
}

function toast(msg: string) {
  uni.showToast({ title: msg, icon: 'none' })
}

// ============================================================================
// 本地评分兜底（沿用设计稿分档公式）
// ============================================================================
function localScore(h: number): number {
  if (h >= 7 && h <= 9) return Math.round(Math.min(95 + (h - 7) * 2, 100))
  if (h >= 6 && h < 7) return Math.round(70 + (h - 6) * 20)
  if (h > 9) return 80
  return Math.round(Math.max(50, h * 10))
}

function localLevel(h: number): string {
  if (h >= 7 && h <= 9) return '良好'
  if (h >= 6 && h < 7) return '一般'
  if (h > 9) return '过长'
  return '不足'
}

// ============================================================================
// 计算与加载
// ============================================================================
/** 根据入睡/起床时间推算时长 */
function calcHours(): number {
  if (!sleepTime.value || !wakeTime.value) return sleepHours.value
  const [sh, sm] = sleepTime.value.split(':').map(Number)
  const [wh, wm] = wakeTime.value.split(':').map(Number)
  let mins = wh * 60 + wm - (sh * 60 + sm)
  if (mins < 0) mins += 24 * 60
  return Math.round((mins / 60) * 10) / 10
}

function onSliderChange(e: { detail: { value: number } }) {
  sleepHours.value = Math.round(e.detail.value) / 10
}

function onSleepTimeChange(e: { detail: { value: string } }) {
  sleepTime.value = e.detail.value
  sleepHours.value = calcHours()
}

function onWakeTimeChange(e: { detail: { value: string } }) {
  wakeTime.value = e.detail.value
  sleepHours.value = calcHours()
}

/** 提交分析 */
async function handleAnalyze() {
  loading.value = true
  try {
    const res = await aiApi.sleepAnalysis(sleepHours.value, wakeTime.value, sleepTime.value)
    score.value = Math.round(Number(res?.score ?? localScore(sleepHours.value)))
    level.value = res?.level || localLevel(sleepHours.value)
    analysis.value = res?.suggestion || buildLocalAnalysis()
  } catch {
    // 接口不可用时用本地公式，保证结果可解释
    score.value = localScore(sleepHours.value)
    level.value = localLevel(sleepHours.value)
    analysis.value = buildLocalAnalysis()
  } finally {
    loading.value = false
  }
}

/** 本地生成分析结论文案 */
function buildLocalAnalysis(): string {
  const h = sleepHours.value
  if (h >= 7 && h <= 9) {
    return `根据您的睡眠数据分析，今日睡眠时长 ${h.toFixed(1)} 小时，落在 7-9 小时最优区间，睡眠质量${localLevel(h)}。深睡占比约 ${structure.value[0].percent}%，属于健康范围。`
  }
  if (h >= 6 && h < 7) {
    return `今日睡眠时长 ${h.toFixed(1)} 小时，略低于推荐的 7-8 小时。长期睡眠不足会影响免疫力与记忆力，建议今晚提前 30 分钟入睡。`
  }
  if (h > 9) {
    return `今日睡眠时长 ${h.toFixed(1)} 小时，睡眠时间偏长。过度睡眠可能与白天活动量不足有关，建议白天增加 30 分钟户外活动。`
  }
  return `今日睡眠时长仅 ${h.toFixed(1)} 小时，明显不足。建议调整作息，保证每天 7-8 小时睡眠，如持续失眠请及时咨询医生。`
}

/** 拉取真实打卡数据填充表单 */
async function loadData() {
  try {
    const today = await checkinApi.today()
    if (today?.sleepTime) sleepTime.value = today.sleepTime
    if (today?.wakeTime) wakeTime.value = today.wakeTime
    if (today?.duration) {
      sleepHours.value = Math.round(Number(today.duration) * 10) / 10
    } else {
      sleepHours.value = calcHours()
    }
  } catch {
    // 使用默认值（7.5 小时），不阻塞分析
  }

  // 近 7 天趋势
  try {
    const res = await checkinApi.records(7)
    const list = res?.list || []
    if (list.length > 0) {
      const labels = ['日', '一', '二', '三', '四', '五', '六']
      const next = list.slice(-7).map((r) => {
        const d = new Date(r.date)
        const isToday = r.date === new Date().toISOString().slice(0, 10)
        return {
          date: r.date,
          label: isToday ? '今天' : (Number.isNaN(d.getTime()) ? '--' : `周${labels[d.getDay()]}`),
          hours: Number(((r.duration as number) || 0).toFixed(1))
        }
      })
      if (next.some((n) => n.hours > 0)) trend.value = next
    }
  } catch {
    trend.value = [...FALLBACK_TREND]
  }

  handleAnalyze()
}

onShow(() => {
  loadData()
})

onPullDownRefresh(async () => {
  await loadData()
  uni.stopPullDownRefresh()
})

function goPage(path: string) {
  const isTab = path.indexOf('ai-chat') > -1
  if (isTab) {
    uni.switchTab({
      url: path,
      fail: () => toast('页面开发中')
    })
    return
  }
  uni.navigateTo({
    url: path,
    fail: () => toast('页面开发中')
  })
}
</script>

<style lang="scss" scoped>
.sleep-page {
  min-height: 100vh;
  padding: 32rpx;
  background: linear-gradient(180deg, #f4efe6 0%, #eef1ee 100%);
}

/* ---------- ① Hero ---------- */
.hero-card {
  position: relative;
  padding: 40rpx 32rpx;
  margin-bottom: 32rpx;
  background: linear-gradient(135deg, #5b7a94 0%, #7d9bb0 100%);
  border-radius: var(--radius-lg);
  box-shadow: 0 12rpx 40rpx rgba(91, 122, 148, 0.25);
  overflow: hidden;
}

.hero-deco {
  position: absolute;
  top: 8rpx;
  right: 24rpx;
  font-size: 140rpx;
  opacity: 0.15;
}

.hero-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 32rpx;
}

.hero-title {
  font-size: 38rpx;
  font-weight: 700;
  color: #ffffff;
}

.hero-level {
  padding: 6rpx 24rpx;
  font-size: 24rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.25);
  color: #ffffff;
}

.hero-body {
  display: flex;
  align-items: center;
  gap: 32rpx;
}

.score-ring {
  width: 220rpx;
  height: 220rpx;
  flex-shrink: 0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ring-inner {
  width: 168rpx;
  height: 168rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 50%;
}

.score-num {
  font-size: 64rpx;
  font-weight: 700;
  color: var(--primary-d);
  line-height: 1.1;
}

.score-unit {
  font-size: 22rpx;
  color: var(--text-2);
}

.hero-detail {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.detail-row {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.detail-label {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.8);
}

.detail-value {
  font-size: 32rpx;
  font-weight: 600;
  color: #ffffff;
}

/* ---------- ② 输入 ---------- */
.card-title {
  display: block;
  margin-bottom: 24rpx;
  font-size: 34rpx;
  font-weight: 600;
  color: var(--text-1);
}

.slider-box {
  padding: 24rpx 0;
}

.slider-value-row {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.slider-value {
  font-size: 72rpx;
  font-weight: 700;
  color: var(--primary-d);
  line-height: 1.1;
}

.slider-unit {
  font-size: 28rpx;
  color: var(--text-2);
}

.slider {
  width: 100%;
  margin: 16rpx 0;
}

.slider-labels {
  display: flex;
  justify-content: space-between;
  font-size: 22rpx;
  color: var(--text-3);
}

.time-row {
  display: flex;
  gap: 24rpx;
  margin-top: 24rpx;
}

.time-field {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.time-label {
  font-size: 26rpx;
  color: var(--text-2);
}

.time-picker {
  padding: 24rpx;
  background: rgba(125, 116, 107, 0.06);
  border-radius: 20rpx;
  font-size: 30rpx;
  color: var(--text-1);
  text-align: center;
}

.analyze-btn {
  margin-top: 32rpx;
  padding: 30rpx 0;
  background: linear-gradient(135deg, var(--primary), #9db89c);
  border-radius: 24rpx;
  font-size: 32rpx;
  font-weight: 600;
  color: #ffffff;
  text-align: center;
}

.analyze-btn.disabled {
  opacity: 0.5;
}

/* ---------- ③ 睡眠结构 ---------- */
.structure-list {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
}

.structure-item {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.structure-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.structure-name-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.structure-dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
}

.structure-name {
  font-size: 30rpx;
  color: var(--text-1);
}

.structure-percent {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--primary-d);
}

.structure-track {
  width: 100%;
  height: 16rpx;
  background: rgba(125, 116, 107, 0.12);
  border-radius: 8rpx;
  overflow: hidden;
}

.structure-fill {
  height: 100%;
  border-radius: 8rpx;
  transition: width 0.4s ease;
}

.structure-desc {
  font-size: 24rpx;
  color: var(--text-2);
}

/* ---------- ④ 趋势 ---------- */
.trend-chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 320rpx;
  padding-top: 24rpx;
}

.trend-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  height: 100%;
}

.trend-hours {
  font-size: 20rpx;
  color: var(--text-2);
}

.trend-bar-wrapper {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.trend-bar {
  width: 40rpx;
  background: linear-gradient(180deg, #c7d5c6, #a8bda7);
  border-radius: 12rpx 12rpx 4rpx 4rpx;
  transition: height 0.4s ease;
}

.trend-bar.good {
  background: linear-gradient(180deg, #9db89c, #5b9a7a);
}

.trend-label {
  font-size: 22rpx;
  color: var(--text-2);
}

/* ---------- ⑤ 结论 ---------- */
.conclusion-box {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  padding: 28rpx;
  background: var(--primary-l);
  border-radius: 24rpx;
  margin-bottom: 28rpx;
}

.conclusion-icon {
  font-size: 40rpx;
}

.conclusion-text {
  flex: 1;
  font-size: 28rpx;
  line-height: 1.7;
  color: var(--text-1);
}

.suggestion-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.suggestion-item {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  background: rgba(125, 116, 107, 0.05);
  border-radius: 20rpx;
}

.suggestion-emoji {
  font-size: 30rpx;
}

.suggestion-text {
  flex: 1;
  font-size: 26rpx;
  line-height: 1.6;
  color: var(--text-2);
}

/* ---------- ⑥ 相关入口 ---------- */
.link-list {
  display: flex;
  flex-direction: column;
}

.link-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 0;
  border-bottom: 2rpx solid var(--divider);
  font-size: 30rpx;
  color: var(--text-1);
}

.link-item:last-child {
  border-bottom: none;
}

.link-arrow {
  font-size: 40rpx;
  color: var(--text-3);
}
</style>
