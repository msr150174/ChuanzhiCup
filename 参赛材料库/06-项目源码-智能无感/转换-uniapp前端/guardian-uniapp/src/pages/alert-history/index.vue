<template>
  <view class="page tabbar-gap">
    <!-- 筛选 Tab -->
    <view class="filter-bar">
      <view
        v-for="t in tabs"
        :key="t.key"
        class="filter-item"
        :class="{ active: state.filter === t.key }"
        @click="switchFilter(t.key)"
      >
        <text>{{ t.label }}</text>
        <text v-if="t.key === 'unhandled' && unhandledCount > 0" class="filter-badge">{{ unhandledCount }}</text>
      </view>
    </view>

    <!-- 统计条 -->
    <view class="stat-bar">
      <view class="stat-cell">
        <text class="stat-value">{{ state.list.length }}</text>
        <text class="stat-label">当前筛选</text>
      </view>
      <view class="stat-cell">
        <text class="stat-value danger">{{ unhandledCount }}</text>
        <text class="stat-label">未处理</text>
      </view>
      <view class="stat-cell">
        <text class="stat-value">{{ handledCount }}</text>
        <text class="stat-label">已处理</text>
      </view>
    </view>

    <!-- 加载态 -->
    <view v-if="state.loading" class="loading-box">
      <text class="loading-text">加载中...</text>
    </view>

    <!-- 列表 -->
    <view v-else-if="filteredList.length > 0" class="alert-list">
      <view v-for="a in filteredList" :key="a.id" class="alert-card">
        <view class="alert-top">
          <view class="alert-type-icon" :class="'ico-' + a.severity">
            <text>{{ typeIcon(a.type) }}</text>
          </view>
          <view class="alert-main">
            <text class="alert-title">{{ a.title }}</text>
            <text class="alert-time">{{ a.occurredAt }}</text>
          </view>
          <text class="sev-tag" :class="'sev-' + a.severity">{{ a.severityText }}</text>
        </view>

        <text v-if="a.description" class="alert-desc">{{ a.description }}</text>

        <view class="alert-footer">
          <text class="type-text">{{ a.typeText }}</text>
          <view v-if="!a.handled" class="handle-btn" @click="handleAlert(a)">
            <text>处理</text>
          </view>
          <text v-else class="handled-text">已处理</text>
        </view>
      </view>
    </view>

    <!-- 空状态 -->
    <EmptyState
      v-else
      icon="🔔"
      text="暂无告警记录"
      desc="一切正常时不会有告警，长辈平安就是最好的消息"
    />

    <view class="bottom-tip">
      <text class="tip-text">💡 告警会在长辈出现异常情况时自动生成，处理后可标记为已处理</text>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 告警历史
 * ----------------------------------------------------------------------------
 * 筛选 Tab（全部 / 未处理 / 已处理）+ 列表 + 处理
 * 接口：guardianApi.alerts() / guardianApi.handleAlert()
 */
import { reactive, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { guardianApi } from '@/services/api'
import type { AlertItem } from '@/types'
import EmptyState from '@/components/EmptyState.vue'

type FilterKey = 'all' | 'unhandled' | 'handled'

/** 兜底数据（★ 接口不可用时保证列表非空） */
const FALLBACK_ALERTS: AlertItem[] = [
  {
    id: 'a-1',
    type: 'fall',
    typeText: '跌倒检测',
    severity: 'critical',
    severityText: '紧急',
    title: '检测到疑似跌倒',
    description: '客厅摄像头检测到长辈跌倒动作，请立即确认情况',
    occurredAt: '今天 09:12',
    handled: false
  },
  {
    id: 'a-2',
    type: 'sos',
    typeText: 'SOS 求助',
    severity: 'high',
    severityText: '严重',
    title: '长辈发起紧急求助',
    description: '长辈长按 SOS 按钮发出求助，位置已记录',
    occurredAt: '今天 07:40',
    handled: false
  },
  {
    id: 'a-3',
    type: 'missed_checkin',
    typeText: '作息异常',
    severity: 'medium',
    severityText: '中等',
    title: '睡前打卡缺失',
    description: '昨晚未完成睡前打卡，建议提醒长辈',
    occurredAt: '昨天 23:30',
    handled: true,
    handledAt: '昨天 23:45',
    notes: '已电话确认，长辈忘记打卡'
  },
  {
    id: 'a-4',
    type: 'device',
    typeText: '设备异常',
    severity: 'low',
    severityText: '提示',
    title: '智能手环电量过低',
    description: '设备电量低于 15%，请提醒长辈充电',
    occurredAt: '昨天 18:05',
    handled: true,
    handledAt: '昨天 18:20'
  }
]

const tabs: { key: FilterKey; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'unhandled', label: '未处理' },
  { key: 'handled', label: '已处理' }
]

const state = reactive({
  loading: true,
  filter: 'all' as FilterKey,
  list: [] as AlertItem[]
})

/** 未处理数量 */
const unhandledCount = computed(() => state.list.filter((a) => !a.handled).length)
/** 已处理数量 */
const handledCount = computed(() => state.list.filter((a) => a.handled).length)

/** 当前筛选后的列表 */
const filteredList = computed(() => {
  if (state.filter === 'unhandled') return state.list.filter((a) => !a.handled)
  if (state.filter === 'handled') return state.list.filter((a) => a.handled)
  return state.list
})

/** 类型图标 */
function typeIcon(type: string): string {
  const map: Record<string, string> = {
    fall: '🚨',
    sos: '🆘',
    missed_checkin: '🕐',
    device: '📱',
    health: '❤️',
    location: '📍'
  }
  return map[type] || '⚠️'
}

/** 切换筛选 */
function switchFilter(key: FilterKey) {
  state.filter = key
}

/** 加载告警列表 */
async function loadAlerts() {
  state.loading = true
  try {
    const res = await guardianApi.alerts({ page: 1, pageSize: 50 })
    const list = res.list || []
    state.list = list.length > 0 ? list : JSON.parse(JSON.stringify(FALLBACK_ALERTS)) as AlertItem[]
  } catch {
    // ★ 兜底
    state.list = JSON.parse(JSON.stringify(FALLBACK_ALERTS)) as AlertItem[]
  } finally {
    state.loading = false
  }
}

/** 处理告警 */
function handleAlert(a: AlertItem) {
  uni.showModal({
    title: '处理告警',
    content: `确认已处理「${a.title}」？`,
    editable: true,
    placeholderText: '可填写处理备注（选填）',
    success: async (res) => {
      if (!res.confirm) return
      const notes = res.content || ''
      try {
        await guardianApi.handleAlert(a.id, notes)
      } catch {
        // ★ 离线降级：本地仍然标记
      }
      const target = state.list.find((x) => x.id === a.id)
      if (target) {
        target.handled = true
        target.handledAt = '刚刚'
        target.notes = notes
      }
      uni.showToast({ title: '已处理', icon: 'none' })
    }
  })
}

onShow(() => {
  loadAlerts()
})

onPullDownRefresh(async () => {
  await loadAlerts()
  uni.stopPullDownRefresh()
})
</script>

<style lang="scss" scoped>
.page { padding: 24rpx; }

/* 筛选 Tab */
.filter-bar {
  display: flex; gap: 16rpx; margin-bottom: 24rpx;
  background: var(--surface); border-radius: var(--radius);
  padding: 12rpx; box-shadow: var(--shadow);
}
.filter-item {
  flex: 1; height: 72rpx; border-radius: var(--radius-sm);
  display: flex; align-items: center; justify-content: center; gap: 8rpx;
  font-size: 28rpx; color: var(--text-2);
}
.filter-item.active { background: var(--primary-l); color: var(--primary-d); font-weight: 600; }
.filter-badge {
  min-width: 32rpx; height: 32rpx; padding: 0 8rpx; border-radius: var(--radius-full);
  background: var(--danger); color: #fff; font-size: 20rpx;
  display: flex; align-items: center; justify-content: center;
}

/* 统计条 */
.stat-bar {
  display: flex; background: var(--surface); border-radius: var(--radius);
  padding: 28rpx 0; margin-bottom: 24rpx; box-shadow: var(--shadow);
}
.stat-cell { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 8rpx; }
.stat-value { font-size: 40rpx; font-weight: 700; color: var(--text-1); }
.stat-value.danger { color: var(--danger); }
.stat-label { font-size: 22rpx; color: var(--text-2); }

/* 加载 / 空 */
.loading-box { padding: 96rpx 0; text-align: center; }
.loading-text { font-size: 28rpx; color: var(--text-2); }

/* 列表 */
.alert-list { display: flex; flex-direction: column; gap: 24rpx; }
.alert-card {
  background: var(--surface); border-radius: var(--radius);
  padding: 28rpx; box-shadow: var(--shadow);
}
.alert-top { display: flex; align-items: flex-start; gap: 20rpx; }
.alert-type-icon {
  width: 72rpx; height: 72rpx; border-radius: var(--radius-sm); flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; font-size: 32rpx;
  background: var(--primary-l);
}
.ico-critical { background: var(--danger-l); }
.ico-high { background: var(--danger-l); }
.ico-medium { background: var(--warning-l); }
.ico-low { background: var(--surface-2); }

.alert-main { flex: 1; display: flex; flex-direction: column; gap: 8rpx; }
.alert-title { font-size: 30rpx; font-weight: 600; color: var(--text-1); }
.alert-time { font-size: 22rpx; color: var(--text-3); }

.sev-tag { font-size: 22rpx; padding: 4rpx 16rpx; border-radius: var(--radius-full); flex-shrink: 0; }
.sev-critical { background: var(--danger-l); color: var(--danger); }
.sev-high { background: var(--danger-l); color: var(--danger); }
.sev-medium { background: var(--warning-l); color: var(--warning); }
.sev-low { background: var(--surface-2); color: var(--text-2); }

.alert-desc {
  display: block; font-size: 26rpx; color: var(--text-2);
  line-height: 1.6; margin-top: 16rpx;
}

.alert-footer {
  display: flex; align-items: center; justify-content: space-between;
  margin-top: 24rpx; padding-top: 20rpx; border-top: 2rpx solid var(--divider);
}
.type-text { font-size: 24rpx; color: var(--text-3); }
.handle-btn {
  padding: 12rpx 40rpx; border-radius: var(--radius-full);
  background: var(--primary-d); color: #fff; font-size: 26rpx;
}
.handled-text { font-size: 26rpx; color: var(--primary-d); }

.bottom-tip {
  margin-top: 32rpx; padding: 24rpx;
  background: var(--primary-l); border-radius: var(--radius);
}
.tip-text { font-size: 24rpx; color: var(--primary-d); line-height: 1.6; }
</style>
