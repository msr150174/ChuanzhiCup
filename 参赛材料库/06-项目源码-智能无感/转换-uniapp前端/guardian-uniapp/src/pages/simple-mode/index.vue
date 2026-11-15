<template>
  <view class="page" :class="{ 'elder-mode': settingsStore.settings.elderMode }">
    <view class="page-inner">
      <!-- ① 主开关 -->
      <view class="card master-card" :class="{ on: settingsStore.settings.elderMode }">
        <view class="master-head">
          <view class="master-icon">
            <text>👵</text>
          </view>
          <view class="master-main">
            <text class="master-title">长辈极简模式</text>
            <text class="master-desc">开启后全站字号放大、按钮变大，操作更省力</text>
          </view>
          <switch
            :checked="settingsStore.settings.elderMode"
            color="#8FA68E"
            @change="onElderModeChange" />
        </view>
        <view class="master-status">
          <text class="tag" :class="{ 'tag-warning': !settingsStore.settings.elderMode }">
            {{ settingsStore.settings.elderMode ? '已开启' : '已关闭' }}
          </text>
          <text class="status-tip">{{ settingsStore.settings.elderMode ? '全站字号已放大' : '当前为默认字号' }}</text>
        </view>
      </view>

      <!-- ② 效果预览 -->
      <view class="card">
        <text class="card-title">👀 效果预览</text>
        <text class="preview-note">下方为示例界面，实际效果随上方开关实时变化</text>

        <view class="preview-box" :class="{ 'elder-mode': settingsStore.settings.elderMode }">
          <view class="preview-card">
            <view class="flex-between">
              <text class="preview-title">今日健康</text>
              <text class="tag">正常</text>
            </view>
            <view class="preview-row">
              <text class="preview-label">睡眠时长</text>
              <text class="preview-value">7 小时 30 分</text>
            </view>
            <view class="preview-row">
              <text class="preview-label">今日步数</text>
              <text class="preview-value">6,820 步</text>
            </view>
            <view class="preview-row">
              <text class="preview-label">用药提醒</text>
              <text class="preview-value">3 次 · 已完成 1 次</text>
            </view>
            <view class="preview-btn">
              <text>立即打卡</text>
            </view>
          </view>
        </view>
      </view>

      <!-- ③ 其他适老化选项 -->
      <view class="card">
        <text class="card-title">⚙️ 更多适老化选项</text>

        <view class="list-item" @click="toggle('voiceBroadcast')">
          <view class="list-item-left">
            <view class="list-item-icon">🔊</view>
            <view class="item-text">
              <text class="item-title">语音播报</text>
              <text class="item-desc">重要提醒用语音读出来</text>
            </view>
          </view>
          <switch :checked="settingsStore.settings.voiceBroadcast" color="#8FA68E" @click.stop="toggle('voiceBroadcast')" />
        </view>

        <view class="list-item">
          <view class="list-item-left">
            <view class="list-item-icon">🌗</view>
            <view class="item-text">
              <text class="item-title">高对比度文字</text>
              <text class="item-desc">加深文字颜色，弱光环境也看得清</text>
            </view>
          </view>
          <switch :checked="highContrast" color="#8FA68E" @change="onHighContrastChange" />
        </view>

        <view class="list-item">
          <view class="list-item-left">
            <view class="list-item-icon">📋</view>
            <view class="item-text">
              <text class="item-title">简化首页菜单</text>
              <text class="item-desc">首页只保留打卡、SOS、用药三个入口</text>
            </view>
          </view>
          <switch :checked="simpleMenu" color="#8FA68E" @change="onSimpleMenuChange" />
        </view>

        <view class="list-item" @click="toggle('alertPush')">
          <view class="list-item-left">
            <view class="list-item-icon">🔔</view>
            <view class="item-text">
              <text class="item-title">告警推送</text>
              <text class="item-desc">异常情况第一时间通知</text>
            </view>
          </view>
          <switch :checked="settingsStore.settings.alertPush" color="#8FA68E" @click.stop="toggle('alertPush')" />
        </view>
      </view>

      <!-- ④ 说明 -->
      <view class="card tip-card">
        <text class="tip-title">📖 使用说明</text>
        <text class="tip-line">· 开启极简模式后，全部页面的字号会放大到约 1.6 倍</text>
        <text class="tip-line">· 按钮高度从 88rpx 增加到 128rpx，手指点按更精准</text>
        <text class="tip-line">· 设置会自动保存在本机，下次打开应用依然生效</text>
        <text class="tip-line">· 建议为家中长辈长期开启，可随时在本页关闭</text>
      </view>

      <view class="btn-ghost reset-btn" @click="resetAll">
        <text>恢复默认设置</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 长辈极简模式
 * ----------------------------------------------------------------------------
 * 大字模式开关（写入 settings store，全站生效）+ 大字界面预览 + 其他适老化选项。
 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useSettingsStore } from '@/stores/settings'

const settingsStore = useSettingsStore()

const HIGH_CONTRAST_KEY = 'guardian_high_contrast'
const SIMPLE_MENU_KEY = 'guardian_simple_menu'

const highContrast = ref(false)
const simpleMenu = ref(false)

/** 大字模式开关 */
function onElderModeChange(e: { detail: { value: boolean } }) {
  settingsStore.set('elderMode', e.detail.value)
  uni.showToast({
    title: e.detail.value ? '已开启极简模式' : '已关闭极简模式',
    icon: 'none'
  })
}

/** 切换布尔设置 */
function toggle(key: 'voiceBroadcast' | 'alertPush') {
  settingsStore.toggle(key)
  const on = settingsStore.settings[key]
  uni.showToast({ title: on ? '已开启' : '已关闭', icon: 'none' })
}

/** 高对比度开关 */
function onHighContrastChange(e: { detail: { value: boolean } }) {
  highContrast.value = e.detail.value
  try {
    uni.setStorageSync(HIGH_CONTRAST_KEY, highContrast.value)
  } catch {
    // 忽略写入异常
  }
}

/** 简化菜单开关 */
function onSimpleMenuChange(e: { detail: { value: boolean } }) {
  simpleMenu.value = e.detail.value
  try {
    uni.setStorageSync(SIMPLE_MENU_KEY, simpleMenu.value)
  } catch {
    // 忽略写入异常
  }
}

/** 恢复默认 */
function resetAll() {
  uni.showModal({
    title: '恢复默认设置',
    content: '将关闭极简模式并恢复全部默认选项，确定继续吗？',
    success: (res) => {
      if (!res.confirm) return
      settingsStore.reset()
      highContrast.value = false
      simpleMenu.value = false
      try {
        uni.removeStorageSync(HIGH_CONTRAST_KEY)
        uni.removeStorageSync(SIMPLE_MENU_KEY)
      } catch {
        // 忽略
      }
      uni.showToast({ title: '已恢复默认', icon: 'none' })
    }
  })
}

onLoad(() => {
  try {
    highContrast.value = Boolean(uni.getStorageSync(HIGH_CONTRAST_KEY))
    simpleMenu.value = Boolean(uni.getStorageSync(SIMPLE_MENU_KEY))
  } catch {
    highContrast.value = false
    simpleMenu.value = false
  }
})
</script>

<style lang="scss" scoped>
.page-inner {
  padding: 24rpx;
}

/* ---------- 主开关 ---------- */
.master-card {
  border-left: 8rpx solid var(--text-3);
}

.master-card.on {
  border-left-color: var(--primary-d);
  background: linear-gradient(135deg, var(--surface), var(--primary-l));
}

.master-head {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.master-icon {
  width: 96rpx;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--primary-l);
  font-size: 48rpx;
}

.master-main {
  flex: 1;
  min-width: 0;
}

.master-title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: var(--text-1);
}

.master-desc {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: var(--text-2);
  line-height: 1.6;
}

.master-status {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 24rpx;
  padding-top: 20rpx;
  border-top: 2rpx solid var(--divider);
}

.status-tip {
  font-size: 24rpx;
  color: var(--text-2);
}

/* ---------- 预览 ---------- */
.preview-note {
  display: block;
  margin: -12rpx 0 20rpx;
  font-size: 24rpx;
  color: var(--text-3);
}

.preview-box {
  padding: 24rpx;
  border-radius: var(--radius);
  background: var(--bg);
  border: 2rpx dashed var(--border);
}

.preview-card {
  padding: 24rpx;
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow);
}

.preview-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--text-1);
}

.preview-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 0;
  border-bottom: 2rpx solid var(--divider);
}

.preview-label {
  font-size: 28rpx;
  color: var(--text-2);
}

.preview-value {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text-1);
}

.preview-btn {
  margin-top: 24rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius);
  background: linear-gradient(135deg, var(--primary), #9db89c);
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 600;
}

/* 预览区里放大文字（复用全局 .elder-mode 规则） */
.preview-box.elder-mode .preview-title {
  font-size: 44rpx;
}

.preview-box.elder-mode .preview-label,
.preview-box.elder-mode .preview-value {
  font-size: 38rpx;
}

.preview-box.elder-mode .preview-row {
  padding: 32rpx 0;
}

.preview-box.elder-mode .preview-btn {
  height: 128rpx;
  font-size: 44rpx;
}

/* ---------- 列表项 ---------- */
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
}

/* ---------- 提示 ---------- */
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
  line-height: 1.9;
}

.reset-btn {
  margin-bottom: 48rpx;
}
</style>
