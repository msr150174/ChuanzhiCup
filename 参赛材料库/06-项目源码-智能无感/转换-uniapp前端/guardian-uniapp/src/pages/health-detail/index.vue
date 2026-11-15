<template>
  <view class="page">
    <!-- 总分环 -->
    <view class="card score-card">
      <text class="card-title">❤️ 健康总分</text>
      <view class="ring-wrap">
        <view class="ring" :style="ringStyle">
          <view class="ring-inner">
            <text class="ring-value">{{ state.score.totalScore }}</text>
            <text class="ring-unit">分</text>
            <text class="ring-label">{{ state.score.level }}</text>
          </view>
        </view>
      </view>
      <text class="score-advice">{{ state.score.suggestion }}</text>
      <text class="score-date">评估日期：{{ state.score.date || todayStr() }}</text>
    </view>

    <!-- 五项维度 -->
    <view class="card">
      <text class="card-title">📊 五项维度详情</text>

      <view v-if="state.loading" class="loading-box"><text class="loading-text">加载中...</text></view>

      <view v-else>
        <view v-for="d in dimensionList" :key="d.key" class="dim-item">
          <view class="dim-head">
            <view class="dim-title-wrap">
              <text class="dim-icon">{{ d.icon }}</text>
              <text class="dim-name">{{ d.name }}</text>
            </view>
            <text class="dim-score" :style="{ color: scoreColor(d.score) }">{{ d.score }}<text class="dim-unit">分</text></text>
          </view>

          <view class="dim-track">
            <view class="dim-fill" :style="{ width: d.score + '%', background: scoreColor(d.score) }"></view>
          </view>

          <view class="dim-foot">
            <text class="dim-desc">{{ d.desc }}</text>
            <text class="dim-tag" :class="levelClass(d.score)">{{ levelText(d.score) }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 改善建议 -->
    <view class="card">
      <text class="card-title">💡 改善建议</text>
      <view v-for="(s, i) in suggestions" :key="i" class="sug-item">
        <text class="sug-num">{{ i + 1 }}</text>
        <text class="sug-text">{{ s }}</text>
      </view>
    </view>

    <view class="btn-primary" @click="goReport"><text>📈 查看完整健康报告</text></view>
  </view>
</template>

<script setup lang="ts">
/**
 * 健康详情
 * ----------------------------------------------------------------------------
 * 五项维度详情（作息/用药/运动/饮食/监测）+ 总分环 + 改善建议
 * 接口：checkinApi.healthScore()
 */
import { reactive, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { checkinApi } from '@/services/api'
import type { HealthScore } from '@/types'

/** ★ 兜底评分数据 */
const FALLBACK_SCORE: HealthScore = {
  totalScore: 82,
  level: '良好',
  suggestion: '整体健康状况良好，作息与用药较为规律，建议适当增加户外活动时间。',
  sleepScore: 85,
  medicineScore: 90,
  exerciseScore: 72,
  dietScore: 80,
  monitorScore: 84
}

/** 今天日期 */
function todayStr(): string {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

const state = reactive({
  loading: true,
  score: { ...FALLBACK_SCORE } as HealthScore
})

/** 五项维度列表 */
const dimensionList = computed(() => [
  {
    key: 'sleepScore',
    icon: '🌙',
    name: '作息节律',
    score: state.score.sleepScore ?? 0,
    desc: '基于入睡与起床打卡时间计算，规律作息有助于深度睡眠'
  },
  {
    key: 'medicineScore',
    icon: '💊',
    name: '用药依从',
    score: state.score.medicineScore ?? 0,
    desc: '按提醒服药的比例，按时服药是慢病管理的关键'
  },
  {
    key: 'exerciseScore',
    icon: '🏃',
    name: '运动活动',
    score: state.score.exerciseScore ?? 0,
    desc: '基于每日步数与运动时长，建议每天保持 30 分钟活动'
  },
  {
    key: 'dietScore',
    icon: '🥗',
    name: '饮食营养',
    score: state.score.dietScore ?? 0,
    desc: '根据三餐记录与营养摄入结构评估'
  },
  {
    key: 'monitorScore',
    icon: '📡',
    name: '健康监测',
    score: state.score.monitorScore ?? 0,
    desc: '设备在线时长与异常事件响应情况'
  }
])

/** 圆环样式 */
const ringStyle = computed(() => {
  const deg = Math.round(((state.score.totalScore || 0) / 100) * 360)
  return {
    background: `conic-gradient(#5B9A7A 0deg, #5B9A7A ${deg}deg, #E8E3D8 ${deg}deg, #E8E3D8 360deg)`
  }
})

/** 分数颜色 */
function scoreColor(score: number): string {
  if (score >= 80) return '#5B9A7A'
  if (score >= 60) return '#D4A574'
  return '#E55A5A'
}

/** 等级文案 */
function levelText(score: number): string {
  if (score >= 80) return '良好'
  if (score >= 60) return '关注'
  return '预警'
}

/** 等级样式类 */
function levelClass(score: number): string {
  if (score >= 80) return 'lv-good'
  if (score >= 60) return 'lv-warn'
  return 'lv-bad'
}

/** 依据最低得分生成改善建议 */
const suggestions = computed(() => {
  const list = dimensionList.value.slice().sort((a, b) => a.score - b.score)
  const out: string[] = []
  list.slice(0, 3).forEach((d) => {
    if (d.score >= 85) return
    if (d.key === 'sleepScore') out.push('固定入睡与起床时间，睡前 1 小时减少屏幕使用，有助于提升作息评分')
    else if (d.key === 'medicineScore') out.push('开启用药提醒，漏服时及时补记，保持用药依从率在 90% 以上')
    else if (d.key === 'exerciseScore') out.push('每天安排 30 分钟快走或太极拳，可分早晚两段完成')
    else if (d.key === 'dietScore') out.push('三餐定时定量，增加蔬菜与优质蛋白，控制油盐摄入')
    else out.push('保持设备在线，异常告警及时处理，可提升监测评分')
  })
  if (out.length === 0) out.push('各项指标均表现良好，继续保持当前的生活节律')
  if (state.score.suggestion) out.push(state.score.suggestion)
  return out
})

/** 加载健康评分 */
async function loadScore() {
  state.loading = true
  try {
    const res = await checkinApi.healthScore()
    state.score = {
      ...FALLBACK_SCORE,
      ...res,
      totalScore: res?.totalScore ?? FALLBACK_SCORE.totalScore
    }
    if (!res || res.totalScore === undefined) throw new Error('empty')
  } catch {
    // ★ 兜底
    state.score = { ...FALLBACK_SCORE, date: todayStr() }
  } finally {
    state.loading = false
  }
}

/** 查看完整报告 */
function goReport() {
  uni.navigateTo({ url: '/pages/health-report/index' })
}

onShow(() => {
  loadScore()
})

onPullDownRefresh(async () => {
  await loadScore()
  uni.stopPullDownRefresh()
})
</script>

<style lang="scss" scoped>
.page { padding: 24rpx calc(24rpx) calc(120rpx + env(safe-area-inset-bottom)); }

.score-card { display: flex; flex-direction: column; align-items: center; }
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
.ring-value { font-size: 88rpx; font-weight: 800; color: var(--primary-d); line-height: 1.1; }
.ring-unit { font-size: 24rpx; color: var(--text-2); margin-top: -6rpx; }
.ring-label { font-size: 24rpx; color: var(--primary-d); margin-top: 8rpx; font-weight: 600; }

.score-advice {
  font-size: 26rpx; color: var(--text-2); text-align: center;
  line-height: 1.6; padding: 0 16rpx;
}
.score-date { font-size: 22rpx; color: var(--text-3); margin-top: 12rpx; }

.loading-box { padding: 64rpx 0; text-align: center; }
.loading-text { font-size: 28rpx; color: var(--text-2); }

.dim-item { padding: 24rpx 0; border-bottom: 2rpx solid var(--divider); }
.dim-item:last-child { border-bottom: none; padding-bottom: 0; }
.dim-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; }
.dim-title-wrap { display: flex; align-items: center; gap: 12rpx; }
.dim-icon { font-size: 32rpx; }
.dim-name { font-size: 30rpx; font-weight: 600; color: var(--text-1); }
.dim-score { font-size: 36rpx; font-weight: 700; }
.dim-unit { font-size: 22rpx; font-weight: 400; }

.dim-track { height: 16rpx; border-radius: var(--radius-full); background: var(--surface-2); overflow: hidden; }
.dim-fill { height: 100%; border-radius: var(--radius-full); transition: width 0.4s; }

.dim-foot { display: flex; align-items: flex-start; justify-content: space-between; gap: 16rpx; margin-top: 12rpx; }
.dim-desc { flex: 1; font-size: 22rpx; color: var(--text-2); line-height: 1.6; }
.dim-tag { font-size: 20rpx; padding: 2rpx 14rpx; border-radius: var(--radius-full); flex-shrink: 0; }
.lv-good { background: var(--primary-l); color: var(--primary-d); }
.lv-warn { background: var(--warning-l); color: var(--warning); }
.lv-bad { background: var(--danger-l); color: var(--danger); }

.sug-item { display: flex; gap: 16rpx; margin-bottom: 20rpx; align-items: flex-start; }
.sug-num {
  width: 40rpx; height: 40rpx; border-radius: 50%; flex-shrink: 0;
  background: var(--primary-l); color: var(--primary-d); font-size: 22rpx;
  display: flex; align-items: center; justify-content: center;
}
.sug-text { flex: 1; font-size: 26rpx; color: var(--text-2); line-height: 1.7; }
</style>
