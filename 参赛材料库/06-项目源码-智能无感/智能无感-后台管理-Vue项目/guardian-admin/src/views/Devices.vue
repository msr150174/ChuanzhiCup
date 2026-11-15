<script setup lang="ts">
import { Connection, Refresh } from '@element-plus/icons-vue'
import { onMounted, ref } from 'vue'
import { deviceApi } from '@/api'
import type { DeviceItem } from '@/types'
import { deviceTypeLabel, formatDateTime } from '@/utils/format'

const loading = ref(false)
const devices = ref<DeviceItem[]>([])
const total = ref(0)

async function loadDevices() {
  loading.value = true
  try {
    const result = await deviceApi.list()
    devices.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

const onlineCount = () => devices.value.filter((device) => device.status === 'ONLINE').length

onMounted(loadDevices)
</script>

<template>
  <div class="devices-page">
    <section class="card device-summary">
      <div class="summary-icon"><el-icon><Connection /></el-icon></div>
      <div>
        <span>设备总数</span>
        <strong>{{ total }}</strong>
      </div>
      <div class="summary-divider" />
      <div>
        <span>在线设备</span>
        <strong class="success-text">{{ onlineCount() }}</strong>
      </div>
      <el-button :icon="Refresh" :loading="loading" class="summary-refresh" @click="loadDevices">刷新</el-button>
    </section>

    <section class="card table-card">
      <el-table v-loading="loading" :data="devices" class="warm-table">
        <el-table-column prop="deviceName" label="设备名称" min-width="170" />
        <el-table-column label="类型" min-width="130">
          <template #default="{ row }: { row: DeviceItem }">
            <el-tag type="success" effect="plain">{{ deviceTypeLabel(row.deviceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="userName" label="所属用户" min-width="110" />
        <el-table-column prop="userId" label="用户ID" min-width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }: { row: DeviceItem }">
            <el-tag :type="row.status === 'ONLINE' ? 'success' : 'info'" effect="light">
              {{ row.status === 'ONLINE' ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后同步" min-width="170">
          <template #default="{ row }: { row: DeviceItem }">{{ formatDateTime(row.lastSync) }}</template>
        </el-table-column>
        <el-table-column label="绑定时间" min-width="170">
          <template #default="{ row }: { row: DeviceItem }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>
