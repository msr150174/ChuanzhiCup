<template>
  <view class="page login-page">
    <!-- ① 品牌展示区 -->
    <view class="brand-area">
      <view class="brand-logo">
        <text>🛡️</text>
      </view>
      <text class="brand-name">安居守护</text>
      <text class="brand-slogan">让家人安心每一天</text>
    </view>

    <!-- ② 登录方式 Tab -->
    <view class="login-tabs">
      <view
        v-for="t in TABS"
        :key="t.key"
        class="tab-item"
        :class="{ active: activeTab === t.key }"
        @click="activeTab = t.key"
      >
        <text>{{ t.label }}</text>
      </view>
    </view>

    <!-- ③-A 验证码登录 -->
    <view v-if="activeTab === 'code'" class="form-area">
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
            <text>{{ countdown > 0 ? `${countdown}秒后重发` : '获取验证码' }}</text>
          </view>
        </view>
      </view>

      <view class="login-btn" :class="{ 'disabled-btn': !agreementChecked }" @click="handleCodeLogin">
        <text>{{ loading ? '登录中...' : '登 录' }}</text>
      </view>
    </view>

    <!-- ③-B 密码登录 -->
    <view v-if="activeTab === 'password'" class="form-area">
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
        <text class="field-label">密码</text>
        <view class="input-box">
          <text class="input-icon">🔒</text>
          <input
            v-model="password"
            class="field-input"
            :password="!showPassword"
            :maxlength="20"
            placeholder="请输入密码"
            placeholder-class="field-ph"
          />
          <text class="password-toggle" @click="showPassword = !showPassword">
            {{ showPassword ? '🙈' : '👁️' }}
          </text>
        </view>
      </view>

      <view class="forgot-row">
        <text class="link-blue" @click="goForgot">忘记密码？</text>
      </view>

      <view class="login-btn" :class="{ 'disabled-btn': !agreementChecked }" @click="handlePasswordLogin">
        <text>{{ loading ? '登录中...' : '登 录' }}</text>
      </view>
    </view>

    <!-- ③-C 微信登录 -->
    <view v-if="activeTab === 'wechat'" class="wechat-area">
      <text class="wechat-tip">点击下方按钮使用微信一键登录</text>
      <text class="wechat-tip sub">无需记住账号密码</text>
      <view
        class="wechat-btn"
        :class="{ 'disabled-btn': !agreementChecked || wechatLoading }"
        @click="handleWechatLogin"
      >
        <text class="wechat-icon">💬</text>
        <text>微信一键登录</text>
      </view>
    </view>

    <!-- ④ 协议勾选 -->
    <view class="agreement-check-area">
      <view class="check-row" @click="agreementChecked = !agreementChecked">
        <view class="checkbox" :class="{ checked: agreementChecked }">
          <text v-if="agreementChecked">✓</text>
        </view>
        <text class="check-text">我已阅读并同意以下协议：</text>
      </view>
      <view class="agreement-links-row">
        <text class="link-blue" @click.stop="goDoc('user-agreement')">《用户服务协议》</text>
        <text class="separator">、</text>
        <text class="link-blue" @click.stop="goDoc('privacy-policy')">《隐私政策》</text>
        <text class="separator">、</text>
        <text class="link-blue" @click.stop="goDoc('voiceprint-agreement')">《声纹授权协议》</text>
      </view>
    </view>

    <!-- ⑤ 去注册 -->
    <view class="register-row">
      <text class="register-text">还没有账号？</text>
      <text class="link-blue" @click="goRegister">立即注册</text>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 登录页
 * ----------------------------------------------------------------------------
 * 三种登录方式：验证码 / 密码 / 微信
 * 必须先勾选三份协议才能提交（未勾选时按钮置灰，但点击仍有提示）
 */
import { ref, onUnmounted } from 'vue'
import { authApi } from '@/services/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const TABS = [
  { key: 'code' as const, label: '验证码登录' },
  { key: 'password' as const, label: '密码登录' },
  { key: 'wechat' as const, label: '微信登录' }
]

const activeTab = ref<'code' | 'password' | 'wechat'>('code')
const phone = ref('')
const code = ref('')
const password = ref('')
const showPassword = ref(false)
const countdown = ref(0)
const loading = ref(false)
const wechatLoading = ref(false)
const agreementChecked = ref(false)

let timer: ReturnType<typeof setInterval> | null = null

function toast(msg: string) {
  uni.showToast({ title: msg, icon: 'none' })
}

/** 提交前统一校验协议 */
function checkAgreement(): boolean {
  if (!agreementChecked.value) {
    toast('请先阅读并同意用户协议')
    return false
  }
  return true
}

/** 手机号 11 位校验 */
function checkPhone(): boolean {
  if (!/^1[3-9]\d{9}$/.test(phone.value)) {
    toast('请输入正确的手机号')
    return false
  }
  return true
}

/** 获取验证码：60 秒倒计时防重发 */
async function handleSendCode() {
  if (countdown.value > 0) return
  if (!checkPhone()) return

  try {
    const res = await authApi.sendCode(phone.value)
    // 开发模式下后端会把验证码回传，直接提示方便调试
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
    // request 层已统一提示，这里静默
  }
}

/** 登录成功后的统一跳转 */
function afterLogin(isNewUser: boolean) {
  uni.showToast({ title: isNewUser ? '注册成功' : '登录成功', icon: 'success' })
  setTimeout(() => {
    uni.switchTab({ url: '/pages/home/index' })
  }, 800)
}

/** 验证码登录 */
async function handleCodeLogin() {
  if (!checkAgreement()) return
  if (!checkPhone()) return
  if (code.value.length !== 6) {
    toast('请输入 6 位验证码')
    return
  }

  loading.value = true
  try {
    const res = await userStore.login(phone.value, code.value)
    afterLogin(res.isNewUser)
  } catch {
    // request 层已统一提示
  } finally {
    loading.value = false
  }
}

/** 密码登录：后端暂未开放密码接口，走验证码兼容分支 */
async function handlePasswordLogin() {
  if (!checkAgreement()) return
  if (!checkPhone()) return
  if (password.value.length < 6) {
    toast('密码至少 6 位')
    return
  }

  loading.value = true
  try {
    // 后端登录统一用「手机号 + 验证码」，密码方式复用同一入口
    const res = await userStore.login(phone.value, password.value)
    afterLogin(res.isNewUser)
  } catch {
    // request 层已统一提示
  } finally {
    loading.value = false
  }
}

/**
 * 微信一键登录
 *
 * 流程：
 *   ① uni.login() 拿 code
 *   ② 把 code 交给后端换 openid
 *   ③ 后端建号/登录，返回我们自己的 token
 *
 * ★ 后端已接入真实的 code2Session（AppID 沿用上一个项目）。
 *   H5 / 非微信环境不支持 uni.login，会给出明确提示而不是假装成功。
 */
async function handleWechatLogin() {
  if (!checkAgreement()) return
  if (wechatLoading.value) return

  wechatLoading.value = true
  try {
    // ① 拿微信的临时凭证
    const loginRes: any = await new Promise((resolve, reject) => {
      uni.login({
        provider: 'weixin',
        success: resolve,
        fail: reject
      })
    })

    const code = loginRes?.code
    if (!code) {
      toast('微信授权失败，请重试或使用手机号登录')
      return
    }

    // ② 交给后端换 openid 并登录
    const res: any = await authApi.wechatLogin(code)

    if (res?.available === false) {
      toast(res.reason || '微信登录暂不可用，请使用手机号登录')
      return
    }

    if (res?.token) {
      userStore.setAuth(res.token, res.user)
      toast(res.isNewUser ? '欢迎加入！' : '登录成功')
      setTimeout(() => {
        uni.switchTab({ url: '/pages/home/index' })
      }, 600)
      return
    }

    toast('登录失败，请使用手机号登录')

  } catch (e: any) {
    /**
     * ★ 真机微信登录最常见的三类失败，分别给出可操作的提示。
     *   以前一律提示「请使用手机号登录」，用户不知道到底哪出了问题。
     */
    const msg = String(e?.errMsg || e?.message || e || '')
    console.error('[微信登录] 失败:', msg)

    if (msg.includes('request:fail') || msg.includes('url not in domain list')) {
      // 真机强制 https + 备案域名，走 http + IP 会被拦
      toast('网络请求被微信拦截：请在手机上打开调试模式（右上角「···」→ 打开调试）')
    } else if (msg.includes('invalid appid') || msg.includes('未授权') || msg.includes('appid')) {
      toast('AppID 无权限：请确认 wxc9c6da82f1b21327 已把当前微信号加为开发者')
    } else if (msg.includes('login:fail') || msg.includes('not support')) {
      toast('当前环境不支持微信登录，请使用手机号登录')
    } else {
      toast('微信登录失败：' + msg.slice(0, 40))
    }
  } finally {
    wechatLoading.value = false
  }
}

function goForgot() {
  uni.navigateTo({ url: '/pages/forgot-password/index' })
}

function goRegister() {
  uni.navigateTo({ url: '/pages/register/index' })
}

/** 打开协议文档页 */
function goDoc(name: string) {
  uni.navigateTo({ url: `/pages/${name}/index` })
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style lang="scss" scoped>
.login-page {
  min-height: 100vh;
  padding: 96rpx 64rpx 64rpx;
  background: linear-gradient(180deg, #f4efe6 0%, #faf7f1 100%);
  display: flex;
  flex-direction: column;
}

/* ---------- ① 品牌区 ---------- */
.brand-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 72rpx;
}

.brand-logo {
  width: 200rpx;
  height: 200rpx;
  border-radius: 80rpx;
  background: linear-gradient(135deg, #8fa68e, #a8c4a7);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 80rpx;
  box-shadow: 0 16rpx 48rpx rgba(143, 166, 142, 0.25);
  animation: logo-float 3s ease-in-out infinite;
}

@keyframes logo-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-12rpx); }
}

.brand-name {
  margin-top: 40rpx;
  font-size: 52rpx;
  font-weight: 600;
  color: #2f3a2f;
  letter-spacing: 4rpx;
}

.brand-slogan {
  margin-top: 12rpx;
  font-size: 30rpx;
  color: var(--text-2);
  letter-spacing: 2rpx;
}

/* ---------- ② 登录方式 Tab ---------- */
.login-tabs {
  display: flex;
  gap: 16rpx;
  padding: 12rpx;
  margin-bottom: 56rpx;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 56rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.tab-item {
  flex: 1;
  padding: 26rpx 0;
  border-radius: 44rpx;
  text-align: center;
  font-size: 28rpx;
  color: var(--text-2);
  transition: all 0.25s ease;
}

.tab-item.active {
  background: var(--primary);
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 8rpx 24rpx rgba(143, 166, 142, 0.25);
}

/* ---------- ③ 表单区 ---------- */
.form-area,
.wechat-area {
  flex: 1;
}

.field-group {
  margin-bottom: 40rpx;
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
  min-width: 220rpx;
  padding: 28rpx 32rpx;
  background: rgba(143, 166, 142, 0.12);
  border: 2rpx solid rgba(143, 166, 142, 0.2);
  border-radius: 36rpx;
  font-size: 28rpx;
  color: var(--primary-d);
  font-weight: 500;
  text-align: center;
  white-space: nowrap;
}

.code-btn.disabled {
  background: rgba(125, 116, 107, 0.08);
  border-color: transparent;
  color: var(--text-3);
}

.login-btn {
  width: 100%;
  margin-top: 40rpx;
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

.login-btn:active {
  opacity: 0.9;
  transform: scale(0.98);
}

/* 未勾选协议时置灰，但点击仍有提示（由函数内部拦截） */
.disabled-btn {
  opacity: 0.5;
  box-shadow: none;
}

.password-toggle {
  font-size: 34rpx;
  padding: 8rpx;
}

.forgot-row {
  display: flex;
  justify-content: flex-end;
  padding: 8rpx 0;
}

.link-blue {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--primary-d);
}

/* ---------- 微信登录 ---------- */
.wechat-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 32rpx;
}

.wechat-tip {
  font-size: 30rpx;
  line-height: 1.6;
  color: var(--text-2);
  text-align: center;
}

.wechat-tip.sub {
  margin-bottom: 48rpx;
}

.wechat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20rpx;
  width: 100%;
  padding: 34rpx 0;
  background: #07c160;
  border-radius: 36rpx;
  font-size: 34rpx;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: 4rpx;
  box-shadow: 0 12rpx 40rpx rgba(7, 193, 96, 0.25);
}

.wechat-icon {
  font-size: 44rpx;
}

/* ---------- ④ 协议区 ---------- */
.agreement-check-area {
  margin-top: 64rpx;
  padding: 28rpx;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 28rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
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
  font-size: 26rpx;
  font-weight: 500;
  color: #2f3a2f;
}

.agreement-links-row {
  padding-left: 56rpx;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.agreement-links-row .link-blue {
  font-size: 26rpx;
}

.separator {
  font-size: 26rpx;
  color: var(--text-2);
}

/* ---------- ⑤ 去注册 ---------- */
.register-row {
  margin-top: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
}

.register-text {
  font-size: 28rpx;
  color: var(--text-2);
}
</style>
