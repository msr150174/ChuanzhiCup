<template>
  <view class="page">
    <view class="page-inner">
      <!-- ① 概览 -->
      <view class="card overview-card">
        <view class="flex-between">
          <view class="ov-left">
            <text class="ov-value">{{ logs.length }}</text>
            <text class="ov-label">条日志</text>
          </view>
          <view class="ov-right">
            <text class="ov-total">{{ sourceText }}</text>
          </view>
        </view>
        <view class="ov-stats">
          <view v-for="s in stats" :key="s.level" class="stat-item">
            <text class="stat-dot" :class="levelClass(s.level)"></text>
            <text class="stat-text">{{ s.label }} {{ s.count }}</text>
          </view>
        </view>
      </view>

      <!-- ② 筛选 -->
      <view class="tabs">
        <view
          v-for="t in LEVEL_TABS"
          :key="t.value"
          class="tab-item"
          :class="{ active: activeLevel === t.value }"
          @click="activeLevel = t.value">
          <text>{{ t.label }}</text>
        </view>
      </view>

      <!-- ③ 日志列表 -->
      <view class="card">
        <view class="flex-between card-title-row">
          <text class="card-title no-margin">📋 日志列表</text>
          <text class="refresh-btn" @click="reload">重新读取</text>
        </view>

        <EmptyState v-if="filtered.length === 0" icon="📭" text="暂无日志" desc="切换筛选条件或重新读取" />

        <view v-for="(log, idx) in filtered" :key="log.id + '-' + idx" class="log-item">
          <view class="log-head">
            <text class="log-level" :class="levelClass(log.level)">{{ log.level.toUpperCase() }}</text>
            <text class="log-time">{{ log.time }}</text>
          </view>
          <text class="log-message">{{ log.message }}</text>
        </view>
      </view>

      <!-- ④ 操作按钮 -->
      <view class="actions">
        <view class="btn-ghost flex-1" @click="copyAll">
          <text>复制全部</text>
        </view>
        <view class="btn-primary flex-1 danger-btn" @click="clearLogs">
          <text>清空日志</text>
        </view>
      </view>

      <view class="card tip-card">
        <text class="tip-title">🧪 关于调试日志</text>
        <text class="tip-line">· 日志保存在本机，键名 guardian_debug_log</text>
        <text class="tip-line">· 日志最多保留最近 200 条，超出后自动丢弃最早的记录</text>
        <text class="tip-line">· 反馈问题时，可点击"复制全部"把日志粘贴给开发同学</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 调试日志
 * ----------------------------------------------------------------------------
 * 从 uni.getStorageSync('guardian_debug_log') 读取本地日志。
 * 没有日志时展示示例日志，保证界面不空白。
 */
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'

/** 日志存储键 */
const LOG_KEY = 'guardian_debug_log'

/** 级别筛选 */
const LEVEL_TABS = [
  { value: 'all', label: '全部' },
  { value: 'info', label: '信息' },
  { value: 'warn', label: '警告' },
  { value: 'error', label: '错误' }
]

interface LogItem {
  id: string
  level: 'info' | 'warn' | 'error'
  time: string
  message: string
}

const logs = ref<LogItem[]>([])
const activeLevel = ref('all')
const usingSample = ref(false)

/** 数据来源说明 */
const sourceText = computed(() => (usingSample.value ? '当前展示示例日志' : '来自本机存储'))

/** 各级别数量统计 */
const stats = computed(() => {
  const count = (lv: string) => logs.value.filter((l) => l.level === lv).length
  return [
    { level: 'info', label: '信息', count: count('info') },
    { level: 'warn', label: '警告', count: count('warn') },
    { level: 'error', label: '错误', count: count('error') }
  ]
})

/** 按级别过滤 */
const filtered = computed(() => {
  if (activeLevel.value === 'all') return logs.value
  return logs.value.filter((l) => l.level === activeLevel.value)
})

/** 构造示例日志（无本地日志时展示） */
function buildSample(): LogItem[] {
  const now = Date.now()
  const t = (offsetSec: number) => formatTime(new Date(now - offsetSec * 1000))
  return [
    { id: 'l-1', level: 'info', time: t(6), message: '[App] 应用启动完成，版本 v1.0.0' },
    { id: 'l-2', level: 'info', time: t(30), message: '[Auth] 从本地读取登录态成功（guardian_token）' },
    { id: 'l-3', level: 'info', time: t(75), message: '[Request] GET /api/checkin/today → 200' },
    { id: 'l-4', level: 'warn', time: t(180), message: '[Device] 设备「客厅血压计」离线，已切换为兜底数据展示' },
    { id: 'l-5', level: 'info', time: t(260), message: '[Monitor] 监测历史加载 12 条记录' },
    { id: 'l-6', level: 'error', time: t(420), message: '[Request] POST /api/monitor/voice-analyze 失败：Network Error，已使用本地分析结果' },
    { id: 'l-7', level: 'warn', time: t(600), message: '[Storage] guardian_debug_log 为空，已展示示例日志' },
    { id: 'l-8', level: 'info', time: t(900), message: '[Settings] 大字模式：关闭；数据可见范围：全部数据' }
  ]
}

/** 级别样式 */
function levelClass(level: string): string {
  if (level === 'error') return 'lv-error'
  if (level === 'warn') return 'lv-warn'
  return 'lv-info'
}

/** 归一化单条日志 */
function normalize(raw: unknown, index: number): LogItem {
  if (typeof raw === 'string') {
    return {
      id: 'log-' + index,
      level: /error|错误/i.test(raw) ? 'error' : /warn|警告/i.test(raw) ? 'warn' : 'info',
      time: '--:--:--',
      message: raw
    }
  }
  const o = (raw || {}) as Record<string, unknown>
  const level = String(o.level ?? o.type ?? 'info').toLowerCase()
  return {
    id: String(o.id ?? 'log-' + index),
    level: level === 'error' || level === 'warn' ? (level as 'error' | 'warn') : 'info',
    time: String(o.time ?? o.timestamp ?? '--:--:--'),
    message: String(o.message ?? o.msg ?? o.content ?? '')
  }
}

/** 读取日志 */
function reload() {
  let raw: unknown = ''
  try {
    raw = uni.getStorageSync(LOG_KEY)
  } catch {
    raw = ''
  }

  // 支持字符串、数组两种存储格式
  let list: LogItem[] = []
  if (Array.isArray(raw) && raw.length > 0) {
    list = raw.map((r, i) => normalize(r, i))
  } else if (typeof raw === 'string' && raw.trim()) {
    list = raw
      .split('\n')
      .filter((line) => line.trim())
      .map((line, i) => normalize(line, i))
  }

  if (list.length > 0) {
    logs.value = list
    usingSample.value = false
  } else {
    logs.value = buildSample()
    usingSample.value = true
  }
}

/** 复制全部日志 */
function copyAll() {
  if (logs.value.length === 0) {
    uni.showToast({ title: '暂无日志可复制', icon: 'none' })
    return
  }
  const text = logs.value.map((l) => `[${l.time}] [${l.level.toUpperCase()}] ${l.message}`).join('\n')
  try {
    uni.setClipboardData({
      data: text,
      success: () => uni.showToast({ title: '日志已复制', icon: 'none' })
    })
  } catch {
    uni.showToast({ title: '当前环境不支持复制', icon: 'none' })
  }
}

/** 清空日志 */
function clearLogs() {
  if (logs.value.length === 0) return
  uni.showModal({
    title: '清空日志',
    content: '将删除本机保存的全部调试日志，确定继续吗？',
    confirmColor: '#E55A5A',
    success: (res) => {
      if (!res.confirm) return
      try {
        uni.removeStorageSync(LOG_KEY)
      } catch {
        // 忽略
      }
      logs.value = []
      usingSample.value = false
      uni.showToast({ title: '已清空', icon: 'none' })
    }
  })
}

/** 时间文案 HH:mm:ss */
function formatTime(d: Date): string {
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  const ss = String(d.getSeconds()).padStart(2, '0')
  return `${hh}:${mi}:${ss}`
}

onLoad(() => {
  reload()
})

onShow(() => {
  // 从其他页面返回时可能有新日志写入
  reload()
})
</script>

<style lang="scss" scoped>
.page-inner {
  padding: 24rpx;
}

/* ---------- 概览 ---------- */
.overview-card {
  background: linear-gradient(135deg, var(--primary), #9db89c);
  color: #ffffff;
}

.ov-left {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
}

.ov-value {
  font-size: 60rpx;
  font-weight: 700;
}

.ov-label {
  font-size: 26rpx;
  opacity: 0.9;
}

.ov-total {
  font-size: 22rpx;
  opacity: 0.9;
}

.ov-stats {
  display: flex;
  gap: 32rpx;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 2rpx solid rgba(255, 255, 255, 0.25);
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.stat-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
}

.stat-text {
  font-size: 24rpx;
  opacity: 0.95;
}

/* ---------- 筛选 Tab ---------- */
.tabs {
  display: flex;
  gap: 12rpx;
  margin-bottom: 24rpx;
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

/* ---------- 日志列表 ---------- */
.card-title-row {
  margin-bottom: 8rpx;
}

.no-margin {
  margin-bottom: 0;
}

.refresh-btn {
  font-size: 24rpx;
  color: var(--primary-d);
}

.log-item {
  padding: 22rpx 0;
  border-bottom: 2rpx solid var(--divider);
}

.log-item:last-child {
  border-bottom: none;
}

.log-head {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.log-level {
  padding: 2rpx 14rpx;
  border-radius: var(--radius-full);
  font-size: 20rpx;
  font-weight: 600;
}

.lv-info {
  background: var(--primary-l);
  color: var(--primary-d);
}

.lv-warn {
  background: var(--warning-l);
  color: var(--warning);
}

.lv-error {
  background: var(--danger-l);
  color: var(--danger);
}

.log-time {
  font-size: 22rpx;
  color: var(--text-3);
}

.log-message {
  display: block;
  margin-top: 10rpx;
  font-size: 26rpx;
  color: var(--text-1);
  line-height: 1.7;
  word-break: break-all;
}

/* ---------- 操作 ---------- */
.actions {
  display: flex;
  gap: 20rpx;
  margin-bottom: 24rpx;
}

.danger-btn {
  background: linear-gradient(135deg, #e55a5a, #f08b8b);
  box-shadow: 0 8rpx 24rpx rgba(229, 90, 90, 0.25);
}

/* ---------- 提示 ---------- */
.tip-card {
  margin-bottom: 48rpx;
}

.tip-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text-1);
  margin-bottom: 12rpx;
}

.tip-line {
  display: block;
  font-size: 24rpx;
  color: var(--text-2);
  line-height: 1.8;
}
</style>
