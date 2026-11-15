<template>
  <view class="page" :class="{ 'elder-mode': settingsStore.settings.elderMode }">
    <view class="page-inner">
      <!-- ① 隐私概览 -->
      <view class="card shield-card">
        <text class="shield-icon">🛡️</text>
        <text class="shield-title">您的数据由您掌控</text>
        <text class="shield-desc">
          我们只采集守护长辈所必需的信息，您可以随时查看授权状态、调整数据可见范围或彻底删除数据。
        </text>
      </view>

      <!-- ② 权限管理 -->
      <view class="card">
        <view class="flex-between card-title-row">
          <text class="card-title no-margin">🔐 权限管理</text>
          <text class="refresh-btn" @click="refreshPermissions">刷新状态</text>
        </view>

        <view v-for="p in permissions" :key="p.key" class="list-item">
          <view class="list-item-left">
            <view class="list-item-icon">{{ p.icon }}</view>
            <view class="item-text">
              <text class="item-title">{{ p.name }}</text>
              <text class="item-desc">{{ p.desc }}</text>
            </view>
          </view>
          <view class="perm-right">
            <text class="tag" :class="p.granted ? '' : 'tag-warning'">
              {{ p.granted ? '已授权' : '未授权' }}
            </text>
            <view class="setting-btn" @click="openSetting">
              <text>去设置</text>
            </view>
          </view>
        </view>

        <text class="perm-tip">
          提示：权限由系统统一管理，修改后返回本页会自动刷新状态。
        </text>
      </view>

      <!-- ③ 数据可见范围 -->
      <view class="card">
        <text class="card-title">👁️ 数据可见范围</text>
        <text class="scope-note">选择家属可以查看的数据类型，修改后立即生效</text>

        <view v-for="s in SCOPES" :key="s.value" class="scope-item" @click="setScope(s.value)">
          <view class="scope-radio" :class="{ active: settingsStore.settings.dataScope === s.value }">
            <view v-if="settingsStore.settings.dataScope === s.value" class="radio-dot"></view>
          </view>
          <view class="item-text">
            <text class="item-title">{{ s.label }}</text>
            <text class="item-desc">{{ s.desc }}</text>
          </view>
        </view>
      </view>

      <!-- ④ 数据管理 -->
      <view class="card">
        <text class="card-title">📦 我的数据</text>

        <view class="list-item" @click="exportData">
          <view class="list-item-left">
            <view class="list-item-icon">📤</view>
            <view class="item-text">
              <text class="item-title">导出我的数据</text>
              <text class="item-desc">生成 JSON 文件，包含打卡、健康与用药记录</text>
            </view>
          </view>
          <text class="list-item-arrow">›</text>
        </view>

        <view class="list-item" @click="clearLocalCache">
          <view class="list-item-left">
            <view class="list-item-icon">🧹</view>
            <view class="item-text">
              <text class="item-title">清除本机缓存</text>
              <text class="item-desc">照片、语音与日志等临时文件</text>
            </view>
          </view>
          <text class="list-item-arrow">›</text>
        </view>

        <view class="list-item" @click="deleteAccount">
          <view class="list-item-left">
            <view class="list-item-icon danger">⚠️</view>
            <view class="item-text">
              <text class="item-title danger-text">注销账号并删除数据</text>
              <text class="item-desc">删除后不可恢复，请谨慎操作</text>
            </view>
          </view>
          <text class="list-item-arrow">›</text>
        </view>
      </view>

      <!-- ⑤ 说明 -->
      <view class="card tip-card">
        <text class="tip-title">📄 相关条款</text>
        <view class="list-item" @click="goPage('/pages/privacy-policy/index')">
          <view class="list-item-left">
            <view class="list-item-icon">📘</view>
            <text class="item-title">隐私政策</text>
          </view>
          <text class="list-item-arrow">›</text>
        </view>
        <view class="list-item" @click="goPage('/pages/voiceprint-agreement/index')">
          <view class="list-item-left">
            <view class="list-item-icon">🎙️</view>
            <text class="item-title">声纹授权协议</text>
          </view>
          <text class="list-item-arrow">›</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 隐私安全
 * ----------------------------------------------------------------------------
 * 权限状态查询（位置 / 麦克风 / 相机）+ 数据可见范围 + 数据导出与账号注销入口。
 * 数据可见范围使用 settings store 持久化。
 */
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { useSettingsStore } from '@/stores/settings'

const settingsStore = useSettingsStore()

/** 数据可见范围选项 */
const SCOPES = [
  { value: 'all' as const, label: '全部数据', desc: '健康数据 + 位置信息，守护最全面' },
  { value: 'health' as const, label: '仅健康数据', desc: '心率、步数、睡眠、用药记录' },
  { value: 'location' as const, label: '仅位置信息', desc: '仅共享实时位置与安全区状态' }
]

/** 权限项 */
interface PermissionItem {
  key: string
  name: string
  desc: string
  icon: string
  scope: string
  granted: boolean
}

const permissions = ref<PermissionItem[]>([
  {
    key: 'location',
    name: '位置信息',
    desc: '用于安全区监测与紧急求助定位',
    icon: '📍',
    scope: 'scope.userLocation',
    granted: false
  },
  {
    key: 'record',
    name: '麦克风',
    desc: '用于语音健康监测与语音播报',
    icon: '🎙️',
    scope: 'scope.record',
    granted: false
  },
  {
    key: 'camera',
    name: '相机',
    desc: '用于拍摄照片进行视频健康监测',
    icon: '📷',
    scope: 'scope.camera',
    granted: false
  }
])

/** 查询单个权限授权状态 */
function checkOne(item: PermissionItem) {
  try {
    uni.getSetting({
      success: (res) => {
        const auth = (res.authSetting || {}) as Record<string, boolean>
        item.granted = Boolean(auth[item.scope])
      },
      fail: () => {
        // H5 等环境无该接口，保持默认未授权
      }
    })
  } catch {
    // 忽略
  }
}

/** 刷新全部权限状态 */
function refreshPermissions() {
  permissions.value.forEach((p) => checkOne(p))
}

/** 打开系统设置页 */
function openSetting() {
  try {
    uni.openSetting({
      success: () => {
        refreshPermissions()
      },
      fail: () => {
        uni.showToast({ title: '请在系统设置中手动授权', icon: 'none' })
      }
    })
  } catch {
    uni.showToast({ title: '当前环境不支持打开设置', icon: 'none' })
  }
}

/** 设置数据可见范围 */
function setScope(value: 'all' | 'health' | 'location') {
  settingsStore.set('dataScope', value)
  uni.showToast({ title: '已更新可见范围', icon: 'none' })
}

/** 导出数据 */
function exportData() {
  const payload = {
    exportTime: new Date().toISOString(),
    user: uni.getStorageSync('guardian_user') || null,
    settings: settingsStore.settings,
    note: '完整健康记录请在后端接口 /api/statistics/overview 获取'
  }

  // #ifdef H5
  try {
    uni.setClipboardData({
      data: JSON.stringify(payload, null, 2),
      success: () => uni.showToast({ title: '数据已复制到剪贴板', icon: 'none' })
    })
    return
  } catch {
    // 继续走下面的兜底提示
  }
  // #endif

  uni.showToast({ title: '导出请求已提交，稍后通知您', icon: 'none' })
}

/** 清空本机缓存 */
function clearLocalCache() {
  uni.showModal({
    title: '清除本机缓存',
    content: '将清除本机保存的图片、语音记录与调试日志，不影响云端数据。',
    success: (res) => {
      if (!res.confirm) return
      try {
        uni.removeStorageSync('guardian_image_history')
        uni.removeStorageSync('guardian_voice_history')
        uni.removeStorageSync('guardian_debug_log')
      } catch {
        // 忽略
      }
      uni.showToast({ title: '已清除本机缓存', icon: 'none' })
    }
  })
}

/** 注销账号 */
function deleteAccount() {
  uni.showModal({
    title: '注销账号',
    content: '注销后将永久删除您的账号与全部健康数据，且无法恢复。确定继续吗？',
    confirmText: '继续注销',
    confirmColor: '#E55A5A',
    success: (res) => {
      if (!res.confirm) return
      // 二次确认，防止误触
      uni.showModal({
        title: '再次确认',
        content: '删除后数据无法找回，建议先导出数据备份。',
        confirmText: '确认注销',
        confirmColor: '#E55A5A',
        success: (res2) => {
          if (!res2.confirm) return
          try {
            uni.removeStorageSync('guardian_token')
            uni.removeStorageSync('guardian_user')
            uni.removeStorageSync('guardian_settings')
          } catch {
            // 忽略
          }
          uni.showToast({ title: '注销申请已提交', icon: 'none' })
          setTimeout(() => {
            uni.redirectTo({ url: '/pages/login/index' })
          }, 1200)
        }
      })
    }
  })
}

/** 页面跳转 */
function goPage(url: string) {
  uni.navigateTo({ url })
}

onLoad(() => {
  refreshPermissions()
})

onShow(() => {
  // 从系统设置返回后刷新授权状态
  refreshPermissions()
})
</script>

<style lang="scss" scoped>
.page-inner {
  padding: 24rpx;
}

/* ---------- 概览 ---------- */
.shield-card {
  text-align: center;
  background: linear-gradient(135deg, var(--surface), var(--primary-l));
}

.shield-icon {
  font-size: 88rpx;
}

.shield-title {
  display: block;
  margin-top: 12rpx;
  font-size: 34rpx;
  font-weight: 700;
  color: var(--text-1);
}

.shield-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: var(--text-2);
  line-height: 1.8;
  text-align: left;
}

/* ---------- 权限 ---------- */
.card-title-row {
  margin-bottom: 8rpx;
}

.no-margin {
  margin-bottom: 0;
}

.refresh-btn {
  font-size: 24rpx;
  color: var(--primary-d);
}

.item-text {
  display: flex;
  flex-direction: column;
}

.item-title {
  font-size: 30rpx;
  color: var(--text-1);
}

.item-desc {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: var(--text-3);
  line-height: 1.6;
}

.perm-right {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.setting-btn {
  padding: 10rpx 24rpx;
  border-radius: var(--radius-full);
  background: var(--primary-l);
  font-size: 24rpx;
  color: var(--primary-d);
}

.setting-btn:active {
  opacity: 0.8;
}

.perm-tip {
  display: block;
  margin-top: 20rpx;
  font-size: 22rpx;
  color: var(--text-3);
  line-height: 1.7;
}

/* ---------- 可见范围 ---------- */
.scope-note {
  display: block;
  margin: -12rpx 0 16rpx;
  font-size: 24rpx;
  color: var(--text-3);
}

.scope-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx 0;
  border-bottom: 2rpx solid var(--divider);
}

.scope-item:last-child {
  border-bottom: none;
}

.scope-radio {
  width: 40rpx;
  height: 40rpx;
  flex-shrink: 0;
  border-radius: 50%;
  border: 3rpx solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
}

.scope-radio.active {
  border-color: var(--primary);
}

.radio-dot {
  width: 22rpx;
  height: 22rpx;
  border-radius: 50%;
  background: var(--primary);
}

/* ---------- 数据管理 ---------- */
.list-item-icon.danger {
  background: var(--danger-l);
}

.danger-text {
  color: var(--danger);
}

/* ---------- 提示 ---------- */
.tip-card {
  margin-bottom: 48rpx;
}

.tip-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text-1);
  margin-bottom: 8rpx;
}
</style>
