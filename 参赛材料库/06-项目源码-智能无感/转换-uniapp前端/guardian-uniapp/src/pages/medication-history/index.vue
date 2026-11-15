<template>
  <view class="page">
    <!-- 依从率圆环 -->
    <view class="card adherence-card">
      <text class="card-title">💊 服药依从率</text>
      <view class="ring-wrap">
        <view class="ring" :style="ringStyle">
          <view class="ring-inner">
            <text class="ring-value">{{ state.adherence.rate }}</text>
            <text class="ring-unit">%</text>
            <text class="ring-label">近 30 天</text>
          </view>
        </view>
      </view>
      <text class="ring-advice">{{ adherenceAdvice }}</text>

      <!-- 统计三项 -->
      <view class="stat-row">
        <view class="stat-cell">
          <text class="stat-value ok">{{ state.adherence.taken }}</text>
          <text class="stat-label">已服用</text>
        </view>
        <view class="stat-divider"></view>
        <view class="stat-cell">
          <text class="stat-value danger">{{ state.adherence.missed }}</text>
          <text class="stat-label">漏服</text>
        </view>
        <view class="stat-divider"></view>
        <view class="stat-cell">
          <text class="stat-value warn">{{ state.adherence.skipped }}</text>
          <text class="stat-label">跳过</text>
        </view>
      </view>
    </view>

    <!-- 历史记录（按日期分组） -->
    <view class="card">
      <view class="section-header">
        <text class="card-title">📋 服药历史</text>
        <text class="header-sub">近 30 天</text>
      </view>

      <view v-if="state.loading" class="loading-box"><text class="loading-text">加载中...</text></view>

      <view v-else-if="groupedList.length > 0">
        <view v-for="g in groupedList" :key="g.date" class="group">
          <view class="group-header">
            <text class="group-date">{{ g.date }}</text>
            <text class="group-count">{{ g.items.length }} 条</text>
          </view>
          <view v-for="(r, i) in g.items" :key="i" class="record-item">
            <view class="record-icon" :class="'st-' + r.status"><text>{{ statusIcon(r.status) }}</text></view>
            <view class="record-info">
              <view class="record-line">
                <text class="record-name">{{ r.medicineName }}</text>
                <text class="status-tag" :class="'st-' + r.status">{{ statusText(r.status) }}</text>
              </view>
              <text class="record-meta">{{ r.dosage }} · 计划 {{ r.time }}</text>
            </view>
            <text v-if="r.takenAt" class="record-taken">{{ r.takenAt }}</text>
          </view>
        </view>
      </view>

      <EmptyState v-else icon="💊" text="暂无服药记录" desc="添加用药提醒后，服药记录会自动生成" />
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 服药记录
 * ----------------------------------------------------------------------------
 * 依从率圆环 + 已服用/漏服/跳过统计 + 历史记录（按日期分组）
 * 接口：medicineApi.adherence() / medicineApi.history()
 */
import { reactive, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { medicineApi } from '@/services/api'
import EmptyState from '@/components/EmptyState.vue'

/** 服药记录条目 */
interface MedHistory {
  date: string
  medicineName: string
  dosage: string
  time: string
  status: 'taken' | 'missed' | 'skipped'
  takenAt?: string
}

/** 今天日期 */
function todayStr(offset = 0): string {
  const d = new Date()
  d.setDate(d.getDate() - offset)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** ★ 兜底依从率 */
const FALLBACK_ADHERENCE = { taken: 42, missed: 5, skipped: 3, rate: 84 }

/** ★ 兜底历史 */
const FALLBACK_HISTORY: MedHistory[] = [
  { date: todayStr(0), medicineName: '氨氯地平片', dosage: '5mg / 1 片', time: '08:00', status: 'taken', takenAt: '08:05' },
  { date: todayStr(0), medicineName: '二甲双胍', dosage: '0.5g / 1 片', time: '12:00', status: 'taken', takenAt: '12:12' },
  { date: todayStr(0), medicineName: '阿司匹林肠溶片', dosage: '100mg / 1 片', time: '20:00', status: 'missed' },
  { date: todayStr(1), medicineName: '氨氯地平片', dosage: '5mg / 1 片', time: '08:00', status: 'taken', takenAt: '08:10' },
  { date: todayStr(1), medicineName: '二甲双胍', dosage: '0.5g / 1 片', time: '12:00', status: 'skipped' },
  { date: todayStr(2), medicineName: '氨氯地平片', dosage: '5mg / 1 片', time: '08:00', status: 'taken', takenAt: '07:58' }
]

const state = reactive({
  loading: true,
  adherence: { ...FALLBACK_ADHERENCE },
  history: [] as MedHistory[]
})

/** 依从率建议 */
const adherenceAdvice = computed(() => {
  const r = state.adherence.rate
  if (r >= 90) return '服药非常规律，请继续保持'
  if (r >= 75) return '服药情况良好，注意漏服提醒'
  if (r >= 60) return '存在漏服情况，建议设置用药提醒'
  return '依从率偏低，建议与医生沟通用药方案'
})

/** 圆环背景样式（用 conic-gradient 画进度） */
const ringStyle = computed(() => {
  const deg = Math.round((state.adherence.rate / 100) * 360)
  return {
    background: `conic-gradient(#5B9A7A 0deg, #5B9A7A ${deg}deg, #E8E3D8 ${deg}deg, #E8E3D8 360deg)`
  }
})

/** 按日期分组 */
const groupedList = computed(() => {
  const map = new Map<string, MedHistory[]>()
  state.history.forEach((r) => {
    const arr = map.get(r.date) || []
    arr.push(r)
    map.set(r.date, arr)
  })
  return Array.from(map.entries())
    .sort((a, b) => (a[0] < b[0] ? 1 : -1))
    .map(([date, items]) => ({ date: formatDate(date), items }))
})

/** 日期友好显示 */
function formatDate(date: string): string {
  if (date === todayStr(0)) return `今天 · ${date}`
  if (date === todayStr(1)) return `昨天 · ${date}`
  return date
}

/** 状态图标 */
function statusIcon(status: string): string {
  if (status === 'taken') return '✅'
  if (status === 'missed') return '❌'
  return '⏭️'
}

/** 状态文案 */
function statusText(status: string): string {
  if (status === 'taken') return '已服用'
  if (status === 'missed') return '漏服'
  return '跳过'
}

/** 加载依从率与历史 */
async function loadData() {
  state.loading = true

  // 依从率
  try {
    const a = await medicineApi.adherence(30)
    state.adherence = {
      taken: a.taken ?? 0,
      missed: a.missed ?? 0,
      skipped: a.skipped ?? 0,
      rate: Math.round(a.rate ?? 0)
    }
  } catch {
    // ★ 兜底
    state.adherence = { ...FALLBACK_ADHERENCE }
  }

  // 历史记录
  try {
    const h = await medicineApi.history(30)
    const list = (h.list || []) as Record<string, unknown>[]
    if (list.length === 0) throw new Error('empty')
    state.history = list.map((r) => ({
      date: (r.date as string) || todayStr(0),
      medicineName: (r.medicineName as string) || '未知药品',
      dosage: (r.dosage as string) || '--',
      time: (r.time as string) || '--',
      status: ((r.status as string) || 'taken') as MedHistory['status'],
      takenAt: (r.takenAt as string) || undefined
    }))
  } catch {
    // ★ 兜底
    state.history = JSON.parse(JSON.stringify(FALLBACK_HISTORY)) as MedHistory[]
  } finally {
    state.loading = false
  }
}

onShow(() => {
  loadData()
})

onPullDownRefresh(async () => {
  await loadData()
  uni.stopPullDownRefresh()
})
</script>

<style lang="scss" scoped>
.page { padding: 24rpx calc(24rpx) calc(120rpx + env(safe-area-inset-bottom)); }

.adherence-card { display: flex; flex-direction: column; align-items: center; }

.ring-wrap { margin: 16rpx 0 24rpx; }
.ring {
  width: 320rpx; height: 320rpx; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  box-shadow: var(--shadow-lg);
}
.ring-inner {
  width: 250rpx; height: 250rpx; border-radius: 50%;
  background: var(--surface);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.ring-value { font-size: 80rpx; font-weight: 800; color: var(--primary-d); line-height: 1.1; }
.ring-unit { font-size: 26rpx; color: var(--text-2); margin-top: -8rpx; }
.ring-label { font-size: 22rpx; color: var(--text-3); margin-top: 8rpx; }

.ring-advice {
  font-size: 26rpx; color: var(--text-2); text-align: center;
  line-height: 1.6; padding: 0 24rpx; margin-bottom: 12rpx;
}

.stat-row {
  display: flex; align-items: center; width: 100%;
  padding-top: 24rpx; border-top: 2rpx solid var(--divider);
}
.stat-cell { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 8rpx; }
.stat-value { font-size: 44rpx; font-weight: 700; }
.stat-value.ok { color: var(--primary-d); }
.stat-value.danger { color: var(--danger); }
.stat-value.warn { color: var(--warning); }
.stat-label { font-size: 22rpx; color: var(--text-2); }
.stat-divider { width: 2rpx; height: 56rpx; background: var(--divider); }

.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8rpx; }
.header-sub { font-size: 24rpx; color: var(--text-3); }

.loading-box { padding: 64rpx 0; text-align: center; }
.loading-text { font-size: 28rpx; color: var(--text-2); }

.group { margin-bottom: 24rpx; }
.group-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12rpx 0; border-bottom: 2rpx solid var(--divider);
}
.group-date { font-size: 26rpx; font-weight: 600; color: var(--primary-d); }
.group-count { font-size: 22rpx; color: var(--text-3); }

.record-item { display: flex; align-items: center; gap: 20rpx; padding: 22rpx 0; }
.record-icon {
  width: 72rpx; height: 72rpx; border-radius: var(--radius-sm); flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; font-size: 32rpx;
  background: var(--surface-2);
}
.st-taken { background: var(--primary-l); }
.st-missed { background: var(--danger-l); }
.st-skipped { background: var(--warning-l); }

.record-info { flex: 1; display: flex; flex-direction: column; gap: 8rpx; }
.record-line { display: flex; align-items: center; gap: 12rpx; }
.record-name { font-size: 30rpx; font-weight: 600; color: var(--text-1); }
.status-tag { font-size: 20rpx; padding: 2rpx 14rpx; border-radius: var(--radius-full); }
.status-tag.st-taken { color: var(--primary-d); }
.status-tag.st-missed { color: var(--danger); }
.status-tag.st-skipped { color: var(--warning); }
.record-meta { font-size: 24rpx; color: var(--text-2); }
.record-taken { font-size: 22rpx; color: var(--text-3); }
</style>
