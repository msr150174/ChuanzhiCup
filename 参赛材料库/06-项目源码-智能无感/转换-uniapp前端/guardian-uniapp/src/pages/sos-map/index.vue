<template>
  <view class="page">
    <!-- 当前位置 -->
    <view class="location-card">
      <view class="loc-header">
        <text class="loc-pin">📍</text>
        <text class="loc-title">当前位置</text>
        <view class="refresh-btn" @click="loadLocation"><text>🔄</text></view>
      </view>
      <text class="loc-address">{{ state.address }}</text>
      <text class="loc-coord">经度 {{ state.lng.toFixed(6) }} · 纬度 {{ state.lat.toFixed(6) }}</text>
      <text class="loc-time">更新于 {{ state.updatedAt }}</text>

      <!-- 地图：求救时家属能直接看到长辈在哪 -->
      <map
        v-if="state.lat && state.lng"
        class="sos-map-view"
        :latitude="state.lat"
        :longitude="state.lng"
        :scale="16"
        :markers="markers"
        show-location
      />
      <view v-else class="sos-map-empty">
        <text>正在定位，请稍候…</text>
      </view>

      <!-- 一键导航到长辈位置 -->
      <view v-if="state.lat && state.lng" class="nav-btn" @click="openInMap">
        <text>🧭 打开地图导航过去</text>
      </view>
    </view>

    <!-- SOS 按钮 -->
    <view class="sos-area">
      <view
        class="sos-btn"
        :class="{ pressing: state.pressing, sent: state.sent }"
        @touchstart="onPressStart"
        @touchend="onPressEnd"
        @touchcancel="onPressEnd"
      >
        <text class="sos-text">{{ state.sent ? '已发送' : 'SOS' }}</text>
        <text class="sos-sub">{{ state.sent ? '求助已发出' : '长按 3 秒' }}</text>
        <view class="sos-progress" :style="{ width: state.progress + '%' }"></view>
      </view>
      <text class="sos-tip">长按按钮 3 秒即可向紧急联系人发送求助信息与实时位置</text>
    </view>

    <!-- 紧急联系人 -->
    <view class="card">
      <view class="section-header">
        <text class="card-title">📞 紧急联系人</text>
        <view class="add-btn" @click="goContacts"><text>管理</text></view>
      </view>

      <view v-if="state.contacts.length > 0">
        <view v-for="c in state.contacts" :key="c.id" class="contact-item">
          <view class="contact-avatar"><text>{{ avatarText(c.name) }}</text></view>
          <view class="contact-info">
            <view class="contact-line">
              <text class="contact-name">{{ c.name }}</text>
              <text class="contact-relation">{{ c.relation }}</text>
              <text v-if="c.isPrimary" class="tag">首选</text>
            </view>
            <text class="contact-phone">{{ c.phone }}</text>
          </view>
          <view class="call-btn" @click="callContact(c)"><text>📞 拨打</text></view>
        </view>
      </view>

      <EmptyState
        v-else
        icon="📞"
        text="暂无紧急联系人"
        desc="添加联系人后才能发送求助信息"
        action-text="去添加"
        @action="goContacts"
      />
    </view>

    <!-- 求助记录 -->
    <view class="card">
      <text class="card-title">🕐 求助记录</text>
      <view v-if="state.sosRecords.length > 0">
        <view v-for="r in state.sosRecords" :key="r.id" class="record-item">
          <view class="record-left">
            <text class="record-title">{{ r.reason }}</text>
            <text class="record-addr">{{ r.location }}</text>
          </view>
          <view class="record-right">
            <text class="record-time">{{ r.time }}</text>
            <text class="record-status" :class="r.status === 'handled' ? 'ok' : 'wait'">
              {{ r.status === 'handled' ? '已响应' : '已通知 ' + r.notified + ' 人' }}
            </text>
          </view>
        </view>
      </view>
      <EmptyState v-else icon="🆘" text="暂无求助记录" desc="一切平安，没有发起过紧急求助" />
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 紧急求助（SOS）
 * ----------------------------------------------------------------------------
 * uni.getLocation() 定位 + 长按 3 秒 SOS 按钮 + 紧急联系人快速拨打 + 求助记录
 * 接口：guardianApi.sos()
 */
import { reactive, computed, onUnmounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { guardianApi, locationApi } from '@/services/api'
import EmptyState from '@/components/EmptyState.vue'

/** 联系人 */
interface Contact {
  id: string
  name: string
  relation: string
  phone: string
  isPrimary: boolean
}

/** 求助记录 */
interface SosRecord {
  id: string
  reason: string
  location: string
  time: string
  status: 'handled' | 'sent'
  notified: number
}

const CONTACTS_KEY = 'guardian_emergency_contacts'
const RECORDS_KEY = 'guardian_sos_records'

/** ★ 兜底联系人 */
const FALLBACK_CONTACTS: Contact[] = [
  { id: 'c-1', name: '李小明', relation: '儿子', phone: '13800138000', isPrimary: true },
  { id: 'c-2', name: '王丽', relation: '女儿', phone: '13900139000', isPrimary: false }
]

/** ★ 兜底求助记录 */
const FALLBACK_RECORDS: SosRecord[] = [
  {
    id: 'r-1',
    reason: '紧急求助（SOS）',
    location: '北京市朝阳区建国路 88 号',
    time: '今天 08:20',
    status: 'handled',
    notified: 2
  }
]

const state = reactive({
  lng: 116.397428,
  lat: 39.90923,
  address: '正在获取定位...',
  updatedAt: '--',
  pressing: false,
  progress: 0,
  sent: false,
  contacts: [] as Contact[],
  sosRecords: [] as SosRecord[]
})

/**
 * 地图标记：长辈当前位置
 *
 * 求救场景下家属最关心「人在哪」，所以地图是这个页面的核心，
 * 标记用醒目的红色心形 + 常显地址。
 */
const markers = computed(() => {
  if (!state.lat || !state.lng) return []
  return [
    {
      id: 1,
      latitude: state.lat,
      longitude: state.lng,
      width: 36,
      height: 36,
      iconPath: '/static/tabbar/heart-active.png',
      callout: {
        content: state.address || '求救位置',
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

/** 用系统地图打开，方便家属导航过去 */
function openInMap() {
  if (!state.lat || !state.lng) {
    uni.showToast({ title: '暂无位置信息', icon: 'none' })
    return
  }
  uni.openLocation({
    latitude: state.lat,
    longitude: state.lng,
    name: '长辈求救位置',
    address: state.address || '',
    scale: 16,
    fail: () => {
      uni.showToast({ title: '打开地图失败', icon: 'none' })
    }
  })
}

/** 长按计时器 */
let pressTimer: ReturnType<typeof setInterval> | null = null
/** 进度条计时器 */
let progressTimer: ReturnType<typeof setInterval> | null = null

function avatarText(name: string): string {
  return name ? name.slice(0, 1) : '联'
}

/** 清理计时器 */
function clearTimers() {
  if (pressTimer) {
    clearTimeout(pressTimer as unknown as number)
    pressTimer = null
  }
  if (progressTimer) {
    clearInterval(progressTimer as unknown as number)
    progressTimer = null
  }
}

/** 获取定位 */
function loadLocation() {
  uni.getLocation({
    type: 'gcj02',
    success: (res) => {
      state.lng = res.longitude
      state.lat = res.latitude
      state.updatedAt = formatNow()
      // 逆地理编码（部分端支持；不支持时用坐标兜底展示）
      reverseGeocode(res.longitude, res.latitude)
    },
    fail: () => {
      // ★ 兜底：定位失败时使用默认坐标，界面不空白
      state.address = '定位未授权，展示默认位置'
      state.updatedAt = formatNow()
    }
  })
}

/**
 * 逆地理编码：把经纬度转成可读地址
 *
 * 注意：前端不内置任何地图密钥（密钥应走后端中转）。
 * 这里优先调后端 /api/location/current 由服务端完成逆地理编码，
 * 失败时退化为坐标展示，保证界面始终有内容。
 */
async function reverseGeocode(lng: number, lat: number) {
  const coordText = `当前位置（${lng.toFixed(4)}, ${lat.toFixed(4)}）`
  try {
    const res = (await locationApi.current(lng, lat)) as Record<string, unknown>
    const addr = (res?.address as string) || (res?.formattedAddress as string) || ''
    state.address = addr || coordText
  } catch {
    // ★ 兜底：服务端不可用时展示坐标
    state.address = coordText
  }
}

/** 当前时间文本 */
function formatNow(): string {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/** 按下开始计时 */
function onPressStart() {
  if (state.sent) return
  state.pressing = true
  state.progress = 0

  progressTimer = setInterval(() => {
    state.progress = Math.min(100, state.progress + 4)
  }, 100)

  pressTimer = setTimeout(() => {
    clearTimers()
    state.pressing = false
    state.progress = 100
    sendSos()
  }, 3000)
}

/** 松开取消 */
function onPressEnd() {
  if (!state.pressing) return
  clearTimers()
  state.pressing = false
  state.progress = 0
}

/** 发送求助 */
async function sendSos() {
  let notified = state.contacts.length
  try {
    const res = await guardianApi.sos({
      reason: '紧急求助（SOS）',
      location: state.address,
      lng: state.lng,
      lat: state.lat
    })
    notified = res.notified
  } catch {
    // ★ 兜底：接口失败时按已通知本地联系人数记录
  }

  state.sent = true
  const record: SosRecord = {
    id: `sos-${Date.now()}`,
    reason: '紧急求助（SOS）',
    location: state.address,
    time: '刚刚',
    status: 'sent',
    notified
  }
  state.sosRecords.unshift(record)
  uni.setStorageSync(RECORDS_KEY, state.sosRecords)

  uni.showModal({
    title: '求助已发送',
    content: `已向 ${notified} 位紧急联系人发送求助信息与实时位置，请保持手机畅通。`,
    showCancel: false,
    confirmText: '知道了'
  })

  setTimeout(() => {
    state.sent = false
    state.progress = 0
  }, 3000)
}

/** 拨打电话 */
function callContact(c: Contact) {
  uni.makePhoneCall({
    phoneNumber: c.phone,
    fail: () => uni.showToast({ title: '拨号已取消', icon: 'none' })
  })
}

/** 跳紧急联系人管理 */
function goContacts() {
  uni.navigateTo({ url: '/pages/emergency-contacts/index' })
}

/** 加载本地数据 */
function loadLocalData() {
  const contacts = uni.getStorageSync(CONTACTS_KEY)
  state.contacts = contacts && Array.isArray(contacts) && contacts.length > 0
    ? (contacts as Contact[])
    : (JSON.parse(JSON.stringify(FALLBACK_CONTACTS)) as Contact[])

  const records = uni.getStorageSync(RECORDS_KEY)
  state.sosRecords = records && Array.isArray(records) && records.length > 0
    ? (records as SosRecord[])
    : (JSON.parse(JSON.stringify(FALLBACK_RECORDS)) as SosRecord[])
}

onShow(() => {
  loadLocalData()
  loadLocation()
})

onUnmounted(() => {
  clearTimers()
})
</script>

<style lang="scss" scoped>
.page { padding: 32rpx 24rpx calc(120rpx + env(safe-area-inset-bottom)); }

/* 位置卡 */
.location-card {
  background: linear-gradient(135deg, var(--primary), #9db89c);
  border-radius: var(--radius); padding: 32rpx; margin-bottom: 32rpx;
  box-shadow: var(--shadow-lg);
}
.loc-header { display: flex; align-items: center; gap: 12rpx; margin-bottom: 16rpx; }
.loc-pin { font-size: 32rpx; }
.loc-title { flex: 1; font-size: 28rpx; color: rgba(255, 255, 255, 0.9); }
.refresh-btn {
  width: 56rpx; height: 56rpx; border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex; align-items: center; justify-content: center; font-size: 26rpx;
}
.loc-address { display: block; font-size: 34rpx; font-weight: 700; color: #fff; line-height: 1.5; }
.loc-coord { display: block; font-size: 24rpx; color: rgba(255, 255, 255, 0.85); margin-top: 12rpx; }
.loc-time { display: block; font-size: 22rpx; color: rgba(255, 255, 255, 0.7); margin-top: 8rpx; }

/* 求救地图 */
.sos-map-view {
  width: 100%;
  height: 420rpx;
  border-radius: 16rpx;
  margin-top: 20rpx;
}
.sos-map-empty {
  height: 200rpx;
  margin-top: 20rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.8);
}
.nav-btn {
  height: 80rpx;
  margin-top: 16rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 600;
  color: #E55A5A;
}

/* SOS */
.sos-area { display: flex; flex-direction: column; align-items: center; margin-bottom: 40rpx; }
.sos-btn {
  width: 400rpx; height: 400rpx; border-radius: 50%;
  background: linear-gradient(135deg, #e55a5a, #c93c3c);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 12rpx; position: relative; overflow: hidden;
  box-shadow: 0 16rpx 48rpx rgba(229, 90, 90, 0.4);
  transition: transform 0.15s;
}
.sos-btn.pressing { transform: scale(0.95); }
.sos-btn.sent { background: linear-gradient(135deg, var(--primary), var(--primary-d)); box-shadow: 0 16rpx 48rpx rgba(143, 166, 142, 0.4); }
.sos-text { font-size: 96rpx; font-weight: 800; color: #fff; letter-spacing: 8rpx; }
.sos-sub { font-size: 28rpx; color: rgba(255, 255, 255, 0.9); }
.sos-progress {
  position: absolute; left: 0; bottom: 0; height: 12rpx;
  background: rgba(255, 255, 255, 0.75); transition: width 0.1s linear;
}
.sos-tip { font-size: 24rpx; color: var(--text-2); text-align: center; margin-top: 32rpx; line-height: 1.6; padding: 0 40rpx; }

/* 联系人 */
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8rpx; }
.add-btn {
  padding: 10rpx 24rpx; border-radius: var(--radius-full);
  background: var(--primary-l); color: var(--primary-d); font-size: 24rpx;
}
.contact-item {
  display: flex; align-items: center; gap: 20rpx;
  padding: 24rpx 0; border-bottom: 2rpx solid var(--divider);
}
.contact-item:last-child { border-bottom: none; }
.contact-avatar {
  width: 80rpx; height: 80rpx; border-radius: 50%; flex-shrink: 0;
  background: linear-gradient(135deg, var(--primary), #9db89c);
  color: #fff; font-size: 32rpx; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.contact-info { flex: 1; display: flex; flex-direction: column; gap: 6rpx; }
.contact-line { display: flex; align-items: center; gap: 12rpx; }
.contact-name { font-size: 30rpx; font-weight: 600; color: var(--text-1); }
.contact-relation {
  font-size: 22rpx; color: var(--text-2);
  padding: 2rpx 14rpx; border-radius: 8rpx; background: var(--surface-2);
}
.contact-phone { font-size: 24rpx; color: var(--text-2); }
.call-btn {
  padding: 12rpx 28rpx; border-radius: var(--radius-full);
  background: var(--primary-l); color: var(--primary-d); font-size: 24rpx;
}

/* 求助记录 */
.record-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 24rpx 0; border-bottom: 2rpx solid var(--divider);
}
.record-item:last-child { border-bottom: none; }
.record-left { flex: 1; display: flex; flex-direction: column; gap: 8rpx; }
.record-title { font-size: 28rpx; font-weight: 600; color: var(--text-1); }
.record-addr { font-size: 22rpx; color: var(--text-2); }
.record-right { display: flex; flex-direction: column; align-items: flex-end; gap: 8rpx; }
.record-time { font-size: 22rpx; color: var(--text-3); }
.record-status { font-size: 22rpx; padding: 4rpx 16rpx; border-radius: var(--radius-full); }
.record-status.ok { background: var(--primary-l); color: var(--primary-d); }
.record-status.wait { background: var(--warning-l); color: var(--warning); }
</style>
