<script setup lang="ts">
import { Avatar, Bell, Calendar, CircleCheck, Clock, DataLine, User, UserFilled } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import type { ECharts, EChartsOption } from 'echarts'
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { dashboardApi } from '@/api'
import type { DashboardStats, TrendData } from '@/types'

const stats = ref<DashboardStats | null>(null)
const trend = ref<TrendData | null>(null)
const loading = ref(false)
const days = ref(7)
const chartRef = ref<HTMLDivElement>()
const chart = shallowRef<ECharts>()
let resizeObserver: ResizeObserver | null = null

const userCards = [
  { key: 'totalUsers', label: '用户总数', icon: User, tone: 'green' },
  { key: 'elders', label: '长辈用户', icon: UserFilled, tone: 'beige' },
  { key: 'families', label: '家属用户', icon: Avatar, tone: 'green' },
  { key: 'newThisWeek', label: '本周新增', icon: Calendar, tone: 'beige' },
] as const

const careCards = [
  { key: 'weekAlerts', label: '近 7 天告警', icon: Bell, tone: 'red' },
  { key: 'todayCheckins', label: '今日打卡', icon: CircleCheck, tone: 'green' },
  { key: 'admins', label: '管理员数', icon: DataLine, tone: 'beige' },
] as const

async function loadDashboard() {
  loading.value = true
  try {
    const [dashboardData, trendData] = await Promise.all([
      dashboardApi.stats(),
      dashboardApi.trend(days.value),
    ])
    stats.value = dashboardData
    trend.value = trendData
    renderChart()
  } finally {
    loading.value = false
  }
}

async function changeDays(value: number) {
  days.value = value
  const trendData = await dashboardApi.trend(value)
  trend.value = trendData
  renderChart()
}

function renderChart() {
  if (!chart.value || !trend.value) return

  const option: EChartsOption = {
    color: ['#8FA68E', '#C97064'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#FFFFFF',
      borderColor: '#ECE5D8',
      borderWidth: 1,
      padding: [10, 14],
      textStyle: { color: '#2F2A24', fontSize: 13 },
      extraCssText: 'border-radius: 12px; box-shadow: 0 6px 20px rgba(47,42,36,.10);',
    },
    legend: {
      top: 4,
      right: 8,
      itemWidth: 18,
      itemHeight: 8,
      textStyle: { color: '#7D746B', fontSize: 13 },
    },
    grid: { top: 56, right: 24, bottom: 32, left: 42 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend.value.dates,
      axisLine: { lineStyle: { color: '#ECE5D8' } },
      axisTick: { show: false },
      axisLabel: { color: '#7D746B' },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#F0EADF' } },
      axisLabel: { color: '#7D746B' },
    },
    series: [
      {
        name: '新增用户',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        data: trend.value.newUsers,
        lineStyle: { width: 3, color: '#8FA68E' },
        itemStyle: { color: '#8FA68E', borderColor: '#FFFFFF', borderWidth: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(143, 166, 142, 0.28)' },
            { offset: 1, color: 'rgba(143, 166, 142, 0.03)' },
          ]),
        },
      },
      {
        name: '告警数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        data: trend.value.alerts,
        lineStyle: { width: 3, color: '#C97064' },
        itemStyle: { color: '#C97064', borderColor: '#FFFFFF', borderWidth: 2 },
      },
    ],
  }

  chart.value.setOption(option, true)
}

onMounted(async () => {
  if (chartRef.value) {
    chart.value = echarts.init(chartRef.value)
    resizeObserver = new ResizeObserver(() => chart.value?.resize())
    resizeObserver.observe(chartRef.value)
  }
  await loadDashboard()
})

watch(days, () => undefined)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart.value?.dispose()
})
</script>

<template>
  <div v-loading="loading" class="dashboard-page">
    <section class="card stat-section">
      <div class="section-heading">
        <div>
          <h2>核心指标</h2>
          <p>掌握社区健康守护用户规模</p>
        </div>
      </div>
      <div class="stat-grid stat-grid-four">
        <article v-for="card in userCards" :key="card.key" class="stat-card">
          <div class="stat-icon" :class="card.tone">
            <el-icon><component :is="card.icon" /></el-icon>
          </div>
          <span class="stat-label">{{ card.label }}</span>
          <strong>{{ stats?.[card.key] ?? '--' }}</strong>
        </article>
      </div>
    </section>

    <section class="card stat-section">
      <div class="section-heading">
        <div>
          <h2>健康守护</h2>
          <p>关注近期风险与日常节律执行情况</p>
        </div>
      </div>
      <div class="stat-grid stat-grid-three">
        <article v-for="card in careCards" :key="card.key" class="stat-card">
          <div class="stat-icon" :class="card.tone">
            <el-icon><component :is="card.icon" /></el-icon>
          </div>
          <span class="stat-label">{{ card.label }}</span>
          <strong>{{ stats?.[card.key] ?? '--' }}</strong>
        </article>
      </div>
    </section>

    <section class="card chart-card">
      <div class="section-heading">
        <div>
          <h2>近 {{ days }} 天趋势</h2>
          <p>新增用户与告警数量变化</p>
        </div>
        <el-radio-group :model-value="days" size="default" @change="changeDays">
          <el-radio-button :value="7">7 天</el-radio-button>
          <el-radio-button :value="14">14 天</el-radio-button>
          <el-radio-button :value="30">30 天</el-radio-button>
        </el-radio-group>
      </div>
      <div ref="chartRef" class="trend-chart" />
      <p class="chart-footnote">
        <el-icon><Clock /></el-icon>
        数据来自真实后端接口，切换时间范围后自动重绘
      </p>
    </section>
  </div>
</template>
