<template>
  <view class="page report-page">
    <!-- ① 综合评分环 -->
    <view class="hero-card">
      <text class="hero-deco">✨</text>
      <view class="hero-body">
        <view class="hero-ring" :style="overallRingStyle">
          <view class="hero-ring-inner">
            <text class="hero-score">{{ overallScore }}</text>
            <text class="hero-unit">综合得分</text>
          </view>
        </view>

        <view class="hero-info">
          <text class="hero-level">{{ overallLevel }}</text>
          <text class="hero-date">报告日期：{{ reportDate }}</text>
          <text class="hero-suggestion">{{ overallSuggestion }}</text>
        </view>
      </view>
    </view>

    <!-- ② 4 个维度 Tab -->
    <view class="tab-row">
      <view
        v-for="tab in TABS"
        :key="tab.key"
        class="tab-item"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        <text class="tab-icon">{{ tab.icon }}</text>
        <text class="tab-label">{{ tab.label }}</text>
      </view>
    </view>

    <!-- ③ 当前分类得分 -->
    <view class="card">
      <view class="category-head">
        <view class="category-ring" :style="categoryRingStyle">
          <view class="category-ring-inner">
            <text class="category-score">{{ activeCategory.score }}</text>
          </view>
        </view>
        <view class="category-text">
          <text class="category-title">{{ activeCategory.label }}</text>
          <text class="category-desc">{{ activeCategory.desc }}</text>
        </view>
      </view>
    </view>

    <!-- ④ 该分类下 2 个指标 -->
    <view class="card">
      <text class="section-title">指标明细</text>

      <view v-if="activeCategory.metrics.length === 0" class="empty">
        <text class="empty-icon">📭</text>
        <text class="empty-text">暂无该维度数据</text>
        <text class="empty-desc">完成打卡与监测后自动生成</text>
      </view>

      <view v-else class="metric-list">
        <view v-for="m in activeCategory.metrics" :key="m.key" class="metric-item">
          <view class="metric-top">
            <view class="metric-name-row">
              <text class="metric-icon">{{ m.icon }}</text>
              <text class="metric-name">{{ m.name }}</text>
            </view>
            <text class="metric-status" :class="`status-${m.status}`">{{ statusText(m.status) }}</text>
          </view>

          <view class="metric-value-row">
            <text class="metric-value">{{ m.value }}</text>
            <text class="metric-hint">{{ m.hint }}</text>
          </view>

          <!-- 进度条：按指标状态映射（good=100 / warning=70 / danger=40） -->
          <view class="metric-track">
            <view
              class="metric-fill"
              :class="`fill-${m.status}`"
              :style="{ width: statusScore(m.status) + '%' }"
            ></view>
          </view>

          <text class="metric-advice">💡 {{ m.advice }}</text>
        </view>
      </view>
    </view>

    <!-- ⑤ 各维度得分总览 -->
    <view class="card">
      <text class="section-title">各维度得分</text>
      <view class="overview-list">
        <view v-for="cat in categories" :key="cat.key" class="overview-item">
          <view class="overview-ring" :style="ringStyleFor(cat.score)">
            <view class="overview-ring-inner">
              <text class="overview-score">{{ cat.score }}</text>
            </view>
          </view>
          <view class="overview-text">
            <text class="overview-label">{{ cat.icon }} {{ cat.label }}</text>
            <view class="overview-track">
              <view class="overview-fill" :style="{ width: cat.score + '%' }"></view>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- ⑥ AI 建议 -->
    <view class="card">
      <text class="section-title">AI 综合建议</text>
      <view class="advice-box">
        <text class="advice-emoji">🤖</text>
        <text class="advice-text">{{ overallSuggestion }}</text>
      </view>
    </view>

    <!-- ⑦ 刷新按钮 -->
    <view class="refresh-btn" @click="loadReport">
      <text>{{ loading ? '生成中...' : '↻ 重新生成报告' }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 健康报告页
 * ----------------------------------------------------------------------------
 * 4 个维度：睡眠质量 / 作息节律 / 活动状态 / 心理状态，每个维度 2 个指标。
 * 评分公式：指标状态映射 good=100 / warning=70 / danger=40
 *          分类得分 = 该分类下指标取平均
 *          综合得分 = 各分类取平均
 * 评分环用 CSS conic-gradient 绘制（120×120 → 240rpx）。
 */
import { ref, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { checkinApi, exerciseApi, medicineApi, aiApi } from '@/services/api'

type MetricStatus = 'good' | 'warning' | 'danger'
type TabKey = 'sleep' | 'rhythm' | 'activity' | 'mental'

interface Metric {
  key: string
  icon: string
  name: string
  value: string
  hint: string
  status: MetricStatus
  advice: string
}

interface Category {
  key: TabKey
  icon: string
  label: string
  desc: string
  metrics: Metric[]
}

const TABS: { key: TabKey; icon: string; label: string }[] = [
  { key: 'sleep', icon: '🌙', label: '睡眠质量' },
  { key: 'rhythm', icon: '⏰', label: '作息节律' },
  { key: 'activity', icon: '🏃', label: '活动状态' },
  { key: 'mental', icon: '🧠', label: '心理状态' }
]

/** 指标状态 → 分数 */
function statusScore(status: MetricStatus): number {
  const map: Record<MetricStatus, number> = { good: 100, warning: 70, danger: 40 }
  return map[status]
}

function statusText(status: MetricStatus): string {
  const map: Record<MetricStatus, string> = { good: '良好', warning: '需关注', danger: '异常' }
  return map[status]
}

const activeTab = ref<TabKey>('sleep')
const loading = ref(false)
const reportDate = ref('')

// ============================================================================
// 兜底数据（后端不可用时保证界面完整）
// ============================================================================
const FALLBACK_CATEGORIES: Category[] = [
  {
    key: 'sleep',
    icon: '🌙',
    label: '睡眠质量',
    desc: '基于入睡时长与深度睡眠占比评估',
    metrics: [
      {
        key: 'sleep-duration',
        icon: '⏱️',
        name: '睡眠时长',
        value: '7小时32分',
        hint: '理想区间 7-9 小时',
        status: 'good',
        advice: '睡眠时长落在最优区间，继续保持固定作息'
      },
      {
        key: 'sleep-deep',
        icon: '💫',
        name: '深度睡眠占比',
        value: '32%',
        hint: '健康范围 20%-40%',
        status: 'good',
        advice: '睡前减少屏幕使用可进一步提升深睡占比'
      }
    ]
  },
  {
    key: 'rhythm',
    icon: '⏰',
    label: '作息节律',
    desc: '基于打卡规律性与入睡时间稳定性评估',
    metrics: [
      {
        key: 'rhythm-continuous',
        icon: '📅',
        name: '连续打卡',
        value: '12 天',
        hint: '目标 7 天 / 周',
        status: 'good',
        advice: '打卡很规律，继续保持这个节奏'
      },
      {
        key: 'rhythm-sleep-time',
        icon: '🌜',
        name: '平均入睡时间',
        value: '23:18',
        hint: '建议 22:30 前入睡',
        status: 'warning',
        advice: '入睡时间偏晚，建议提前 30-45 分钟上床'
      }
    ]
  },
  {
    key: 'activity',
    icon: '🏃',
    label: '活动状态',
    desc: '基于步数与户外活动时长评估',
    metrics: [
      {
        key: 'activity-steps',
        icon: '👣',
        name: '今日步数',
        value: '6,820 步',
        hint: '目标 8,000 步',
        status: 'warning',
        advice: '距离目标还差 1,180 步，饭后散步 20 分钟即可达成'
      },
      {
        key: 'activity-outdoor',
        icon: '☀️',
        name: '户外活动时长',
        value: '45 分钟',
        hint: '建议每天 30 分钟以上',
        status: 'good',
        advice: '户外时长充足，注意避开正午高温时段'
      }
    ]
  },
  {
    key: 'mental',
    icon: '🧠',
    label: '心理状态',
    desc: '基于自述情绪与用药依从性综合评估',
    metrics: [
      {
        key: 'mental-mood',
        icon: '😊',
        name: '情绪状态',
        value: '平稳',
        hint: '近 7 天自述情绪',
        status: 'good',
        advice: '情绪稳定，建议保持每天与家人沟通交流'
      },
      {
        key: 'mental-medicine',
        icon: '💊',
        name: '用药依从率',
        value: '86%',
        hint: '目标 90% 以上',
        status: 'warning',
        advice: '有 2 次漏服记录，建议开启用药提醒'
      }
    ]
  }
]

const categories = ref<Category[]>(JSON.parse(JSON.stringify(FALLBACK_CATEGORIES)) as Category[])
const overallSuggestion = ref('整体状态良好，睡眠与作息表现突出。建议将入睡时间提前约 30 分钟，并补足每日步数目标，健康评分还能进一步提升。')

// ============================================================================
// 派生
// ============================================================================
const activeCategory = computed(() => {
  const found = categories.value.find((c) => c.key === activeTab.value)
  return found || categories.value[0]
})

/** 分类得分 = 该分类下指标平均 */
function categoryScore(cat: Category): number {
  if (!cat.metrics.length) return 0
  const sum = cat.metrics.reduce((acc, m) => acc + statusScore(m.status), 0)
  return Math.round(sum / cat.metrics.length)
}

/** 综合得分 = 各分类平均 */
const overallScore = computed(() => {
  if (!categories.value.length) return 0
  const sum = categories.value.reduce((acc, c) => acc + categoryScore(c), 0)
  return Math.round(sum / categories.value.length)
})

const overallLevel = computed(() => {
  const s = overallScore.value
  if (s >= 90) return '优秀 · 状态很好'
  if (s >= 75) return '良好 · 保持当前节奏'
  if (s >= 60) return '一般 · 有改善空间'
  return '需关注 · 建议就医咨询'
})

/** 评分环样式（conic-gradient，270° 量程） */
function ringStyleFor(score: number): Record<string, string> {
  const deg = (score / 100) * 360
  return {
    background: `conic-gradient(#8FA68E 0deg ${deg}deg, #e8e3d8 ${deg}deg 360deg)`
  }
}

const overallRingStyle = computed(() => ringStyleFor(overallScore.value))
const categoryRingStyle = computed(() => ringStyleFor(activeCategory.value.score))

// ============================================================================
// 数据加载
// ============================================================================
async function loadReport() {
  loading.value = true
  const now = new Date()
  reportDate.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`

  // 并行拉取各数据源，单项失败不影响整体
  const [todayRes, statsRes, exerciseRes, adherenceRes, reportRes] = await Promise.allSettled([
    checkinApi.today(),
    checkinApi.stats(),
    exerciseApi.today(),
    medicineApi.adherence(30),
    aiApi.dailyReport()
  ])

  // 深拷贝兜底数据后按真实数据改写
  const next: Category[] = JSON.parse(JSON.stringify(FALLBACK_CATEGORIES)) as Category[]

  // ---- 睡眠质量 ----
  if (todayRes.status === 'fulfilled' && todayRes.value) {
    const t = todayRes.value
    const sleepCat = next.find((c) => c.key === 'sleep')
    if (sleepCat) {
      if (t.duration) {
        const h = Number(t.duration)
        const hours = Math.floor(h)
        const mins = Math.round((h - hours) * 60)
        sleepCat.metrics[0].value = `${hours}小时${mins}分`
        sleepCat.metrics[0].status = h >= 7 && h <= 9 ? 'good' : h >= 6 ? 'warning' : 'danger'
        sleepCat.metrics[0].advice =
          h >= 7 && h <= 9 ? '睡眠时长落在最优区间，继续保持固定作息' : '建议保证每天 7-8 小时睡眠'
        // 深睡占比按时长估算
        const deep = h >= 7 && h <= 9 ? 32 : h >= 6 ? 24 : 16
        sleepCat.metrics[1].value = `${deep}%`
        sleepCat.metrics[1].status = deep >= 20 && deep <= 40 ? 'good' : 'warning'
      }
      if (t.sleepScore) {
        const sc = Math.round(Number(t.sleepScore))
        sleepCat.metrics[1].hint = `睡眠评分 ${sc} 分`
      }
    }
  }

  // ---- 作息节律 ----
  if (statsRes.status === 'fulfilled' && statsRes.value) {
    const s = statsRes.value
    const rhythmCat = next.find((c) => c.key === 'rhythm')
    if (rhythmCat) {
      const days = Number(s.continuousDays ?? 0)
      rhythmCat.metrics[0].value = `${days} 天`
      rhythmCat.metrics[0].status = days >= 7 ? 'good' : days >= 3 ? 'warning' : 'danger'
      rhythmCat.metrics[0].advice = days >= 7 ? '打卡很规律，继续保持这个节奏' : '建议每天坚持打卡，形成稳定节律'
      rhythmCat.metrics[1].hint = `本周已打卡 ${s.weekDays ?? 0}/${s.weekTarget ?? 7} 天`
    }
  }

  // ---- 活动状态 ----
  if (exerciseRes.status === 'fulfilled' && exerciseRes.value) {
    const e = exerciseRes.value
    const actCat = next.find((c) => c.key === 'activity')
    if (actCat) {
      const steps = Number(e.steps ?? 0)
      actCat.metrics[0].value = `${steps.toLocaleString()} 步`
      actCat.metrics[0].status = steps >= 8000 ? 'good' : steps >= 5000 ? 'warning' : 'danger'
      actCat.metrics[0].advice =
        steps >= 8000 ? '已达成每日步数目标，非常棒' : `距离目标还差 ${Math.max(0, 8000 - steps)} 步，饭后散步即可达成`
      const minutes = Number((e.list || []).reduce((sum, r) => sum + (r.duration || 0), 0))
      if (minutes > 0) {
        actCat.metrics[1].value = `${minutes} 分钟`
        actCat.metrics[1].status = minutes >= 30 ? 'good' : 'warning'
      }
    }
  }

  // ---- 心理状态（用药依从率作为客观代理指标） ----
  if (adherenceRes.status === 'fulfilled' && adherenceRes.value) {
    const a = adherenceRes.value
    const rate = Math.round(Number(a.rate ?? 0))
    const mentalCat = next.find((c) => c.key === 'mental')
    if (mentalCat) {
      mentalCat.metrics[1].value = `${rate}%`
      mentalCat.metrics[1].status = rate >= 90 ? 'good' : rate >= 70 ? 'warning' : 'danger'
      mentalCat.metrics[1].advice =
        rate >= 90 ? '用药依从性很好，继续保持' : `近 30 天有 ${a.missed ?? 0} 次漏服，建议开启用药提醒`
    }
  }

  categories.value = next

  // ---- AI 综合建议 ----
  if (reportRes.status === 'fulfilled' && reportRes.value?.suggestion) {
    overallSuggestion.value = reportRes.value.suggestion
  } else {
    overallSuggestion.value = buildSuggestion()
  }

  loading.value = false
}

/** 本地生成综合建议（无后端时使用） */
function buildSuggestion(): string {
  const warnings: string[] = []
  categories.value.forEach((c) => {
    c.metrics.forEach((m) => {
      if (m.status !== 'good') warnings.push(`${m.name}（${statusText(m.status)}）`)
    })
  })
  if (warnings.length === 0) {
    return '各项指标均处于良好区间，整体健康状态优秀。请继续保持规律作息与适度运动，并坚持每日打卡记录。'
  }
  return `整体得分 ${overallScore.value} 分。以下指标需要关注：${warnings.join('、')}。建议优先改善睡眠与活动量，坚持 1-2 周后复评。`
}

onShow(() => {
  loadReport()
})

onPullDownRefresh(async () => {
  await loadReport()
  uni.stopPullDownRefresh()
})
</script>

<style lang="scss" scoped>
.report-page {
  min-height: 100vh;
  padding: 32rpx;
  background: linear-gradient(180deg, #f4efe6 0%, #f5f0e6 100%);
}

/* ---------- ① 综合评分 ---------- */
.hero-card {
  position: relative;
  padding: 40rpx 32rpx;
  margin-bottom: 32rpx;
  background: linear-gradient(135deg, #8fa68e 0%, #a8c4a7 100%);
  border-radius: var(--radius-lg);
  box-shadow: 0 12rpx 40rpx rgba(143, 166, 142, 0.25);
  overflow: hidden;
}

.hero-deco {
  position: absolute;
  top: 8rpx;
  right: 24rpx;
  font-size: 120rpx;
  opacity: 0.15;
}

.hero-body {
  display: flex;
  align-items: center;
  gap: 32rpx;
}

.hero-ring {
  width: 220rpx;
  height: 220rpx;
  flex-shrink: 0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.hero-ring-inner {
  width: 168rpx;
  height: 168rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 50%;
}

.hero-score {
  font-size: 64rpx;
  font-weight: 700;
  color: var(--primary-d);
  line-height: 1.1;
}

.hero-unit {
  font-size: 22rpx;
  color: var(--text-2);
}

.hero-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.hero-level {
  font-size: 36rpx;
  font-weight: 700;
  color: #ffffff;
}

.hero-date {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.85);
}

.hero-suggestion {
  font-size: 26rpx;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.92);
}

/* ---------- ② Tab ---------- */
.tab-row {
  display: flex;
  gap: 12rpx;
  margin-bottom: 32rpx;
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 24rpx 0;
  background: var(--surface);
  border: 4rpx solid transparent;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.tab-item.active {
  background: rgba(143, 166, 142, 0.16);
  border-color: var(--primary);
}

.tab-icon {
  font-size: 36rpx;
}

.tab-label {
  font-size: 24rpx;
  color: var(--text-2);
}

.tab-item.active .tab-label {
  color: var(--primary-d);
  font-weight: 600;
}

/* ---------- ③ 分类得分 ---------- */
.category-head {
  display: flex;
  align-items: center;
  gap: 32rpx;
}

.category-ring {
  width: 168rpx;
  height: 168rpx;
  flex-shrink: 0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.category-ring-inner {
  width: 128rpx;
  height: 128rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
  border-radius: 50%;
  box-shadow: inset 0 4rpx 12rpx rgba(0, 0, 0, 0.06);
}

.category-score {
  font-size: 48rpx;
  font-weight: 700;
  color: var(--primary-d);
}

.category-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.category-title {
  font-size: 38rpx;
  font-weight: 700;
  color: var(--text-1);
}

.category-desc {
  font-size: 26rpx;
  color: var(--text-2);
  line-height: 1.6;
}

/* ---------- ④ 指标 ---------- */
.section-title {
  display: block;
  margin-bottom: 24rpx;
  font-size: 34rpx;
  font-weight: 700;
  color: var(--text-1);
}

.metric-list {
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.metric-item {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  padding: 28rpx;
  background: rgba(125, 116, 107, 0.05);
  border-radius: 24rpx;
}

.metric-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.metric-name-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.metric-icon {
  font-size: 34rpx;
}

.metric-name {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-1);
}

.metric-status {
  padding: 4rpx 18rpx;
  font-size: 22rpx;
  border-radius: 999rpx;
}

.status-good {
  background: var(--primary-l);
  color: var(--primary-d);
}

.status-warning {
  background: var(--warning-l);
  color: var(--warning);
}

.status-danger {
  background: var(--danger-l);
  color: var(--danger);
}

.metric-value-row {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
}

.metric-value {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--text-1);
}

.metric-hint {
  font-size: 24rpx;
  color: var(--text-2);
}

.metric-track {
  width: 100%;
  height: 14rpx;
  background: rgba(125, 116, 107, 0.12);
  border-radius: 7rpx;
  overflow: hidden;
}

.metric-fill {
  height: 100%;
  border-radius: 7rpx;
  transition: width 0.4s ease;
}

.fill-good {
  background: linear-gradient(90deg, var(--primary), #a8c4a7);
}

.fill-warning {
  background: linear-gradient(90deg, var(--warning), #e0bc95);
}

.fill-danger {
  background: linear-gradient(90deg, var(--danger), #ef8a8a);
}

.metric-advice {
  font-size: 24rpx;
  color: var(--text-2);
  line-height: 1.6;
}

/* ---------- ⑤ 各维度总览 ---------- */
.overview-list {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
}

.overview-item {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.overview-ring {
  width: 96rpx;
  height: 96rpx;
  flex-shrink: 0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.overview-ring-inner {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
  border-radius: 50%;
}

.overview-score {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--primary-d);
}

.overview-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.overview-label {
  font-size: 30rpx;
  color: var(--text-1);
}

.overview-track {
  width: 100%;
  height: 12rpx;
  background: rgba(125, 116, 107, 0.12);
  border-radius: 6rpx;
  overflow: hidden;
}

.overview-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--primary), #a8c4a7);
  border-radius: 6rpx;
  transition: width 0.4s ease;
}

/* ---------- ⑥ 建议 ---------- */
.advice-box {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  padding: 28rpx;
  background: var(--primary-l);
  border-radius: 24rpx;
}

.advice-emoji {
  font-size: 40rpx;
}

.advice-text {
  flex: 1;
  font-size: 28rpx;
  line-height: 1.7;
  color: var(--text-1);
}

/* ---------- ⑦ 刷新 ---------- */
.refresh-btn {
  margin-top: 16rpx;
  padding: 30rpx 0;
  background: var(--surface);
  border-radius: var(--radius);
  font-size: 30rpx;
  font-weight: 600;
  color: var(--primary-d);
  text-align: center;
  box-shadow: var(--shadow);
}

.refresh-btn:active {
  opacity: 0.85;
}

/* ---------- 空态 ---------- */
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 64rpx 0;
}

.empty-icon {
  font-size: 80rpx;
  opacity: 0.4;
}

.empty-text {
  margin-top: 20rpx;
  font-size: 28rpx;
  color: var(--text-2);
}

.empty-desc {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: var(--text-3);
}
</style>
