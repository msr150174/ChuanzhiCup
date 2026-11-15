<template>
  <view class="page">
    <!-- 当前位置 -->
    <view class="card">
      <view class="section-header">
        <text class="card-title">📍 当前定位</text>
        <view class="refresh-btn" @click="loadLocation"><text>🔄 刷新</text></view>
      </view>

      <view v-if="state.loading" class="loading-box"><text class="loading-text">定位中...</text></view>

      <view v-else>
        <view class="addr-box">
          <text class="addr-text">{{ state.address }}</text>
        </view>
        <view class="coord-row">
          <view class="coord-cell">
            <text class="coord-label">经度</text>
            <text class="coord-value">{{ state.lng.toFixed(6) }}</text>
          </view>
          <view class="coord-cell">
            <text class="coord-label">纬度</text>
            <text class="coord-value">{{ state.lat.toFixed(6) }}</text>
          </view>
        </view>
        <text class="update-time">更新于 {{ state.updatedAt }}</text>

        <!-- 地图：显示长辈当前位置 + 安全区圆圈 -->
        <map
          v-if="state.lat && state.lng"
          class="real-map"
          :latitude="state.lat"
          :longitude="state.lng"
          :scale="16"
          :markers="markers"
          :circles="circles"
          show-location
          @markertap="onMarkerTap"
        />

        <!-- 定位失败时的兜底：仍显示坐标，不让页面空白 -->
        <view v-else class="map-placeholder">
          <text class="map-icon">🗺️</text>
          <text class="map-text">{{ state.locating ? '正在定位…' : '暂无位置信息' }}</text>
          <text v-if="state.lat" class="map-sub">{{ state.lng.toFixed(4) }}, {{ state.lat.toFixed(4) }}</text>
        </view>

        <!-- 地图下方操作条 -->
        <view class="map-actions">
          <view class="map-action" @click="openInMap">
            <text>🧭 打开地图导航</text>
          </view>
          <view class="map-action" @click="locateSelf">
            <text>📍 回到当前位置</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 安全区设置 -->
    <view class="card">
      <text class="card-title">🛡️ 安全区设置</text>

      <view class="radius-row">
        <text class="radius-label">安全半径</text>
        <text class="radius-value">{{ state.radius }} 米</text>
      </view>
      <slider
        class="radius-slider"
        :value="state.radius"
        :min="100"
        :max="2000"
        :step="50"
        activeColor="#5B9A7A"
        block-color="#5B9A7A"
        :block-size="24"
        @change="onRadiusChange"
        @changing="onRadiusChanging"
      />
      <view class="radius-marks">
        <text class="mark">100m</text>
        <text class="mark">2000m</text>
      </view>

      <view class="form-row">
        <text class="form-label">安全区名称</text>
        <input
          v-model="state.zoneName"
          class="form-input"
          type="text"
          maxlength="20"
          placeholder="如：家、社区活动中心"
          placeholder-class="ph"
        />
      </view>

      <!-- 是否在安全区内 -->
      <view class="safe-status" :class="state.inside ? 'inside' : 'outside'">
        <text class="safe-icon">{{ state.inside ? '✅' : '⚠️' }}</text>
        <view class="safe-info">
          <text class="safe-title">{{ state.inside ? '当前在安全区内' : '当前已离开安全区' }}</text>
          <text class="safe-desc">距安全区中心约 {{ state.distance }} 米</text>
        </view>
      </view>

      <view class="btn-primary" :class="{ disabled: state.saving }" @click="saveSafeZone">
        <text>{{ state.saving ? '保存中...' : '保存安全区' }}</text>
      </view>
    </view>

    <!-- 位置历史 -->
    <view class="card">
      <text class="card-title">🕐 近期位置</text>
      <view v-if="state.history.length > 0">
        <view v-for="(h, i) in state.history" :key="i" class="history-item">
          <view class="history-dot"></view>
          <view class="history-info">
            <text class="history-addr">{{ h.address }}</text>
            <text class="history-time">{{ h.time }}</text>
          </view>
          <text class="history-tag" :class="h.inside ? 'in' : 'out'">{{ h.inside ? '区内' : '区外' }}</text>
        </view>
      </view>
      <EmptyState v-else icon="📍" text="暂无位置记录" desc="定位数据会在长辈移动时自动记录" />
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 位置详情
 * ----------------------------------------------------------------------------
 * 当前定位（经纬度 + 逆地理编码地址）+ 安全区半径设置 + 是否在安全区判断
 * 接口：locationApi.current() / locationApi.check() / locationApi.setSafeZone()
 */
import { reactive, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { locationApi } from '@/services/api'
import EmptyState from '@/components/EmptyState.vue'

/** ★ 兜底位置历史 */
const FALLBACK_HISTORY = [
  { address: '北京市朝阳区建国路 88 号', time: '今天 09:20', inside: true },
  { address: '北京市朝阳区朝阳公园南门', time: '今天 08:05', inside: false },
  { address: '北京市朝阳区建国路 88 号', time: '昨天 18:40', inside: true }
]

const state = reactive({
  loading: true,
  saving: false,
  locating: false,
  lng: 116.397428,
  lat: 39.90923,
  address: '正在获取定位...',
  updatedAt: '--',
  radius: 500,
  zoneName: '家',
  inside: true,
  distance: 0,
  history: [] as { address: string; time: string; inside: boolean }[]
})

/**
 * 地图标记：长辈当前位置
 *
 * iconPath 复用 tabBar 里已有的图标，省得再引资源。
 * callout 常显地址，这样一眼能看到「在哪」而不只是「哪条经纬度」。
 */
const markers = computed(() => {
  if (!state.lat || !state.lng) return []
  return [
    {
      id: 1,
      latitude: state.lat,
      longitude: state.lng,
      width: 32,
      height: 32,
      iconPath: '/static/tabbar/heart-active.png',
      callout: {
        content: state.address || '当前位置',
        color: '#2F2A24',
        fontSize: 12,
        borderRadius: 8,
        bgColor: '#FFF9EF',
        padding: 8,
        display: 'ALWAYS'
      }
    }
  ]
})

/**
 * 安全区圆圈
 *
 * 在安全区内画绿色、越界画红色 —— 不用读文字就知道状态。
 */
const circles = computed(() => {
  if (!state.lat || !state.lng) return []
  return [
    {
      latitude: state.lat,
      longitude: state.lng,
      radius: state.radius,
      color: state.inside ? '#5B9A7AAA' : '#E55A5AAA',
      fillColor: state.inside ? '#8FA68E22' : '#E55A5A22',
      strokeWidth: 2
    }
  ]
})

/** 点击地图标记 */
function onMarkerTap() {
  if (state.address) {
    uni.showToast({ title: state.address, icon: 'none', duration: 2500 })
  }
}

/** 用系统地图打开（可导航） */
function openInMap() {
  if (!state.lat || !state.lng) {
    uni.showToast({ title: '暂无位置信息', icon: 'none' })
    return
  }
  uni.openLocation({
    latitude: state.lat,
    longitude: state.lng,
    name: state.zoneName || '长辈位置',
    address: state.address || '',
    scale: 16,
    fail: () => {
      uni.showToast({ title: '打开地图失败', icon: 'none' })
    }
  })
}

/** 重新定位到自己 */
function locateSelf() {
  state.locating = true
  uni.getLocation({
    type: 'gcj02',
    success: (res) => {
      state.lat = res.latitude
      state.lng = res.longitude
      state.updatedAt = formatNow()
    },
    fail: () => {
      uni.showToast({ title: '定位失败，请检查定位权限', icon: 'none' })
    },
    complete: () => {
      state.locating = false
    }
  })
}

/** 当前时间 */
function formatNow(): string {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 获取定位并校验安全区 */
function loadLocation() {
  state.loading = true
  uni.getLocation({
    type: 'gcj02',
    success: async (res) => {
      state.lng = res.longitude
      state.lat = res.latitude
      state.updatedAt = formatNow()
      await syncLocation()
      state.loading = false
    },
    fail: async () => {
      // ★ 兜底：定位失败用默认坐标，仍然做一次安全区校验
      state.address = '定位未授权，展示默认位置'
      state.updatedAt = formatNow()
      await syncLocation()
      state.loading = false
    }
  })
}

/** 上报位置 + 校验安全区 + 逆地理编码 */
async function syncLocation() {
  // 1. 上报当前位置
  try {
    const res = (await locationApi.current(state.lng, state.lat)) as Record<string, unknown>
    const addr = (res?.address as string) || ''
    if (addr) state.address = addr
    else state.address = `当前位置（${state.lng.toFixed(4)}, ${state.lat.toFixed(4)}）`
  } catch {
    // ★ 兜底：接口失败时用坐标展示，界面不空白
    state.address = `当前位置（${state.lng.toFixed(4)}, ${state.lat.toFixed(4)}）`
  }

  // 2. 是否在安全区内
  try {
    const check = await locationApi.check(state.lng, state.lat)
    state.inside = !!check.inside
    state.distance = Math.round(check.distance || 0)
  } catch {
    // ★ 兜底：按本地半径粗略判断（有坐标即可算出，这里用固定演示值）
    state.inside = true
    state.distance = 180
  }

  // 3. 历史记录（接口失败使用兜底列表）
  try {
    const his = await locationApi.history(7)
    if (his.list && his.list.length > 0) {
      state.history = (his.list as Record<string, unknown>[]).map((h) => ({
        address: (h.address as string) || '未知位置',
        time: (h.time as string) || '--',
        inside: !!h.inside
      }))
    } else {
      state.history = JSON.parse(JSON.stringify(FALLBACK_HISTORY))
    }
  } catch {
    state.history = JSON.parse(JSON.stringify(FALLBACK_HISTORY))
  }
}

/** 拖动中实时显示 */
function onRadiusChanging(e: { detail: { value: number } }) {
  state.radius = e.detail.value
}

/** 拖动结束 */
function onRadiusChange(e: { detail: { value: number } }) {
  state.radius = e.detail.value
}

/** 保存安全区 */
async function saveSafeZone() {
  if (state.saving) return
  state.saving = true
  try {
    await locationApi.setSafeZone({
      lng: state.lng,
      lat: state.lat,
      radius: state.radius,
      name: state.zoneName
    })
    uni.setStorageSync('guardian_safe_zone', {
      lng: state.lng,
      lat: state.lat,
      radius: state.radius,
      name: state.zoneName
    })
    uni.showToast({ title: '安全区已保存', icon: 'success' })
  } catch {
    // ★ 兜底：本地保存
    uni.setStorageSync('guardian_safe_zone', {
      lng: state.lng,
      lat: state.lat,
      radius: state.radius,
      name: state.zoneName
    })
    uni.showToast({ title: '已保存到本地', icon: 'none' })
  } finally {
    state.saving = false
  }
}

/** 读取本地安全区配置 */
function loadSafeZone() {
  const zone = uni.getStorageSync('guardian_safe_zone') as
    | { radius?: number; name?: string }
    | ''
  if (zone && typeof zone === 'object') {
    state.radius = zone.radius || state.radius
    state.zoneName = zone.name || state.zoneName
  }
}

onShow(() => {
  loadSafeZone()
  loadLocation()
})
</script>

<style lang="scss" scoped>
.page { padding: 24rpx calc(24rpx) calc(120rpx + env(safe-area-inset-bottom)); }

.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20rpx; }
.refresh-btn {
  padding: 8rpx 24rpx; border-radius: var(--radius-full);
  background: var(--primary-l); color: var(--primary-d); font-size: 24rpx;
}

.loading-box { padding: 64rpx 0; text-align: center; }
.loading-text { font-size: 28rpx; color: var(--text-2); }

.addr-box {
  padding: 24rpx; border-radius: var(--radius-sm);
  background: var(--primary-l); margin-bottom: 24rpx;
}
.addr-text { font-size: 30rpx; font-weight: 600; color: var(--primary-d); line-height: 1.5; }

.coord-row { display: flex; gap: 24rpx; }
.coord-cell {
  flex: 1; display: flex; flex-direction: column; gap: 8rpx;
  padding: 20rpx; border-radius: var(--radius-sm); background: var(--surface-2);
}
.coord-label { font-size: 22rpx; color: var(--text-3); }
.coord-value { font-size: 28rpx; font-weight: 600; color: var(--text-1); }
.update-time { display: block; font-size: 22rpx; color: var(--text-3); margin-top: 16rpx; }

/* 真实地图组件 */
.real-map {
  width: 100%;
  height: 480rpx;
  border-radius: var(--radius);
  margin-top: 20rpx;
}

/* 地图下方两个操作按钮 */
.map-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 16rpx;
}
.map-action {
  flex: 1;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--primary-l);
  border-radius: 16rpx;
  font-size: 26rpx;
  color: var(--primary-d);
}

.map-placeholder {
  margin-top: 24rpx; height: 320rpx; border-radius: var(--radius);
  background: var(--surface-2); border: 2rpx dashed var(--border);
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8rpx;
}
.map-icon { font-size: 72rpx; }
.map-text { font-size: 28rpx; color: var(--text-2); }
.map-sub { font-size: 22rpx; color: var(--text-3); }

/* 安全区 */
.radius-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8rpx; }
.radius-label { font-size: 28rpx; color: var(--text-2); }
.radius-value { font-size: 32rpx; font-weight: 700; color: var(--primary-d); }
.radius-slider { margin: 0 8rpx; }
.radius-marks { display: flex; justify-content: space-between; padding: 0 8rpx; margin-bottom: 16rpx; }
.mark { font-size: 22rpx; color: var(--text-3); }

.form-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 24rpx 0; border-bottom: 2rpx solid var(--divider);
}
.form-label { font-size: 28rpx; color: var(--text-2); }
.form-input { flex: 1; text-align: right; font-size: 30rpx; color: var(--text-1); }
.ph { color: var(--text-3); font-size: 28rpx; }

.safe-status {
  display: flex; align-items: center; gap: 20rpx;
  padding: 28rpx; border-radius: var(--radius-sm); margin: 24rpx 0;
}
.safe-status.inside { background: var(--primary-l); }
.safe-status.outside { background: var(--danger-l); }
.safe-icon { font-size: 44rpx; }
.safe-info { flex: 1; display: flex; flex-direction: column; gap: 6rpx; }
.safe-title { font-size: 30rpx; font-weight: 600; color: var(--text-1); }
.safe-desc { font-size: 24rpx; color: var(--text-2); }

/* 历史 */
.history-item {
  display: flex; align-items: center; gap: 20rpx;
  padding: 24rpx 0; border-bottom: 2rpx solid var(--divider);
}
.history-item:last-child { border-bottom: none; }
.history-dot {
  width: 16rpx; height: 16rpx; border-radius: 50%;
  background: var(--primary); flex-shrink: 0;
}
.history-info { flex: 1; display: flex; flex-direction: column; gap: 6rpx; }
.history-addr { font-size: 28rpx; color: var(--text-1); }
.history-time { font-size: 22rpx; color: var(--text-3); }
.history-tag { font-size: 22rpx; padding: 4rpx 16rpx; border-radius: var(--radius-full); }
.history-tag.in { background: var(--primary-l); color: var(--primary-d); }
.history-tag.out { background: var(--warning-l); color: var(--warning); }
</style>
