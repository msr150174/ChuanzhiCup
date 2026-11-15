<template>
  <view class="page register-page">
    <view class="reg-header">
      <text class="reg-title">创建账号</text>
      <text class="reg-sub">加入安居守护，为家人多一份安心</text>
    </view>

    <!-- ① 角色选择 -->
    <view class="role-section">
      <text class="role-label">我的身份</text>
      <view class="role-grid">
        <view
          v-for="r in ROLES"
          :key="r.key"
          class="role-card"
          :class="{ active: role === r.key }"
          @click="role = r.key"
        >
          <text class="role-icon">{{ r.icon }}</text>
          <text class="role-name">{{ r.name }}</text>
          <text class="role-desc">{{ r.desc }}</text>
        </view>
      </view>
    </view>

    <!-- ② 基本信息 -->
    <view class="field-group">
      <text class="field-label">手机号码</text>
      <view class="input-box">
        <text class="input-icon">📱</text>
        <input
          v-model="phone"
          class="field-input"
          type="number"
          :maxlength="11"
          placeholder="请输入手机号"
          placeholder-class="field-ph"
        />
      </view>
    </view>

    <view class="field-group">
      <text class="field-label">验证码</text>
      <view class="code-wrapper">
        <view class="input-box flex-1">
          <text class="input-icon">🔑</text>
          <input
            v-model="code"
            class="field-input"
            type="number"
            :maxlength="6"
            placeholder="请输入验证码"
            placeholder-class="field-ph"
          />
        </view>
        <view class="code-btn" :class="{ disabled: countdown > 0 }" @click="handleSendCode">
          <text>{{ countdown > 0 ? `${countdown}秒` : '获取验证码' }}</text>
        </view>
      </view>
    </view>

    <view class="field-group">
      <text class="field-label">称呼</text>
      <view class="input-box">
        <text class="input-icon">👤</text>
        <input
          v-model="name"
          class="field-input"
          :maxlength="20"
          placeholder="怎么称呼您（选填）"
          placeholder-class="field-ph"
        />
      </view>
    </view>

    <!-- ③ 年龄 / 性别（长辈身份时展示） -->
    <view v-if="role === 'elder'" class="field-group">
      <text class="field-label">年龄</text>
      <view class="input-box">
        <text class="input-icon">🎂</text>
        <input
          v-model="age"
          class="field-input"
          type="number"
          :maxlength="3"
          placeholder="请输入年龄（选填）"
          placeholder-class="field-ph"
        />
      </view>
    </view>

    <view class="field-group">
      <text class="field-label">紧急联系人电话</text>
      <view class="input-box">
        <text class="input-icon">📞</text>
        <input
          v-model="emergencyPhone"
          class="field-input"
          type="number"
          :maxlength="11"
          placeholder="家属手机号（选填）"
          placeholder-class="field-ph"
        />
      </view>
    </view>

    <!-- ④ 提交 -->
    <view class="reg-btn" :class="{ 'disabled-btn': !agreementChecked || loading }" @click="handleRegister">
      <text>{{ loading ? '注册中...' : '注 册' }}</text>
    </view>

    <!-- ⑤ 协议 -->
    <view class="agreement-check-area">
      <view class="check-row" @click="agreementChecked = !agreementChecked">
        <view class="checkbox" :class="{ checked: agreementChecked }">
          <text v-if="agreementChecked">✓</text>
        </view>
        <text class="check-text">我已阅读并同意《用户服务协议》与《隐私政策》</text>
      </view>
    </view>

    <view class="login-hint">
      <text>已有账号？</text>
      <text class="link-blue" @click="goLogin">去登录</text>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 注册页
 * ----------------------------------------------------------------------------
 * 两步流程：选身份 → 填资料
 * 后端「首次登录自动建号」，所以这里先登录拿 token，再补资料。
 */
import { ref, onUnmounted } from 'vue'
import { authApi } from '@/services/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const ROLES = [
  { key: 'elder' as const, icon: '👴', name: '长辈', desc: '我是被守护的人' },
  { key: 'family' as const, icon: '👨‍👩‍👧', name: '家属', desc: '我要守护家人' }
]

const role = ref<'elder' | 'family'>('elder')
const phone = ref('')
const code = ref('')
const name = ref('')
const age = ref('')
const emergencyPhone = ref('')
const countdown = ref(0)
const loading = ref(false)
const agreementChecked = ref(false)

let timer: ReturnType<typeof setInterval> | null = null

function toast(msg: string) {
  uni.showToast({ title: msg, icon: 'none' })
}

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

/** 注册 = 首次登录建号 + 补资料 */
async function handleRegister() {
  if (!agreementChecked.value) {
    toast('请先阅读并同意用户协议')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    toast('请输入正确的手机号')
    return
  }
  if (code.value.length !== 6) {
    toast('请输入 6 位验证码')
    return
  }
  if (emergencyPhone.value && !/^1[3-9]\d{9}$/.test(emergencyPhone.value)) {
    toast('紧急联系人手机号格式不正确')
    return
  }

  loading.value = true
  try {
    // ① 登录即建号
    await userStore.login(phone.value, code.value)

    // ② 补充资料（身份 + 称呼 + 可选字段）
    const payload: Record<string, unknown> = {
      role: role.value,
      name: name.value || (role.value === 'elder' ? '长辈用户' : '家属用户')
    }
    if (age.value) payload.age = Number(age.value)
    if (emergencyPhone.value) payload.emergencyPhone = emergencyPhone.value

    await userStore.updateProfile(payload)

    uni.showToast({ title: '注册成功', icon: 'success' })
    setTimeout(() => {
      uni.switchTab({ url: '/pages/home/index' })
    }, 900)
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
.register-page {
  min-height: 100vh;
  padding: 80rpx 64rpx 64rpx;
  background: linear-gradient(180deg, #f4efe6 0%, #faf7f1 100%);
}

/* ---------- 标题 ---------- */
.reg-header {
  margin-bottom: 56rpx;
  display: flex;
  flex-direction: column;
}

.reg-title {
  font-size: 56rpx;
  font-weight: 700;
  color: #2f3a2f;
}

.reg-sub {
  margin-top: 12rpx;
  font-size: 28rpx;
  color: var(--text-2);
}

/* ---------- ① 角色选择 ---------- */
.role-section {
  margin-bottom: 40rpx;
}

.role-label {
  display: block;
  margin-bottom: 20rpx;
  padding-left: 8rpx;
  font-size: 30rpx;
  font-weight: 500;
  color: #2f3a2f;
}

.role-grid {
  display: flex;
  gap: 24rpx;
}

.role-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 32rpx 16rpx;
  background: rgba(255, 255, 255, 0.85);
  border: 4rpx solid transparent;
  border-radius: 32rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
  transition: all 0.25s ease;
}

.role-card.active {
  background: rgba(143, 166, 142, 0.15);
  border-color: var(--primary);
}

.role-icon {
  font-size: 56rpx;
  line-height: 1.2;
}

.role-name {
  font-size: 32rpx;
  font-weight: 600;
  color: #2f3a2f;
}

.role-desc {
  font-size: 24rpx;
  color: var(--text-2);
  text-align: center;
}

/* ---------- ② 表单 ---------- */
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

/* ---------- ④ 提交 ---------- */
.reg-btn {
  width: 100%;
  margin-top: 48rpx;
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

.reg-btn:active {
  opacity: 0.9;
  transform: scale(0.98);
}

.disabled-btn {
  opacity: 0.5;
  box-shadow: none;
}

/* ---------- ⑤ 协议 ---------- */
.agreement-check-area {
  margin-top: 40rpx;
  padding: 28rpx;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 28rpx;
}

.check-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.checkbox {
  width: 40rpx;
  height: 40rpx;
  flex-shrink: 0;
  border: 3rpx solid var(--primary);
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  color: #ffffff;
}

.checkbox.checked {
  background: var(--primary);
}

.check-text {
  flex: 1;
  font-size: 26rpx;
  color: #2f3a2f;
}

/* ---------- 去登录 ---------- */
.login-hint {
  margin-top: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  font-size: 28rpx;
  color: var(--text-2);
}

.link-blue {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--primary-d);
}
</style>
