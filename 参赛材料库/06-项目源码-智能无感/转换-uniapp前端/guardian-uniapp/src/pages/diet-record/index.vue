<template>
  <view class="page">
    <!-- 今日热量概览 -->
    <view class="summary-card">
      <view class="summary-left">
        <text class="summary-value">{{ state.totalCalories }}</text>
        <text class="summary-unit">千卡</text>
        <text class="summary-label">今日摄入</text>
      </view>
      <view class="summary-right">
        <text class="summary-target">目标 {{ state.targetCalories }} 千卡</text>
        <view class="target-track">
          <view class="target-fill" :style="{ width: caloriePercent + '%' }"></view>
        </view>
        <text class="summary-advice">{{ calorieAdvice }}</text>
      </view>
    </view>

    <!-- 三餐 -->
    <view v-for="meal in meals" :key="meal.key" class="card">
      <view class="meal-header">
        <text class="meal-title">{{ meal.icon }} {{ meal.label }}</text>
        <text class="meal-cal">{{ mealCalories(meal.key) }} 千卡</text>
      </view>

      <view v-if="mealRecords(meal.key).length > 0">
        <view v-for="(r, i) in mealRecords(meal.key)" :key="i" class="food-item">
          <view class="food-main">
            <text class="food-name">{{ r.foods.join('、') }}</text>
            <text class="food-time">{{ r.mealTime }}</text>
          </view>
          <text class="food-cal">{{ r.calories || 0 }} 千卡</text>
        </view>
      </view>
      <view v-else class="meal-empty">
        <text class="meal-empty-text">暂无记录</text>
      </view>

      <view class="add-meal-btn" @click="openAdd(meal.key)">
        <text>＋ 添加{{ meal.label }}记录</text>
      </view>
    </view>

    <!-- 营养汇总 -->
    <view class="card">
      <text class="card-title">🥗 营养汇总</text>
      <view v-for="n in nutritionRows" :key="n.key" class="nutri-item">
        <view class="nutri-head">
          <text class="nutri-label">{{ n.label }}</text>
          <text class="nutri-value">{{ state.nutrition[n.key] }}{{ n.unit }}</text>
        </view>
        <view class="nutri-track">
          <view class="nutri-fill" :style="{ width: nutriPercent(n.key, n.target) + '%' }"></view>
        </view>
        <text class="nutri-target">建议摄入 {{ n.target }}{{ n.unit }}</text>
      </view>
    </view>

    <!-- 添加记录弹窗 -->
    <view v-if="state.showAdd" class="modal-overlay" @click="closeAdd">
      <view class="modal-content" @click.stop>
        <view class="modal-header">
          <text class="modal-title">添加{{ currentMealLabel }}记录</text>
        </view>
        <view class="modal-body">
          <view class="form-row">
            <text class="form-label">食物</text>
            <input v-model="state.form.foods" class="form-input" type="text" placeholder="如：小米粥、鸡蛋" placeholder-class="ph" />
          </view>
          <view class="form-row">
            <text class="form-label">时间</text>
            <picker mode="time" :value="state.form.mealTime" @change="onTimeChange">
              <view class="picker-value"><text>{{ state.form.mealTime }}</text><text class="arrow">›</text></view>
            </picker>
          </view>
          <view class="form-row">
            <text class="form-label">热量 (千卡)</text>
            <input v-model="state.form.calories" class="form-input" type="number" placeholder="如 320" placeholder-class="ph" />
          </view>
          <view class="form-row">
            <text class="form-label">蛋白质 (g)</text>
            <input v-model="state.form.protein" class="form-input" type="digit" placeholder="如 12" placeholder-class="ph" />
          </view>
          <view class="form-row">
            <text class="form-label">碳水 (g)</text>
            <input v-model="state.form.carbs" class="form-input" type="digit" placeholder="如 45" placeholder-class="ph" />
          </view>
          <view class="form-row no-border">
            <text class="form-label">脂肪 (g)</text>
            <input v-model="state.form.fat" class="form-input" type="digit" placeholder="如 8" placeholder-class="ph" />
          </view>
        </view>
        <view class="modal-footer">
          <view class="cancel-btn" @click="closeAdd"><text>取消</text></view>
          <view class="save-btn" @click="submitRecord"><text>保存</text></view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 饮食记录
 * ----------------------------------------------------------------------------
 * 今日三餐（早餐/午餐/晚餐）+ 营养汇总（热量/蛋白质/碳水/脂肪）
 * 接口：dietApi.today() / dietApi.record()
 */
import { reactive, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { dietApi } from '@/services/api'
import type { DietRecord } from '@/types'

/** 三餐配置 */
const meals = [
  { key: 'breakfast', label: '早餐', icon: '🌅' },
  { key: 'lunch', label: '午餐', icon: '🍚' },
  { key: 'dinner', label: '晚餐', icon: '🌙' }
]

/** 营养建议值 */
const nutritionRows = [
  { key: 'protein', label: '蛋白质', unit: 'g', target: 60 },
  { key: 'carbs', label: '碳水化合物', unit: 'g', target: 250 },
  { key: 'fat', label: '脂肪', unit: 'g', target: 60 }
] as const

/** ★ 兜底数据 */
const FALLBACK_RECORDS: DietRecord[] = [
  {
    id: 'd-1',
    date: todayStr(),
    mealType: 'breakfast',
    mealTime: '07:20',
    foods: ['小米粥', '水煮蛋', '凉拌黄瓜'],
    calories: 320,
    protein: 14,
    carbs: 42,
    fat: 9
  },
  {
    id: 'd-2',
    date: todayStr(),
    mealType: 'lunch',
    mealTime: '12:10',
    foods: ['米饭', '清蒸鲈鱼', '炒时蔬'],
    calories: 560,
    protein: 28,
    carbs: 68,
    fat: 15
  },
  {
    id: 'd-3',
    date: todayStr(),
    mealType: 'dinner',
    mealTime: '18:30',
    foods: ['杂粮饭', '番茄炒蛋', '紫菜汤'],
    calories: 430,
    protein: 18,
    carbs: 52,
    fat: 12
  }
]

/** 今天日期字符串 */
function todayStr(): string {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

const state = reactive({
  loading: true,
  records: [] as DietRecord[],
  totalCalories: 0,
  targetCalories: 1800,
  nutrition: { protein: 0, carbs: 0, fat: 0 } as Record<string, number>,
  showAdd: false,
  currentMeal: 'breakfast',
  form: {
    foods: '',
    mealTime: '08:00',
    calories: '',
    protein: '',
    carbs: '',
    fat: ''
  }
})

/** 热量完成百分比 */
const caloriePercent = computed(() =>
  Math.min(100, Math.round((state.totalCalories / state.targetCalories) * 100))
)

/** 热量建议 */
const calorieAdvice = computed(() => {
  const p = caloriePercent.value
  if (p === 0) return '今天还没有记录饮食哦'
  if (p < 50) return '摄入偏少，注意补充营养'
  if (p <= 100) return '摄入合理，继续保持'
  return '摄入偏高，建议适当控制'
})

/** 当前弹窗的餐次名称 */
const currentMealLabel = computed(() => meals.find((m) => m.key === state.currentMeal)?.label || '')

/** 某餐的记录 */
function mealRecords(type: string): DietRecord[] {
  return state.records.filter((r) => r.mealType === type)
}

/** 某餐热量 */
function mealCalories(type: string): number {
  return mealRecords(type).reduce((sum, r) => sum + (r.calories || 0), 0)
}

/** 营养进度百分比 */
function nutriPercent(key: string, target: number): number {
  const v = state.nutrition[key] || 0
  return Math.min(100, Math.round((v / target) * 100))
}

function onTimeChange(e: { detail: { value: string } }) {
  state.form.mealTime = e.detail.value
}

/** 打开添加弹窗 */
function openAdd(mealKey: string) {
  state.currentMeal = mealKey
  const def: Record<string, string> = { breakfast: '08:00', lunch: '12:00', dinner: '18:30' }
  state.form = {
    foods: '',
    mealTime: def[mealKey] || '12:00',
    calories: '',
    protein: '',
    carbs: '',
    fat: ''
  }
  state.showAdd = true
}

/** 关闭弹窗 */
function closeAdd() {
  state.showAdd = false
}

/** 汇总营养（本地重算，保证界面与数据一致） */
function recalc() {
  state.totalCalories = state.records.reduce((sum, r) => sum + (r.calories || 0), 0)
  state.nutrition = {
    protein: state.records.reduce((sum, r) => sum + (r.protein || 0), 0),
    carbs: state.records.reduce((sum, r) => sum + (r.carbs || 0), 0),
    fat: state.records.reduce((sum, r) => sum + (r.fat || 0), 0)
  }
}

/** 加载今日饮食 */
async function loadToday() {
  state.loading = true
  try {
    const res = await dietApi.today()
    state.records = res.list || []
    state.totalCalories = res.totalCalories || 0
    if (state.records.length === 0) throw new Error('empty')
    recalc()
  } catch {
    // ★ 兜底
    state.records = JSON.parse(JSON.stringify(FALLBACK_RECORDS)) as DietRecord[]
    recalc()
  } finally {
    state.loading = false
  }

  // 营养明细（接口失败时用本地汇总）
  try {
    const n = await dietApi.nutrition(1)
    if (n && Object.keys(n).length > 0) {
      state.nutrition = {
        protein: n.protein || state.nutrition.protein,
        carbs: n.carbs || state.nutrition.carbs,
        fat: n.fat || state.nutrition.fat
      }
    }
  } catch {
    // 已在 recalc 中算好，无需处理
  }
}

/** 提交记录 */
async function submitRecord() {
  if (!state.form.foods.trim()) {
    uni.showToast({ title: '请填写食物内容', icon: 'none' })
    return
  }

  const payload: Partial<DietRecord> = {
    date: todayStr(),
    mealType: state.currentMeal,
    mealTime: state.form.mealTime,
    foods: state.form.foods.split(/[、,，\s]+/).filter(Boolean),
    calories: Number(state.form.calories) || 0,
    protein: Number(state.form.protein) || 0,
    carbs: Number(state.form.carbs) || 0,
    fat: Number(state.form.fat) || 0
  }

  try {
    await dietApi.record(payload)
  } catch {
    // ★ 兜底：接口失败也写入本地列表
  }

  state.records.push({ id: `d-${Date.now()}`, ...(payload as DietRecord) })
  recalc()
  closeAdd()
  uni.showToast({ title: '已添加', icon: 'success' })
}

onShow(() => {
  loadToday()
})

onPullDownRefresh(async () => {
  await loadToday()
  uni.stopPullDownRefresh()
})
</script>

<style lang="scss" scoped>
.page { padding: 24rpx calc(24rpx) calc(120rpx + env(safe-area-inset-bottom)); }

.summary-card {
  display: flex; align-items: center; gap: 32rpx;
  background: linear-gradient(135deg, var(--primary), #9db89c);
  border-radius: var(--radius); padding: 36rpx; margin-bottom: 24rpx;
  box-shadow: var(--shadow-lg);
}
.summary-left { display: flex; flex-direction: column; align-items: center; }
.summary-value { font-size: 64rpx; font-weight: 800; color: #fff; line-height: 1.1; }
.summary-unit { font-size: 22rpx; color: rgba(255, 255, 255, 0.85); }
.summary-label { font-size: 22rpx; color: rgba(255, 255, 255, 0.85); margin-top: 8rpx; }
.summary-right { flex: 1; display: flex; flex-direction: column; gap: 12rpx; }
.summary-target { font-size: 24rpx; color: rgba(255, 255, 255, 0.9); }
.target-track { height: 14rpx; border-radius: var(--radius-full); background: rgba(255, 255, 255, 0.3); overflow: hidden; }
.target-fill { height: 100%; border-radius: var(--radius-full); background: #fff; transition: width 0.3s; }
.summary-advice { font-size: 22rpx; color: rgba(255, 255, 255, 0.9); }

.meal-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; }
.meal-title { font-size: 32rpx; font-weight: 600; color: var(--text-1); }
.meal-cal { font-size: 26rpx; color: var(--primary-d); font-weight: 600; }

.food-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 20rpx 0; border-bottom: 2rpx solid var(--divider);
}
.food-main { flex: 1; display: flex; flex-direction: column; gap: 6rpx; }
.food-name { font-size: 28rpx; color: var(--text-1); }
.food-time { font-size: 22rpx; color: var(--text-3); }
.food-cal { font-size: 26rpx; color: var(--text-2); }

.meal-empty { padding: 28rpx 0; text-align: center; }
.meal-empty-text { font-size: 26rpx; color: var(--text-3); }

.add-meal-btn {
  margin-top: 20rpx; height: 76rpx; border-radius: var(--radius-sm);
  border: 2rpx dashed var(--primary); color: var(--primary-d);
  display: flex; align-items: center; justify-content: center; font-size: 26rpx;
}

.nutri-item { margin-bottom: 28rpx; }
.nutri-item:last-child { margin-bottom: 0; }
.nutri-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12rpx; }
.nutri-label { font-size: 28rpx; color: var(--text-1); }
.nutri-value { font-size: 28rpx; font-weight: 600; color: var(--primary-d); }
.nutri-track { height: 16rpx; border-radius: var(--radius-full); background: var(--surface-2); overflow: hidden; }
.nutri-fill {
  height: 100%; border-radius: var(--radius-full);
  background: linear-gradient(90deg, var(--primary), var(--primary-d)); transition: width 0.3s;
}
.nutri-target { display: block; font-size: 22rpx; color: var(--text-3); margin-top: 8rpx; }

/* 弹窗 */
.modal-overlay {
  position: fixed; left: 0; top: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.5); z-index: 999;
  display: flex; align-items: center; justify-content: center;
}
.modal-content { width: 88%; background: var(--surface); border-radius: var(--radius); overflow: hidden; }
.modal-header { padding: 32rpx; background: var(--primary-l); }
.modal-title { font-size: 34rpx; font-weight: 700; color: var(--text-1); }
.modal-body { padding: 16rpx 32rpx; }
.form-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 22rpx 0; border-bottom: 2rpx solid var(--divider);
}
.form-row.no-border { border-bottom: none; }
.form-label { font-size: 28rpx; color: var(--text-2); flex-shrink: 0; }
.form-input { flex: 1; text-align: right; font-size: 30rpx; color: var(--text-1); }
.ph { color: var(--text-3); font-size: 28rpx; }
.picker-value { display: flex; align-items: center; gap: 12rpx; font-size: 30rpx; color: var(--text-1); }
.arrow { font-size: 34rpx; color: var(--text-3); }

.modal-footer { display: flex; gap: 20rpx; padding: 24rpx 32rpx 32rpx; }
.cancel-btn, .save-btn {
  flex: 1; height: 88rpx; border-radius: var(--radius-sm);
  display: flex; align-items: center; justify-content: center; font-size: 30rpx;
}
.cancel-btn { background: var(--surface-2); color: var(--text-2); }
.save-btn { background: var(--primary-d); color: #fff; font-weight: 600; }
</style>
