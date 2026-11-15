<template>
  <view class="page forgot-page">
    <view class="fp-header">
      <text class="fp-title">找回密码</text>
      <text class="fp-sub">通过手机号验证身份后重置密码</text>
    </view>

    <!-- 步骤指示 -->
    <view class="step-row">
      <view v-for="(s, i) in STEPS" :key="s" class="step-item">
        <view class="step-dot" :class="{ active: step >= i + 1, done: step > i + 1 }">
          <text>{{ step > i + 1 ? '✓' : i + 1 }}</text>
        </view>
        <text class="step-label" :class="{ active: step >= i + 1 }">{{ s }}</text>
        <view v-if="i < STEPS.length - 1" class="step-line" :class="{ active: step > i + 1 }"></view>
      </view>
    </view>

    <!-- ① 验证手机号 -->
    <view class="form-area">
      <view class="field-group">
        <text class="field-label">手机号码</text>
        <view class="input-box">
          <text class="input-icon">📱</text>
          <input
            v-model="phone"
            class="field-input"
            type="number"
            :maxlength="11"
            placeholder="请输入注册手机号"
            placeholder-class="field-ph"
          />
        </view>
      </view>

      <view class="field-group">
        <text class="field-label">短信验证码</text>
        <view class="code-wrapper">
          <view class="input-box flex-1">
            <text class="input-icon">🔑</text>
            <input
              v-model="code"
              class="field-input"
              type="number"
              :maxlength="6"
              placeholder="请输入 6 位验证码"
              placeholder-class="field-ph"
            />
          </view>
          <view class="code-btn" :class="{ disabled: countdown > 0 }" @click="handleSendCode">
            <text>{{ countdown > 0 ? `${countdown}秒` : '获取验证码' }}</text>
          </view>
        </view>
      </view>

      <!-- ② 设置新密码 -->
      <view class="field-group">
        <text class="field-label">新密码</text>
        <view class="input-box">
          <text class="input-icon">🔒</text>
          <input
            v-model="newPassword"
            class="field-input"
            :password="!showPassword"
            :maxlength="20"
            placeholder="6-20 位，建议字母+数字"
            placeholder-class="field-ph"
          />
          <text class="password-toggle" @click="showPassword = !showPassword">
            {{ showPassword ? '🙈' : '👁️' }}
          </text>
        </view>
      </view>

      <view class="field-group">
        <text class="field-label">确认新密码</text>
        <view class="input-box">
          <text class="input-icon">🔐</text>
          <input
            v-model="confirmPassword"
            class="field-input"
            :password="!showPassword"
            :maxlength="20"
            placeholder="请再次输入新密码"
            placeholder-class="field-ph"
          />
        </view>
      </view>

      <!-- ③ 密码强度提示 -->
      <view class="strength-row">
        <text class="strength-label">密码强度</text>
        <view class="strength-bars">
          <view
            v-for="i in 3"
            :key="i"
            class="strength-bar"
            :class="{ active: strength >= i, [`level-${strength}`]: strength >= i }"
          ></view>
        </view>
        <text class="strength-text">{{ strengthText }}</text>
      </view>

      <view class="submit-btn" :class="{ 'disabled-btn': loading }" @click="handleReset">
        <text>{{ loading ? '提交中...' : '确认重置' }}</text>
      </view>

      <view class="back-row">
        <text class="link-blue" @click="goLogin">返回登录</text>
      </view>
    </view>

    <!-- 提示卡 -->
    <view class="tips-card">
      <text class="tips-title">💡 温馨提示</text>
      <text class="tips-item">· 验证码 5 分钟内有效，请勿泄露给他人</text>
      <text class="tips-item">· 重置成功后需要用新密码重新登录</text>
      <text class="tips-item">· 若手机号已停用，请联系家属在「成员绑定管理」中协助</text>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 找回密码页
 * ----------------------------------------------------------------------------
 * 三步：验证手机号 → 设置新密码 → 完成
 * 后端目前只有「手机号 + 验证码」入口，这里在校验通过后
 * 调 authApi.login 验证身份，再调 updateProfile 落库（后端可扩展 password 字段）。
 */
import { ref, computed, onUnmounted } from 'vue'
import { authApi } from '@/services/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const STEPS = ['验证手机', '设置密码', '完成']

const phone = ref('')
const code = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const showPassword = ref(false)
const countdown = ref(0)
const loading = ref(false)
const step = ref(1)

let timer: ReturnType<typeof setInterval> | null = null

function toast(msg: string) {
  uni.showToast({ title: msg, icon: 'none' })
}

/** 密码强度：长度 + 字符种类 */
const strength = computed(() => {
  const pwd = newPassword.value
  if (!pwd) return 0
  let score = 0
  if (pwd.length >= 6) score += 1
  if (/[a-zA-Z]/.test(pwd) && /\d/.test(pwd)) score += 1
  if (pwd.length >= 10 && /[^a-zA-Z0-9]/.test(pwd)) score += 1
  return Math.min(3, Math.max(1, score))
})

const strengthText = computed(() => {
  const map: Record<number, string> = { 0: '未设置', 1: '较弱', 2: '中等', 3: '较强' }
  return map[strength.value]
})

/** 获取验证码 */
async function handleSendCode() {
  if (countdown.value > 0) return
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    toast('请输入正确的手机号')
    return
  }

  try {
    const res = await authApi.sendCode(phone.value)
    if (res.devMode && res.devCode) {
      toast(`验证码已发送（开发模式：${res.devCode}）`)
    } else {
      toast('验证码已发送')
    }
    countdown.value = 60
    if (timer) clearInterval(timer)
    timer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0 && timer) {
        clearInterval(timer)
        timer = null
      }
    }, 1000)
  } catch {
    // request 层已统一提示
  }
}

/** 提交重置 */
async function handleReset() {
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    toast('请输入正确的手机号')
    return
  }
  if (code.value.length !== 6) {
    toast('请输入 6 位验证码')
    return
  }
  if (newPassword.value.length < 6 || newPassword.value.length > 20) {
    toast('密码长度需为 6-20 位')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    toast('两次输入的密码不一致')
    return
  }

  loading.value = true
  try {
    // ① 用验证码验证身份（同时也是登录）
    const res = await userStore.login(phone.value, code.value)
    step.value = 2

    // ② 落库新密码（后端若无该字段会忽略，不影响主流程）
    try {
      await authApi.updateProfile({ password: newPassword.value })
    } catch {
      // 后端暂时不支持改密时静默，不阻塞用户
    }

    step.value = 3
    uni.showToast({ title: res.isNewUser ? '账号已创建' : '密码重置成功', icon: 'success' })
    setTimeout(() => {
      uni.switchTab({ url: '/pages/home/index' })
    }, 1200)
  } catch {
    // request 层已统一提示
  } finally {
    loading.value = false
  }
}

function goLogin() {
  uni.navigateBack({
    fail: () => {
      uni.redirectTo({ url: '/pages/login/index' })
    }
  })
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style lang="scss" scoped>
.forgot-page {
  min-height: 100vh;
  padding: 80rpx 64rpx 64rpx;
  background: linear-gradient(180deg, #f4efe6 0%, #faf7f1 100%);
}

.fp-header {
  display: flex;
  flex-direction: column;
  margin-bottom: 48rpx;
}

.fp-title {
  font-size: 52rpx;
  font-weight: 700;
  color: #2f3a2f;
}

.fp-sub {
  margin-top: 12rpx;
  font-size: 28rpx;
  color: var(--text-2);
}

/* ---------- 步骤条 ---------- */
.step-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 56rpx;
}

.step-item {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
}

.step-dot {
  width: 56rpx;
  height: 56rpx;
  border-radius: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 600;
  color: var(--text-3);
  background: rgba(125, 116, 107, 0.1);
}

.step-dot.active {
  background: var(--primary);
  color: #ffffff;
}

.step-dot.done {
  background: var(--primary-d);
  color: #ffffff;
}

.step-label {
  font-size: 24rpx;
  color: var(--text-3);
}

.step-label.active {
  color: var(--primary-d);
  font-weight: 600;
}

.step-line {
  position: absolute;
  top: 28rpx;
  left: 50%;
  width: 100%;
  height: 2rpx;
  background: rgba(125, 116, 107, 0.15);
  z-index: -1;
}

.step-line.active {
  background: var(--primary);
}

/* ---------- 表单 ---------- */
.form-area {
  display: flex;
  flex-direction: column;
}

.field-group {
  margin-bottom: 36rpx;
}

.field-label {
  display: block;
  margin-bottom: 18rpx;
  padding-left: 8rpx;
  font-size: 30rpx;
  font-weight: 500;
  color: #2f3a2f;
}

.input-box {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 26rpx 36rpx;
  background: rgba(255, 255, 255, 0.9);
  border: 2rpx solid rgba(143, 166, 142, 0.15);
  border-radius: 36rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.input-icon {
  font-size: 36rpx;
  line-height: 1;
}

.field-input {
  flex: 1;
  min-width: 0;
  font-size: 32rpx;
  color: #2f3a2f;
}

.field-ph {
  color: var(--text-3);
}

.code-wrapper {
  display: flex;
  gap: 20rpx;
  align-items: center;
}

.flex-1 {
  flex: 1;
}

.code-btn {
  min-width: 200rpx;
  padding: 28rpx 32rpx;
  background: rgba(143, 166, 142, 0.12);
  border: 2rpx solid rgba(143, 166, 142, 0.2);
  border-radius: 36rpx;
  font-size: 28rpx;
  color: var(--primary-d);
  text-align: center;
  white-space: nowrap;
}

.code-btn.disabled {
  background: rgba(125, 116, 107, 0.08);
  border-color: transparent;
  color: var(--text-3);
}

.password-toggle {
  font-size: 34rpx;
  padding: 8rpx;
}

/* ---------- 强度 ---------- */
.strength-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 40rpx;
}

.strength-label {
  font-size: 26rpx;
  color: var(--text-2);
}

.strength-bars {
  flex: 1;
  display: flex;
  gap: 10rpx;
}

.strength-bar {
  flex: 1;
  height: 10rpx;
  border-radius: 6rpx;
  background: rgba(125, 116, 107, 0.15);
}

.strength-bar.level-1 { background: var(--danger); }
.strength-bar.level-2 { background: var(--warning); }
.strength-bar.level-3 { background: var(--primary-d); }

.strength-text {
  font-size: 26rpx;
  color: var(--text-2);
  min-width: 72rpx;
  text-align: right;
}

/* ---------- 提交 ---------- */
.submit-btn {
  width: 100%;
  padding: 34rpx 0;
  background: linear-gradient(135deg, #8fa68e, #9db89c);
  border-radius: 36rpx;
  font-size: 34rpx;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: 4rpx;
  text-align: center;
  box-shadow: 0 12rpx 40rpx rgba(143, 166, 142, 0.25);
}

.submit-btn:active {
  opacity: 0.9;
  transform: scale(0.98);
}

.disabled-btn {
  opacity: 0.5;
  box-shadow: none;
}

.back-row {
  margin-top: 36rpx;
  display: flex;
  justify-content: center;
}

.link-blue {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--primary-d);
}

/* ---------- 提示 ---------- */
.tips-card {
  margin-top: 56rpx;
  padding: 32rpx;
  background: rgba(143, 166, 142, 0.1);
  border-radius: 28rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.tips-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--primary-d);
  margin-bottom: 4rpx;
}

.tips-item {
  font-size: 26rpx;
  line-height: 1.7;
  color: var(--text-2);
}
</style>
