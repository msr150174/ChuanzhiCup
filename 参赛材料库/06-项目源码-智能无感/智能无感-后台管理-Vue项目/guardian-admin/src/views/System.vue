<script setup lang="ts">
import { Cpu, RefreshRight, Timer } from '@element-plus/icons-vue'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { systemApi } from '@/api'
import type { SystemInfo } from '@/types'
import { formatDateTime, formatUptime } from '@/utils/format'

const loading = ref(false)
const autoRefresh = ref(true)
const systemInfo = ref<SystemInfo | null>(null)
const lastUpdated = ref('')
let timer: ReturnType<typeof setInterval> | null = null

async function loadSystemInfo(showLoading = true) {
  if (showLoading) loading.value = true
  try {
    systemInfo.value = await systemApi.info()
    lastUpdated.value = formatDateTime(new Date().toISOString())
  } finally {
    loading.value = false
  }
}

function startTimer() {
  stopTimer()
  if (autoRefresh.value) {
    timer = setInterval(() => loadSystemInfo(false), 30000)
  }
}

function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function toggleRefresh(value: boolean) {
  autoRefresh.value = value
  startTimer()
}

function memoryPercent(): number {
  if (!systemInfo.value) return 0
  const { usedMemoryMB, maxMemoryMB } = systemInfo.value.runtime
  return Math.round((usedMemoryMB / maxMemoryMB) * 100)
}

onMounted(async () => {
  await loadSystemInfo()
  startTimer()
})

onBeforeUnmount(stopTimer)
</script>

<template>
  <div v-loading="loading" class="system-page">
    <section class="card system-card">
      <div class="section-heading system-heading">
        <div>
          <h2>系统监控</h2>
          <p>每 30 秒静默刷新，不打断当前查看状态</p>
        </div>
        <div class="system-tools">
          <span v-if="lastUpdated" class="last-updated">最近更新：{{ lastUpdated }}</span>
          <el-switch
            :model-value="autoRefresh"
            active-text="自动刷新"
            inline-prompt
            @change="toggleRefresh"
          />
          <el-button :icon="RefreshRight" :loading="loading" @click="loadSystemInfo()">立即刷新</el-button>
        </div>
      </div>
    </section>

    <div v-if="systemInfo" class="system-grid">
      <section class="card monitor-card">
        <div class="monitor-title">
          <span class="monitor-icon database"><el-icon><Cpu /></el-icon></span>
          <div>
            <h3>数据库</h3>
            <p>{{ systemInfo.database.name }} {{ systemInfo.database.version }}</p>
          </div>
          <el-tag :type="systemInfo.database.connected ? 'success' : 'danger'" effect="light">
            {{ systemInfo.database.connected ? '连接正常' : '连接异常' }}
          </el-tag>
        </div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="连接状态">
            {{ systemInfo.database.connected ? '已连接' : '未连接' }}
          </el-descriptions-item>
          <el-descriptions-item label="用户数">{{ systemInfo.database.users }} 人</el-descriptions-item>
        </el-descriptions>
      </section>

      <section class="card monitor-card">
        <div class="monitor-title">
          <span class="monitor-icon cache"><el-icon><Timer /></el-icon></span>
          <div>
            <h3>缓存</h3>
            <p>{{ systemInfo.cache.name }} {{ systemInfo.cache.version }}</p>
          </div>
          <el-tag :type="systemInfo.cache.connected ? 'success' : 'danger'" effect="light">
            {{ systemInfo.cache.connected ? '连接正常' : '连接异常' }}
          </el-tag>
        </div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="连接状态">
            {{ systemInfo.cache.connected ? '已连接' : '未连接' }}
          </el-descriptions-item>
          <el-descriptions-item label="键数量">{{ systemInfo.cache.keyCount.toLocaleString() }} 个</el-descriptions-item>
        </el-descriptions>
      </section>

      <section class="card monitor-card runtime-card">
        <div class="monitor-title">
          <span class="monitor-icon runtime"><el-icon><Cpu /></el-icon></span>
          <div>
            <h3>运行时</h3>
            <p>{{ systemInfo.runtime.javaVersion }}</p>
          </div>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="最大内存">{{ systemInfo.runtime.maxMemoryMB }} MB</el-descriptions-item>
          <el-descriptions-item label="已用内存">{{ systemInfo.runtime.usedMemoryMB }} MB</el-descriptions-item>
          <el-descriptions-item label="可用内存">{{ systemInfo.runtime.freeMemoryMB }} MB</el-descriptions-item>
          <el-descriptions-item label="CPU 核心">{{ systemInfo.runtime.processors }} 核</el-descriptions-item>
          <el-descriptions-item label="已运行" :span="2">
            {{ formatUptime(systemInfo.runtime.uptimeSeconds) }}
          </el-descriptions-item>
        </el-descriptions>
        <div class="memory-bar">
          <div class="memory-meta">
            <span>JVM 内存占用</span>
            <strong>{{ memoryPercent() }}%</strong>
          </div>
          <el-progress :percentage="memoryPercent()" :stroke-width="10" :show-text="false" color="#8FA68E" />
        </div>
      </section>
    </div>
  </div>
</template>
