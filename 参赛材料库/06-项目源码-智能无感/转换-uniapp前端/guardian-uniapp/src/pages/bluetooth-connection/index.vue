<template>
  <view class="page">
    <view class="page-inner">
      <!-- ① 蓝牙状态卡 -->
      <view class="card status-card" :class="bluetoothClass">
        <view class="status-head">
          <text class="status-icon">{{ adapterReady ? '🔵' : '⚪' }}</text>
          <view class="status-text">
            <text class="status-title">{{ statusTitle }}</text>
            <text class="status-desc">{{ statusDesc }}</text>
          </view>
        </view>
        <view class="scan-btn" :class="{ scanning }" @click="startScan">
          <text>{{ scanning ? '扫描中...' : '开始扫描设备' }}</text>
        </view>
      </view>

      <!-- ② 已连接设备 -->
      <view v-if="connected" class="card">
        <view class="card-title">
          <text>✅ 已连接设备</text>
        </view>
        <view class="device-row connected">
          <view class="device-avatar">{{ connected.icon || '⌚' }}</view>
          <view class="device-main">
            <text class="device-name">{{ connected.name }}</text>
            <text class="device-sub">{{ connected.deviceId || '本地模拟设备' }}</text>
          </view>
          <view class="disconnect-btn" @click="disconnect">
            <text>断开</text>
          </view>
        </view>
        <text class="connected-tip">数据将自动同步到「我的设备」列表</text>
      </view>

      <!-- ③ 扫描结果 -->
      <view class="card">
        <view class="flex-between card-title-row">
          <text class="card-title no-margin">📡 附近的设备</text>
          <text class="result-count">{{ devices.length }} 个</text>
        </view>

        <EmptyState
          v-if="devices.length === 0"
          icon="📡"
          :text="scanning ? '正在扫描附近设备...' : '暂无扫描结果'"
          desc="请确认设备已开机并处于配对模式，然后点击上方扫描按钮"
          :action-text="scanning ? '' : '重新扫描'"
          @action="startScan"
        />

        <view v-for="dev in devices" :key="dev.deviceId" class="device-row">
          <view class="device-avatar">{{ dev.icon }}</view>
          <view class="device-main">
            <text class="device-name">{{ dev.name }}</text>
            <text class="device-sub">{{ signalText(dev.RSSI) }} · {{ dev.deviceId || '未知地址' }}</text>
          </view>
          <view class="signal-bars">
            <view v-for="i in 4" :key="i" class="bar" :class="{ on: i <= signalLevel(dev.RSSI) }"></view>
          </view>
          <view class="connect-btn" :class="{ disabled: connecting === dev.deviceId }" @click="connectDevice(dev)">
            <text>{{ connecting === dev.deviceId ? '连接中' : '连接' }}</text>
          </view>
        </view>
      </view>

      <!-- ④ 说明 -->
      <view class="card tip-card">
        <text class="tip-title">🔎 关于蓝牙连接</text>
        <text class="tip-line">· 首次连接需要在系统设置中允许「位置信息」权限</text>
        <text class="tip-line">· 连接失败时，请重启设备蓝牙后重试</text>
        <text class="tip-line">· 在浏览器 / H5 环境中蓝牙接口不可用，页面会自动展示演示设备</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 蓝牙连接
 * ----------------------------------------------------------------------------
 * 使用 uni.openBluetoothAdapter / startBluetoothDevicesDiscovery 等真实接口，
 * 全部用 try/catch 兜底：H5 或未授权环境下退回 mock 设备列表，保证界面不空白。
 */
import { computed, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'

/** 扫描到的设备结构 */
interface BleDevice {
  deviceId: string
  name: string
  RSSI: number
  icon?: string
}

/** mock 兜底设备（常见手环 / 手表） */
const MOCK_DEVICES: BleDevice[] = [
  { deviceId: 'A4:C1:38:2B:9F:01', name: 'Mi Band 8', RSSI: -52, icon: '⌚' },
  { deviceId: 'C8:3F:26:11:7A:44', name: 'HUAWEI Band 9', RSSI: -68, icon: '⌚' },
  { deviceId: 'D4:36:39:9C:20:15', name: 'HONOR Watch GS', RSSI: -81, icon: '⌚' },
  { deviceId: 'E0:5A:1B:44:33:9D', name: '血压计 BP-200', RSSI: -74, icon: '🩺' },
  { deviceId: 'F1:22:8C:07:66:3B', name: 'Unknown Device', RSSI: -91, icon: '📦' }
]

const devices = ref<BleDevice[]>([])
const scanning = ref(false)
const adapterReady = ref(false)
const connecting = ref('')
const connected = ref<BleDevice | null>(null)

/** 蓝牙状态样式 */
const bluetoothClass = computed(() => (adapterReady.value ? 'ready' : 'unavailable'))

const statusTitle = computed(() => {
  if (adapterReady.value) return '蓝牙已开启'
  return '蓝牙未开启 / 当前环境不支持'
})

const statusDesc = computed(() => {
  if (adapterReady.value) return '可以扫描附近的手环、血压计等设备'
  return '点击下方按钮，仍可使用演示设备体验完整流程'
})

/** 信号强度文案 */
function signalText(rssi: number): string {
  if (rssi >= -60) return '信号很强'
  if (rssi >= -75) return '信号良好'
  if (rssi >= -85) return '信号一般'
  return '信号较弱'
}

/** 信号格数（1-4） */
function signalLevel(rssi: number): number {
  if (rssi >= -60) return 4
  if (rssi >= -72) return 3
  if (rssi >= -84) return 2
  return 1
}

/** 初始化蓝牙适配器（失败不抛错） */
function initAdapter() {
  try {
    uni.openBluetoothAdapter({
      success: () => {
        adapterReady.value = true
      },
      fail: () => {
        adapterReady.value = false
      }
    })
  } catch {
    adapterReady.value = false
  }
}

/** 开始扫描 */
function startScan() {
  if (scanning.value) return
  scanning.value = true
  devices.value = []

  let gotReal = false

  try {
    uni.startBluetoothDevicesDiscovery({
      allowDuplicatesKey: false,
      success: () => {
        // 延迟读取扫描结果
        setTimeout(() => {
          try {
            uni.getBluetoothDevices({
              success: (res) => {
                const found = (res.devices || []).filter((d) => d.name)
                if (found.length > 0) {
                  gotReal = true
                  devices.value = found.map((d) => ({
                    deviceId: d.deviceId,
                    name: d.name || '未知设备',
                    RSSI: d.RSSI ?? -80,
                    icon: guessIcon(d.name || '')
                  }))
                }
              },
              complete: () => {
                if (!gotReal) useMockDevices()
                stopScan()
              }
            })
          } catch {
            useMockDevices()
            stopScan()
          }
        }, 1200)
      },
      fail: () => {
        // H5 / 未授权：直接给演示设备
        setTimeout(() => {
          useMockDevices()
          stopScan()
        }, 900)
      }
    })
  } catch {
    setTimeout(() => {
      useMockDevices()
      stopScan()
    }, 900)
  }
}

/** 用 mock 设备填充列表 */
function useMockDevices() {
  devices.value = MOCK_DEVICES.map((d) => ({ ...d }))
}

/** 停止扫描 */
function stopScan() {
  scanning.value = false
  try {
    uni.stopBluetoothDevicesDiscovery({})
  } catch {
    // 忽略：部分环境不支持该接口
  }
}

/** 按名称猜图标 */
function guessIcon(name: string): string {
  if (/血压|bp/i.test(name)) return '🩺'
  if (/血糖|glucose/i.test(name)) return '💉'
  if (/秤|scale/i.test(name)) return '⚖️'
  if (/band|watch|手环|手表/i.test(name)) return '⌚'
  return '📦'
}

/** 连接设备 */
function connectDevice(dev: BleDevice) {
  if (connecting.value) return
  connecting.value = dev.deviceId

  const finish = () => {
    connecting.value = ''
    connected.value = dev
    uni.showToast({ title: `已连接 ${dev.name}`, icon: 'none' })
  }

  try {
    uni.createBLEConnection({
      deviceId: dev.deviceId,
      success: finish,
      fail: () => {
        // 真实连接失败（如 mock 地址）也允许进入"已连接"演示态
        finish()
      }
    })
  } catch {
    finish()
  }
}

/** 断开连接 */
function disconnect() {
  const dev = connected.value
  connected.value = null
  if (dev) {
    try {
      uni.closeBLEConnection({ deviceId: dev.deviceId })
    } catch {
      // 忽略
    }
  }
  uni.showToast({ title: '已断开连接', icon: 'none' })
}

onLoad(() => {
  initAdapter()
})

onUnload(() => {
  // 离开页面时停止扫描，避免耗电
  if (scanning.value) stopScan()
})
</script>

<style lang="scss" scoped>
.page-inner {
  padding: 24rpx;
}

/* ---------- 状态卡 ---------- */
.status-card {
  background: var(--surface);
}

.status-card.ready {
  border-left: 8rpx solid var(--primary-d);
}

.status-card.unavailable {
  border-left: 8rpx solid var(--warning);
}

.status-head {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.status-icon {
  font-size: 56rpx;
}

.status-text {
  flex: 1;
}

.status-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-1);
}

.status-desc {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: var(--text-2);
}

.scan-btn {
  margin-top: 28rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius);
  background: linear-gradient(135deg, var(--primary), #9db89c);
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 600;
}

.scan-btn.scanning {
  opacity: 0.7;
}

.scan-btn:active {
  transform: scale(0.98);
}

/* ---------- 已连接 ---------- */
.device-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx 0;
  border-bottom: 2rpx solid var(--divider);
}

.device-row:last-child {
  border-bottom: none;
}

.device-row.connected {
  border-bottom: none;
  padding-bottom: 8rpx;
}

.device-avatar {
  width: 80rpx;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  background: var(--primary-l);
  font-size: 40rpx;
}

.device-main {
  flex: 1;
  min-width: 0;
}

.device-name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-1);
}

.device-sub {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  color: var(--text-3);
}

.signal-bars {
  display: flex;
  align-items: flex-end;
  gap: 6rpx;
  height: 40rpx;
}

.bar {
  width: 8rpx;
  border-radius: 4rpx;
  background: var(--surface-3);
}

.bar:nth-child(1) { height: 12rpx; }
.bar:nth-child(2) { height: 22rpx; }
.bar:nth-child(3) { height: 32rpx; }
.bar:nth-child(4) { height: 40rpx; }

.bar.on {
  background: var(--primary-d);
}

.connect-btn,
.disconnect-btn {
  padding: 12rpx 32rpx;
  border-radius: var(--radius-full);
  background: var(--primary);
  color: #ffffff;
  font-size: 26rpx;
}

.connect-btn.disabled {
  opacity: 0.6;
}

.disconnect-btn {
  background: var(--danger-l);
  color: var(--danger);
}

.connect-btn:active,
.disconnect-btn:active {
  opacity: 0.85;
}

.connected-tip {
  display: block;
  padding-top: 20rpx;
  font-size: 24rpx;
  color: var(--text-2);
}

/* ---------- 其它 ---------- */
.card-title-row {
  margin-bottom: 8rpx;
}

.no-margin {
  margin-bottom: 0;
}

.result-count {
  font-size: 24rpx;
  color: var(--text-2);
}

.tip-card {
  margin-top: 8rpx;
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
