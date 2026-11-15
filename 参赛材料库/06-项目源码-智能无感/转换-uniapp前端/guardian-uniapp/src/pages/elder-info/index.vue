<template>
  <view class="page">
    <!-- 标题 -->
    <view class="page-header">
      <text class="header-icon">📋</text>
      <view class="header-text">
        <text class="page-title">长辈档案</text>
        <text class="page-subtitle">完善基础健康信息，帮助守护更精准</text>
      </view>
    </view>

    <!-- 基础体征 -->
    <view class="card">
      <text class="card-title">🩺 基础体征</text>

      <view class="form-row">
        <text class="form-label">血型</text>
        <picker mode="selector" :range="bloodTypes" :value="bloodIndex" @change="onBloodChange">
          <view class="picker-value">
            <text :class="{ placeholder: !state.form.bloodType }">{{ state.form.bloodType || '请选择血型' }}</text>
            <text class="arrow">›</text>
          </view>
        </picker>
      </view>

      <view class="form-row">
        <text class="form-label">身高 (cm)</text>
        <input
          v-model="state.form.height"
          class="form-input"
          type="digit"
          placeholder="如 165"
          placeholder-class="ph"
        />
      </view>

      <view class="form-row no-border">
        <text class="form-label">体重 (kg)</text>
        <input
          v-model="state.form.weight"
          class="form-input"
          type="digit"
          placeholder="如 60"
          placeholder-class="ph"
        />
      </view>

      <view class="bmi-box">
        <text class="bmi-text">BMI 指数：{{ bmiText }}</text>
      </view>
    </view>

    <!-- 慢性病 -->
    <view class="card">
      <text class="card-title">💊 慢性病</text>
      <view class="chip-wrap">
        <view
          v-for="c in chronicOptions"
          :key="c"
          class="chip"
          :class="{ active: state.form.chronic.includes(c) }"
          @click="toggleChronic(c)"
        >
          <text>{{ c }}</text>
        </view>
      </view>
      <text v-if="state.form.chronic.length === 0" class="hint-text">暂未选择，可多选</text>
    </view>

    <!-- 过敏源 -->
    <view class="card">
      <text class="card-title">⚠️ 过敏源</text>
      <view class="chip-wrap">
        <view
          v-for="a in allergyOptions"
          :key="a"
          class="chip"
          :class="{ active: state.form.allergies.includes(a) }"
          @click="toggleAllergy(a)"
        >
          <text>{{ a }}</text>
        </view>
      </view>
      <view class="form-row no-border custom-row">
        <text class="form-label">其他过敏源</text>
        <input
          v-model="state.form.allergyOther"
          class="form-input"
          type="text"
          placeholder="如：芒果、花粉"
          placeholder-class="ph"
        />
      </view>
    </view>

    <!-- 作息目标 -->
    <view class="card">
      <text class="card-title">🌙 目标作息时间</text>
      <view class="form-row">
        <text class="form-label">目标入睡时间</text>
        <picker mode="time" :value="state.form.sleepTarget" @change="onSleepChange">
          <view class="picker-value">
            <text>{{ state.form.sleepTarget }}</text>
            <text class="arrow">›</text>
          </view>
        </picker>
      </view>
      <view class="form-row no-border">
        <text class="form-label">目标起床时间</text>
        <picker mode="time" :value="state.form.wakeTarget" @change="onWakeChange">
          <view class="picker-value">
            <text>{{ state.form.wakeTarget }}</text>
            <text class="arrow">›</text>
          </view>
        </picker>
      </view>
      <text class="hint-text">系统会依据目标作息判断打卡是否达标</text>
    </view>

    <!-- 运动频率 -->
    <view class="card">
      <text class="card-title">🏃 运动频率</text>
      <view class="chip-wrap">
        <view
          v-for="f in exerciseOptions"
          :key="f"
          class="chip"
          :class="{ active: state.form.exerciseFrequency === f }"
          @click="state.form.exerciseFrequency = f"
        >
          <text>{{ f }}</text>
        </view>
      </view>
    </view>

    <!-- 提交 -->
    <view class="btn-primary" :class="{ disabled: state.saving }" @click="submit">
      <text>{{ state.saving ? '保存中...' : '保存档案' }}</text>
    </view>
    <view class="btn-ghost reset-btn" @click="resetForm"><text>恢复默认</text></view>
  </view>
</template>

<script setup lang="ts">
/**
 * 长辈档案
 * ----------------------------------------------------------------------------
 * 血型 / 身高 / 体重 / 慢性病 / 过敏源 / 目标作息 / 运动频率
 * 本地存储 + 提交时调 authApi.updateProfile 接口
 */
import { reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { authApi } from '@/services/api'
import { useUserStore } from '@/stores/user'

/** 本地存储键 */
const PROFILE_KEY = 'guardian_elder_profile'

const bloodTypes = ['A 型', 'B 型', 'O 型', 'AB 型', '不清楚']
const chronicOptions = ['高血压', '糖尿病', '冠心病', '高血脂', '关节炎', '慢性胃炎', '颈椎病']
const allergyOptions = ['青霉素', '海鲜', '花粉', '牛奶', '鸡蛋', '尘螨']
const exerciseOptions = ['几乎不运动', '每周 1-2 次', '每周 3-4 次', '几乎每天']

/** 默认档案（★ 兜底数据） */
const DEFAULT_FORM = {
  bloodType: 'O 型',
  height: '165',
  weight: '60',
  chronic: ['高血压'] as string[],
  allergies: ['青霉素'] as string[],
  allergyOther: '',
  sleepTarget: '22:30',
  wakeTarget: '06:30',
  exerciseFrequency: '每周 3-4 次'
}

const state = reactive({
  saving: false,
  form: JSON.parse(JSON.stringify(DEFAULT_FORM)) as typeof DEFAULT_FORM
})

const userStore = useUserStore()

/** 血型 picker 当前索引 */
const bloodIndex = computed(() => {
  const i = bloodTypes.indexOf(state.form.bloodType)
  return i < 0 ? 0 : i
})

/** BMI 计算 */
const bmiText = computed(() => {
  const h = parseFloat(state.form.height)
  const w = parseFloat(state.form.weight)
  if (!h || !w || h <= 0) return '--'
  const bmi = w / Math.pow(h / 100, 2)
  return `${bmi.toFixed(1)}（${bmiLevel(bmi)}）`
})

/** BMI 等级 */
function bmiLevel(bmi: number): string {
  if (bmi < 18.5) return '偏瘦'
  if (bmi < 24) return '正常'
  if (bmi < 28) return '超重'
  return '肥胖'
}

function onBloodChange(e: { detail: { value: number | string } }) {
  state.form.bloodType = bloodTypes[Number(e.detail.value)]
}

function onSleepChange(e: { detail: { value: string } }) {
  state.form.sleepTarget = e.detail.value
}

function onWakeChange(e: { detail: { value: string } }) {
  state.form.wakeTarget = e.detail.value
}

/** 切换慢性病 */
function toggleChronic(v: string) {
  const i = state.form.chronic.indexOf(v)
  if (i >= 0) state.form.chronic.splice(i, 1)
  else state.form.chronic.push(v)
}

/** 切换过敏源 */
function toggleAllergy(v: string) {
  const i = state.form.allergies.indexOf(v)
  if (i >= 0) state.form.allergies.splice(i, 1)
  else state.form.allergies.push(v)
}

/** 组装提交载荷 */
function buildPayload(): Record<string, unknown> {
  return {
    height: Number(state.form.height) || 0,
    weight: Number(state.form.weight) || 0,
    bloodType: state.form.bloodType,
    chronicDiseases: state.form.chronic,
    allergies: state.form.allergies.concat(state.form.allergyOther ? [state.form.allergyOther] : []),
    sleepTarget: state.form.sleepTarget,
    wakeTarget: state.form.wakeTarget,
    exerciseFrequency: state.form.exerciseFrequency
  }
}

/** 保存：先写本地缓存，再调接口 */
async function submit() {
  if (state.saving) return
  state.saving = true
  const payload = buildPayload()

  // 1. 本地存储（接口不可用时数据也不丢失）
  uni.setStorageSync(PROFILE_KEY, payload)

  try {
    await authApi.updateProfile(payload)
    await userStore.refreshProfile()
    uni.showToast({ title: '档案已保存', icon: 'success' })
  } catch {
    // ★ 兜底：仅本地保存
    uni.showToast({ title: '已保存到本地', icon: 'none' })
  } finally {
    state.saving = false
  }
}

/** 恢复默认值 */
function resetForm() {
  uni.showModal({
    title: '恢复默认',
    content: '确认恢复为默认档案内容？',
    success: (res) => {
      if (!res.confirm) return
      state.form = JSON.parse(JSON.stringify(DEFAULT_FORM)) as typeof DEFAULT_FORM
      uni.showToast({ title: '已恢复默认', icon: 'none' })
    }
  })
}

/** 加载档案：本地缓存优先，其次用户信息 */
function loadProfile() {
  const cached = uni.getStorageSync(PROFILE_KEY) as Record<string, unknown> | ''
  if (cached && typeof cached === 'object') {
    const c = cached as Record<string, unknown>
    state.form.bloodType = (c.bloodType as string) || state.form.bloodType
    state.form.height = String(c.height || state.form.height)
    state.form.weight = String(c.weight || state.form.weight)
    state.form.chronic = Array.isArray(c.chronicDiseases) ? (c.chronicDiseases as string[]) : state.form.chronic
    state.form.allergies = Array.isArray(c.allergies) ? (c.allergies as string[]) : state.form.allergies
    state.form.sleepTarget = (c.sleepTarget as string) || state.form.sleepTarget
    state.form.wakeTarget = (c.wakeTarget as string) || state.form.wakeTarget
    state.form.exerciseFrequency = (c.exerciseFrequency as string) || state.form.exerciseFrequency
    return
  }
  // 没有本地缓存时用兜底数据（界面不会空白）
}

onLoad(() => {
  loadProfile()
})
</script>

<style lang="scss" scoped>
.page { padding: 32rpx 24rpx calc(120rpx + env(safe-area-inset-bottom)); }

.page-header { display: flex; align-items: center; gap: 16rpx; margin-bottom: 32rpx; }
.header-icon { font-size: 48rpx; }
.header-text { display: flex; flex-direction: column; }
.page-title { font-size: 44rpx; font-weight: 700; color: var(--text-1); }
.page-subtitle { font-size: 26rpx; color: var(--text-2); margin-top: 6rpx; }

.form-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 28rpx 0; border-bottom: 2rpx solid var(--divider);
}
.form-row.no-border { border-bottom: none; }
.form-label { font-size: 28rpx; color: var(--text-2); flex-shrink: 0; }
.form-input {
  flex: 1; text-align: right; font-size: 30rpx; color: var(--text-1);
}
.ph { color: var(--text-3); font-size: 28rpx; }

.picker-value {
  display: flex; align-items: center; gap: 12rpx;
  font-size: 30rpx; color: var(--text-1);
}
.picker-value .placeholder { color: var(--text-3); }
.arrow { font-size: 34rpx; color: var(--text-3); }

.bmi-box {
  margin-top: 20rpx; padding: 20rpx 24rpx;
  background: var(--primary-l); border-radius: var(--radius-sm);
}
.bmi-text { font-size: 26rpx; color: var(--primary-d); }

.chip-wrap { display: flex; flex-wrap: wrap; gap: 16rpx; }
.chip {
  padding: 14rpx 28rpx; border-radius: var(--radius-full);
  background: var(--surface-2); color: var(--text-2); font-size: 26rpx;
  border: 2rpx solid transparent;
}
.chip.active {
  background: var(--primary-l); color: var(--primary-d);
  border-color: var(--primary); font-weight: 600;
}

.hint-text { display: block; font-size: 22rpx; color: var(--text-3); margin-top: 16rpx; }
.custom-row { margin-top: 16rpx; border-top: 2rpx solid var(--divider); }

.reset-btn { margin-top: 24rpx; }
</style>
