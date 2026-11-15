<template>
  <view class="page">
    <!-- ① 筛选 Tab -->
    <view class="tabs">
      <view
        v-for="t in TABS"
        :key="t.value"
        class="tab-item"
        :class="{ active: activeType === t.value }"
        @click="switchType(t.value)">
        <text>{{ t.label }}</text>
      </view>
    </view>

    <!-- ② 统计条 -->
    <view class="summary">
      <view class="summary-item">
        <text class="summary-value">{{ filtered.length }}</text>
        <text class="summary-label">条记录</text>
      </view>
      <view class="summary-item">
        <text class="summary-value">{{ todayCount }}</text>
        <text class="summary-label">今日采集</text>
      </view>
      <view class="summary-item">
        <text class="summary-value">{{ abnormalCount }}</text>
        <text class="summary-label">异常提醒</text>
      </view>
    </view>

    <!-- ③ 列表 -->
    <scroll-view
      scroll-y
      class="list-scroll"
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore">
      <view class="list-inner">
        <EmptyState v-if="filtered.length === 0" icon="📊" text="暂无监测记录" desc="佩戴设备后数据会自动同步到这里" />

        <view v-for="(item, idx) in filtered" :key="item.id + '-' + idx" class="card record-card">
          <view class="record-head">
            <view class="record-icon">{{ typeIcon(item.type) }}</view>
            <view class="record-main">
              <view class="record-title-row">
                <text class="record-title">{{ typeText(item.type) }}</text>
                <text v-if="item.abnormal" class="tag tag-danger">异常</text>
                <text v-else class="tag">正常</text>
              </view>
              <text class="record-time">{{ item.time }}</text>
            </view>
            <view class="record-value">
              <text class="value-num">{{ item.value }}</text>
              <text class="value-unit">{{ item.unit }}</text>
            </view>
          </view>
          <text v-if="item.note" class="record-note">💬 {{ item.note }}</text>
        </view>

        <!-- 加载状态 -->
        <view v-if="filtered.length > 0" class="load-more">
          <text>{{ loadingMore ? '加载中...' : hasMore ? '上拉加载更多' : '— 没有更多记录了 —' }}</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 监测历史
 * ----------------------------------------------------------------------------
 * 筛选 Tab（全部 / 心率 / 步数 / 睡眠 / 血氧）+ 下拉刷新 + 触底加载。
 * 调用 monitorApi.history()，失败时使用本地兜底数据。
 */
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { monitorApi } from '@/services/api'
import EmptyState from '@/components/EmptyState.vue'

/** 监测类型 */
type MonitorType = 'heart' | 'steps' | 'sleep' | 'spo2'

interface RecordItem {
  id: string
  type: MonitorType
  value: number
  unit: string
  time: string
  abnormal?: boolean
  note?: string
}

/** 筛选 Tab 配置 */
const TABS = [
  { value: 'all', label: '全部' },
  { value: 'heart', label: '心率' },
  { value: 'steps', label: '步数' },
  { value: 'sleep', label: '睡眠' },
  { value: 'spo2', label: '血氧' }
]

/** 类型展示配置 */
const TYPE_MAP: Record<MonitorType, { icon: string; text: string; unit: string }> = {
  heart: { icon: '💓', text: '心率', unit: '次/分' },
  steps: { icon: '👣', text: '步数', unit: '步' },
  sleep: { icon: '🌙', text: '睡眠', unit: '小时' },
  spo2: { icon: '🫁', text: '血氧', unit: '%' }
}

/** 本地兜底数据：覆盖四种类型、含异常样本 */
function buildFallback(): RecordItem[] {
  const base: RecordItem[] = [
    { id: 'm-1', type: 'heart', value: 72, unit: '次/分', time: '今天 08:15', note: '静息心率正常' },
    { id: 'm-2', type: 'steps', value: 6820, unit: '步', time: '今天 20:30', note: '已完成今日目标 85%' },
    { id: 'm-3', type: 'spo2', value: 97, unit: '%', time: '今天 07:50' },
    { id: 'm-4', type: 'sleep', value: 7.5, unit: '小时', time: '今天 06:42', note: '深睡占比 32%' },
    { id: 'm-5', type: 'heart', value: 118, unit: '次/分', time: '昨天 21:05', abnormal: true, note: '运动后心率偏高，已恢复' },
    { id: 'm-6', type: 'spo2', value: 92, unit: '%', time: '昨天 03:20', abnormal: true, note: '夜间血氧偏低，建议复测' },
    { id: 'm-7', type: 'steps', value: 5240, unit: '步', time: '昨天 20:10' },
    { id: 'm-8', type: 'sleep', value: 6.2, unit: '小时', time: '昨天 06:10', note: '睡眠时长略短' },
    { id: 'm-9', type: 'heart', value: 68, unit: '次/分', time: '前天 09:00' },
    { id: 'm-10', type: 'steps', value: 8110, unit: '步', time: '前天 21:15', note: '超额完成目标' },
    { id: 'm-11', type: 'spo2', value: 98, unit: '%', time: '前天 07:30' },
    { id: 'm-12', type: 'sleep', value: 8.1, unit: '小时', time: '前天 07:05', note: '睡眠充足' }
  ]
  return base
}

const allRecords = ref<RecordItem[]>([])
const activeType = ref('all')
const page = ref(1)
const pageSize = 20
const hasMore = ref(true)
const refreshing = ref(false)
const loadingMore = ref(false)

/** 兜底数据是否已启用 */
const usingFallback = ref(false)

/** 按类型过滤 */
const filtered = computed(() => {
  if (activeType.value === 'all') return allRecords.value
  return allRecords.value.filter((r) => r.type === activeType.value)
})

/** 今日采集条数 */
const todayCount = computed(() => allRecords.value.filter((r) => r.time.includes('今天')).length)

/** 异常条数 */
const abnormalCount = computed(() => allRecords.value.filter((r) => r.abnormal).length)

function typeIcon(type: MonitorType): string {
  return TYPE_MAP[type]?.icon || '📊'
}

function typeText(type: MonitorType): string {
  return TYPE_MAP[type]?.text || '监测'
}

/** 把后端返回的任意结构归一化成 RecordItem */
function normalize(raw: unknown, index: number): RecordItem {
  const o = (raw || {}) as Record<string, unknown>
  const rawType = String(o.type ?? o.monitorType ?? '')
  const type: MonitorType = (['heart', 'steps', 'sleep', 'spo2'] as const).includes(rawType as MonitorType)
    ? (rawType as MonitorType)
    : /heart|心率/i.test(rawType)
      ? 'heart'
      : /step|步/i.test(rawType)
        ? 'steps'
        : /sleep|睡/i.test(rawType)
          ? 'sleep'
          : 'spo2'
  const value = Number(o.value ?? o.data ?? 0)
  return {
    id: String(o.id ?? 'r-' + index),
    type,
    value,
    unit: String(o.unit ?? TYPE_MAP[type].unit),
    time: String(o.time ?? o.createTime ?? o.occurredAt ?? '时间未知'),
    abnormal: Boolean(o.abnormal ?? o.isAbnormal ?? false),
    note: o.note ? String(o.note) : undefined
  }
}

/** 加载数据 */
async function loadPage(reset = false) {
  if (reset) {
    page.value = 1
    hasMore.value = true
  }

  try {
    const res = await monitorApi.history(page.value, pageSize)
    const rows = ((res?.list || []) as unknown[]).map((r, i) => normalize(r, i))
    if (rows.length > 0) {
      allRecords.value = reset ? rows : allRecords.value.concat(rows)
      usingFallback.value = false
      hasMore.value = rows.length >= pageSize
    } else if (reset) {
      allRecords.value = buildFallback()
      usingFallback.value = true
      hasMore.value = false
    }
  } catch {
    // 接口失败：首次加载用兜底数据，加载更多则直接结束
    if (reset) {
      allRecords.value = buildFallback()
      usingFallback.value = true
    }
    hasMore.value = false
  }
}

/** 切换筛选 */
function switchType(type: string) {
  activeType.value = type
}

/** 下拉刷新 */
async function onRefresh() {
  refreshing.value = true
  await loadPage(true)
  refreshing.value = false
  uni.showToast({ title: '已刷新', icon: 'none' })
}

/** 触底加载 */
async function loadMore() {
  if (loadingMore.value || !hasMore.value || usingFallback.value) return
  loadingMore.value = true
  page.value += 1
  await loadPage(false)
  loadingMore.value = false
}

onLoad(() => {
  loadPage(true)
})
</script>

<style lang="scss" scoped>
/* ---------- Tab ---------- */
.tabs {
  display: flex;
  gap: 12rpx;
  padding: 20rpx 24rpx;
  background: var(--bg);
  position: sticky;
  top: 0;
  z-index: 10;
}

.tab-item {
  flex: 1;
  height: 68rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-full);
  background: var(--surface);
  font-size: 26rpx;
  color: var(--text-2);
}

.tab-item.active {
  background: var(--primary);
  color: #ffffff;
  font-weight: 600;
}

/* ---------- 统计 ---------- */
.summary {
  display: flex;
  padding: 0 24rpx 20rpx;
}

.summary-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20rpx 0;
  background: var(--surface);
  border-radius: var(--radius);
  margin-right: 16rpx;
}

.summary-item:last-child {
  margin-right: 0;
}

.summary-value {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--primary-d);
}

.summary-label {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: var(--text-2);
}

/* ---------- 列表 ---------- */
.list-scroll {
  height: calc(100vh - 220rpx);
}

.list-inner {
  padding: 0 24rpx 48rpx;
}

.record-card {
  padding: 24rpx;
}

.record-head {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.record-icon {
  width: 78rpx;
  height: 78rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  background: var(--primary-l);
  font-size: 38rpx;
}

.record-main {
  flex: 1;
  min-width: 0;
}

.record-title-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.record-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-1);
}

.record-time {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: var(--text-3);
}

.record-value {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
}

.value-num {
  font-size: 42rpx;
  font-weight: 700;
  color: var(--text-1);
}

.value-unit {
  font-size: 22rpx;
  color: var(--text-2);
}

.record-note {
  display: block;
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 2rpx solid var(--divider);
  font-size: 24rpx;
  color: var(--text-2);
}

.load-more {
  padding: 32rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: var(--text-3);
}
</style>
