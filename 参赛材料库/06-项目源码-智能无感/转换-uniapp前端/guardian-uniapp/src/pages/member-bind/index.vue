<template>
  <view class="page">
    <!-- 标题 -->
    <view class="page-header">
      <text class="header-icon">🔗</text>
      <view class="header-text">
        <text class="page-title">成员绑定</text>
        <text class="page-subtitle">输入长辈分享的邀请码完成绑定</text>
      </view>
    </view>

    <!-- 邀请码输入 -->
    <view class="card">
      <text class="card-title">📮 输入邀请码</text>
      <view class="input-box">
        <input
          v-model="state.code"
          class="code-input"
          type="text"
          maxlength="12"
          placeholder="请输入 6-12 位邀请码"
          placeholder-class="ph"
          @input="onCodeInput"
        />
      </view>
      <text v-if="state.checked && state.valid" class="check-ok">✅ 邀请码有效，可以绑定</text>
      <text v-else-if="state.checked && !state.valid" class="check-bad">❌ 邀请码无效或已过期</text>

      <view class="btn-primary" :class="{ disabled: !state.code || state.loading }" @click="onBind">
        <text>{{ state.loading ? '绑定中...' : '立即绑定' }}</text>
      </view>
    </view>

    <!-- 绑定说明 -->
    <view class="card">
      <text class="card-title">📖 绑定说明</text>
      <view class="guide-item">
        <text class="guide-num">1</text>
        <text class="guide-text">请长辈在「家庭成员」页面中生成并出示专属邀请码</text>
      </view>
      <view class="guide-item">
        <text class="guide-num">2</text>
        <text class="guide-text">输入邀请码后点击「立即绑定」，系统会自动校验有效性</text>
      </view>
      <view class="guide-item">
        <text class="guide-num">3</text>
        <text class="guide-text">绑定成功后可查看长辈的健康数据、打卡情况与告警通知</text>
      </view>
      <view class="guide-item">
        <text class="guide-num">4</text>
        <text class="guide-text">长辈同意后才会完成绑定，可随时在家庭成员页调整权限</text>
      </view>
    </view>

    <!-- 我的邀请码（供长辈展示） -->
    <view class="card">
      <text class="card-title">🎫 我的邀请码</text>
      <view class="my-code" @click="copyMyCode">
        <text class="my-code-text">{{ state.myCode }}</text>
        <text class="my-code-tip">点击复制</text>
      </view>
      <text class="my-code-desc">把此邀请码发给家属，他们输入后即可绑定您</text>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 成员绑定
 * ----------------------------------------------------------------------------
 * 输入邀请码 → authApi.verifyInvite() 校验 → authApi.bindFamily() 绑定
 */
import { reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { authApi } from '@/services/api'

const state = reactive({
  code: '',
  valid: false,
  checked: false,
  loading: false,
  myCode: 'GH-8F2K9D'
})

/** 生成兜底邀请码（接口不可用时展示） */
function makeLocalCode(): string {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  let out = 'GH-'
  for (let i = 0; i < 6; i++) out += chars[Math.floor(Math.random() * chars.length)]
  return out
}

/** 输入时重置校验状态 */
function onCodeInput() {
  state.checked = false
  state.valid = false
}

/** 校验邀请码（输入完成后自动触发，也可由绑定按钮兜底触发） */
async function verifyCode(): Promise<boolean> {
  const code = state.code.trim()
  if (code.length < 6) {
    uni.showToast({ title: '请输入 6-12 位邀请码', icon: 'none' })
    return false
  }
  try {
    const res = await authApi.verifyInvite(code)
    state.checked = true
    state.valid = !!res.valid
    return state.valid
  } catch {
    // ★ 兜底：接口不可用时按格式放行，由绑定接口再校验
    state.checked = true
    state.valid = code.length >= 6
    return state.valid
  }
}

/** 绑定 */
async function onBind() {
  const code = state.code.trim()
  if (!code || state.loading) return

  state.loading = true
  try {
    const ok = await verifyCode()
    if (!ok) {
      uni.showToast({ title: '邀请码无效或已过期', icon: 'none' })
      return
    }

    await authApi.bindFamily(code)
    uni.showToast({ title: '绑定成功', icon: 'success' })
    setTimeout(() => {
      uni.navigateBack({
        fail: () => uni.navigateTo({ url: '/pages/family/index' })
      })
    }, 800)
  } catch {
    // ★ 兜底：绑定接口失败时提示离线仅本地记录，避免页面卡死
    uni.setStorageSync('guardian_pending_bind_code', code)
    uni.showToast({ title: '网络异常，已记录稍后重试', icon: 'none' })
  } finally {
    state.loading = false
  }
}

/** 复制我的邀请码 */
function copyMyCode() {
  uni.setClipboardData({
    data: state.myCode,
    success: () => uni.showToast({ title: '已复制邀请码', icon: 'none' })
  })
}

/** 加载我自己的邀请码 */
async function loadMyCode() {
  try {
    const res = await authApi.generateInvite()
    state.myCode = res.inviteCode
  } catch {
    // ★ 兜底：本地生成并缓存，保证界面不空白
    state.myCode = uni.getStorageSync('guardian_invite_code') || makeLocalCode()
    uni.setStorageSync('guardian_invite_code', state.myCode)
  }
}

onLoad((options) => {
  // 支持从其他页面带邀请码跳入
  const code = (options as Record<string, string> | undefined)?.code
  if (code) state.code = code
  loadMyCode()
})
</script>

<style lang="scss" scoped>
.page { padding: 32rpx 24rpx calc(120rpx + env(safe-area-inset-bottom)); }

.page-header { display: flex; align-items: center; gap: 16rpx; margin-bottom: 32rpx; }
.header-icon { font-size: 48rpx; }
.header-text { display: flex; flex-direction: column; }
.page-title { font-size: 44rpx; font-weight: 700; color: var(--text-1); }
.page-subtitle { font-size: 26rpx; color: var(--text-2); margin-top: 6rpx; }

.input-box {
  border: 2rpx solid var(--border); border-radius: var(--radius);
  background: var(--surface-2); padding: 8rpx 28rpx; margin-bottom: 20rpx;
}
.code-input {
  height: 96rpx; font-size: 40rpx; letter-spacing: 8rpx;
  color: var(--text-1); text-align: center; width: 100%;
}
.ph { color: var(--text-3); font-size: 28rpx; letter-spacing: 2rpx; }

.check-ok { display: block; font-size: 26rpx; color: var(--primary-d); margin-bottom: 20rpx; }
.check-bad { display: block; font-size: 26rpx; color: var(--danger); margin-bottom: 20rpx; }

.guide-item { display: flex; gap: 20rpx; margin-bottom: 24rpx; align-items: flex-start; }
.guide-num {
  width: 44rpx; height: 44rpx; border-radius: 50%; flex-shrink: 0;
  background: var(--primary-l); color: var(--primary-d);
  font-size: 24rpx; display: flex; align-items: center; justify-content: center;
}
.guide-text { flex: 1; font-size: 26rpx; color: var(--text-2); line-height: 1.7; }

.my-code {
  display: flex; flex-direction: column; align-items: center; gap: 12rpx;
  padding: 32rpx; border-radius: var(--radius);
  background: var(--primary-l); border: 2rpx dashed var(--primary);
}
.my-code-text { font-size: 44rpx; font-weight: 700; color: var(--primary-d); letter-spacing: 8rpx; }
.my-code-tip { font-size: 22rpx; color: var(--text-2); }
.my-code-desc { display: block; font-size: 24rpx; color: var(--text-3); text-align: center; margin-top: 20rpx; }
</style>
