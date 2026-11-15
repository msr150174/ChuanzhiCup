<template>
  <view class="page medicine-page">
    <!-- ① 今日服药进度三宫格 -->
    <view class="progress-card">
      <view class="progress-header">
        <text class="progress-title">今日服药进度</text>
        <text class="progress-rate">{{ adherenceRate }}% 依从率</text>
      </view>

      <view class="progress-grid">
        <view class="progress-cell">
          <text class="progress-value taken">{{ todayStats.taken }}</text>
          <text class="progress-label">已服用</text>
        </view>
        <view class="progress-divider"></view>
        <view class="progress-cell">
          <text class="progress-value pending">{{ todayStats.pending }}</text>
          <text class="progress-label">待服用</text>
        </view>
        <view class="progress-divider"></view>
        <view class="progress-cell">
          <text class="progress-value">{{ todayStats.total }}</text>
          <text class="progress-label">总计</text>
        </view>
      </view>

      <!-- 总进度条 -->
      <view class="progress-track">
        <view class="progress-fill" :style="{ width: takenPercent + '%' }"></view>
      </view>
      <text class="progress-hint">今日完成度 {{ takenPercent }}%，{{ takenPercent >= 100 ? '全部按时服用，很棒！' : '记得按时服药哦' }}</text>
    </view>

    <!-- ② Tab：进行中 / 已暂停 -->
    <view class="tab-row">
      <view
        v-for="t in TABS"
        :key="t.key"
        class="tab-item"
        :class="{ active: activeTab === t.key }"
        @click="activeTab = t.key"
      >
        <text>{{ t.label }}</text>
        <text class="tab-count">{{ t.key === 'active' ? activeList.length : pausedList.length }}</text>
      </view>
    </view>

    <!-- ③ 按时段分组的提醒列表 -->
    <view v-if="currentList.length === 0" class="card">
      <view class="empty">
        <text class="empty-icon">💊</text>
        <text class="empty-text">{{ activeTab === 'active' ? '暂无进行中的用药提醒' : '暂无已暂停的提醒' }}</text>
        <text class="empty-desc">点击右下角按钮添加新的用药提醒</text>
      </view>
    </view>

    <view v-for="group in groupedList" :key="group.period" class="period-group">
      <view class="period-header">
        <text class="period-icon">{{ group.icon }}</text>
        <text class="period-title">{{ group.title }}</text>
        <text class="period-count">{{ group.items.length }} 项</text>
      </view>

      <view class="card">
        <view
          v-for="(item, index) in group.items"
          :key="item.id"
          class="med-item"
          :class="{ last: index === group.items.length - 1, paused: !item.isActive }"
        >
          <view class="med-left">
            <view class="med-icon-wrapper" :class="{ taken: item.taken }">
              <text>{{ item.taken ? '✓' : '💊' }}</text>
            </view>
            <view class="med-info">
              <view class="med-name-row">
                <text class="med-name">{{ item.medicineName }}</text>
                <text class="med-pill" :class="item.taken ? 'pill-success' : 'pill-warning'">
                  {{ item.taken ? '已服用' : '待服用' }}
                </text>
              </view>
              <text class="med-desc">{{ item.dosage }} · {{ frequencyText(item.frequency) }}</text>
              <view class="med-times">
                <text v-for="t in item.times" :key="t" class="med-time-chip">{{ t }}</text>
              </view>
              <text v-if="item.notes" class="med-notes">📝 {{ item.notes }}</text>
            </view>
          </view>

          <view class="med-right">
            <!-- 启用开关 -->
            <view class="switch" :class="{ on: item.isActive }" @click.stop="toggleActive(item)">
              <view class="switch-knob"></view>
            </view>
            <!-- 服药打卡 -->
            <view
              v-if="item.isActive && !item.taken"
              class="med-check-btn"
              @click.stop="takeMedicine(item)"
            >
              <text>打卡</text>
            </view>
            <view class="med-delete-btn" @click.stop="confirmRemove(item)">
              <text>删除</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- ④ 依从率卡片 -->
    <view class="card">
      <text class="card-title">📊 近 30 天依从率</text>
      <view class="adherence-row">
        <view class="adherence-cell">
          <text class="adherence-value good">{{ adherence.taken }}</text>
          <text class="adherence-label">按时服用</text>
        </view>
        <view class="adherence-cell">
          <text class="adherence-value warn">{{ adherence.missed }}</text>
          <text class="adherence-label">漏服</text>
        </view>
        <view class="adherence-cell">
          <text class="adherence-value">{{ adherence.skipped }}</text>
          <text class="adherence-label">跳过</text>
        </view>
        <view class="adherence-cell">
          <text class="adherence-value primary">{{ adherenceRate }}%</text>
          <text class="adherence-label">依从率</text>
        </view>
      </view>
    </view>

    <!-- 悬浮添加按钮 -->
    <view class="fab" @click="openAddDialog">
      <text>＋</text>
    </view>

    <!-- 添加提醒弹窗 -->
    <view v-if="showAddDialog" class="modal-overlay" @click.self="showAddDialog = false">
      <view class="modal-box">
        <text class="modal-title">添加用药提醒</text>

        <view class="form-field">
          <text class="form-label">药品名称</text>
          <input
            v-model="form.medicineName"
            class="form-input"
            :maxlength="20"
            placeholder="例如：降压药"
            placeholder-class="field-ph"
          />
        </view>

        <view class="form-field">
          <text class="form-label">剂量</text>
          <input
            v-model="form.dosage"
            class="form-input"
            :maxlength="20"
            placeholder="例如：1 片"
            placeholder-class="field-ph"
          />
        </view>

        <view class="form-field">
          <text class="form-label">服用频次</text>
          <view class="freq-row">
            <view
              v-for="f in FREQUENCIES"
              :key="f.value"
              class="freq-chip"
              :class="{ active: form.frequency === f.value }"
              @click="form.frequency = f.value"
            >
              <text>{{ f.label }}</text>
            </view>
          </view>
        </view>

        <view class="form-field">
          <text class="form-label">提醒时间点</text>
          <view class="time-chips">
            <view
              v-for="t in TIME_PRESETS"
              :key="t"
              class="time-chip"
              :class="{ active: form.times.includes(t) }"
              @click="toggleTime(t)"
            >
              <text>{{ t }}</text>
            </view>
          </view>
        </view>

        <view class="form-field">
          <text class="form-label">备注（选填）</text>
          <input
            v-model="form.notes"
            class="form-input"
            :maxlength="30"
            placeholder="例如：饭后服用"
            placeholder-class="field-ph"
          />
        </view>

        <view class="modal-actions">
          <view class="modal-btn cancel" @click="showAddDialog = false"><text>取消</text></view>
          <view class="modal-btn confirm" @click="handleAdd"><text>保存</text></view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 用药提醒页
 * ----------------------------------------------------------------------------
 * 今日服药进度三宫格 + Tab（进行中 / 已暂停）+ 按时段分组的提醒列表
 * 时段划分：hour < 12 → 晨起；hour < 18 → 午后；否则 → 睡前
 */
import { ref, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { medicineApi } from '@/services/api'
import type { MedicineReminder } from '@/types'

interface MedItem extends MedicineReminder {
  taken: boolean
}

const TABS = [
  { key: 'active' as const, label: '进行中' },
  { key: 'paused' as const, label: '已暂停' }
]

const FREQUENCIES = [
  { label: '每日一次', value: 'daily' },
  { label: '每日两次', value: 'twice' },
  { label: '每日三次', value: 'three_times' }
]

const TIME_PRESETS = ['07:00', '08:00', '11:30', '14:00', '17:30', '19:00', '21:00']

const activeTab = ref<'active' | 'paused'>('active')

/** 提醒列表兜底 */
const FALLBACK_LIST: MedItem[] = [
  {
    id: 'med-1',
    medicineName: '降压药',
    dosage: '1 片',
    frequency: 'daily',
    times: ['08:00'],
    startDate: '2026-01-01',
    notes: '早餐后服用',
    isActive: true,
    taken: true
  },
  {
    id: 'med-2',
    medicineName: '降糖药',
    dosage: '半片',
    frequency: 'twice',
    times: ['11:30', '19:00'],
    startDate: '2026-01-01',
    notes: '餐前 30 分钟',
    isActive: true,
    taken: false
  },
  {
    id: 'med-3',
    medicineName: '钙片',
    dosage: '2 片',
    frequency: 'daily',
    times: ['19:00'],
    startDate: '2026-01-01',
    isActive: true,
    taken: false
  },
  {
    id: 'med-4',
    medicineName: '安眠药',
    dosage: '1 片',
    frequency: 'daily',
    times: ['21:00'],
    startDate: '2026-01-01',
    notes: '遵医嘱，已暂停',
    isActive: false,
    taken: false
  }
]
const list = ref<MedItem[]>([])

/** 今日服药统计兜底 */
const todayStats = ref({ taken: 0, pending: 0, total: 0 })
const adherence = ref({ taken: 26, missed: 2, skipped: 2, rate: 86 })

/** 添加表单 */
const showAddDialog = ref(false)
const form = ref({
  medicineName: '',
  dosage: '',
  frequency: 'daily',
  times: [] as string[],
  notes: ''
})

// ============================================================================
// 派生
// ============================================================================
const activeList = computed(() => list.value.filter((m) => m.isActive))
const pausedList = computed(() => list.value.filter((m) => !m.isActive))
const currentList = computed(() => (activeTab.value === 'active' ? activeList.value : pausedList.value))

const takenPercent = computed(() => {
  if (todayStats.value.total === 0) return 0
  return Math.round((todayStats.value.taken / todayStats.value.total) * 100)
})

const adherenceRate = computed(() => Math.round(adherence.value.rate))

/** 时段判断：hour < 12 晨起 / hour < 18 午后 / 否则 睡前 */
function getPeriod(hour: number): 'morning' | 'afternoon' | 'night' {
  if (hour < 12) return 'morning'
  if (hour < 18) return 'afternoon'
  return 'night'
}

const PERIOD_META: Record<string, { title: string; icon: string }> = {
  morning: { title: '晨起', icon: '🌅' },
  afternoon: { title: '午后', icon: '☀️' },
  night: { title: '睡前', icon: '🌙' }
}

/** 按「最早时间点所属时段」分组 */
const groupedList = computed(() => {
  const groups: Record<string, MedItem[]> = { morning: [], afternoon: [], night: [] }

  currentList.value.forEach((item) => {
    const times = item.times && item.times.length > 0 ? item.times : ['08:00']
    // 用最早的时间点决定归属时段
    const earliest = times.slice().sort()[0]
    const hour = Number(earliest.split(':')[0]) || 8
    groups[getPeriod(hour)].push(item)
  })

  return (['morning', 'afternoon', 'night'] as const)
    .filter((key) => groups[key].length > 0)
    .map((key) => ({
      period: key,
      title: PERIOD_META[key].title,
      icon: PERIOD_META[key].icon,
      items: groups[key]
    }))
})

function frequencyText(freq: string): string {
  const map: Record<string, string> = {
    daily: '每日一次',
    twice: '每日两次',
    three_times: '每日三次'
  }
  return map[freq] || freq
}

function toast(msg: string) {
  uni.showToast({ title: msg, icon: 'none' })
}

// ============================================================================
// 数据加载
// ============================================================================
async function loadList() {
  try {
    const res = await medicineApi.reminders()
    const raw = res?.list || []
    list.value = raw.length > 0
      ? raw.map((m) => ({ ...m, taken: false }))
      : FALLBACK_LIST.map((m) => ({ ...m }))
  } catch {
    list.value = FALLBACK_LIST.map((m) => ({ ...m }))
  }
  recomputeTodayStats()
}

async function loadToday() {
  try {
    const data = await medicineApi.today()
    todayStats.value = {
      taken: Number(data?.taken ?? 0),
      pending: Number(data?.pending ?? 0),
      total: Number(data?.total ?? 0)
    }
    if (todayStats.value.total === 0) recomputeTodayStats()
  } catch {
    recomputeTodayStats()
  }

  try {
    const a = await medicineApi.adherence(30)
    adherence.value = {
      taken: Number(a?.taken ?? 26),
      missed: Number(a?.missed ?? 2),
      skipped: Number(a?.skipped ?? 2),
      rate: Number(a?.rate ?? 86)
    }
  } catch {
    adherence.value = { taken: 26, missed: 2, skipped: 2, rate: 86 }
  }
}

/** 本地按列表推算今日统计 */
function recomputeTodayStats() {
  const active = list.value.filter((m) => m.isActive)
  const total = active.reduce((sum, m) => sum + Math.max(1, (m.times || []).length), 0)
  const taken = active.filter((m) => m.taken).reduce((sum, m) => sum + Math.max(1, (m.times || []).length), 0)
  todayStats.value = { taken, pending: Math.max(0, total - taken), total }
}

async function loadAll() {
  await Promise.all([loadList(), loadToday()])
}

onShow(() => {
  loadAll()
})

onPullDownRefresh(async () => {
  await loadAll()
  uni.stopPullDownRefresh()
})

// ============================================================================
// 交互
// ============================================================================
/** 服药打卡 */
async function takeMedicine(item: MedItem) {
  if (item.taken) return
  item.taken = true
  recomputeTodayStats()
  try {
    await medicineApi.record({ reminderId: item.id, medicineName: item.medicineName, status: 'taken' })
    toast('已记录服药')
  } catch {
    toast('已在本地记录服药')
  }
}

/** 启用 / 暂停提醒 */
async function toggleActive(item: MedItem) {
  const next = !item.isActive
  item.isActive = next
  try {
    await medicineApi.updateReminder(item.id, { isActive: next })
  } catch {
    // 本地已切换，界面保持一致
  }
  recomputeTodayStats()
  toast(next ? '提醒已启用' : '提醒已暂停')
}

/** 删除提醒（软删除） */
function confirmRemove(item: MedItem) {
  uni.showModal({
    title: '删除提醒',
    content: `确定删除「${item.medicineName}」的用药提醒吗？`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await medicineApi.removeReminder(item.id)
      } catch {
        // 接口失败也做本地移除，保证交互闭环
      }
      list.value = list.value.filter((m) => m.id !== item.id)
      recomputeTodayStats()
      toast('已删除')
    }
  })
}

function openAddDialog() {
  form.value = { medicineName: '', dosage: '', frequency: 'daily', times: [], notes: '' }
  showAddDialog.value = true
}

function toggleTime(t: string) {
  const idx = form.value.times.indexOf(t)
  if (idx > -1) {
    form.value.times.splice(idx, 1)
  } else {
    form.value.times.push(t)
  }
}

/** 保存新提醒 */
async function handleAdd() {
  if (!form.value.medicineName.trim()) {
    toast('请输入药品名称')
    return
  }
  if (form.value.times.length === 0) {
    toast('请至少选择一个提醒时间')
    return
  }

  const payload: Partial<MedicineReminder> = {
    medicineName: form.value.medicineName.trim(),
    dosage: form.value.dosage.trim() || '1 片',
    frequency: form.value.frequency,
    times: form.value.times.slice().sort(),
    startDate: new Date().toISOString().slice(0, 10),
    notes: form.value.notes.trim() || undefined,
    isActive: true
  }

  try {
    const created = await medicineApi.addReminder(payload)
    list.value.push({ ...(created || (payload as MedicineReminder)), id: created?.id || 'local-' + Date.now(), taken: false })
    toast('提醒已添加')
  } catch {
    // 后端不可用时本地新增，保证可用
    list.value.push({
      id: 'local-' + Date.now(),
      medicineName: payload.medicineName as string,
      dosage: payload.dosage as string,
      frequency: payload.frequency as string,
      times: payload.times as string[],
      startDate: payload.startDate as string,
      notes: payload.notes,
      isActive: true,
      taken: false
    })
    toast('已本地添加提醒')
  }

  showAddDialog.value = false
  activeTab.value = 'active'
  recomputeTodayStats()
}
</script>

<style lang="scss" scoped>
.medicine-page {
  min-height: 100vh;
  padding: 32rpx 32rpx 160rpx;
  background: linear-gradient(180deg, #f4efe6 0%, #f5f0e6 100%);
}

/* ---------- ① 进度三宫格 ---------- */
.progress-card {
  padding: 36rpx 32rpx;
  margin-bottom: 32rpx;
  background: linear-gradient(135deg, #8fa68e 0%, #a8c4a7 100%);
  border-radius: var(--radius-lg);
  box-shadow: 0 12rpx 40rpx rgba(143, 166, 142, 0.25);
}

.progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28rpx;
}

.progress-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #ffffff;
}

.progress-rate {
  padding: 6rpx 20rpx;
  font-size: 24rpx;
  color: #ffffff;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 999rpx;
}

.progress-grid {
  display: flex;
  align-items: center;
}

.progress-cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}

.progress-value {
  font-size: 56rpx;
  font-weight: 700;
  color: #ffffff;
  line-height: 1.1;
}

.progress-value.taken {
  color: #eaffe8;
}

.progress-value.pending {
  color: #fff3d6;
}

.progress-label {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.85);
}

.progress-divider {
  width: 2rpx;
  height: 64rpx;
  background: rgba(255, 255, 255, 0.25);
}

.progress-track {
  width: 100%;
  height: 16rpx;
  margin-top: 28rpx;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 8rpx;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: #ffffff;
  border-radius: 8rpx;
  transition: width 0.4s ease;
}

.progress-hint {
  display: block;
  margin-top: 16rpx;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.9);
}

/* ---------- ② Tab ---------- */
.tab-row {
  display: flex;
  gap: 16rpx;
  margin-bottom: 32rpx;
}

.tab-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  padding: 26rpx 0;
  background: var(--surface);
  border: 4rpx solid transparent;
  border-radius: var(--radius);
  font-size: 30rpx;
  color: var(--text-2);
  box-shadow: var(--shadow);
}

.tab-item.active {
  background: rgba(143, 166, 142, 0.16);
  border-color: var(--primary);
  color: var(--primary-d);
  font-weight: 600;
}

.tab-count {
  padding: 2rpx 14rpx;
  font-size: 22rpx;
  border-radius: 999rpx;
  background: rgba(125, 116, 107, 0.12);
  color: inherit;
}

/* ---------- ③ 时段分组 ---------- */
.period-group {
  margin-bottom: 32rpx;
}

.period-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 16rpx;
  padding-left: 12rpx;
}

.period-icon {
  font-size: 32rpx;
}

.period-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-1);
}

.period-count {
  font-size: 24rpx;
  color: var(--text-3);
}

.med-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20rpx;
  padding: 28rpx 0;
  border-bottom: 2rpx solid var(--divider);
}

.med-item.last {
  border-bottom: none;
}

.med-item.paused {
  opacity: 0.6;
}

.med-left {
  flex: 1;
  display: flex;
  gap: 20rpx;
}

.med-icon-wrapper {
  width: 72rpx;
  height: 72rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  background: var(--primary-l);
  border-radius: 20rpx;
}

.med-icon-wrapper.taken {
  background: var(--primary);
  color: #ffffff;
}

.med-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.med-name-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.med-name {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-1);
}

.med-pill {
  padding: 4rpx 16rpx;
  font-size: 20rpx;
  border-radius: 999rpx;
}

.pill-success {
  background: var(--primary-l);
  color: var(--primary-d);
}

.pill-warning {
  background: var(--warning-l);
  color: var(--warning);
}

.med-desc {
  font-size: 26rpx;
  color: var(--text-2);
}

.med-times {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.med-time-chip {
  padding: 4rpx 16rpx;
  font-size: 22rpx;
  color: var(--primary-d);
  background: var(--primary-l);
  border-radius: 999rpx;
}

.med-notes {
  font-size: 22rpx;
  color: var(--text-3);
}

.med-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12rpx;
}

.switch {
  width: 80rpx;
  height: 44rpx;
  padding: 4rpx;
  display: flex;
  align-items: center;
  background: rgba(125, 116, 107, 0.2);
  border-radius: 22rpx;
  transition: background 0.2s ease;
}

.switch.on {
  background: var(--primary);
}

.switch-knob {
  width: 36rpx;
  height: 36rpx;
  background: #ffffff;
  border-radius: 50%;
  transition: transform 0.2s ease;
}

.switch.on .switch-knob {
  transform: translateX(36rpx);
}

.med-check-btn {
  padding: 10rpx 26rpx;
  background: var(--primary);
  border-radius: 999rpx;
  font-size: 24rpx;
  color: #ffffff;
}

.med-delete-btn {
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  color: var(--danger);
  background: var(--danger-l);
}

/* ---------- ④ 依从率 ---------- */
.card-title {
  display: block;
  margin-bottom: 24rpx;
  font-size: 34rpx;
  font-weight: 600;
  color: var(--text-1);
}

.adherence-row {
  display: flex;
}

.adherence-cell {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}

.adherence-value {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--text-1);
}

.adherence-value.good {
  color: var(--primary-d);
}

.adherence-value.warn {
  color: var(--warning);
}

.adherence-value.primary {
  color: var(--primary-d);
}

.adherence-label {
  font-size: 22rpx;
  color: var(--text-2);
}

/* ---------- 空态 ---------- */
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 72rpx 0;
}

.empty-icon {
  font-size: 88rpx;
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

/* ---------- 悬浮按钮 ---------- */
.fab {
  position: fixed;
  right: 48rpx;
  bottom: calc(72rpx + env(safe-area-inset-bottom));
  width: 112rpx;
  height: 112rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 60rpx;
  color: #ffffff;
  background: linear-gradient(135deg, var(--primary), #9db89c);
  border-radius: 56rpx;
  box-shadow: 0 12rpx 32rpx rgba(143, 166, 142, 0.4);
  z-index: 50;
}

.fab:active {
  transform: scale(0.95);
}

/* ---------- 弹窗 ---------- */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48rpx;
  background: rgba(0, 0, 0, 0.45);
}

.modal-box {
  width: 100%;
  max-height: 80vh;
  overflow-y: auto;
  padding: 40rpx 32rpx;
  background: var(--surface);
  border-radius: 32rpx;
  display: flex;
  flex-direction: column;
  gap: 28rpx;
}

.modal-title {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--text-1);
  text-align: center;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.form-label {
  font-size: 26rpx;
  color: var(--text-2);
}

.form-input {
  padding: 24rpx 28rpx;
  background: rgba(125, 116, 107, 0.06);
  border-radius: 20rpx;
  font-size: 30rpx;
  color: var(--text-1);
}

.field-ph {
  color: var(--text-3);
}

.freq-row,
.time-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.freq-chip,
.time-chip {
  padding: 14rpx 28rpx;
  border-radius: 999rpx;
  font-size: 26rpx;
  color: var(--text-2);
  background: rgba(125, 116, 107, 0.08);
}

.freq-chip.active,
.time-chip.active {
  background: var(--primary);
  color: #ffffff;
}

.modal-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 12rpx;
}

.modal-btn {
  flex: 1;
  padding: 26rpx 0;
  border-radius: 24rpx;
  font-size: 30rpx;
  font-weight: 600;
  text-align: center;
}

.modal-btn.cancel {
  background: rgba(125, 116, 107, 0.1);
  color: var(--text-2);
}

.modal-btn.confirm {
  background: var(--primary);
  color: #ffffff;
}
</style>
