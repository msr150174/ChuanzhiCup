<template>
  <view class="page settings-page" :class="{ 'elder-mode': settingsStore.settings.elderMode }">
    <!-- 大字模式（最显眼位置） -->
    <view class="elder-hero">
      <view class="elder-hero-left">
        <text class="elder-hero-icon">🔍</text>
        <view class="elder-hero-text">
          <text class="elder-hero-title">长辈大字模式</text>
          <text class="elder-hero-desc">字号放大 60%，按钮更大更好点</text>
        </view>
      </view>
      <view class="hero-switch" :class="{ on: settingsStore.settings.elderMode }" @click="toggle('elderMode')">
        <view class="hero-switch-knob"></view>
      </view>
    </view>

    <!-- 分组渲染 22 个设置项 -->
    <view v-for="group in GROUPS" :key="group.title" class="group">
      <text class="group-title">{{ group.title }}</text>
      <view class="group-card">
        <view
          v-for="item in group.items"
          :key="item.key"
          class="setting-item"
          :class="{ last: isLastInGroup(group, item) }"
          @click="handleItem(item)"
        >
          <view class="setting-icon-wrapper"><text>{{ item.icon }}</text></view>

          <view class="setting-text">
            <text class="setting-label">{{ item.label }}</text>
            <text v-if="item.desc" class="setting-desc">{{ item.desc }}</text>
          </view>

          <!-- 开关类型 -->
          <view
            v-if="item.type === 'switch'"
            class="switch"
            :class="{ on: boolValue(item.key) }"
            @click.stop="toggle(item.key)"
          >
            <view class="switch-knob"></view>
          </view>

          <!-- 选择类型 -->
          <view v-else-if="item.type === 'select'" class="setting-right" @click.stop="openSelect(item)">
            <text class="setting-value">{{ selectLabel(item.key) }}</text>
            <text class="setting-arrow">›</text>
          </view>

          <!-- 跳转/动作类型 -->
          <view v-else class="setting-right">
            <text v-if="item.value" class="setting-value">{{ item.value }}</text>
            <text class="setting-arrow">›</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 底部操作 -->
    <view class="footer-actions">
      <view class="footer-btn" @click="resetAll">
        <text>恢复默认设置</text>
      </view>
      <view class="footer-btn danger" @click="handleLogout">
        <text>退出登录</text>
      </view>
    </view>

    <text class="version-text">安居守护 v1.0.0 · Build 20260622</text>

    <!-- 选择弹窗 -->
    <view v-if="selectVisible" class="modal-overlay" @click.self="selectVisible = false">
      <view class="select-box">
        <text class="select-title">{{ selectTitle }}</text>
        <view
          v-for="opt in selectOptions"
          :key="opt.value"
          class="select-option"
          :class="{ active: opt.value === currentSelectValue }"
          @click="confirmSelect(opt.value)"
        >
          <text>{{ opt.label }}</text>
          <text v-if="opt.value === currentSelectValue">✓</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 设置页
 * ----------------------------------------------------------------------------
 * 22 个设置项分 5 组：基础设置 / 隐私与安全 / 功能管理 / 账号管理 / 关于我们
 * 开关类全部走 useSettingsStore 持久化；大字模式放在最显眼位置。
 */
import { ref, computed } from 'vue'
import { useSettingsStore } from '@/stores/settings'
import { useUserStore } from '@/stores/user'

const settingsStore = useSettingsStore()
const userStore = useUserStore()

type SettingKey =
  | 'elderMode'
  | 'checkinReminder'
  | 'medicineReminder'
  | 'alertPush'
  | 'voiceBroadcast'
  | 'dataScope'

interface SelectOption {
  label: string
  value: string
}

interface SettingItem {
  key: string
  icon: string
  label: string
  desc?: string
  type: 'switch' | 'select' | 'link'
  value?: string
  path?: string
  options?: SelectOption[]
}

interface SettingGroup {
  title: string
  items: SettingItem[]
}

/** 22 个设置项 / 5 组 */
const GROUPS: SettingGroup[] = [
  {
    title: '基础设置',
    items: [
      { key: 'elderMode', icon: '🔍', label: '长辈大字模式', desc: '放大字号与按钮', type: 'switch' },
      { key: 'checkinReminder', icon: '⏰', label: '作息打卡提醒', desc: '到点提醒睡觉与起床', type: 'switch' },
      { key: 'voiceBroadcast', icon: '🔊', label: '语音播报', desc: '关键信息自动朗读', type: 'switch' },
      { key: 'theme', icon: '🎨', label: '主题外观', desc: '暖米白 + 雾绿（默认）', type: 'link', value: '默认' },
      { key: 'fontScale', icon: '🔠', label: '字体大小', type: 'select', options: [
        { label: '标准', value: 'normal' },
        { label: '较大', value: 'large' },
        { label: '超大', value: 'xlarge' }
      ] },
      { key: 'language', icon: '🌐', label: '语言', type: 'select', options: [
        { label: '简体中文', value: 'zh-CN' },
        { label: 'English', value: 'en-US' }
      ] }
    ]
  },
  {
    title: '隐私与安全',
    items: [
      { key: 'dataScope', icon: '👁️', label: '数据可见范围', desc: '家属可查看的数据类型', type: 'select', options: [
        { label: '全部数据', value: 'all' },
        { label: '仅健康数据', value: 'health' },
        { label: '仅位置数据', value: 'location' }
      ] },
      { key: 'locationPermission', icon: '📍', label: '位置权限', desc: '用于跌倒求助时上报位置', type: 'link', value: '已授权' },
      { key: 'recordPermission', icon: '🎤', label: '麦克风权限', desc: '语音监测与声纹识别', type: 'link', value: '已授权' },
      { key: 'cameraPermission', icon: '📷', label: '摄像头权限', desc: '图像监测与跌倒检测', type: 'link', value: '已授权' },
      { key: 'privacyPolicy', icon: '📄', label: '隐私政策', type: 'link', path: '/pages/privacy-policy/index' },
      { key: 'userAgreement', icon: '📜', label: '用户服务协议', type: 'link', path: '/pages/user-agreement/index' },
      { key: 'voiceprintAgreement', icon: '🎙️', label: '声纹授权协议', type: 'link', path: '/pages/voiceprint-agreement/index' },
      { key: 'clearCache', icon: '🧹', label: '清理缓存', desc: '清除本地对话与临时数据', type: 'link' }
    ]
  },
  {
    title: '功能管理',
    items: [
      { key: 'medicineReminder', icon: '💊', label: '用药提醒', desc: '按设定时间推送服药提醒', type: 'switch' },
      { key: 'alertPush', icon: '🚨', label: '告警推送', desc: '跌倒 / 久坐等异常立即通知', type: 'switch' },
      { key: 'fallDetect', icon: '🏃', label: '实时跌倒监测', desc: '后台持续检测姿态', type: 'switch' },
      { key: 'deviceSync', icon: '⌚', label: '设备管理', desc: '智能手环 / 跌倒雷达', type: 'link', value: '2 台在线', path: '/pages/device/index' },
      { key: 'memberBind', icon: '🔗', label: '成员绑定管理', type: 'link', path: '/pages/member-bind/index' },
      { key: 'emergencyContacts', icon: '📞', label: '紧急联系人', type: 'link', path: '/pages/emergency-contacts/index' }
    ]
  },
  {
    title: '账号管理',
    items: [
      { key: 'profile', icon: '👤', label: '个人资料', type: 'link', path: '/pages/profile/index' },
      { key: 'logout', icon: '🚪', label: '退出登录', type: 'link' }
    ]
  },
  {
    title: '关于我们',
    items: [
      { key: 'about', icon: 'ℹ️', label: '关于安居守护', type: 'link', path: '/pages/about/index' },
      { key: 'feedback', icon: '💬', label: '意见反馈', type: 'link' },
      { key: 'debugLog', icon: '🧠', label: '开发者管理', type: 'link', path: '/pages/debug-log/index' },
      { key: 'version', icon: '🏷️', label: '版本信息', type: 'link', value: 'v1.0.0' }
    ]
  }
]

/** 本地选择型设置的存储 */
const LOCAL_KEY = 'guardian_settings_local'
const localSettings = ref<Record<string, string>>(
  (uni.getStorageSync(LOCAL_KEY) as Record<string, string>) || {
    fontScale: 'normal',
    language: 'zh-CN'
  }
)

/** 选择弹窗 */
const selectVisible = ref(false)
const selectTitle = ref('')
const selectOptions = ref<SelectOption[]>([])
const currentSelectKey = ref('')
const currentSelectValue = ref('')

function toast(msg: string) {
  uni.showToast({ title: msg, icon: 'none' })
}

/** 开关类取值 */
function boolValue(key: string): boolean {
  const k = key as SettingKey
  const v = settingsStore.settings[k]
  return typeof v === 'boolean' ? v : false
}

/** 选择类显示文案 */
function selectLabel(key: string): string {
  if (key === 'dataScope') {
    const map: Record<string, string> = { all: '全部数据', health: '仅健康数据', location: '仅位置数据' }
    return map[settingsStore.settings.dataScope] || '全部数据'
  }
  return localSettings.value[key] || '未设置'
}

function isLastInGroup(group: SettingGroup, item: SettingItem): boolean {
  return group.items[group.items.length - 1].key === item.key
}

/** 切换开关（持久化） */
function toggle(key: string) {
  const k = key as SettingKey
  const cur = settingsStore.settings[k]
  if (typeof cur !== 'boolean') return
  const next = !cur
  settingsStore.set(k, next)

  const labelMap: Record<string, string> = {
    elderMode: '长辈大字模式',
    checkinReminder: '作息打卡提醒',
    medicineReminder: '用药提醒',
    alertPush: '告警推送',
    voiceBroadcast: '语音播报',
    fallDetect: '实时跌倒监测'
  }
  const name = labelMap[key] || '设置'
  toast(`${name}${next ? '已开启' : '已关闭'}`)
}

/** 打开选择弹窗 */
function openSelect(item: SettingItem) {
  if (!item.options) return
  selectTitle.value = item.label
  selectOptions.value = item.options
  currentSelectKey.value = item.key
  currentSelectValue.value = selectLabel(item.key)
  selectVisible.value = true
}

/** 确认选择 */
function confirmSelect(value: string) {
  const key = currentSelectKey.value
  if (key === 'dataScope') {
    settingsStore.set('dataScope', value as 'all' | 'health' | 'location')
  } else {
    localSettings.value[key] = value
    uni.setStorageSync(LOCAL_KEY, localSettings.value)
  }
  selectVisible.value = false
  toast('设置已保存')
}

/** 清理缓存 */
function clearCache() {
  uni.showModal({
    title: '清理缓存',
    content: '将清除本地对话记录与临时数据，确定继续？',
    success: (res) => {
      if (!res.confirm) return
      uni.removeStorageSync('guardian_chat')
      uni.removeStorageSync('guardian_temp')
      toast('缓存已清理')
    }
  })
}

/** 退出登录 */
function handleLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出当前账号吗？',
    success: async (res) => {
      if (!res.confirm) return
      await userStore.logout()
      uni.redirectTo({ url: '/pages/login/index' })
    }
  })
}

/** 恢复默认 */
function resetAll() {
  uni.showModal({
    title: '恢复默认设置',
    content: '所有开关与偏好将恢复为初始状态，确定继续？',
    success: (res) => {
      if (!res.confirm) return
      settingsStore.reset()
      localSettings.value = { fontScale: 'normal', language: 'zh-CN' }
      uni.setStorageSync(LOCAL_KEY, localSettings.value)
      toast('已恢复默认设置')
    }
  })
}

/** 设置项点击分发 */
function handleItem(item: SettingItem) {
  if (item.type === 'switch') {
    toggle(item.key)
    return
  }
  if (item.type === 'select') {
    openSelect(item)
    return
  }

  switch (item.key) {
    case 'clearCache':
      clearCache()
      break
    case 'logout':
      handleLogout()
      break
    case 'feedback':
      uni.showModal({
        title: '意见反馈',
        content: '如有问题请联系：support@anjushouhu.com',
        showCancel: false
      })
      break
    case 'locationPermission':
    case 'recordPermission':
    case 'cameraPermission':
      uni.showModal({
        title: item.label,
        content: '请前往系统设置中管理该权限',
        showCancel: false
      })
      break
    case 'fontScale':
    case 'language':
    case 'theme':
    case 'version':
      toast(item.value ? `当前：${item.value}` : '已是最新')
      break
    default:
      if (item.path) {
        uni.navigateTo({
          url: item.path,
          fail: () => toast('页面开发中')
        })
      }
      break
  }
}

/** 大字模式下的分组标题（响应式刷新用） */
const isElder = computed(() => settingsStore.settings.elderMode)
</script>

<style lang="scss" scoped>
.settings-page {
  min-height: 100vh;
  padding: 32rpx 32rpx 64rpx;
  background: linear-gradient(180deg, #f4efe6 0%, #f5f0e6 100%);
}

/* ---------- 大字模式 Hero ---------- */
.elder-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 36rpx 32rpx;
  margin-bottom: 40rpx;
  background: linear-gradient(135deg, #8fa68e 0%, #a8c4a7 100%);
  border-radius: var(--radius-lg);
  box-shadow: 0 12rpx 40rpx rgba(143, 166, 142, 0.25);
}

.elder-hero-left {
  display: flex;
  align-items: center;
  gap: 24rpx;
  flex: 1;
}

.elder-hero-icon {
  font-size: 56rpx;
  line-height: 1;
}

.elder-hero-text {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.elder-hero-title {
  font-size: 38rpx;
  font-weight: 700;
  color: #ffffff;
}

.elder-hero-desc {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.85);
}

.hero-switch {
  width: 104rpx;
  height: 56rpx;
  padding: 4rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 28rpx;
  transition: background 0.2s ease;
}

.hero-switch.on {
  background: rgba(255, 255, 255, 0.9);
}

.hero-switch-knob {
  width: 48rpx;
  height: 48rpx;
  background: #ffffff;
  border-radius: 50%;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.15);
  transition: transform 0.2s ease;
}

.hero-switch.on .hero-switch-knob {
  transform: translateX(48rpx);
  background: var(--primary-d);
}

/* ---------- 分组 ---------- */
.group {
  margin-bottom: 40rpx;
}

.group-title {
  display: block;
  margin-bottom: 16rpx;
  padding-left: 16rpx;
  font-size: 26rpx;
  font-weight: 600;
  color: var(--text-2);
  letter-spacing: 2rpx;
}

.group-card {
  background: var(--surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow);
  padding: 0 32rpx;
}

.setting-item {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 30rpx 0;
  border-bottom: 2rpx solid var(--divider);
}

.setting-item.last,
.setting-item:last-child {
  border-bottom: none;
}

.setting-item:active {
  opacity: 0.7;
}

.setting-icon-wrapper {
  width: 68rpx;
  height: 68rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  background: var(--primary-l);
  border-radius: 20rpx;
}

.setting-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.setting-label {
  font-size: 32rpx;
  color: var(--text-1);
}

.setting-desc {
  font-size: 22rpx;
  color: var(--text-2);
}

.setting-right {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.setting-value {
  font-size: 28rpx;
  color: var(--text-2);
}

.setting-arrow {
  font-size: 40rpx;
  color: var(--text-3);
  line-height: 1;
}

/* 开关 */
.switch {
  width: 88rpx;
  height: 48rpx;
  padding: 4rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  background: rgba(125, 116, 107, 0.2);
  border-radius: 24rpx;
  transition: background 0.2s ease;
}

.switch.on {
  background: var(--primary);
}

.switch-knob {
  width: 40rpx;
  height: 40rpx;
  background: #ffffff;
  border-radius: 50%;
  transition: transform 0.2s ease;
}

.switch.on .switch-knob {
  transform: translateX(40rpx);
}

/* ---------- 底部操作 ---------- */
.footer-actions {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-top: 48rpx;
}

.footer-btn {
  padding: 30rpx 0;
  background: var(--surface);
  border-radius: var(--radius);
  font-size: 30rpx;
  color: var(--text-1);
  text-align: center;
  box-shadow: var(--shadow);
}

.footer-btn.danger {
  color: var(--danger);
}

.footer-btn:active {
  opacity: 0.85;
}

.version-text {
  display: block;
  margin-top: 40rpx;
  text-align: center;
  font-size: 24rpx;
  color: var(--text-3);
}

/* ---------- 选择弹窗 ---------- */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  align-items: flex-end;
  background: rgba(0, 0, 0, 0.45);
}

.select-box {
  width: 100%;
  padding: 32rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
  background: var(--surface);
  border-radius: 32rpx 32rpx 0 0;
}

.select-title {
  display: block;
  margin-bottom: 24rpx;
  text-align: center;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-1);
}

.select-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 30rpx 24rpx;
  border-radius: 20rpx;
  font-size: 30rpx;
  color: var(--text-1);
}

.select-option.active {
  background: var(--primary-l);
  color: var(--primary-d);
  font-weight: 600;
}

/* ---------- 大字模式增强 ---------- */
.elder-mode .setting-label {
  font-size: 44rpx;
}

.elder-mode .setting-desc {
  font-size: 30rpx;
}

.elder-mode .setting-item {
  padding: 40rpx 0;
}

.elder-mode .setting-icon-wrapper {
  width: 88rpx;
  height: 88rpx;
  font-size: 42rpx;
}

.elder-mode .group-title {
  font-size: 34rpx;
}

.elder-mode .switch {
  width: 112rpx;
  height: 60rpx;
}

.elder-mode .switch.on .switch-knob {
  transform: translateX(52rpx);
}
</style>
