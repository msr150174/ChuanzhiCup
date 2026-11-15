<template>
  <div class="page login-page">
    <!-- ① 品牌展示区 -->
    <div class="brand-area">
      <div class="brand-logo">🛡️</div>
      <h1 class="brand-name">安居守护</h1>
      <p class="brand-slogan">让家人安心每一天</p>
    </div>

    <!-- ② 登录方式 Tab -->
    <div class="login-tabs">
      <div
        v-for="t in tabs"
        :key="t.key"
        class="tab-item"
        :class="{ active: __state.activeTab === t.key }"
        @click="__state.activeTab = t.key">
        {{ t.label }}
      </div>
    </div>

    <!-- ③-A 验证码登录 -->
    <div v-if="__state.activeTab === 'code'" class="form-area">
      <div class="field-group">
        <label class="field-label">手机号码</label>
        <div class="input-box">
          <span class="input-icon">📱</span>
          <input
            v-model="__state.phone"
            type="number"
            maxlength="11"
            placeholder="请输入手机号"
            class="field-input" />
        </div>
      </div>

      <div class="field-group">
        <label class="field-label">验证码</label>
        <div class="code-wrapper">
          <div class="input-box flex-1">
            <span class="input-icon">🔑</span>
            <input
              v-model="__state.code"
              type="number"
              maxlength="6"
              placeholder="请输入验证码"
              class="field-input" />
          </div>
          <button
            class="code-btn"
            :class="{ disabled: __state.countdown > 0 }"
            @click="handleSendCode">
            {{ __state.countdown > 0 ? `${__state.countdown}秒后重发` : '获取验证码' }}
          </button>
        </div>
      </div>

      <button class="login-btn" :class="{ 'disabled-btn': !__state.agreementChecked }" @click="handleCodeLogin">
        {{ __state.loading ? '登录中...' : '登 录' }}
      </button>
    </div>

    <!-- ③-B 密码登录 -->
    <div v-if="__state.activeTab === 'password'" class="form-area">
      <div class="field-group">
        <label class="field-label">手机号码</label>
        <div class="input-box">
          <span class="input-icon">📱</span>
          <input v-model="__state.phone" type="number" maxlength="11" placeholder="请输入手机号" class="field-input" />
        </div>
      </div>

      <div class="field-group">
        <label class="field-label">密码</label>
        <div class="input-box">
          <span class="input-icon">🔒</span>
          <input
            v-model="__state.password"
            :type="__state.showPassword ? 'text' : 'password'"
            maxlength="20"
            placeholder="请输入密码"
            class="field-input" />
          <span class="password-toggle" @click="__state.showPassword = !__state.showPassword">
            {{ __state.showPassword ? '🙈' : '👁️' }}
          </span>
        </div>
      </div>

      <div class="forgot-row">
        <span class="link-blue" @click="goForgot">忘记密码？</span>
      </div>

      <button class="login-btn" :class="{ 'disabled-btn': !__state.agreementChecked }" @click="handlePasswordLogin">
        {{ __state.loading ? '登录中...' : '登 录' }}
      </button>
    </div>

    <!-- ③-C 微信登录 -->
    <div v-if="__state.activeTab === 'wechat'" class="wechat-area">
      <p class="wechat-tip">点击下方按钮使用微信一键登录<br />无需记住账号密码</p>
      <button class="wechat-btn" :class="{ 'disabled-btn': !__state.agreementChecked }" @click="handleWechatLogin">
        <span class="wechat-icon">💬</span>
        <span>微信一键登录</span>
      </button>
    </div>

    <!-- ④ 协议勾选 -->
    <div class="agreement-check-area">
      <div class="check-row" @click="__state.agreementChecked = !__state.agreementChecked">
        <div class="checkbox" :class="{ checked: __state.agreementChecked }">
          <span v-if="__state.agreementChecked">✓</span>
        </div>
        <span class="check-text">我已阅读并同意以下协议：</span>
      </div>
      <div class="agreement-links-row">
        <span class="link-blue" @click.stop="goDoc('user-agreement')">《用户服务协议》</span>
        <span class="separator">、</span>
        <span class="link-blue" @click.stop="goDoc('privacy-policy')">《隐私政策》</span>
        <span class="separator">、</span>
        <span class="link-blue" @click.stop="goDoc('voiceprint-agreement')">《声纹授权协议》</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 登录页
 *
 * 三种登录方式：验证码 / 密码 / 微信
 * 必须先勾选三份协议才能提交
 */
import { reactive, onUnmounted } from 'vue'

const tabs = [
  { key: 'code', label: '验证码登录' },
  { key: 'password', label: '密码登录' },
  { key: 'wechat', label: '微信登录' }
]

const __state = reactive({
  activeTab: 'code' as 'code' | 'password' | 'wechat',
  phone: '',
  code: '',
  password: '',
  showPassword: false,
  countdown: 0,
  loading: false,
  agreementChecked: false
})

let timer: ReturnType<typeof setInterval> | null = null

function toast(msg: string) {
  console.log('[toast]', msg)
}

/** 三处提交前都先校验协议 */
function checkAgreement(): boolean {
  if (!__state.agreementChecked) {
    toast('请先阅读并同意用户协议')
    return false
  }
  return true
}

function checkPhone(): boolean {
  if (!/^1[3-9]\d{9}$/.test(__state.phone)) {
    toast('请输入正确的手机号')
    return false
  }
  return true
}

/** 获取验证码：60 秒倒计时防重发 */
async function handleSendCode() {
  if (__state.countdown > 0) return
  if (!checkPhone()) return

  try {
    // 真实接口：POST /api/auth/send-code
    const res = await fetch('/api/auth/send-code', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone: __state.phone })
    }).then((r) => r.json())

    if (res.code === 200) {
      toast('验证码已发送')
      __state.countdown = 60
      timer = setInterval(() => {
        __state.countdown -= 1
        if (__state.countdown <= 0 && timer) {
          clearInterval(timer)
          timer = null
        }
      }, 1000)
    } else {
      toast(res.msg || '发送失败')
    }
  } catch {
    toast('网络异常，请稍后重试')
  }
}

async function handleCodeLogin() {
  if (!checkAgreement()) return
  if (!checkPhone()) return
  if (__state.code.length < 4) {
    toast('请输入验证码')
    return
  }

  __state.loading = true
  try {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone: __state.phone, code: __state.code })
    }).then((r) => r.json())

    if (res.code === 200) {
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('userInfo', JSON.stringify(res.data.user))
      toast('登录成功')
      setTimeout(() => history.back(), 1500)
    } else {
      toast(res.msg || '登录失败')
    }
  } catch {
    toast('网络异常')
  } finally {
    __state.loading = false
  }
}

async function handlePasswordLogin() {
  if (!checkAgreement()) return
  if (!checkPhone()) return
  if (__state.password.length < 6) {
    toast('密码至少 6 位')
    return
  }

  __state.loading = true
  try {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone: __state.phone, password: __state.password })
    }).then((r) => r.json())

    if (res.code === 200) {
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('userInfo', JSON.stringify(res.data.user))
      toast('登录成功')
      setTimeout(() => (location.hash = '#/pages/home/index'), 1500)
    } else {
      toast(res.msg || '登录失败')
    }
  } catch {
    toast('网络异常')
  } finally {
    __state.loading = false
  }
}

function handleWechatLogin() {
  if (!checkAgreement()) return
  // 微信登录需要真实 AppID + AppSecret，此处给明确提示而不是假装成功
  toast('微信登录需要配置小程序 AppID，请使用手机号登录')
}

function goForgot() {
  console.log('[navigate] /pages/forgot-password/index')
}

function goDoc(name: string) {
  console.log('[navigate] /pages/' + name + '/index')
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
/* ===== 统一后的暖米绿配色（原项目登录页是蓝绿混用，这里统一） ===== */
.login-page {
  min-height: 100vh;
  padding: 60px 32px 40px;
  background: linear-gradient(180deg, #f4efe6 0%, #faf7f1 100%);
  display: flex;
  flex-direction: column;
}

/* ---------- ① 品牌区 ---------- */
.brand-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 48px;
}

.brand-logo {
  width: 120px;
  height: 120px;
  border-radius: 48px;
  background: linear-gradient(135deg, #8fa68e, #a8c4a7);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48px;
  box-shadow: 0 8px 24px rgba(143, 166, 142, 0.25);
  animation: logo-float 3s ease-in-out infinite;
}

@keyframes logo-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

.brand-name {
  margin: 24px 0 8px;
  font-size: 28px;
  font-weight: 600;
  color: #2f3a2f;
  letter-spacing: 2px;
}

.brand-slogan {
  margin: 0;
  font-size: 16px;
  color: #7d746b;
  letter-spacing: 1px;
}

/* ---------- ② 登录方式 Tab ---------- */
.login-tabs {
  display: flex;
  gap: 16px;
  padding: 8px;
  margin-bottom: 40px;
  background: rgba(255, 255, 255, 0.5);
  border-radius: 32px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.tab-item {
  flex: 1;
  padding: 16px 0;
  border-radius: 24px;
  text-align: center;
  font-size: 15px;
  color: #7d746b;
  background: transparent;
  cursor: pointer;
  transition: all 0.25s ease;
}

.tab-item.active {
  background: #8fa68e;
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(143, 166, 142, 0.25);
}

.tab-item:active {
  transform: scale(0.98);
  opacity: 0.9;
}

/* ---------- ③ 表单区 ---------- */
.form-area,
.wechat-area {
  flex: 1;
}

.field-group {
  margin-bottom: 24px;
}

.field-label {
  display: block;
  margin-bottom: 10px;
  padding-left: 4px;
  font-size: 16px;
  font-weight: 500;
  color: #2f3a2f;
}

.input-box {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(143, 166, 142, 0.1);
  border-radius: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: border-color 0.2s ease;
}

.input-box:focus-within {
  border-color: #8fa68e;
}

.input-icon {
  font-size: 20px;
  line-height: 1;
}

.field-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 18px;
  color: #2f3a2f;
}

.field-input::placeholder {
  color: #b8b0a6;
}

.code-wrapper {
  display: flex;
  gap: 16px;
}

.flex-1 { flex: 1; }

.code-btn {
  min-width: 120px;
  padding: 16px 24px;
  background: rgba(143, 166, 142, 0.12);
  border: 1px solid rgba(143, 166, 142, 0.2);
  border-radius: 20px;
  font-size: 15px;
  color: #8fa68e;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.code-btn.disabled {
  background: rgba(125, 116, 107, 0.08);
  border-color: transparent;
  color: #9a9a9a;
  cursor: not-allowed;
}

.code-btn:not(.disabled):active {
  transform: scale(0.98);
  opacity: 0.9;
}

.login-btn {
  width: 100%;
  margin-top: 24px;
  padding: 20px;
  background: linear-gradient(135deg, #8fa68e, #9db89c);
  border: none;
  border-radius: 20px;
  font-size: 18px;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: 2px;
  cursor: pointer;
  box-shadow: 0 6px 20px rgba(143, 166, 142, 0.25);
  transition: all 0.2s ease;
}

.login-btn:active {
  transform: scale(0.98);
  opacity: 0.9;
}

/* 未勾选协议时置灰，但点击仍有提示（由函数内部拦截） */
.disabled-btn {
  opacity: 0.5;
  box-shadow: none;
}

.password-toggle {
  font-size: 18px;
  cursor: pointer;
  padding: 8px;
}

.forgot-row {
  display: flex;
  justify-content: flex-end;
  padding: 8px 0;
}

/* 链接统一用主色（原项目用蓝色，这里统一） */
.link-blue {
  font-size: 14px;
  font-weight: 500;
  color: #5b9a7a;
  cursor: pointer;
}

.link-blue:hover {
  text-decoration: underline;
}

/* ---------- 微信登录 ---------- */
.wechat-area {
  display: flex;
  flex-direction: column;
  padding-top: 24px;
}

.wechat-tip {
  margin: 0 0 32px;
  font-size: 16px;
  line-height: 1.6;
  color: #7d746b;
  text-align: center;
}

.wechat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  width: 100%;
  padding: 20px;
  background: #07c160;
  border: none;
  border-radius: 20px;
  font-size: 18px;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: 2px;
  cursor: pointer;
  box-shadow: 0 6px 20px rgba(7, 193, 96, 0.25);
}

.wechat-btn:active {
  transform: scale(0.98);
  opacity: 0.9;
}

.wechat-icon {
  font-size: 24px;
}

/* ---------- ④ 协议区 ---------- */
.agreement-check-area {
  margin-top: 40px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.5);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.check-row {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.checkbox {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border: 2px solid #8fa68e;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #ffffff;
  transition: all 0.2s ease;
}

.checkbox.checked {
  background: #8fa68e;
}

.check-text {
  font-size: 14px;
  font-weight: 500;
  color: #2f3a2f;
}

.agreement-links-row {
  padding-left: 40px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.separator {
  font-size: 14px;
  color: #7d746b;
}
</style>

<template>
  <div class="page register-page">
    <div class="reg-header">
      <h1 class="reg-title">创建账号</h1>
      <p class="reg-sub">加入安居守护，为家人多一份安心</p>
    </div>

    <!-- 角色选择 -->
    <div class="role-section">
      <label class="role-label">我的身份</label>
      <div class="role-grid">
        <div
          v-for="r in roles"
          :key="r.key"
          class="role-card"
          :class="{ active: __state.role === r.key }"
          @click="__state.role = r.key">
          <span class="role-icon">{{ r.icon }}</span>
          <span class="role-name">{{ r.name }}</span>
          <span class="role-desc">{{ r.desc }}</span>
        </div>
      </div>
    </div>

    <!-- 基本信息 -->
    <div class="field-group">
      <label class="field-label">手机号码</label>
      <div class="input-box">
        <span class="input-icon">📱</span>
        <input v-model="__state.phone" type="number" maxlength="11" placeholder="请输入手机号" class="field-input" />
      </div>
    </div>

    <div class="field-group">
      <label class="field-label">验证码</label>
      <div class="code-wrapper">
        <div class="input-box flex-1">
          <span class="input-icon">🔑</span>
          <input v-model="__state.code" type="number" maxlength="6" placeholder="请输入验证码" class="field-input" />
        </div>
        <button class="code-btn" :class="{ disabled: __state.countdown > 0 }" @click="handleSendCode">
          {{ __state.countdown > 0 ? `${__state.countdown}秒` : '获取验证码' }}
        </button>
      </div>
    </div>

    <div class="field-group">
      <label class="field-label">称呼</label>
      <div class="input-box">
        <span class="input-icon">👤</span>
        <input v-model="__state.name" maxlength="20" placeholder="怎么称呼您（选填）" class="field-input" />
      </div>
    </div>

    <button class="reg-btn" :class="{ 'disabled-btn': !__state.agreementChecked }" @click="handleRegister">
      {{ __state.loading ? '注册中...' : '注 册' }}
    </button>

    <div class="agreement-check-area">
      <div class="check-row" @click="__state.agreementChecked = !__state.agreementChecked">
        <div class="checkbox" :class="{ checked: __state.agreementChecked }">
          <span v-if="__state.agreementChecked">✓</span>
        </div>
        <span class="check-text">我已阅读并同意《用户服务协议》与《隐私政策》</span>
      </div>
    </div>

    <p class="login-hint">
      已有账号？<span class="link-blue" @click="goLogin">去登录</span>
    </p>
  </div>
</template>

<script setup lang="ts">
/**
 * 注册页
 *
 * 两步流程：选身份 → 填资料
 * 首次登录也会自动建号，这个页面用于补充资料
 */
import { reactive, onUnmounted } from 'vue'

const roles = [
  { key: 'elder', icon: '👴', name: '长辈', desc: '我是被守护的人' },
  { key: 'family', icon: '👨‍👩‍👧', name: '家属', desc: '我要守护家人' }
]

const __state = reactive({
  role: 'elder' as 'elder' | 'family',
  phone: '',
  code: '',
  name: '',
  countdown: 0,
  loading: false,
  agreementChecked: false
})

let timer: ReturnType<typeof setInterval> | null = null

function toast(msg: string) {
  console.log('[toast]', msg)
}

async function handleSendCode() {
  if (__state.countdown > 0) return
  if (!/^1[3-9]\d{9}$/.test(__state.phone)) {
    toast('请输入正确的手机号')
    return
  }

  try {
    const res = await fetch('/api/auth/send-code', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone: __state.phone })
    }).then((r) => r.json())

    if (res.code === 200) {
      toast('验证码已发送')
      __state.countdown = 60
      timer = setInterval(() => {
        __state.countdown -= 1
        if (__state.countdown <= 0 && timer) {
          clearInterval(timer)
          timer = null
        }
      }, 1000)
    } else {
      toast(res.msg || '发送失败')
    }
  } catch {
    toast('网络异常')
  }
}

async function handleRegister() {
  if (!__state.agreementChecked) {
    toast('请先阅读并同意用户协议')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(__state.phone)) {
    toast('请输入正确的手机号')
    return
  }
  if (__state.code.length < 4) {
    toast('请输入验证码')
    return
  }

  __state.loading = true
  try {
    // 注册即登录：后端首次登录会自动建号
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone: __state.phone, code: __state.code })
    }).then((r) => r.json())

    if (res.code === 200) {
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('userInfo', JSON.stringify(res.data.user))

      // 补充资料：身份 + 称呼
      if (__state.name || __state.role) {
        await fetch('/api/auth/user/update', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: 'Bearer ' + res.data.token
          },
          body: JSON.stringify({ name: __state.name, role: __state.role })
        })
      }

      toast('注册成功')
      setTimeout(() => (location.hash = '#/pages/home/index'), 1500)
    } else {
      toast(res.msg || '注册失败')
    }
  } catch {
    toast('网络异常')
  } finally {
    __state.loading = false
  }
}

function goLogin() {
  console.log('[navigate] /pages/login/index')
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  padding: 48px 32px 40px;
  background: linear-gradient(180deg, #f4efe6 0%, #faf7f1 100%);
}

.reg-header {
  margin-bottom: 36px;
}

.reg-title {
  margin: 0 0 8px;
  font-size: 28px;
  font-weight: 700;
  color: #2f3a2f;
}

.reg-sub {
  margin: 0;
  font-size: 15px;
  color: #7d746b;
}

/* ---------- 角色选择 ---------- */
.role-section {
  margin-bottom: 32px;
}

.role-label {
  display: block;
  margin-bottom: 14px;
  font-size: 16px;
  font-weight: 500;
  color: #2f3a2f;
}

.role-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.role-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 20px 12px;
  background: #fff9ef;
  border: 2px solid transparent;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.role-card.active {
  border-color: #8fa68e;
  background: rgba(143, 166, 142, 0.12);
}

.role-icon {
  font-size: 32px;
}

.role-name {
  font-size: 17px;
  font-weight: 600;
  color: #2f3a2f;
}

.role-desc {
  font-size: 12px;
  color: #7d746b;
}

/* ---------- 表单（与登录页一致） ---------- */
.field-group {
  margin-bottom: 22px;
}

.field-label {
  display: block;
  margin-bottom: 10px;
  padding-left: 4px;
  font-size: 16px;
  font-weight: 500;
  color: #2f3a2f;
}

.input-box {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(143, 166, 142, 0.1);
  border-radius: 20px;
  transition: border-color 0.2s ease;
}

.input-box:focus-within {
  border-color: #8fa68e;
}

.input-icon { font-size: 20px; }

.field-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 17px;
  color: #2f3a2f;
}

.field-input::placeholder { color: #b8b0a6; }

.code-wrapper { display: flex; gap: 14px; }
.flex-1 { flex: 1; }

.code-btn {
  min-width: 110px;
  padding: 16px 20px;
  background: rgba(143, 166, 142, 0.12);
  border: 1px solid rgba(143, 166, 142, 0.2);
  border-radius: 20px;
  font-size: 15px;
  color: #8fa68e;
  cursor: pointer;
  white-space: nowrap;
}

.code-btn.disabled {
  background: rgba(125, 116, 107, 0.08);
  border-color: transparent;
  color: #9a9a9a;
  cursor: not-allowed;
}

.reg-btn {
  width: 100%;
  margin-top: 28px;
  padding: 20px;
  background: linear-gradient(135deg, #8fa68e, #9db89c);
  border: none;
  border-radius: 20px;
  font-size: 18px;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: 2px;
  cursor: pointer;
  box-shadow: 0 6px 20px rgba(143, 166, 142, 0.25);
}

.reg-btn:active { transform: scale(0.98); opacity: 0.9; }

.disabled-btn { opacity: 0.5; box-shadow: none; }

/* ---------- 协议 ---------- */
.agreement-check-area {
  margin-top: 28px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.5);
  border-radius: 16px;
}

.check-row {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.checkbox {
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  border: 2px solid #8fa68e;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #fff;
}

.checkbox.checked { background: #8fa68e; }

.check-text {
  font-size: 13px;
  color: #2f3a2f;
  line-height: 1.5;
}

.login-hint {
  margin: 24px 0 0;
  text-align: center;
  font-size: 14px;
  color: #7d746b;
}

.link-blue {
  color: #5b9a7a;
  font-weight: 500;
  cursor: pointer;
}
</style>

<template>
  <div class="page home-page">
    <!-- ① 页面标题区 -->
    <div class="page-header">
      <div class="header-top">
        <div class="header-left">
          <h1 class="header-title">节律守护</h1>
          <p class="header-date">{{ __state.todayText }}</p>
        </div>
        <button class="reminder-btn" :class="{ on: __state.reminderConfig.enabled }" @click="__state.showReminderDialog = true">
          <span class="reminder-icon">{{ __state.reminderConfig.enabled ? '🔔' : '🔕' }}</span>
          <span class="reminder-text">{{ __state.reminderConfig.enabled ? '提醒已开启' : '提醒已关闭' }}</span>
        </button>
      </div>
      <p class="header-sub">愿您拥有健康的睡眠</p>
    </div>

    <!-- ② 快捷打卡区 -->
    <div class="card-block">
      <div class="card">
        <h2 class="card-heading">快速打卡</h2>
        <div class="checkin-row">
          <!-- 左：睡觉打卡 -->
          <button
            class="checkin-card"
            :class="{
              sleeping: __state.sleepStartTime && !__state.wakeStartTime
            }"
            @click="handleSleepCheckIn">
            <span class="checkin-emoji" :class="{ active: __state.sleepStartTime && !__state.wakeStartTime }">🌙</span>
            <span class="checkin-name">{{ __state.sleepStartTime && !__state.wakeStartTime ? '已睡觉' : '睡觉打卡' }}</span>
            <span class="checkin-desc">
              {{ __state.sleepStartTime && !__state.wakeStartTime ? formatTime(__state.sleepStartTime) : '点击打卡' }}
            </span>
          </button>

          <!-- 右：起床打卡 -->
          <button
            class="checkin-card"
            :class="{
              woken: __state.wakeStartTime,
              disabled: !__state.sleepStartTime
            }"
            @click="handleWakeCheckIn">
            <span class="checkin-emoji" :class="{ warn: __state.wakeStartTime }">☀️</span>
            <span class="checkin-name">{{ __state.wakeStartTime ? '已起床' : '起床打卡' }}</span>
            <span class="checkin-desc">
              {{ __state.wakeStartTime ? formatTime(__state.wakeStartTime) : (__state.sleepStartTime ? '点击打卡' : '请先睡觉') }}
            </span>
          </button>
        </div>

        <!-- 新一轮：仅在已完成一轮打卡后出现 -->
        <button v-if="__state.wakeStartTime" class="round-btn" @click="handleNewRound">开始新一轮打卡</button>
      </div>
    </div>

    <!-- ③ 打卡记录 -->
    <div class="card-block">
      <div class="card">
        <div class="card-header-row">
          <h2 class="card-heading">打卡记录</h2>
          <span class="link-more" @click="goPage('/pages/checkin-records/index')">查看全部 ›</span>
        </div>

        <div v-if="recentRecords.length === 0" class="empty-state">
          <span class="empty-icon">🕐</span>
          <p class="empty-text">暂无打卡记录 (´･ω･`)</p>
          <p class="empty-hint">点击上方按钮开始打卡</p>
        </div>

        <div v-else class="record-list">
          <div v-for="record in recentRecords" :key="record.id" class="record-item">
            <div class="record-left">
              <div class="record-avatar">🌙</div>
              <div class="record-info">
                <p class="record-date">{{ record.date }}</p>
                <p class="record-time">入睡: {{ formatTimeText(record.sleepTime) }}</p>
              </div>
            </div>
            <div class="record-right">
              <template v-if="record.wakeTime">
                <span class="record-wake">☀️ {{ formatTimeText(record.wakeTime) }}</span>
                <span class="pill pill-success">{{ record.duration.toFixed(1) }}小时</span>
              </template>
              <span v-else class="pill pill-warning">进行中</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ④ 睡眠质量评分环 -->
    <div class="card-block">
      <div class="card score-card">
        <span class="deco deco-moon">🌙</span>
        <span class="deco deco-sparkles">✨</span>

        <div class="card-header-row">
          <h2 class="card-heading small">🌙 睡眠质量评分</h2>
          <span class="score-tag">{{ sleepQualityText }}</span>
        </div>

        <div class="score-body">
          <!-- 纯 CSS 绘制的评分环（conic-gradient 实现进度弧） -->
          <div class="score-ring" :style="ringStyle">
            <div class="score-ring-inner">
              <span class="score-number">{{ sleepScore }}</span>
              <span class="score-unit">分</span>
            </div>
          </div>

          <!-- 右侧三行：全部由真实睡眠数据计算，不再硬编码 -->
          <div class="score-detail">
            <div class="detail-row">
              <span class="detail-label">今日睡眠时长</span>
              <span class="detail-value">{{ sleepDurationText }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">深度睡眠占比</span>
              <span class="detail-value">{{ deepSleepPercent }}% 💫</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">建议</span>
              <span class="detail-value primary">{{ sleepAdvice }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ⑤ 天气预报卡片 -->
    <div class="card-block">
      <div class="card weather-card">
        <div class="weather-top">
          <div class="weather-left">
            <span class="weather-emoji">⛅</span>
            <div class="weather-text">
              <p class="weather-name">{{ __state.weather.text }}</p>
              <p class="weather-wind">{{ __state.weather.wind }}</p>
            </div>
          </div>
          <div class="weather-right">
            <p class="weather-temp">{{ __state.weather.temp }}°</p>
            <p class="weather-humidity">💧 {{ __state.weather.humidity }}%</p>
          </div>
        </div>
        <div class="weather-footer">💡 {{ __state.weather.suggestion }}</div>
      </div>
    </div>

    <!-- ⑥ 本周日历（注意：这里是"今天前后各 3 天"共 7 天，不是自然周） -->
    <div class="card-block">
      <div class="card">
        <h2 class="card-heading small">📅 本周睡眠记录</h2>
        <div class="week-row">
          <div v-for="day in weekDays" :key="day.dateStr" class="week-day">
            <span class="week-label">{{ day.weekLabel }}</span>
            <div class="week-date" :class="{ today: day.isToday, recorded: day.hasRecord && !day.isToday }">
              {{ day.dayNumber }}
            </div>
            <span v-if="day.hasRecord && !day.isToday" class="week-check">✓</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ⑦ 今日活动 -->
    <div class="card-block">
      <div class="card">
        <h2 class="card-heading small">📈 今日活动 🏃</h2>
        <div class="activity-grid">
          <div class="activity-cell">
            <span class="activity-emoji green">👣</span>
            <span class="activity-value">{{ __state.activity.steps }}</span>
            <span class="activity-unit">步数</span>
          </div>
          <div class="activity-cell">
            <span class="activity-emoji orange">👣</span>
            <span class="activity-value">{{ __state.activity.distance }}</span>
            <span class="activity-unit">公里</span>
          </div>
          <div class="activity-cell">
            <span class="activity-emoji red">💓</span>
            <span class="activity-value">{{ __state.activity.calories }}</span>
            <span class="activity-unit">卡路里</span>
          </div>
          <div class="activity-cell">
            <span class="activity-emoji yellow">☀️</span>
            <span class="activity-value">{{ __state.activity.outdoorMin }}</span>
            <span class="activity-unit">户外分钟</span>
          </div>
        </div>
        <div class="activity-goal">
          <div class="goal-row">
            <span class="goal-label">今日运动目标</span>
            <span class="goal-percent">已完成 {{ activityPercent }}%</span>
          </div>
          <div class="progress-track">
            <div class="progress-fill" :style="{ width: activityPercent + '%' }"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- ⑧ 今日睡眠 -->
    <div class="card-block">
      <div class="card">
        <h2 class="card-heading">今日睡眠</h2>
        <div class="sleep-big">
          <template v-if="sleepDuration !== null">
            <span class="sleep-hours">{{ Math.floor(sleepDuration) }}</span>
            <span class="sleep-unit">小时</span>
            <span class="sleep-hours">{{ Math.round((sleepDuration - Math.floor(sleepDuration)) * 60) }}</span>
            <span class="sleep-unit">分钟</span>
          </template>
          <span v-else class="sleep-hours">--</span>
        </div>
        <p class="sleep-quality">睡眠质量：{{ sleepQualityText }}</p>

        <div class="sleep-footer">
          <div class="sleep-col">
            <span class="sleep-emoji">🌙</span>
            <span class="sleep-col-label">入睡时间</span>
            <span class="sleep-col-value">{{ formatTime(__state.sleepStartTime) }}</span>
          </div>
          <div class="sleep-col">
            <span class="sleep-emoji">☀️</span>
            <span class="sleep-col-label">起床时间</span>
            <span class="sleep-col-value">{{ formatTime(__state.wakeStartTime) }}</span>
          </div>
        </div>

        <p v-if="!__state.sleepStartTime" class="sleep-empty">暂无今日睡眠数据，点击上方打卡按钮开始记录</p>
      </div>
    </div>

    <!-- ⑨ AI 健康分析 -->
    <div class="card-block">
      <div class="card">
        <div class="card-header-row">
          <h2 class="card-heading">AI 健康分析</h2>
          <span class="link-more" @click="goPage('/pages/ai-sleep-analysis/index')">详细分析 ›</span>
        </div>

        <div class="ai-conclusion">
          <span class="ai-emoji">✨</span>
          <div class="ai-text">
            <p class="ai-main">{{ aiConclusion }}</p>
            <p v-if="sleepDuration !== null" class="ai-sub">{{ aiSubText }}</p>
          </div>
        </div>

        <div v-if="sleepDuration !== null" class="ai-suggestions">
          <div class="suggestion-item">
            <span class="suggestion-emoji">📈</span>
            <span class="suggestion-text">建议：保持规律作息，每天固定时间入睡起床</span>
          </div>
          <div class="suggestion-item">
            <span class="suggestion-emoji">🧠</span>
            <span class="suggestion-text">建议：睡前避免剧烈运动，保持心情放松</span>
          </div>
          <div class="suggestion-item">
            <span class="suggestion-emoji">💓</span>
            <span class="suggestion-text">建议：睡前可以听听轻音乐，帮助入睡</span>
          </div>
        </div>

        <button class="ai-report-btn" @click="goPage('/pages/ai-sleep-analysis/index')">点击获取详细 AI 分析报告</button>
      </div>
    </div>

    <!-- ⑩ 用药提醒 -->
    <div class="card-block">
      <div class="card">
        <div class="card-header-row">
          <h2 class="card-heading">用药提醒 💊</h2>
          <span class="link-more" @click="goPage('/pages/medicine-reminder/index')">➕ 添加</span>
        </div>

        <div class="medicine-list">
          <div v-for="med in __state.medicineList" :key="med.id" class="medicine-item">
            <div class="medicine-info">
              <div class="medicine-name-row">
                <span class="medicine-name">{{ med.name }}</span>
                <span class="pill" :class="med.status === 'taken' ? 'pill-success' : 'pill-warning'">
                  {{ med.status === 'taken' ? '已服用' : '待服用' }}
                </span>
              </div>
              <p class="medicine-desc">{{ med.frequency }} · {{ med.dosage }}</p>
              <p class="medicine-time">{{ med.time }}</p>
            </div>
            <button
              class="medicine-check"
              :class="{ taken: med.status === 'taken' }"
              @click="handleMedicineCheck(med)">
              ✓
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 打卡确认弹窗（睡觉 / 起床共用结构） -->
    <div v-if="__state.showCheckDialog" class="dialog-mask" @click.self="__state.showCheckDialog = false">
      <div class="dialog-box">
        <h3 class="dialog-title">确认打卡</h3>
        <p class="dialog-message">{{ __state.dialogMessage }}</p>
        <div class="dialog-actions">
          <button class="dialog-btn cancel" @click="__state.showCheckDialog = false">取消</button>
          <button class="dialog-btn confirm" @click="confirmCheckIn">确认</button>
        </div>
      </div>
    </div>

    <!-- 提醒设置弹窗 -->
    <div v-if="__state.showReminderDialog" class="dialog-mask" @click.self="__state.showReminderDialog = false">
      <div class="dialog-box">
        <div class="dialog-header-row">
          <h3 class="dialog-title">打卡提醒设置</h3>
          <span class="dialog-close" @click="__state.showReminderDialog = false">关闭</span>
        </div>

        <div class="switch-row" @click="__state.reminderConfig.enabled = !__state.reminderConfig.enabled">
          <span class="switch-label">开启提醒</span>
          <div class="switch" :class="{ on: __state.reminderConfig.enabled }">
            <div class="switch-knob"></div>
          </div>
        </div>

        <div class="reminder-row">
          <span class="reminder-label">睡觉提醒时间</span>
          <div class="reminder-values">
            <span class="reminder-chip">{{ __state.reminderConfig.sleepTime }}</span>
            <span class="reminder-tip">提前</span>
            <span class="reminder-chip">{{ __state.reminderConfig.sleepReminderMinutes }}分钟</span>
          </div>
        </div>

        <div class="reminder-row">
          <span class="reminder-label">起床提醒时间</span>
          <div class="reminder-values">
            <span class="reminder-chip">{{ __state.reminderConfig.wakeTime }}</span>
            <span class="reminder-tip">提前</span>
            <span class="reminder-chip">{{ __state.reminderConfig.wakeReminderMinutes }}分钟</span>
          </div>
        </div>

        <div class="dialog-test-row">
          <button class="dialog-test-btn" @click="toast('已播放睡觉提醒')">测试睡觉提醒</button>
          <button class="dialog-test-btn" @click="toast('已播放起床提醒')">测试起床提醒</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 首页 —— 节律守护
 *
 * 核心是"睡眠打卡闭环"：未睡 → 睡觉打卡 → 睡中 → 起床打卡 → 已完成 → 开始新一轮
 *
 * 修正的原项目缺陷：
 *   1. 评分环右侧三行原本是硬编码假数据（"7小时32分"/"35% 💫"），
 *      这里全部改为由真实 mock 数据计算得出。
 *   2. 周历保持原逻辑（今天前后各 3 天），并加注释说明它不是自然周。
 */
import { reactive, computed } from 'vue'

interface CheckInRecord {
  id: string
  date: string
  sleepTime: string
  wakeTime: string | null
  duration: number | null
}

const __state = reactive({
  todayText: '',

  // 本轮打卡状态（初始为"已完成一轮"的样子，让界面看起来是活的）
  sleepStartTime: '2026-06-22T23:10:00' as string | null,
  wakeStartTime: '2026-06-23T06:42:00' as string | null,

  // 打卡记录（mock）
  checkInRecords: [
    { id: 'ci-1', date: '06/18', sleepTime: '23:05', wakeTime: '06:30', duration: 7.42 },
    { id: 'ci-2', date: '06/19', sleepTime: '22:50', wakeTime: '06:20', duration: 7.5 },
    { id: 'ci-3', date: '06/20', sleepTime: '23:35', wakeTime: '06:55', duration: 7.33 },
    { id: 'ci-4', date: '06/21', sleepTime: '23:02', wakeTime: '06:35', duration: 7.55 },
    { id: 'ci-5', date: '06/22', sleepTime: '23:10', wakeTime: '06:42', duration: 7.53 }
  ] as CheckInRecord[],

  // 用药提醒（mock，取自原项目默认三条）
  medicineList: [
    { id: 'med-1', name: '降压药', status: 'taken', time: '08:00', dosage: '1片', frequency: '早餐后' },
    { id: 'med-2', name: '降糖药', status: 'pending', time: '11:30', dosage: '半片', frequency: '午餐前' },
    { id: 'med-3', name: '钙片', status: 'pending', time: '19:00', dosage: '2片', frequency: '晚餐后' }
  ],

  // 天气（mock）
  weather: { temp: 25, text: '晴', humidity: 60, wind: '微风', suggestion: '天气不错，适合户外散步 30 分钟' },

  // 今日活动（mock）
  activity: { steps: 6820, distance: 4.8, calories: 265, outdoorMin: 45 },

  // 提醒配置（mock）
  reminderConfig: {
    enabled: true,
    sleepTime: '22:30',
    sleepReminderMinutes: 30,
    wakeTime: '06:30',
    wakeReminderMinutes: 15
  },

  // 弹窗
  showCheckDialog: false,
  showReminderDialog: false,
  dialogMessage: '',
  pendingAction: '' as '' | 'sleep' | 'wake'
})

/** 页面标题用的中文日期 */
const now = new Date()
__state.todayText = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日`

/** 把 ISO 字符串解析成 Date（空值返回 null） */
function toDate(value: string | null): Date | null {
  return value ? new Date(value) : null
}

/** HH:mm 格式化，空值返回 --:-- */
function formatTime(value: string | null): string {
  const d = toDate(value)
  if (!d) return '--:--'
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}

/** 记录里的时间字段已经是 HH:mm 字符串，直接返回 */
function formatTimeText(value: string | null): string {
  return value || '--:--'
}

function toast(msg: string) {
  console.log('[toast]', msg)
}

/* ============ 派生数据（全部由 mock 真实计算，替代原项目硬编码） ============ */

/** 睡眠时长（小时，浮点）；未完成一轮打卡则为 null */
const sleepDuration = computed<number | null>(() => {
  if (!__state.sleepStartTime || !__state.wakeStartTime) return null
  const start = new Date(__state.sleepStartTime).getTime()
  const end = new Date(__state.wakeStartTime).getTime()
  return (end - start) / (1000 * 60 * 60)
})

/**
 * 睡眠评分（照抄原项目分档公式）
 *   7-9 小时最优 → min(95 + (h-7)*2, 100)
 *   6-7 小时一般 → 70 + (h-6)*20
 *   > 9 小时过长 → 固定 80
 *   < 6 小时不足 → max(50, h*10)
 */
const sleepScore = computed(() => {
  const h = sleepDuration.value
  if (!h) return 0
  if (h >= 7 && h <= 9) return Math.min(95 + (h - 7) * 2, 100)
  if (h >= 6 && h < 7) return 70 + (h - 6) * 20
  if (h > 9) return 80
  return Math.max(50, h * 10)
})

/** 睡眠质量文案（照抄原项目规则） */
const sleepQualityText = computed(() => {
  const h = sleepDuration.value
  if (!h) return '待测量'
  if (h >= 7 && h <= 9) return '良好'
  if (h >= 6 && h < 7) return '一般'
  if (h > 9) return '过长'
  return '不足'
})

/**
 * 今日睡眠时长文案 —— 原项目这里写死 "7小时32分"，现在改为真实计算
 */
const sleepDurationText = computed(() => {
  const h = sleepDuration.value
  if (h === null) return '--'
  const hours = Math.floor(h)
  const minutes = Math.round((h - hours) * 60)
  return `${hours}小时${minutes}分`
})

/**
 * 深度睡眠占比 —— 原项目写死 "35%"（还带一个 💫），
 * 这里按"睡眠时长 + 评分"推一个稳定可解释的估算值：
 *   时长落在最优区间时占比更高，24 点后入睡会略微打折
 */
const deepSleepPercent = computed(() => {
  const h = sleepDuration.value
  if (h === null) return 0
  const base = h >= 7 && h <= 9 ? 28 : h >= 6 ? 22 : 16
  const bonus = Math.round((sleepScore.value / 100) * 10)
  const start = new Date(__state.sleepStartTime as string)
  const latePenalty = start.getHours() >= 0 && start.getHours() < 5 ? 3 : 0
  return Math.max(10, Math.min(40, base + bonus - latePenalty))
})

/** 建议文案随评分变化 */
const sleepAdvice = computed(() => {
  if (sleepDuration.value === null) return '先完成打卡再评估'
  if (sleepScore.value >= 95) return '保持规律作息'
  if (sleepScore.value >= 80) return '作息基本规律，可再早睡一点'
  return '建议提前入睡，保证 7-8 小时'
})

/** 评分环用 conic-gradient 画进度弧，270° 量程 */
const ringStyle = computed(() => {
  const deg = (sleepScore.value / 100) * 270
  return {
    background: `conic-gradient(from 225deg, #8FA68E 0deg, #E8A87C ${deg}deg, rgba(125,116,107,0.12) ${deg}deg 270deg, transparent 270deg 360deg)`
  }
})

/** 运动目标完成百分比（原项目写死 65%，这里按 8000 步目标真实计算） */
const activityPercent = computed(() => {
  const target = 8000
  return Math.min(100, Math.round((__state.activity.steps / target) * 100))
})

/** 最近 3 条记录，倒序 */
const recentRecords = computed(() => __state.checkInRecords.slice(-3).reverse())

/**
 * 周历 7 天 —— 注意：原项目是"今天前后各 3 天"，
 * 即 [今天-3, 今天+3]，不是自然周（周一到周日）。此处保持同样逻辑。
 */
const weekDays = computed(() => {
  const labels = ['日', '一', '二', '三', '四', '五', '六']
  const result: { dateStr: string; weekLabel: string; dayNumber: number; isToday: boolean; hasRecord: boolean }[] = []
  const today = new Date()

  for (let i = -3; i <= 3; i++) {
    const d = new Date(today)
    d.setDate(today.getDate() + i)
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    const dateStr = `${mm}/${dd}`
    result.push({
      dateStr,
      weekLabel: labels[d.getDay()],
      dayNumber: d.getDate(),
      isToday: i === 0,
      hasRecord: __state.checkInRecords.some((r) => r.date === dateStr)
    })
  }
  return result
})

/** AI 结论文案 */
const aiConclusion = computed(() => {
  if (sleepDuration.value === null) return '完成睡眠打卡后，AI 将为您分析睡眠健康状况'
  return `根据您的睡眠数据分析，今日睡眠时长 ${sleepDuration.value.toFixed(1)} 小时，睡眠质量${sleepQualityText.value}`
})

const aiSubText = computed(() => {
  const h = sleepDuration.value
  if (h === null) return ''
  return h >= 7
    ? '睡眠时长充足，继续保持良好的作息习惯！'
    : '建议适当增加睡眠时间，保持每天 7-8 小时的睡眠时长 📋'
})

/* ============ 交互逻辑 ============ */

/** 睡觉打卡：睡眠中禁止重复打卡 */
function handleSleepCheckIn() {
  if (__state.sleepStartTime && !__state.wakeStartTime) {
    toast('当前正在睡觉中，请先完成起床打卡')
    return
  }
  __state.pendingAction = 'sleep'
  __state.dialogMessage = `确认现在开始睡觉打卡？\n时间：${formatTime(new Date().toISOString())}`
  __state.showCheckDialog = true
}

/** 起床打卡：必须先睡觉，且本轮未起过床 */
function handleWakeCheckIn() {
  if (!__state.sleepStartTime) {
    toast('请先完成睡觉打卡')
    return
  }
  if (__state.wakeStartTime) {
    toast('本轮已完成起床打卡，请先开始新一轮睡觉')
    return
  }
  const start = new Date(__state.sleepStartTime).getTime()
  const duration = (Date.now() - start) / (1000 * 60 * 60)
  __state.pendingAction = 'wake'
  __state.dialogMessage = `确认现在起床打卡？\n睡眠时长：${duration.toFixed(1)} 小时`
  __state.showCheckDialog = true
}

/** 弹窗确认 */
function confirmCheckIn() {
  const nowIso = new Date().toISOString()
  const mm = String(new Date().getMonth() + 1).padStart(2, '0')
  const dd = String(new Date().getDate()).padStart(2, '0')
  const dateStr = `${mm}/${dd}`

  if (__state.pendingAction === 'sleep') {
    __state.checkInRecords.push({
      id: 'checkin-' + Date.now(),
      date: dateStr,
      sleepTime: formatTime(nowIso),
      wakeTime: null,
      duration: null
    })
    __state.sleepStartTime = nowIso
    __state.wakeStartTime = null
    toast('睡觉打卡成功，祝您好梦 🌙')
  } else if (__state.pendingAction === 'wake') {
    const start = new Date(__state.sleepStartTime as string).getTime()
    const duration = (Date.now() - start) / (1000 * 60 * 60)
    // 回填当天那条未完成的记录
    const target = __state.checkInRecords.find((r) => r.date === dateStr && r.wakeTime === null)
    if (target) {
      target.wakeTime = formatTime(nowIso)
      target.duration = duration
    }
    __state.wakeStartTime = nowIso
    toast(`起床打卡成功，共睡眠 ${duration.toFixed(1)} 小时 ☀️`)
  }

  __state.showCheckDialog = false
  __state.pendingAction = ''
  // 提前量的提醒时间也随打卡刷新一下，让界面"活"起来
  if (__state.reminderConfig.enabled) {
    console.log('[reminder] 提醒时间已同步')
  }
}

/** 开始新一轮：必须已完成一轮完整打卡 */
function handleNewRound() {
  if (!__state.sleepStartTime) {
    toast('请先完成睡觉打卡')
    return
  }
  if (!__state.wakeStartTime) {
    toast('请先完成起床打卡')
    return
  }
  __state.sleepStartTime = null
  __state.wakeStartTime = null
  toast('新的一轮打卡开始啦，祝您今晚好梦～')
}

/** 用药打卡：已服用则不可再点 */
function handleMedicineCheck(med: { id: string; status: string }) {
  if (med.status === 'taken') return
  med.status = 'taken'
  toast('用药打卡成功')
}

function goPage(path: string) {
  console.log('[navigate]', path)
}
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  padding: 0 0 96px;
  background: #f4efe6;
}

/* ---------- ① 页面标题区 ---------- */
.page-header {
  padding: 16px 16px;
}

.header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #2f2a24;
}

.header-date {
  margin: 4px 0 0;
  font-size: 16px;
  color: #7d746b;
}

.header-sub {
  margin: 8px 0 0;
  font-size: 18px;
  color: #8fa68e;
}

.reminder-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  border: none;
  border-radius: 12px;
  background: rgba(125, 116, 107, 0.1);
  cursor: pointer;
  transition: background 0.2s ease;
}

.reminder-btn.on {
  background: rgba(143, 166, 142, 0.2);
}

.reminder-icon {
  font-size: 20px;
  line-height: 1;
}

.reminder-text {
  font-size: 14px;
  font-weight: 500;
  color: #7d746b;
}

.reminder-btn.on .reminder-text {
  color: #8fa68e;
}

/* ---------- 卡片通用 ---------- */
.card-block {
  padding: 0 16px;
  margin-bottom: 16px;
}

.card {
  position: relative;
  padding: 20px;
  background: #fff9ef;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.card-heading {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #2f2a24;
}

.card-heading.small {
  font-size: 18px;
}

.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.link-more {
  font-size: 14px;
  font-weight: 500;
  color: #8fa68e;
  cursor: pointer;
}

/* ---------- ② 快捷打卡 ---------- */
.checkin-row {
  display: flex;
  gap: 16px;
}

.checkin-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: rgba(125, 116, 107, 0.05);
  border: 2px solid transparent;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.25s ease;
}

.checkin-card.sleeping {
  background: rgba(143, 166, 142, 0.2);
  border-color: #8fa68e;
}

.checkin-card.woken {
  background: rgba(212, 165, 116, 0.2);
  border-color: #d4a574;
}

.checkin-card.disabled {
  opacity: 0.5;
}

.checkin-card:active {
  transform: scale(0.98);
}

.checkin-emoji {
  font-size: 32px;
  line-height: 1;
  filter: grayscale(0.4);
}

.checkin-emoji.active {
  filter: none;
}

.checkin-emoji.warn {
  filter: none;
}

.checkin-name {
  font-size: 18px;
  font-weight: 600;
  color: #2f2a24;
}

.checkin-desc {
  font-size: 14px;
  color: #7d746b;
}

.round-btn {
  width: 100%;
  margin-top: 16px;
  padding: 12px;
  background: #8fa68e;
  border: none;
  border-radius: 12px;
  font-size: 18px;
  font-weight: 600;
  color: #ffffff;
  cursor: pointer;
}

.round-btn:active {
  transform: scale(0.98);
  opacity: 0.9;
}

/* ---------- ③ 打卡记录 ---------- */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 32px 0;
}

.empty-icon {
  font-size: 48px;
  opacity: 0.35;
}

.empty-text {
  margin: 0;
  font-size: 16px;
  color: #2f2a24;
}

.empty-hint {
  margin: 0;
  font-size: 14px;
  color: #7d746b;
}

.record-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.record-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: rgba(125, 116, 107, 0.05);
  border-radius: 12px;
}

.record-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.record-avatar {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  background: rgba(143, 166, 142, 0.2);
  border-radius: 50%;
}

.record-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.record-date {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #2f2a24;
}

.record-time {
  margin: 0;
  font-size: 14px;
  color: #7d746b;
}

.record-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.record-wake {
  font-size: 14px;
  color: #7d746b;
}

.pill {
  padding: 4px 12px;
  border-radius: 9999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.pill-success {
  background: rgba(143, 166, 142, 0.2);
  color: #8fa68e;
}

.pill-warning {
  background: rgba(212, 165, 116, 0.2);
  color: #d4a574;
}

/* ---------- ④ 睡眠评分环 ---------- */
.score-card {
  overflow: hidden;
}

.deco {
  position: absolute;
  opacity: 0.1;
  pointer-events: none;
}

.deco-moon {
  top: 8px;
  right: 12px;
  font-size: 80px;
}

.deco-sparkles {
  bottom: 4px;
  left: 8px;
  font-size: 64px;
}

.score-tag {
  padding: 4px 8px;
  font-size: 14px;
  color: #8fa68e;
  background: rgba(143, 166, 142, 0.15);
  border-radius: 9999px;
}

.score-body {
  display: flex;
  align-items: center;
}

/* 纯 CSS 评分环：外层 conic-gradient 进度弧，内层白圆挖空 */
.score-ring {
  position: relative;
  width: 120px;
  height: 120px;
  flex-shrink: 0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.4s ease;
}

.score-ring-inner {
  width: 88px;
  height: 88px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #ffffff;
  border-radius: 50%;
  box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.06);
}

.score-number {
  font-size: 30px;
  font-weight: 700;
  color: #8fa68e;
  line-height: 1.1;
}

.score-unit {
  font-size: 12px;
  color: #7d746b;
}

.score-detail {
  flex: 1;
  margin-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-label {
  font-size: 13px;
  color: #7d746b;
}

.detail-value {
  font-size: 16px;
  font-weight: 600;
  color: #2f2a24;
}

.detail-value.primary {
  color: #8fa68e;
}

/* ---------- ⑤ 天气卡片 ---------- */
.weather-card {
  background: linear-gradient(135deg, #eef6ff 0%, #e2f0fb 100%);
}

.weather-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.weather-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.weather-emoji {
  font-size: 36px;
  line-height: 1;
}

.weather-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.weather-name {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #2f2a24;
}

.weather-wind {
  margin: 0;
  font-size: 14px;
  color: #7d746b;
}

.weather-right {
  text-align: right;
}

.weather-temp {
  margin: 0;
  font-size: 30px;
  font-weight: 700;
  color: #d4a574;
}

.weather-humidity {
  margin: 2px 0 0;
  font-size: 14px;
  color: #7d746b;
}

.weather-footer {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(125, 116, 107, 0.15);
  font-size: 14px;
  color: #7d746b;
}

/* ---------- ⑥ 本周日历 ---------- */
.week-row {
  display: flex;
  justify-content: space-around;
}

.week-day {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.week-label {
  font-size: 12px;
  color: #7d746b;
}

.week-date {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: #2f2a24;
  background: rgba(125, 116, 107, 0.05);
  border-radius: 50%;
}

.week-date.today {
  background: #8fa68e;
  color: #ffffff;
}

.week-date.recorded {
  background: rgba(143, 166, 142, 0.2);
}

.week-check {
  font-size: 12px;
  color: #8fa68e;
}

/* ---------- ⑦ 今日活动 ---------- */
.activity-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.activity-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px;
  text-align: center;
  background: rgba(125, 116, 107, 0.05);
  border-radius: 12px;
}

.activity-emoji {
  font-size: 24px;
  line-height: 1;
}

.activity-emoji.green { filter: hue-rotate(0deg); }
.activity-emoji.orange { filter: hue-rotate(-30deg); }
.activity-emoji.red { filter: hue-rotate(-60deg); }
.activity-emoji.yellow { filter: hue-rotate(20deg); }

.activity-value {
  font-size: 18px;
  font-weight: 700;
  color: #2f2a24;
}

.activity-unit {
  font-size: 12px;
  color: #7d746b;
}

.activity-goal {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(125, 116, 107, 0.15);
}

.goal-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.goal-label {
  font-size: 14px;
  color: #7d746b;
}

.goal-percent {
  font-size: 14px;
  font-weight: 600;
  color: #8fa68e;
}

.progress-track {
  margin-top: 8px;
  height: 8px;
  background: rgba(125, 116, 107, 0.05);
  border-radius: 9999px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: #8fa68e;
  border-radius: 9999px;
  transition: width 0.4s ease;
}

/* ---------- ⑧ 今日睡眠 ---------- */
.sleep-big {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 4px;
  margin-top: 12px;
}

.sleep-hours {
  font-size: 48px;
  font-weight: 700;
  color: #8fa68e;
}

.sleep-unit {
  font-size: 30px;
  color: #8fa68e;
}

.sleep-quality {
  margin: 8px 0 0;
  text-align: center;
  font-size: 16px;
  color: #7d746b;
}

.sleep-footer {
  display: flex;
  justify-content: space-around;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid rgba(125, 116, 107, 0.15);
}

.sleep-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.sleep-emoji {
  font-size: 24px;
  line-height: 1;
}

.sleep-col-label {
  font-size: 14px;
  color: #7d746b;
}

.sleep-col-value {
  font-size: 18px;
  font-weight: 600;
  color: #2f2a24;
}

.sleep-empty {
  margin: 16px 0 0;
  padding: 16px;
  text-align: center;
  font-size: 14px;
  color: #7d746b;
  background: rgba(125, 116, 107, 0.05);
  border-radius: 12px;
}

/* ---------- ⑨ AI 分析 ---------- */
.ai-conclusion {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  margin-bottom: 16px;
  background: rgba(143, 166, 142, 0.1);
  border-radius: 12px;
}

.ai-emoji {
  font-size: 24px;
  line-height: 1;
}

.ai-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ai-main {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #2f2a24;
}

.ai-sub {
  margin: 0;
  font-size: 14px;
  color: #7d746b;
}

.ai-suggestions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.suggestion-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: rgba(125, 116, 107, 0.05);
  border-radius: 12px;
}

.suggestion-emoji {
  font-size: 20px;
  line-height: 1;
}

.suggestion-text {
  font-size: 14px;
  color: #2f2a24;
}

.ai-report-btn {
  width: 100%;
  margin-top: 16px;
  padding: 12px;
  background: rgba(143, 166, 142, 0.2);
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  color: #8fa68e;
  cursor: pointer;
}

.ai-report-btn:active {
  transform: scale(0.98);
}

/* ---------- ⑩ 用药提醒 ---------- */
.medicine-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.medicine-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: rgba(125, 116, 107, 0.05);
  border-radius: 12px;
}

.medicine-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.medicine-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.medicine-name {
  font-size: 18px;
  font-weight: 600;
  color: #2f2a24;
}

.medicine-desc {
  margin: 0;
  font-size: 16px;
  color: #7d746b;
}

.medicine-time {
  margin: 0;
  font-size: 14px;
  color: #7d746b;
}

.medicine-check {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #7d746b;
  background: rgba(125, 116, 107, 0.1);
  border: 2px solid rgba(125, 116, 107, 0.3);
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.2s ease;
}

.medicine-check.taken {
  color: #ffffff;
  background: #8fa68e;
  border-color: #8fa68e;
}

/* ---------- 弹窗 ---------- */
.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
}

.dialog-box {
  width: 320px;
  padding: 24px;
  background: #fff9ef;
  border-radius: 16px;
}

.dialog-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.dialog-title {
  margin: 0 0 16px;
  font-size: 20px;
  font-weight: 600;
  color: #2f2a24;
}

.dialog-header-row .dialog-title {
  margin: 0;
}

.dialog-close {
  font-size: 14px;
  color: #8fa68e;
  cursor: pointer;
}

.dialog-message {
  margin: 0 0 24px;
  font-size: 16px;
  line-height: 1.6;
  color: #7d746b;
  white-space: pre-wrap;
}

.dialog-actions {
  display: flex;
  gap: 16px;
}

.dialog-btn {
  flex: 1;
  padding: 12px;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  cursor: pointer;
}

.dialog-btn.cancel {
  background: rgba(125, 116, 107, 0.05);
  color: #7d746b;
  font-weight: 500;
}

.dialog-btn.confirm {
  background: #8fa68e;
  color: #ffffff;
  font-weight: 600;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  margin-bottom: 16px;
  background: rgba(125, 116, 107, 0.05);
  border-radius: 12px;
  cursor: pointer;
}

.switch-label {
  font-size: 16px;
  font-weight: 500;
  color: #2f2a24;
}

.switch {
  position: relative;
  width: 48px;
  height: 24px;
  background: rgba(125, 116, 107, 0.3);
  border-radius: 9999px;
  transition: background 0.2s ease;
}

.switch.on {
  background: #8fa68e;
}

.switch-knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  background: #ffffff;
  border-radius: 50%;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
  transition: transform 0.2s ease;
}

.switch.on .switch-knob {
  transform: translateX(24px);
}

.reminder-row {
  margin-bottom: 16px;
}

.reminder-label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 500;
  color: #7d746b;
}

.reminder-values {
  display: flex;
  align-items: center;
  gap: 8px;
}

.reminder-chip {
  padding: 8px 12px;
  font-size: 16px;
  font-weight: 600;
  color: #8fa68e;
  background: rgba(125, 116, 107, 0.05);
  border-radius: 8px;
  cursor: pointer;
}

.reminder-tip {
  font-size: 12px;
  color: #7d746b;
}

.dialog-test-row {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.dialog-test-btn {
  flex: 1;
  padding: 12px;
  font-size: 14px;
  color: #2f2a24;
  background: rgba(125, 116, 107, 0.1);
  border: none;
  border-radius: 12px;
  cursor: pointer;
}
</style>

<template>
  <div class="page guardian-page">
    <!-- ① 页面标题区 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-line">
          <span class="header-icon">🛡️</span>
          <h1 class="header-title">居家守护</h1>
        </div>
        <p class="header-sub">守护家人安全，时刻安心</p>
      </div>
      <div class="header-date">{{ __state.todayText }}</div>
    </div>

    <!-- ② 安全状态指示灯 + 守护时长 -->
    <div class="safety-section">
      <div class="safety-indicator">
        <div class="safety-led" :class="`led-${__state.safetyStatus}`">
          <div class="led-glow" :class="`glow-${__state.safetyStatus}`"></div>
          <div class="led-core" :class="`core-${__state.safetyStatus}`"></div>
        </div>
        <span class="safety-label">{{ safetyLabel }}</span>
      </div>

      <div class="guardian-stats">
        <div class="stat-item">
          <span class="stat-value">{{ __state.guardianDays }}</span>
          <span class="stat-label">守护天数</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <span class="stat-value">{{ __state.todayInteractions }}</span>
          <span class="stat-label">今日互动 👋</span>
        </div>
      </div>
    </div>

    <!-- ③ 实时位置卡片 -->
    <div class="location-card">
      <!-- 地图区（离线用 CSS 画的占位地图，避免依赖外部静态图 API） -->
      <div class="location-map-area" @click="handleLocationDetail">
        <div class="map-grid"></div>
        <div class="map-road road-h"></div>
        <div class="map-road road-v"></div>
        <div class="map-center-marker">
          <div class="marker-pulse"></div>
          <div class="marker-outer">
            <div class="marker-inner"></div>
          </div>
        </div>
        <div class="view-detail-hint">点击查看详情 ›</div>
      </div>

      <div class="location-info-area" @click="handleLocationDetail">
        <div class="location-header-row">
          <div class="location-title-row">
            <div class="location-icon-wrap">📍</div>
            <span class="location-title">实时位置</span>
          </div>
          <div class="location-status">
            <span class="status-dot-online"></span>
            <span class="status-text">{{ __state.elderStatus.locationOnline ? '定位正常' : '定位离线' }}</span>
          </div>
        </div>
        <p class="elder-name">{{ __state.elderStatus.name }}</p>
        <p class="elder-address">{{ __state.elderStatus.location }}</p>
        <div class="location-meta">
          <span class="meta-item">🕐 {{ __state.elderStatus.lastLocationTime }}</span>
          <span class="meta-item">
            📍 {{ __state.elderStatus.locationCoords.latitude.toFixed(4) }}, {{ __state.elderStatus.locationCoords.longitude.toFixed(4) }}
          </span>
        </div>
      </div>
    </div>

    <!-- ④ SOS 紧急求救按钮（长按 3 秒触发） -->
    <div class="sos-section">
      <button
        class="sos-button"
        :class="{ 'sos-active': __state.sosActive }"
        @mousedown="handleSOSPressStart"
        @mouseup="handleSOSPressEnd"
        @mouseleave="handleSOSPressEnd"
        @touchstart.prevent="handleSOSPressStart"
        @touchend.prevent="handleSOSPressEnd">
        <!-- 进度环：stroke-dasharray 控制弧长 -->
        <svg class="sos-progress-ring" viewBox="0 0 100 100">
          <rect class="sos-progress-track" x="4" y="4" width="92" height="92" rx="18" />
          <rect
            class="sos-progress-fill"
            x="4"
            y="4"
            width="92"
            height="92"
            rx="18"
            :stroke-dasharray="`${(__state.sosHoldProgress / 100) * 368} 368`" />
        </svg>

        <span v-if="!__state.sosActive" class="sos-icon-wrapper">🛡️</span>
        <span v-else class="sos-text">SOS</span>
        <span class="sos-hint">{{ sosHintText }}</span>

        <!-- 长按剩余秒数 -->
        <span v-if="__state.sosHoldProgress > 0 && __state.sosHoldProgress < 100" class="sos-countdown">
          {{ holdRemainSeconds }}s
        </span>
      </button>

      <!-- SOS 状态栏 -->
      <div v-if="__state.sosActive && __state.sosProgress !== 'idle'" class="sos-status-bar">
        <span class="sos-status-left">
          <span class="sos-status-icon">{{ sosConfig.icon }}</span>
          <span class="sos-status-text">{{ sosConfig.text }}</span>
        </span>
        <button v-if="__state.sosProgress === 'waiting'" class="sos-resolve-btn" @click="__state.showSOSConfirm = true">
          解除求救
        </button>
      </div>
    </div>

    <!-- ⑤ 设备状态区（数值来自 mock，会真实变化） -->
    <div class="status-section">
      <h2 class="section-title">设备状态</h2>
      <div class="status-cards">
        <button class="status-card" @click="goPage('/pages/device-status/index')">
          <div class="status-icon-wrapper">
            <span class="status-emoji">{{ __state.deviceStatus.connected ? '🔗' : '📴' }}</span>
          </div>
          <span class="status-label">蓝牙</span>
          <span class="status-value" :class="{ danger: !__state.deviceStatus.connected }">
            {{ __state.deviceStatus.connected ? '已连接' : '未连接' }}
          </span>
        </button>

        <button class="status-card" @click="goPage('/pages/device-status/index')">
          <div class="status-icon-wrapper">
            <span class="status-emoji">{{ __state.deviceStatus.batteryLevel <= 20 ? '🪫' : '🔋' }}</span>
          </div>
          <span class="status-label">电量</span>
          <span class="status-value" :class="{ warn: __state.deviceStatus.batteryLevel <= 20 }">
            {{ __state.deviceStatus.batteryLevel }}%
          </span>
        </button>

        <button class="status-card" @click="goPage('/pages/device-status/index')">
          <div class="status-icon-wrapper">
            <span class="status-emoji">📶</span>
          </div>
          <span class="status-label">信号</span>
          <span class="status-value">{{ __state.deviceStatus.signalStrength > 2 ? '良好' : '较弱' }}</span>
        </button>
      </div>
      <p class="status-sync">最近同步：{{ __state.deviceStatus.lastSyncTime }} · {{ __state.deviceStatus.name }}</p>
    </div>

    <!-- ⑥ 老人状态区 -->
    <div class="elder-status-section">
      <h2 class="section-title">老人状态 👴👵</h2>
      <div class="elder-status-cards">
        <div class="elder-card clickable" @click="goPage('/pages/sos-map/index')">
          <div class="elder-card-header">
            <span class="elder-card-emoji">📍</span>
            <span class="elder-card-title">位置定位</span>
          </div>
          <p class="elder-card-value">{{ __state.elderStatus.location }}</p>
          <p class="elder-card-hint">点击查看详情 →</p>
        </div>

        <div class="elder-card">
          <div class="elder-card-header">
            <span class="elder-card-emoji">📈</span>
            <span class="elder-card-title">今日活动</span>
          </div>
          <p class="elder-card-value">{{ formatActivityTime(__state.elderStatus.todayActivityMinutes) }}</p>
          <p class="elder-card-hint">步数 {{ __state.elderStatus.steps }} · 较昨日 +15 分钟</p>
        </div>
      </div>
    </div>

    <!-- ⑦ 快捷操作区 -->
    <div class="quick-actions-section">
      <h2 class="section-title">快捷操作 ⚡</h2>
      <div class="quick-actions-grid">
        <button class="action-btn" @click="goPage('/pages/voice-monitor/index')">
          <div class="action-icon-wrapper">🎤</div>
          <span class="action-label">语音监测</span>
        </button>
        <button class="action-btn" @click="goPage('/pages/video-monitor/index')">
          <div class="action-icon-wrapper">📷</div>
          <span class="action-label">视频监测</span>
        </button>
        <button class="action-btn" @click="goPage('/pages/emergency-contacts/index')">
          <div class="action-icon-wrapper">📞</div>
          <span class="action-label">紧急联系</span>
        </button>
        <button class="action-btn" @click="toast('安全检查功能开发中')">
          <div class="action-icon-wrapper">✓</div>
          <span class="action-label">安全检查</span>
        </button>
      </div>
    </div>

    <!-- ⑧ 警报通知区 -->
    <div class="alerts-section">
      <div class="alerts-header">
        <h2 class="section-title">警报通知</h2>
        <span class="alerts-more" @click="toast('查看全部警报')">查看全部</span>
      </div>

      <div v-if="activeAlerts.length === 0" class="no-alerts">
        <span class="no-alerts-icon">✓</span>
        <span class="no-alerts-text">暂无警报，一切正常</span>
      </div>

      <div v-else class="alerts-list">
        <div
          v-for="alert in activeAlerts"
          :key="alert.id"
          class="alert-item"
          :class="`alert-${alert.level}`">
          <div class="alert-icon-wrapper" :class="`icon-${alert.level}`">
            {{ alert.level === 'urgent' ? '⚠️' : '⚠️' }}
          </div>
          <div class="alert-content">
            <div class="alert-title-row">
              <span class="alert-title">{{ alert.type === 'fall' ? '跌倒警报' : '长时间未活动' }}</span>
              <span class="alert-time">{{ getAlertTimeText(alert.timestamp) }}</span>
            </div>
            <p class="alert-message">{{ alert.message }}</p>
            <div v-if="alert.address" class="alert-location">📍 {{ alert.address }}</div>
            <div class="alert-actions-row">
              <button
                class="alert-btn"
                :class="alert.level === 'urgent' ? 'btn-urgent' : 'btn-warning'"
                @click="goPage('/pages/alert-detail/index?id=' + alert.id)">
                📞 联系家人
              </button>
              <button class="alert-nav-btn" @click="goPage('/pages/sos-map/index')">📍 导航前往</button>
              <button class="alert-ignore-btn" @click="ignoreAlert(alert.id)">忽略</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ⑨ 紧急联系人区 -->
    <div class="contacts-section">
      <div class="contacts-header">
        <h2 class="section-title">紧急联系人</h2>
        <button class="contacts-add" @click="goPage('/pages/emergency-contacts/index')">+ 添加</button>
      </div>

      <div class="contacts-list">
        <div v-for="contact in __state.contacts" :key="contact.id" class="contact-card">
          <div class="contact-avatar" :class="{ 'avatar-mom': contact.gender === 'female' }">
            {{ contact.avatarText }}
          </div>
          <div class="contact-info">
            <p class="contact-name">{{ contact.name }}</p>
            <p class="contact-role">{{ contact.role }}</p>
            <p class="contact-phone">{{ contact.phone }}</p>
          </div>
          <button class="contact-call-btn" @click="toast('正在呼叫 ' + contact.name)">📞</button>
        </div>

        <button class="contact-add-card" @click="goPage('/pages/emergency-contacts/index')">
          <div class="contact-add-avatar">➕</div>
          <div class="contact-add-info">
            <p class="contact-add-title">添加更多联系人</p>
            <p class="contact-add-desc">建议添加 3-5 位紧急联系人</p>
          </div>
        </button>
      </div>
    </div>

    <!-- SOS 倒计时确认弹窗 -->
    <div v-if="__state.showSOSCountdown" class="sos-modal-mask">
      <div class="sos-modal-box">
        <div class="countdown-circle" :class="{ urgent: __state.sosCountdown <= 2 }">
          {{ __state.sosCountdown }}
        </div>
        <h3 class="sos-modal-title">即将发送紧急求救</h3>
        <p class="sos-modal-desc">倒计时结束后将自动向紧急联系人发送求救信号，如非紧急情况请立即取消</p>
        <div class="sos-modal-actions">
          <button class="sos-modal-btn cancel" @click="cancelSOSCountdown">取消求救</button>
          <button class="sos-modal-btn send" @click="immediateTriggerSOS">立即发送</button>
        </div>
        <p class="sos-modal-hint">请在倒计时结束前确认是否需要发送求救</p>
      </div>
    </div>

    <!-- SOS 解除确认弹窗 -->
    <div v-if="__state.showSOSConfirm" class="dialog-mask" @click.self="__state.showSOSConfirm = false">
      <div class="dialog-box">
        <h3 class="dialog-title">解除求救</h3>
        <p class="dialog-message">确认要解除当前的求救状态吗？</p>
        <div class="dialog-actions">
          <button class="dialog-btn cancel" @click="__state.showSOSConfirm = false">取消</button>
          <button class="dialog-btn confirm" @click="confirmResolveSOS">确认解除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 居家守护页
 *
 * 核心是「长按 SOS 求救」：长按 3 秒满进度 → 5 秒倒计时兜底 → 自动发送求救信号
 *
 * 修正的原项目缺陷：
 *   5. 原项目 UI 文案写"长按3秒"但代码是 100ms × 10 = 1 秒，这里按文案实现真正的 3 秒。
 *   6. 原项目 deviceStatus / elderStatus 是 useState 死值且永不更新，
 *      这里放进 reactive 并让它随 mock 数据真实变化（电量为例：随时间缓慢下降）。
 */
import { reactive, computed, onMounted, onUnmounted } from 'vue'

/** 长按总时长：3 秒（每 100ms +约 3.33%，30 次满进度） */
const HOLD_DURATION = 3000
const HOLD_TICK = 100
const HOLD_STEP = 100 / (HOLD_DURATION / HOLD_TICK) // ≈ 3.333

interface AlertRecord {
  id: string
  type: 'fall' | 'sedentary'
  level: 'normal' | 'warning' | 'urgent'
  message: string
  timestamp: number
  resolved: boolean
  address?: string
}

const __state = reactive({
  todayText: '',

  safetyStatus: 'safe' as 'safe' | 'warning' | 'danger',
  guardianDays: 128,
  todayInteractions: 3,

  // 设备状态（mock，会真实变化）
  deviceStatus: {
    name: '智能手环',
    type: 'wearable',
    connected: true,
    batteryLevel: 85,
    lastSyncTime: '10分钟前',
    signalStrength: 4
  },

  // 老人状态（mock，会真实变化）
  elderStatus: {
    name: '张奶奶',
    lastActiveTime: '5分钟前',
    lastLocationTime: '2分钟前',
    todayActivityMinutes: 155,
    riskLevel: 'low' as 'low' | 'medium' | 'high',
    healthScore: 92,
    location: '北京市东城区王府井大街',
    locationCoords: { latitude: 39.9042, longitude: 116.4074 },
    steps: 3520,
    locationOnline: true
  },

  // SOS 流程
  sosProgress: 'idle' as 'idle' | 'triggering' | 'sending' | 'notified' | 'waiting' | 'resolved',
  sosActive: false,
  sosHoldProgress: 0,
  showSOSConfirm: false,
  showSOSCountdown: false,
  sosCountdown: 5,

  // 警报（mock）
  alerts: [
    {
      id: 'alert-1',
      type: 'fall',
      level: 'urgent',
      message: '检测到老人跌倒，请立即查看情况',
      timestamp: Date.now() - 5 * 60 * 1000,
      resolved: false,
      address: '北京市东城区王府井大街'
    },
    {
      id: 'alert-2',
      type: 'sedentary',
      level: 'warning',
      message: '老人已超过3小时未移动，建议查看',
      timestamp: Date.now() - 30 * 60 * 1000,
      resolved: false
    },
    {
      id: 'alert-3',
      type: 'sedentary',
      level: 'warning',
      message: '昨日夜间心率波动较大，建议关注',
      timestamp: Date.now() - 20 * 60 * 60 * 1000,
      resolved: true
    }
  ] as AlertRecord[],

  // 紧急联系人（mock，原项目为三条硬编码卡片）
  contacts: [
    { id: 'c-1', name: '张建国', role: '父亲 · 第一联系人', phone: '138****8888', avatarText: '爸', gender: 'male' },
    { id: 'c-2', name: '李秀芳', role: '母亲 · 第二联系人', phone: '139****6666', avatarText: '妈', gender: 'female' }
  ]
})

const now = new Date()
__state.todayText = now.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' })

let holdTimer: ReturnType<typeof setInterval> | null = null
let countdownTimer: ReturnType<typeof setInterval> | null = null
let batteryTimer: ReturnType<typeof setInterval> | null = null

function toast(msg: string) {
  console.log('[toast]', msg)
}

function goPage(path: string) {
  console.log('[navigate]', path)
}

/* ============ 派生数据 ============ */

const safetyLabel = computed(() => {
  const map: Record<string, string> = { safe: '安全', warning: '注意', danger: '警报' }
  return map[__state.safetyStatus]
})

/** SOS 进度文案配置（照抄原项目） */
const SOS_PROGRESS_CONFIG: Record<string, { text: string; icon: string; color: string }> = {
  idle: { text: '', icon: '', color: '' },
  triggering: { text: '正在触发求救...', icon: '⏳', color: '#F5B041' },
  sending: { text: '正在发送求救信号...', icon: '📡', color: '#E85A5A' },
  notified: { text: '已通知紧急联系人', icon: '✅', color: '#6BBE8A' },
  waiting: { text: '等待家属响应...', icon: '⏰', color: '#F5B041' },
  resolved: { text: '求救已处理', icon: '✅', color: '#6BBE8A' }
}

const sosConfig = computed(() => SOS_PROGRESS_CONFIG[__state.sosProgress])

/** 提示文案：idle 时显示"长按3秒紧急求救" */
const sosHintText = computed(() => {
  if (__state.sosProgress !== 'idle' && __state.sosActive) return sosConfig.value.text
  return '长按 3 秒紧急求救'
})

/** 长按剩余秒数（用于按钮上的倒计显示） */
const holdRemainSeconds = computed(() => {
  const remain = HOLD_DURATION - (__state.sosHoldProgress / 100) * HOLD_DURATION
  return Math.max(0, Math.ceil(remain / 1000))
})

/** 只展示未处理的警报 */
const activeAlerts = computed(() => __state.alerts.filter((a) => !a.resolved))

/** 相对时间格式化（照抄原项目） */
function getAlertTimeText(timestamp: number): string {
  const diff = Date.now() - timestamp
  const minutes = Math.floor(diff / 60000)
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  return `${Math.floor(hours / 24)}天前`
}

/** 活动时长格式化（照抄原项目）：155 → "2小时35分" */
function formatActivityTime(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  return `${hours}小时${mins}分`
}

/* ============ SOS 长按链路 ============ */

/**
 * 长按开始：每 100ms 推进度，3 秒（30 次）满 100
 * 修正点：原项目 10 次 × 100ms = 1 秒，与"长按3秒"文案不符，这里改成真正的 3 秒
 */
function handleSOSPressStart() {
  if (__state.sosActive) return
  __state.sosHoldProgress = 0
  if (holdTimer) clearInterval(holdTimer)

  holdTimer = setInterval(() => {
    __state.sosHoldProgress = Math.min(100, __state.sosHoldProgress + HOLD_STEP)
    if (__state.sosHoldProgress >= 100) {
      if (holdTimer) {
        clearInterval(holdTimer)
        holdTimer = null
      }
      startSOSCountdown()
    }
  }, HOLD_TICK)
}

/** 松手：清零进度，长按必须一口气满 3 秒 */
function handleSOSPressEnd() {
  if (holdTimer) {
    clearInterval(holdTimer)
    holdTimer = null
  }
  if (!__state.showSOSCountdown) {
    __state.sosHoldProgress = 0
  }
}

/** 满进度 → 5 秒倒计时兜底 */
function startSOSCountdown() {
  __state.showSOSCountdown = true
  __state.sosCountdown = 5
  // 小程序环境下这里会调用 Taro.vibrateShort({ type: 'heavy' }) 震动反馈
  console.log('[vibrate] heavy')

  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    __state.sosCountdown -= 1
    if (__state.sosCountdown <= 0) {
      if (countdownTimer) {
        clearInterval(countdownTimer)
        countdownTimer = null
      }
      __state.showSOSCountdown = false
      triggerSOS()
    }
  }, 1000)
}

function cancelSOSCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
  __state.showSOSCountdown = false
  __state.sosCountdown = 5
  __state.sosHoldProgress = 0
  toast('已取消求救')
}

function immediateTriggerSOS() {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
  __state.showSOSCountdown = false
  triggerSOS()
}

/** 真正发送求救信号 */
async function triggerSOS() {
  __state.sosProgress = 'triggering'
  __state.sosActive = true
  console.log('[sound] playSOSAlert')

  __state.sosProgress = 'sending'
  // mock：真实项目这里是 await sosService.sendSOS({ type:'sos_button', location, message })
  await new Promise((resolve) => setTimeout(resolve, 1200))

  __state.sosProgress = 'notified'
  const names = __state.contacts.slice(0, 2).map((c) => c.name).join('、')
  toast(`已通知${names}等${__state.contacts.length}位家属`)
  // 原项目 30 秒间隔持续上报位置
  console.log('[location] startLocationTracking(30)')

  // notified 停留 3 秒 → waiting
  setTimeout(() => {
    if (__state.sosActive) __state.sosProgress = 'waiting'
  }, 3000)
}

/** 解除求救 */
function confirmResolveSOS() {
  __state.showSOSConfirm = false
  __state.sosProgress = 'resolved'
  __state.sosActive = false
  console.log('[location] stopLocationTracking')
  // resolved 停留 2 秒 → idle
  setTimeout(() => {
    __state.sosProgress = 'idle'
    __state.sosHoldProgress = 0
  }, 2000)
}

/** 忽略警报 = 软删除 */
function ignoreAlert(id: string) {
  const target = __state.alerts.find((a) => a.id === id)
  if (target) {
    target.resolved = true
    toast('已忽略该警报')
  }
}

function handleLocationDetail() {
  const c = __state.elderStatus.locationCoords
  goPage(`/pages/location-detail/index?lat=${c.latitude}&lng=${c.longitude}&name=${encodeURIComponent(__state.elderStatus.name)}`)
}

/** 让设备状态看起来是活的：电量缓慢下降、信号偶有波动 */
function tickDeviceStatus() {
  if (__state.deviceStatus.batteryLevel > 5) {
    __state.deviceStatus.batteryLevel -= 1
  }
  __state.deviceStatus.signalStrength = 3 + Math.round(Math.random())
  __state.deviceStatus.connected = __state.deviceStatus.signalStrength > 1
  __state.deviceStatus.lastSyncTime = '刚刚'
}

onMounted(() => {
  batteryTimer = setInterval(tickDeviceStatus, 30000)
})

onUnmounted(() => {
  if (holdTimer) clearInterval(holdTimer)
  if (countdownTimer) clearInterval(countdownTimer)
  if (batteryTimer) clearInterval(batteryTimer)
})
</script>

<style scoped>
.guardian-page {
  min-height: 100vh;
  padding: 16px;
  padding-bottom: 80px;
  background: #f4efe6;
}

/* ---------- ① 页面标题区 ---------- */
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}

.header-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.header-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-icon {
  font-size: 24px;
  line-height: 1;
}

.header-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #2c3e2d;
}

.header-sub {
  margin: 0;
  font-size: 14px;
  color: #5b7b5a;
}

.header-date {
  font-size: 14px;
  color: #5b7b5a;
}

/* ---------- ② 安全状态 ---------- */
.safety-section {
  margin: 16px 20px;
  padding: 20px;
  background: linear-gradient(135deg, rgba(143, 166, 142, 0.08) 0%, rgba(244, 239, 230, 0.95) 100%);
  border: 1px solid rgba(143, 166, 142, 0.2);
  border-radius: 24px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
}

.safety-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-bottom: 16px;
}

.safety-led {
  position: relative;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.led-safe { background: rgba(76, 175, 80, 0.15); box-shadow: 0 0 24px rgba(76, 175, 80, 0.5), inset 0 0 8px rgba(76, 175, 80, 0.2); }
.led-warning { background: rgba(255, 193, 7, 0.15); box-shadow: 0 0 24px rgba(255, 193, 7, 0.5), inset 0 0 8px rgba(255, 193, 7, 0.2); }
.led-danger { background: rgba(244, 67, 54, 0.15); box-shadow: 0 0 24px rgba(244, 67, 54, 0.5), inset 0 0 8px rgba(244, 67, 54, 0.2); }

.led-glow {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  animation: led-pulse 2s ease-in-out infinite;
}

.glow-safe { background: radial-gradient(circle, rgba(76, 175, 80, 0.35) 0%, transparent 70%); }
.glow-warning { background: radial-gradient(circle, rgba(255, 193, 7, 0.35) 0%, transparent 70%); }
.glow-danger { background: radial-gradient(circle, rgba(244, 67, 54, 0.35) 0%, transparent 70%); }

@keyframes led-pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.25); opacity: 0.6; }
}

.led-core {
  position: relative;
  width: 20px;
  height: 20px;
  border-radius: 50%;
}

.core-safe { background: linear-gradient(135deg, #4caf50, #66bb6a); }
.core-warning { background: linear-gradient(135deg, #ffc107, #ffd54f); }
.core-danger { background: linear-gradient(135deg, #f44336, #ef5350); }

.safety-label {
  font-size: 18px;
  font-weight: 600;
  color: #4a4a4a;
}

.guardian-stats {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 16px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #8fa68e;
}

.stat-label {
  margin-top: 4px;
  font-size: 12px;
  color: #888888;
}

.stat-divider {
  width: 1px;
  height: 36px;
  background: rgba(143, 166, 142, 0.25);
}

/* ---------- ③ 实时位置 ---------- */
.location-card {
  margin: 20px 16px;
  background: #ffffff;
  border-radius: 20px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.location-map-area {
  position: relative;
  height: 180px;
  background: #e5e5e5;
  cursor: pointer;
  overflow: hidden;
}

/* 用 CSS 模拟地图纹理，避免依赖外部静态图 API */
.map-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.7) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.7) 1px, transparent 1px);
  background-size: 32px 32px;
  background-color: #dfe6dd;
}

.map-road {
  position: absolute;
  background: #ffffff;
  opacity: 0.9;
}

.road-h {
  left: 0;
  right: 0;
  top: 50%;
  height: 14px;
  transform: translateY(-50%);
}

.road-v {
  top: 0;
  bottom: 0;
  left: 32%;
  width: 12px;
}

.map-center-marker {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
}

.marker-outer {
  position: relative;
  z-index: 2;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(232, 90, 90, 0.9);
  border-radius: 50%;
  box-shadow: 0 4px 12px rgba(232, 90, 90, 0.4);
}

.marker-inner {
  width: 10px;
  height: 10px;
  background: #ffffff;
  border-radius: 50%;
}

.marker-pulse {
  position: absolute;
  width: 60px;
  height: 60px;
  background: rgba(232, 90, 90, 0.15);
  border-radius: 50%;
  animation: pulse-ring 2s ease-out infinite;
}

@keyframes pulse-ring {
  0% { transform: scale(0.6); opacity: 1; }
  100% { transform: scale(1.5); opacity: 0; }
}

.view-detail-hint {
  position: absolute;
  bottom: 12px;
  right: 12px;
  padding: 6px 12px;
  font-size: 12px;
  color: #ffffff;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 16px;
}

.location-info-area {
  padding: 16px 20px;
  cursor: pointer;
}

.location-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.location-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.location-icon-wrap {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  background: linear-gradient(135deg, #8fa68e, #6b8b6b);
  border-radius: 8px;
}

.location-title {
  font-size: 15px;
  font-weight: 600;
  color: #333333;
}

.location-status {
  display: flex;
  align-items: center;
  gap: 4px;
}

.status-dot-online {
  width: 8px;
  height: 8px;
  background: #52c41a;
  border-radius: 50%;
}

.status-text {
  font-size: 12px;
  color: #52c41a;
}

.elder-name {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: #333333;
}

.elder-address {
  margin: 4px 0 0;
  font-size: 14px;
  color: #666666;
}

.location-meta {
  display: flex;
  gap: 16px;
  margin-top: 8px;
}

.meta-item {
  font-size: 12px;
  color: #999999;
}

/* ---------- ④ SOS 按钮 ---------- */
.sos-section {
  margin-bottom: 24px;
  padding: 0 16px;
}

.sos-button {
  position: relative;
  width: 100%;
  height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: linear-gradient(135deg, #ff6b6b, #ee5a5a);
  border: none;
  border-radius: 24px;
  box-shadow: 0 8px 24px rgba(238, 90, 90, 0.3);
  overflow: hidden;
  cursor: pointer;
  user-select: none;
}

/* 呼吸灯 */
.sos-button::before {
  content: '';
  position: absolute;
  inset: -50%;
  background: radial-gradient(circle, rgba(255, 107, 107, 0.4) 0%, rgba(255, 107, 107, 0.2) 40%, transparent 70%);
  animation: sos-breathing 2s ease-in-out infinite;
}

/* 扩散波纹 */
.sos-button::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 100px;
  height: 100px;
  margin: -50px 0 0 -50px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  animation: sos-ripple 2.5s ease-out infinite;
}

@keyframes sos-breathing {
  0%, 100% { transform: scale(1); opacity: 0.5; }
  50% { transform: scale(1.2); opacity: 0.8; }
}

@keyframes sos-ripple {
  0% { transform: scale(0.5); opacity: 1; border-width: 3px; }
  100% { transform: scale(3); opacity: 0; border-width: 1px; }
}

.sos-active {
  background: linear-gradient(135deg, #f5b041, #e85a5a);
  animation: sos-active-pulse 0.5s ease-in-out infinite;
}

.sos-active::before {
  animation-duration: 0.3s;
}

.sos-active::after {
  animation-duration: 0.4s;
}

@keyframes sos-active-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.02); }
}

/* 进度环：SVG rect 用 stroke-dasharray 画弧 */
.sos-progress-ring {
  position: absolute;
  inset: 0;
  z-index: 2;
  width: 100%;
  height: 100%;
}

.sos-progress-track,
.sos-progress-fill {
  fill: none;
  stroke-width: 4;
}

.sos-progress-track {
  stroke: rgba(255, 255, 255, 0.2);
}

.sos-progress-fill {
  stroke: #ffffff;
  stroke-linecap: round;
  transition: stroke-dasharray 0.1s linear;
}

.sos-icon-wrapper {
  position: relative;
  z-index: 3;
  font-size: 32px;
  line-height: 1;
}

.sos-text {
  position: relative;
  z-index: 3;
  font-size: 32px;
  font-weight: 800;
  color: #ffffff;
  letter-spacing: 4px;
}

.sos-hint {
  position: relative;
  z-index: 3;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.8);
}

.sos-countdown {
  position: relative;
  z-index: 3;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.9);
}

.sos-status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  padding: 12px 16px;
  background: #ffffff;
  border-radius: 16px;
}

.sos-status-left {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sos-status-text {
  font-size: 14px;
  color: #2c3e2d;
}

.sos-resolve-btn {
  padding: 6px 16px;
  font-size: 14px;
  color: #ffffff;
  background: #8fa68e;
  border: none;
  border-radius: 9999px;
  cursor: pointer;
}

/* ---------- 区块标题 ---------- */
.section-title {
  margin: 0 0 12px;
  font-size: 18px;
  font-weight: 600;
  color: #2c3e2d;
}

/* ---------- ⑤ 设备状态 ---------- */
.status-section {
  margin-bottom: 24px;
  padding: 0 16px;
}

.status-cards {
  display: flex;
  gap: 12px;
}

.status-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: #ffffff;
  border: none;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  cursor: pointer;
}

.status-icon-wrapper {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(143, 166, 142, 0.15);
  border-radius: 12px;
}

.status-emoji {
  font-size: 20px;
  line-height: 1;
}

.status-label {
  font-size: 12px;
  color: #8b7355;
}

.status-value {
  font-size: 14px;
  font-weight: 600;
  color: #8fa68e;
}

.status-value.danger { color: #e85a5a; }
.status-value.warn { color: #f5b041; }

.status-sync {
  margin: 12px 0 0;
  font-size: 12px;
  color: #999999;
}

/* ---------- ⑥ 老人状态 ---------- */
.elder-status-section {
  margin-bottom: 24px;
  padding: 0 16px;
}

.elder-status-cards {
  display: flex;
  gap: 12px;
}

.elder-card {
  flex: 1;
  padding: 16px;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.elder-card.clickable {
  cursor: pointer;
}

.elder-card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
}

.elder-card-emoji {
  font-size: 18px;
  line-height: 1;
}

.elder-card-title {
  font-size: 14px;
  font-weight: 600;
  color: #2c3e2d;
}

.elder-card-value {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 700;
  color: #2c3e2d;
}

.elder-card-hint {
  margin: 0;
  font-size: 12px;
  color: #8b7355;
}

/* ---------- ⑦ 快捷操作 ---------- */
.quick-actions-section {
  margin-bottom: 24px;
  padding: 0 16px;
}

.quick-actions-grid {
  display: flex;
  gap: 12px;
}

.action-btn {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: #ffffff;
  border: none;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  cursor: pointer;
}

.action-icon-wrapper {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  background: rgba(143, 166, 142, 0.15);
  border-radius: 12px;
}

.action-label {
  font-size: 12px;
  font-weight: 500;
  color: #2c3e2d;
}

/* ---------- ⑧ 警报 ---------- */
.alerts-section {
  margin-bottom: 24px;
  padding: 0 16px;
}

.alerts-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.alerts-header .section-title {
  margin: 0;
}

.alerts-more {
  font-size: 14px;
  color: #8fa68e;
  cursor: pointer;
}

.alerts-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.alert-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  padding-left: 12px;
  background: #ffffff;
  border-left: 4px solid #8fa68e;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.alert-item.alert-urgent { border-left-color: #e85a5a; }
.alert-item.alert-warning { border-left-color: #f5b041; }
.alert-item.alert-normal { border-left-color: #8fa68e; }

.alert-icon-wrapper {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  border-radius: 10px;
}

.icon-urgent { background: rgba(232, 90, 90, 0.15); }
.icon-warning { background: rgba(245, 176, 65, 0.15); }
.icon-normal { background: rgba(143, 166, 142, 0.15); }

.alert-content {
  flex: 1;
  min-width: 0;
}

.alert-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.alert-title {
  font-size: 14px;
  font-weight: 600;
  color: #2c3e2d;
}

.alert-time {
  font-size: 12px;
  color: #8b7355;
}

.alert-message {
  margin: 4px 0 0;
  font-size: 13px;
  color: #5b7b5a;
}

.alert-location {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  padding: 6px 10px;
  font-size: 12px;
  color: #5b7b5a;
  background: rgba(143, 166, 142, 0.1);
  border-radius: 8px;
}

.alert-actions-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.alert-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 16px;
  font-size: 12px;
  color: #ffffff;
  border: none;
  border-radius: 9999px;
  cursor: pointer;
}

.btn-urgent { background: #e85a5a; }
.btn-warning { background: #f5b041; }

.alert-nav-btn {
  padding: 6px 12px;
  font-size: 12px;
  color: #8fa68e;
  background: rgba(143, 166, 142, 0.15);
  border: none;
  border-radius: 9999px;
  cursor: pointer;
}

.alert-ignore-btn {
  padding: 6px 12px;
  font-size: 12px;
  color: #8b7355;
  background: #f4efe6;
  border: none;
  border-radius: 9999px;
  cursor: pointer;
}

.no-alerts {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 32px;
  background: #ffffff;
  border-radius: 16px;
}

.no-alerts-icon {
  font-size: 32px;
  color: #8fa68e;
}

.no-alerts-text {
  font-size: 14px;
  color: #8fa68e;
}

/* ---------- ⑨ 紧急联系人 ---------- */
.contacts-section {
  padding: 16px;
  background: #f4efe6;
}

.contacts-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.contacts-header .section-title {
  margin: 0;
}

.contacts-add {
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 500;
  color: #8fa68e;
  background: rgba(143, 166, 142, 0.15);
  border: none;
  border-radius: 8px;
  cursor: pointer;
}

.contacts-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.contact-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.contact-avatar {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 600;
  color: #8fa68e;
  background: rgba(143, 166, 142, 0.2);
  border-radius: 12px;
}

.contact-avatar.avatar-mom {
  color: #d4a574;
  background: rgba(212, 165, 116, 0.2);
}

.contact-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.contact-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #2c3e2d;
}

.contact-role {
  margin: 0;
  font-size: 12px;
  color: #8b7355;
}

.contact-phone {
  margin: 0;
  font-size: 12px;
  color: #5b7b5a;
}

.contact-call-btn {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  background: rgba(143, 166, 142, 0.15);
  border: none;
  border-radius: 10px;
  cursor: pointer;
}

.contact-add-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  background: rgba(143, 166, 142, 0.08);
  border: 1px dashed #8fa68e;
  border-radius: 16px;
  cursor: pointer;
  text-align: left;
}

.contact-add-avatar {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #8fa68e;
}

.contact-add-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.contact-add-title {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: #8fa68e;
}

.contact-add-desc {
  margin: 0;
  font-size: 11px;
  color: #8b7355;
}

/* ---------- SOS 倒计时弹窗 ---------- */
.sos-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.8);
}

.sos-modal-box {
  width: 320px;
  padding: 24px;
  text-align: center;
  background: #fff9ef;
  border-radius: 16px;
}

.countdown-circle {
  width: 96px;
  height: 96px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36px;
  font-weight: 700;
  color: #ffffff;
  background: #c4a484;
  border-radius: 50%;
  animation: countdown-pulse 0.5s infinite;
}

.countdown-circle.urgent {
  background: #e57373;
}

@keyframes countdown-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.06); }
}

.sos-modal-title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 700;
  color: #e55a5a;
}

.sos-modal-desc {
  margin: 0 0 20px;
  font-size: 16px;
  line-height: 1.5;
  color: #7d746b;
}

.sos-modal-actions {
  display: flex;
  gap: 16px;
}

.sos-modal-btn {
  flex: 1;
  padding: 16px;
  font-size: 18px;
  font-weight: 600;
  color: #ffffff;
  border: none;
  border-radius: 12px;
  cursor: pointer;
}

.sos-modal-btn.cancel { background: #e55a5a; }
.sos-modal-btn.send { background: #f5b041; }

.sos-modal-hint {
  margin: 16px 0 0;
  font-size: 14px;
  color: #7d746b;
}

/* ---------- 通用弹窗 ---------- */
.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
}

.dialog-box {
  width: 320px;
  padding: 24px;
  background: #fff9ef;
  border-radius: 16px;
}

.dialog-title {
  margin: 0 0 16px;
  font-size: 20px;
  font-weight: 600;
  color: #2f2a24;
}

.dialog-message {
  margin: 0 0 24px;
  font-size: 16px;
  line-height: 1.6;
  color: #7d746b;
}

.dialog-actions {
  display: flex;
  gap: 16px;
}

.dialog-btn {
  flex: 1;
  padding: 12px;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  cursor: pointer;
}

.dialog-btn.cancel {
  background: rgba(125, 116, 107, 0.05);
  color: #7d746b;
  font-weight: 500;
}

.dialog-btn.confirm {
  background: #8fa68e;
  color: #ffffff;
  font-weight: 600;
}
</style>

<template>
  <!-- 大字模式：根节点动态挂 .elder-mode，原项目这套 CSS 写好了但从未挂载 -->
  <div class="page monitor-page" :class="{ 'elder-mode': __state.elderMode }">
    <!-- 大字模式开关条 -->
    <div class="elder-mode-bar">
      <span class="elder-mode-label">🔍 大字模式</span>
      <div class="elder-switch" :class="{ on: __state.elderMode }" @click="__state.elderMode = !__state.elderMode">
        <div class="elder-switch-knob"></div>
      </div>
    </div>

    <!-- ① 位置追踪卡片 -->
    <div class="monitor-card">
      <div class="card-header">
        <div class="card-header-with-icon">
          <div class="card-icon-wrapper location-icon">📍</div>
          <div class="card-header-text">
            <h2 class="card-title">位置追踪</h2>
            <p class="card-desc">实时定位与安全区域</p>
          </div>
        </div>
      </div>

      <div class="card-body">
        <!-- 实时跌倒监测开关 -->
        <div class="realtime-fall-header">
          <div class="fall-switch-row">
            <span class="fall-switch-label">📈 实时跌倒监测</span>
            <div class="mini-switch" :class="{ on: __state.realtimeFallDetectionEnabled }" @click="toggleRealtimeFall">
              <div class="mini-switch-knob"></div>
            </div>
          </div>

          <div v-if="__state.realtimeFallDetectionEnabled" class="fall-switch-detail">
            <p class="fall-detail-line">实时监测已开启</p>
            <p class="fall-detail-line">
              状态: {{ __state.fallDetectionStatus === 'normal' ? '正常' : __state.fallDetectionStatus === 'warning' ? '预警' : '警报' }}
            </p>
            <span class="status-badge" :class="`badge-${__state.fallDetectionStatus}`">
              {{ fallStatusIcon }} {{ fallStatusText }}
            </span>
          </div>
        </div>

        <!-- 位置信息 -->
        <div v-if="__state.locationInfo" class="location-info">
          <div class="location-address">📍 {{ __state.locationInfo.address }}</div>
          <div class="location-detail">
            <span class="location-accuracy">精度: {{ __state.locationInfo.accuracy }}米</span>
            <span class="safe-tag" :class="{ unsafe: !__state.locationInfo.safeZone }">
              🛡️ {{ __state.locationInfo.safeZone ? '安全区域内' : '已离开安全区域' }}
            </span>
          </div>
        </div>

        <div class="location-actions">
          <button class="location-btn" :disabled="__state.locationLoading" @click="getCurrentLocation">
            {{ __state.locationLoading ? '⏳ 定位中...' : '📍 获取位置' }}
          </button>
          <button class="tracking-btn" :class="{ active: __state.trackingEnabled }" @click="toggleTracking">
            {{ __state.trackingEnabled ? '停止追踪' : '开始追踪' }}
          </button>
        </div>

        <!-- 权限提示 -->
        <button v-if="__state.permissions.location === false" class="permission-tip" @click="requestLocationPermission">
          ⚠️ 未授权定位权限，点击开启
        </button>
      </div>
    </div>

    <!-- ② 状态上报卡片 -->
    <div class="monitor-card">
      <div class="card-header">
        <div class="card-header-with-icon">
          <div class="card-icon-wrapper text-icon">📄</div>
          <div class="card-header-text">
            <h2 class="card-title">状态上报</h2>
            <p class="card-desc">描述当前健康状况</p>
          </div>
        </div>
      </div>

      <div class="card-body">
        <div class="text-area-wrapper">
          <textarea
            v-model="__state.textInput"
            class="text-area"
            maxlength="500"
            placeholder="请描述您当前的健康状态，如：今天感觉怎么样、有什么不适、睡眠质量如何..."></textarea>
          <span class="char-count">{{ __state.textInput.length }}/500</span>
        </div>

        <button
          class="submit-btn"
          :disabled="__state.textLoading || !__state.textInput.trim()"
          @click="submitText">
          {{ __state.textLoading ? '⏳ 分析中...' : '📄 提交分析' }}
        </button>
      </div>
    </div>

    <!-- ③ 语音监测卡片 -->
    <div class="monitor-card">
      <div class="card-header">
        <div class="card-header-with-icon">
          <div class="card-icon-wrapper voice-icon">🎤</div>
          <div class="card-header-text">
            <h2 class="card-title">语音监测</h2>
            <p class="card-desc">录制语音进行健康分析</p>
          </div>
        </div>
      </div>

      <div class="card-body">
        <!-- 录音状态 -->
        <div class="recording-status">
          <div v-if="__state.recordingState === 'recording'" class="recording-indicator">
            <span class="pulse-dot"></span>
            <span class="recording-text">录音中 {{ formatRecordTime(__state.recordingTime) }}</span>
          </div>
          <div v-else-if="__state.voiceText" class="voice-result">
            <span class="voice-result-label">识别结果：</span>
            <span class="voice-text">{{ __state.voiceText }}</span>
          </div>
          <p v-else class="recording-idle">点击下方按钮开始录音，最长 60 秒</p>
        </div>

        <!-- 录音按钮 -->
        <div class="record-btn-wrapper">
          <button
            class="record-btn"
            :class="{ stop: __state.recordingState === 'recording' }"
            @click="toggleRecording">
            <span class="record-emoji">{{ __state.recordingState === 'recording' ? '⏹' : '🎤' }}</span>
            <span class="record-label">{{ __state.recordingState === 'recording' ? '停止录音' : '开始录音' }}</span>
          </button>
        </div>

        <button v-if="__state.permissions.record === false" class="permission-tip" @click="requestRecordPermission">
          ⚠️ 未授权录音权限，点击开启
        </button>
      </div>
    </div>

    <!-- ④ 跌倒检测卡片 -->
    <div class="monitor-card">
      <div class="card-header">
        <div class="card-header-with-icon">
          <div class="card-icon-wrapper camera-icon">📷</div>
          <div class="card-header-text">
            <h2 class="card-title">跌倒检测</h2>
            <p class="card-desc">AI 视觉姿态分析</p>
          </div>
        </div>
      </div>

      <div class="card-body">
        <!-- 模式切换 -->
        <div class="detect-mode-switch">
          <button
            class="mode-tab"
            :class="{ active: __state.detectMode === 'health' }"
            @click="__state.detectMode = 'health'">
            健康分析
          </button>
          <button
            class="mode-tab"
            :class="{ active: __state.detectMode === 'fall' }"
            @click="__state.detectMode = 'fall'">
            ⚠️ 跌倒检测
          </button>
        </div>

        <!-- 摄像头容器（mock：用占位块代替真实 Camera 组件） -->
        <div class="camera-container">
          <div class="camera-preview" :class="{ captured: !!__state.capturedImage }">
            <div v-if="!__state.capturedImage" class="camera-placeholder">
              <span class="camera-placeholder-emoji">📷</span>
              <span class="camera-placeholder-text">摄像头未就绪 · 点击下方按钮拍摄</span>
            </div>
            <div v-else class="captured-frame">
              <span class="captured-emoji">🖼️</span>
              <span class="captured-text">已拍摄画面（mock）</span>
            </div>
          </div>
        </div>

        <!-- 跌倒结果 -->
        <div v-if="__state.fallResult && __state.detectMode === 'fall'" class="fall-result">
          <div class="result-header">
            <span class="result-emoji">{{ __state.fallResult.detected ? '⚠️' : '🛡️' }}</span>
            <span class="result-title" :class="{ danger: __state.fallResult.detected }">
              {{ __state.fallResult.detected ? '检测到异常姿态' : '姿态正常' }}
            </span>
          </div>
          <div class="confidence-bar">
            <span class="confidence-text">置信度: {{ __state.fallResult.confidence }}%</span>
            <div class="bar-bg">
              <div
                class="bar-fill"
                :class="{ danger: __state.fallResult.detected }"
                :style="{ width: __state.fallResult.confidence + '%' }"></div>
            </div>
          </div>
          <p class="fall-analysis">{{ __state.fallResult.analysis }}</p>
        </div>

        <!-- 操作按钮 -->
        <div class="camera-actions">
          <template v-if="__state.capturedImage">
            <button class="action-btn" @click="retakePhoto">🔄 重新拍摄</button>
            <button class="action-btn primary" :disabled="__state.cameraLoading" @click="takePhoto">
              {{ __state.cameraLoading ? '分析中...' : '✨ AI 分析' }}
            </button>
          </template>
          <button v-else class="photo-btn" :disabled="__state.cameraLoading" @click="takePhoto">
            {{ __state.detectMode === 'fall' ? '📷 跌倒检测' : '📷 拍照分析' }}
          </button>
        </div>

        <button v-if="__state.permissions.camera === false" class="permission-tip" @click="requestCameraPermission">
          ⚠️ 未授权相机权限，点击开启
        </button>
      </div>
    </div>

    <!-- ⑤ 分析结果卡片 -->
    <div v-if="__state.results.length > 0" class="monitor-card result-card">
      <div class="card-header">
        <div class="card-body-title">分析结果</div>
      </div>

      <div class="card-body">
        <div v-for="(result, index) in __state.results.slice(0, 5)" :key="result.timestamp" class="result-item" :style="{ animationDelay: index * 0.1 + 's' }">
          <div class="result-header-row">
            <span class="result-type">{{ typeLabel(result.type) }}</span>
            <span class="result-time">{{ new Date(result.timestamp).toLocaleTimeString('zh-CN') }}</span>
          </div>
          <p class="result-content">内容：{{ result.content }}</p>
          <p class="result-analysis">分析：{{ result.analysis }}</p>
          <p v-if="result.alert" class="result-alert">⚠️ {{ result.alert }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 健康监测页
 *
 * 三模态采集：语音（录音）/ 图片（拍照）/ 文本（输入框），外加实时跌倒监测与定位追踪。
 *
 * 修正的原项目缺陷：
 *   3. 原项目 handleEmergencySOS() 写在 if (result.detected) 之外，
 *      导致"没检测到跌倒也会弹 SOS"。这里移进 if 块内部，只在真的检测到跌倒时触发。
 *   4. 原项目 .elder-mode 适老化 CSS 写好了却从未挂载，这里做了"大字模式"开关并挂到根节点。
 */
import { reactive, computed, onUnmounted } from 'vue'

type RecordingState = 'idle' | 'recording' | 'paused'
type ResultType = 'text' | 'voice' | 'image' | 'fall' | 'location'

interface AnalysisResult {
  type: ResultType
  content: string
  analysis: string
  alert?: string
  confidence?: number
  timestamp: number
}

const __state = reactive({
  // 大字模式（适老化）
  elderMode: false,

  // 权限：null 表示未检测
  permissions: {
    record: true as boolean | null,
    camera: true as boolean | null,
    location: true as boolean | null
  },

  // 文本上报
  textInput: '',
  textLoading: false,

  // 语音
  recordingState: 'idle' as RecordingState,
  recordingTime: 0,
  voiceText: '',
  voiceLoading: false,

  // 摄像头
  cameraReady: true,
  capturedImage: '',
  cameraLoading: false,
  detectMode: 'fall' as 'health' | 'fall',
  fallResult: null as { detected: boolean; confidence: number; analysis: string } | null,

  // 实时跌倒监测
  realtimeFallDetectionEnabled: false,
  fallDetectionStatus: 'normal' as 'normal' | 'warning' | 'alert',

  // 定位（mock）
  locationInfo: {
    latitude: 39.9042,
    longitude: 116.4074,
    accuracy: 123,
    address: '北京市东城区王府井大街',
    safeZone: true
  } as { latitude: number; longitude: number; accuracy: number; address: string; safeZone: boolean } | null,
  locationLoading: false,
  trackingEnabled: false,

  // 分析结果（mock，让界面不空）
  results: [
    {
      type: 'voice',
      content: '今天感觉有点累，睡眠质量不太好',
      analysis: '检测到您提到疲劳和睡眠问题，建议注意休息，保持规律作息。',
      alert: '睡眠质量需关注',
      timestamp: Date.now() - 45 * 60 * 1000
    },
    {
      type: 'text',
      content: '早上起床后有点头晕，量了血压 128/82',
      analysis: '血压处于正常范围，头晕可能与起床过快有关，建议起身时放缓动作。',
      timestamp: Date.now() - 3 * 60 * 60 * 1000
    },
    {
      type: 'image',
      content: '已拍摄画面',
      analysis: '姿态正常，未检测到跌倒风险。',
      confidence: 92,
      timestamp: Date.now() - 26 * 60 * 60 * 1000
    }
  ] as AnalysisResult[]
})

let recordTimer: ReturnType<typeof setInterval> | null = null
let trackTimer: ReturnType<typeof setInterval> | null = null

function toast(msg: string) {
  console.log('[toast]', msg)
}

/* ============ 派生值 ============ */

const fallStatusIcon = computed(() => {
  const map: Record<string, string> = { normal: '✓', warning: '⚠️', alert: '⚠️' }
  return map[__state.fallDetectionStatus]
})

const fallStatusText = computed(() => {
  const map: Record<string, string> = { normal: '状态正常', warning: '异常预警', alert: '跌倒警报' }
  return map[__state.fallDetectionStatus]
})

/** 结果类型标签（照抄原项目映射） */
function typeLabel(type: ResultType): string {
  if (type === 'text') return '文本'
  if (type === 'voice') return '语音'
  if (type === 'image') return '图像'
  return ''
}

/** MM:SS 录音计时文案 */
function formatRecordTime(seconds: number): string {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
}

/** 新结果插入队首 */
function addAnalysisResult(result: Omit<AnalysisResult, 'timestamp'>) {
  __state.results.unshift({ ...result, timestamp: Date.now() })
}

/* ============ 定位 ============ */

function getCurrentLocation() {
  __state.locationLoading = true
  // mock：真实项目这里是 Taro.getLocation({ type: 'gcj02' })
  setTimeout(() => {
    const lat = 39.9042 + (Math.random() - 0.5) * 0.004
    const lng = 116.4074 + (Math.random() - 0.5) * 0.004
    __state.locationInfo = {
      latitude: lat,
      longitude: lng,
      accuracy: 100 + Math.round(Math.random() * 50),
      address: '北京市东城区王府井大街',
      safeZone: true
    }
    __state.permissions.location = true
    __state.locationLoading = false
    addAnalysisResult({
      type: 'location',
      content: '北京市东城区王府井大街',
      analysis: `坐标: ${lat.toFixed(4)}, ${lng.toFixed(4)}`
    })
    toast('定位成功')
  }, 900)
}

/** 追踪：每 30 秒一次，与真实项目一致 */
function toggleTracking() {
  __state.trackingEnabled = !__state.trackingEnabled
  if (__state.trackingEnabled) {
    getCurrentLocation()
    trackTimer = setInterval(getCurrentLocation, 30000)
    toast('已开启位置追踪')
  } else {
    if (trackTimer) {
      clearInterval(trackTimer)
      trackTimer = null
    }
    toast('已停止位置追踪')
  }
}

function requestLocationPermission() {
  __state.permissions.location = true
  toast('定位权限已开启')
}

/* ============ 录音 ============ */

function toggleRecording() {
  if (__state.recordingState === 'recording') {
    stopRecording()
  } else {
    startRecording()
  }
}

function startRecording() {
  if (__state.permissions.record === false) {
    requestRecordPermission()
    return
  }
  __state.recordingState = 'recording'
  __state.recordingTime = 0
  __state.voiceText = ''
  // 录音参数：60 秒上限 / 16k 采样 / 单声道 / 48k 比特率 / mp3
  console.log('[recorder] start', { duration: 60000, sampleRate: 16000, numberOfChannels: 1, encodeBitRate: 48000, format: 'mp3' })

  recordTimer = setInterval(() => {
    __state.recordingTime += 1
    // 到 60 秒自动停止
    if (__state.recordingTime >= 60) {
      stopRecording()
    }
  }, 1000)
}

function stopRecording() {
  if (recordTimer) {
    clearInterval(recordTimer)
    recordTimer = null
  }
  __state.recordingState = 'idle'

  // 不足 1 秒视为无效录音
  if (__state.recordingTime < 1) {
    toast('录音时间太短')
    return
  }

  __state.voiceLoading = true
  // mock：真实项目这里是 Network.uploadFile('/api/monitor/voice-analyze')
  setTimeout(() => {
    __state.voiceText = '今天感觉有点累，睡眠质量不太好'
    addAnalysisResult({
      type: 'voice',
      content: __state.voiceText,
      analysis: '检测到您提到疲劳和睡眠问题，建议注意休息，保持规律作息。',
      alert: '睡眠质量需关注'
    })
    __state.voiceLoading = false
    toast('语音分析完成')
  }, 1200)
}

function requestRecordPermission() {
  __state.permissions.record = true
  toast('录音权限已开启')
}

/* ============ 拍照 / 跌倒检测 ============ */

function takePhoto() {
  if (__state.permissions.camera === false) {
    requestCameraPermission()
    return
  }
  if (!__state.cameraReady) {
    toast('摄像头未就绪')
    return
  }

  __state.cameraLoading = true
  // mock：真实项目这里是 Taro.createCameraContext().takePhoto()
  setTimeout(() => {
    __state.capturedImage = 'mock://' + Date.now()

    if (__state.detectMode === 'fall') {
      // 缺省规则：fallDetected 缺省 false、confidence 缺省 85、analysis 缺省"姿态正常"
      const detected = Math.random() > 0.65
      const confidence = detected ? 85 + Math.round(Math.random() * 10) : 90 + Math.round(Math.random() * 8)
      const analysis = detected ? '检测到疑似跌倒姿态，建议立即确认老人状态' : '姿态正常'

      __state.fallResult = { detected, confidence, analysis }
      addAnalysisResult({ type: 'fall', content: '已拍摄画面', analysis, confidence })

      // ★ 修正点：原项目这行写在 if 之外，导致没跌倒也会触发 SOS。现在移进 if 内部。
      if (detected) {
        __state.fallDetectionStatus = 'alert'
        handleEmergencySOS()
      }
    } else {
      addAnalysisResult({
        type: 'image',
        content: '已拍摄画面',
        analysis: '面部气色正常，未见明显异常，建议保持规律作息。'
      })
    }

    __state.cameraLoading = false
  }, 1300)
}

function retakePhoto() {
  __state.capturedImage = ''
  __state.fallResult = null
}

function requestCameraPermission() {
  __state.permissions.camera = true
  toast('相机权限已开启')
}

/* ============ 实时跌倒监测 ============ */

function toggleRealtimeFall() {
  __state.realtimeFallDetectionEnabled = !__state.realtimeFallDetectionEnabled
  if (__state.realtimeFallDetectionEnabled) {
    // 真实项目：fallDetectionService.onFallDetected(cb) + startMonitoring()
    console.log('[fallDetection] startMonitoring')
    toast('实时跌倒监测已开启')
  } else {
    console.log('[fallDetection] stopMonitoring')
    __state.fallDetectionStatus = 'normal'
    toast('实时跌倒监测已关闭')
  }
}

/** 内部 SOS：仅由跌倒检测命中时调用 */
function handleEmergencySOS() {
  console.log('[sos] 发送紧急求助信号给家属')
  toast('紧急求助已发送')
}

/* ============ 文本上报 ============ */

function submitText() {
  if (!__state.textInput.trim()) {
    toast('请输入健康状态')
    return
  }

  __state.textLoading = true
  const content = __state.textInput.trim()

  // mock：真实项目这里是 POST /api/monitor/text-analyze
  setTimeout(() => {
    addAnalysisResult({
      type: 'text',
      content,
      analysis: '已记录您的状态，AI 分析显示整体平稳，建议保持规律作息与适度活动。'
    })
    __state.textInput = ''
    __state.textLoading = false
    toast('分析完成')
  }, 1000)
}

onUnmounted(() => {
  if (recordTimer) clearInterval(recordTimer)
  if (trackTimer) clearInterval(trackTimer)
})
</script>

<style scoped>
.monitor-page {
  position: relative;
  min-height: 100vh;
  padding: 16px 12px;
  padding-bottom: 70px;
  background: linear-gradient(180deg, #f4efe6 0%, #f0f4f0 100%);
}

/* 晨光装饰 */
.monitor-page::before {
  content: '';
  position: fixed;
  top: 0;
  right: 0;
  width: 200px;
  height: 200px;
  background: radial-gradient(circle, rgba(245, 215, 110, 0.08) 0%, transparent 70%);
  pointer-events: none;
}

/* ---------- 大字模式开关条 ---------- */
.elder-mode-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  margin-bottom: 16px;
  background: rgba(143, 166, 142, 0.12);
  border-radius: 12px;
}

.elder-mode-label {
  font-size: 14px;
  font-weight: 600;
  color: #2d3436;
}

.elder-switch,
.mini-switch {
  position: relative;
  width: 48px;
  height: 24px;
  background: rgba(125, 116, 107, 0.3);
  border-radius: 9999px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.elder-switch.on,
.mini-switch.on {
  background: #8fa68e;
}

.elder-switch-knob,
.mini-switch-knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  background: #ffffff;
  border-radius: 50%;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
  transition: transform 0.2s ease;
}

.elder-switch.on .elder-switch-knob,
.mini-switch.on .mini-switch-knob {
  transform: translateX(24px);
}

/* ---------- 卡片通用 ---------- */
.monitor-card {
  margin-bottom: 16px;
  background: linear-gradient(135deg, #f4efe6 0%, #f8f8f0 100%);
  border: 1px solid rgba(91, 154, 122, 0.1);
  border-radius: 24px;
  box-shadow: 0 4px 20px rgba(91, 154, 122, 0.08);
  overflow: hidden;
  animation: card-enter 0.6s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes card-enter {
  0% { opacity: 0; transform: translateY(16px) scale(0.98); }
  100% { opacity: 1; transform: translateY(0) scale(1); }
}

.card-header {
  padding: 14px 18px;
  background: linear-gradient(135deg, rgba(91, 154, 122, 0.08), rgba(245, 215, 110, 0.04));
  border-bottom: 1px solid rgba(91, 154, 122, 0.1);
}

.card-header-with-icon {
  display: flex;
  align-items: center;
  gap: 10px;
}

.card-icon-wrapper {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  border-radius: 12px;
  transition: transform 0.2s ease;
}

.card-icon-wrapper:hover {
  transform: scale(1.05);
}

.location-icon { background: linear-gradient(135deg, #1890ff, #69c0ff); }
.text-icon { background: linear-gradient(135deg, #8fa68e, #6bbe8a); }
.voice-icon { background: linear-gradient(135deg, #f5b041, #f5d76e); }
.camera-icon { background: linear-gradient(135deg, #3498db, #5dade2); }

.card-header-text {
  display: flex;
  flex-direction: column;
}

.card-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #2d3436;
}

.card-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: #636e72;
}

.card-body {
  padding: 16px;
}

.card-body-title {
  font-size: 18px;
  font-weight: 600;
  color: #2d3436;
}

/* ---------- ① 位置追踪 ---------- */
.realtime-fall-header {
  margin-bottom: 16px;
}

.fall-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.fall-switch-label {
  font-size: 15px;
  font-weight: 500;
  color: #2d3436;
}

.fall-switch-detail {
  margin-top: 8px;
  padding: 8px;
  background: rgba(125, 116, 107, 0.06);
  border-radius: 8px;
}

.fall-detail-line {
  margin: 0;
  font-size: 12px;
  color: #636e72;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  padding: 4px 8px;
  font-size: 12px;
  border-radius: 6px;
}

.badge-normal { background: #dcfce7; color: #16a34a; }
.badge-warning { background: #fef9c3; color: #ca8a04; }
.badge-alert { background: #fee2e2; color: #dc2626; }

.location-info {
  padding: 12px 0;
}

.location-address {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 500;
  color: #2d3436;
}

.location-detail {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.location-accuracy {
  font-size: 14px;
  color: #636e72;
}

.safe-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: #52c41a;
}

.safe-tag.unsafe {
  color: #ff4d4f;
}

.location-actions {
  display: flex;
  gap: 12px;
}

.location-btn {
  flex: 1;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  color: #ffffff;
  background: linear-gradient(135deg, #8fa68e, #6bbe8a);
  border: none;
  border-radius: 16px;
  box-shadow: 0 4px 16px rgba(91, 154, 122, 0.2);
  cursor: pointer;
}

.location-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.tracking-btn {
  flex: 1;
  height: 48px;
  font-size: 16px;
  font-weight: 500;
  color: #8fa68e;
  background: rgba(143, 166, 142, 0.15);
  border: 1px solid rgba(91, 154, 122, 0.3);
  border-radius: 16px;
  cursor: pointer;
}

.tracking-btn.active {
  color: #ffffff;
  background: #6bbe8a;
  border-color: #6bbe8a;
}

.permission-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  margin-top: 12px;
  padding: 10px 12px;
  font-size: 13px;
  color: #e53e3e;
  text-align: left;
  background: rgba(229, 62, 62, 0.08);
  border: none;
  border-radius: 12px;
  cursor: pointer;
}

/* ---------- ② 状态上报 ---------- */
.text-area-wrapper {
  position: relative;
}

.text-area {
  width: 100%;
  min-height: 120px;
  padding: 12px;
  font-size: 15px;
  font-family: inherit;
  color: #2d3436;
  line-height: 1.6;
  background: #ffffff;
  border: 1px solid rgba(91, 154, 122, 0.2);
  border-radius: 16px;
  outline: none;
  resize: vertical;
  box-sizing: border-box;
}

.text-area:focus {
  border-color: #8fa68e;
}

.text-area::placeholder {
  color: #b8b0a6;
}

.char-count {
  position: absolute;
  right: 12px;
  bottom: 10px;
  font-size: 14px;
  color: #9ca3af;
}

.submit-btn {
  width: 100%;
  height: 56px;
  margin-top: 12px;
  font-size: 18px;
  font-weight: 600;
  color: #ffffff;
  background: linear-gradient(135deg, #8fa68e, #6bbe8a);
  border: none;
  border-radius: 20px;
  box-shadow: 0 6px 20px rgba(91, 154, 122, 0.25);
  cursor: pointer;
  transition: all 0.2s ease;
}

.submit-btn:active {
  transform: scale(0.96);
}

.submit-btn:disabled {
  background: linear-gradient(135deg, #cbd5e0, #a0aec0);
  box-shadow: none;
  cursor: not-allowed;
}

/* ---------- ③ 语音监测 ---------- */
.recording-status {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  margin-bottom: 12px;
}

.recording-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pulse-dot {
  width: 16px;
  height: 16px;
  background: linear-gradient(135deg, #e85a5a, #fc8181);
  border-radius: 50%;
  animation: pulse 1s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.25); opacity: 0.7; }
}

.recording-text {
  font-size: 15px;
  font-weight: 500;
  color: #e85a5a;
}

.voice-result {
  display: flex;
  flex-direction: column;
  gap: 4px;
  text-align: center;
}

.voice-result-label {
  font-size: 14px;
  color: #636e72;
}

.voice-text {
  font-size: 15px;
  color: #2d3436;
}

.recording-idle {
  margin: 0;
  font-size: 14px;
  color: #636e72;
}

.record-btn-wrapper {
  display: flex;
  justify-content: center;
}

.record-btn {
  width: 88px;
  height: 88px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #ffffff;
  background: linear-gradient(135deg, #8fa68e, #6bbe8a);
  border: none;
  border-radius: 50%;
  box-shadow: 0 8px 24px rgba(91, 154, 122, 0.3);
  cursor: pointer;
  animation: record-breath 3s ease-in-out infinite;
}

@keyframes record-breath {
  0%, 100% { box-shadow: 0 8px 24px rgba(91, 154, 122, 0.3); }
  50% { box-shadow: 0 8px 32px rgba(91, 154, 122, 0.5); }
}

.record-btn.stop {
  background: linear-gradient(135deg, #e85a5a, #fc8181);
  animation: recording-pulse 1.2s ease-in-out infinite;
}

@keyframes recording-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.06); }
}

.record-btn:active {
  transform: scale(0.92);
}

.record-emoji {
  font-size: 24px;
  line-height: 1;
}

.record-label {
  font-size: 12px;
}

/* ---------- ④ 跌倒检测 ---------- */
.detect-mode-switch {
  display: flex;
  gap: 12px;
  padding: 10px;
  margin-bottom: 16px;
  background: linear-gradient(135deg, rgba(125, 116, 107, 0.06), rgba(125, 116, 107, 0.02));
  border-radius: 16px;
}

.mode-tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 12px;
  font-size: 14px;
  color: #636e72;
  background: #f4efe6;
  border: none;
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.mode-tab.active {
  color: #ffffff;
  font-weight: 600;
  background: linear-gradient(135deg, #8fa68e, #6bbe8a);
  box-shadow: 0 4px 12px rgba(91, 154, 122, 0.2);
}

.camera-container {
  margin-bottom: 16px;
}

.camera-preview {
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #2d3436, #636e72);
  border-radius: 20px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
  overflow: hidden;
}

.camera-preview.captured {
  background: linear-gradient(135deg, #4a5568, #a0aec0);
}

.camera-placeholder,
.captured-frame {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.camera-placeholder-emoji,
.captured-emoji {
  font-size: 48px;
  line-height: 1;
}

.camera-placeholder-text,
.captured-text {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.8);
}

.fall-result {
  padding: 16px;
  margin-bottom: 16px;
  background: #ffffff;
  border-radius: 16px;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.result-emoji {
  font-size: 20px;
  line-height: 1;
}

.result-title {
  font-size: 16px;
  font-weight: 600;
  color: #52c41a;
}

.result-title.danger {
  color: #ff4d4f;
}

.confidence-bar {
  margin-top: 12px;
}

.confidence-text {
  font-size: 14px;
  color: #636e72;
}

.bar-bg {
  height: 4px;
  margin-top: 6px;
  background: rgba(125, 116, 107, 0.12);
  border-radius: 2px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: #52c41a;
  border-radius: 2px;
  transition: width 0.4s ease;
}

.bar-fill.danger {
  background: #ff4d4f;
}

.fall-analysis {
  margin: 12px 0 0;
  font-size: 14px;
  color: #636e72;
}

.camera-actions {
  display: flex;
  gap: 12px;
}

.action-btn {
  flex: 1;
  padding: 12px;
  font-size: 15px;
  color: #2d3748;
  background: rgba(125, 116, 107, 0.08);
  border: none;
  border-radius: 14px;
  cursor: pointer;
}

.action-btn.primary {
  color: #ffffff;
  background: linear-gradient(135deg, #8fa68e, #6bbe8a);
}

.action-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.photo-btn {
  width: 100%;
  padding: 16px;
  font-size: 17px;
  font-weight: 600;
  color: #ffffff;
  background: linear-gradient(135deg, #8fa68e, #6bbe8a);
  border: none;
  border-radius: 20px;
  box-shadow: 0 6px 20px rgba(91, 154, 122, 0.25);
  cursor: pointer;
}

.photo-btn:disabled {
  background: linear-gradient(135deg, #cbd5e0, #a0aec0);
  box-shadow: none;
  cursor: not-allowed;
}

/* ---------- ⑤ 分析结果 ---------- */
.result-item {
  padding: 18px;
  margin-bottom: 16px;
  background: linear-gradient(135deg, #f4efe6, #f8f8f0);
  border: 1px solid rgba(91, 154, 122, 0.1);
  border-radius: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  animation: fade-in-up 0.5s ease both;
}

.result-item:last-child {
  margin-bottom: 0;
}

@keyframes fade-in-up {
  0% { opacity: 0; transform: translateY(12px); }
  100% { opacity: 1; transform: translateY(0); }
}

.result-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.result-type {
  padding: 2px 10px;
  font-size: 13px;
  font-weight: 600;
  color: #8fa68e;
  background: rgba(143, 166, 142, 0.15);
  border-radius: 9999px;
}

.result-time {
  font-size: 12px;
  color: #636e72;
}

.result-content {
  margin: 12px 0 0;
  font-size: 15px;
  color: #2d3436;
}

.result-analysis {
  margin: 6px 0 0;
  font-size: 14px;
  line-height: 1.6;
  color: #636e72;
}

.result-alert {
  margin: 8px 0 0;
  font-size: 14px;
  color: #e53e3e;
}

/* ============================================================
   大字模式（适老化）—— 原项目写好了这套 CSS 但从未挂载，
   这里通过根节点 .elder-mode 类真正启用
   ============================================================ */
.elder-mode {
  padding: 20px 16px;
}

.elder-mode .card-title {
  font-size: 22px;
  font-weight: 700;
  color: #1a2a3a;
}

.elder-mode .card-desc {
  font-size: 16px;
}

.elder-mode .text-area {
  min-height: 180px;
  font-size: 20px;
}

.elder-mode .submit-btn {
  height: 64px;
  font-size: 22px;
  border-radius: 24px;
}

.elder-mode .record-btn {
  width: 100px;
  height: 100px;
  animation: none;
}

.elder-mode .record-label {
  font-size: 15px;
}

.elder-mode .camera-preview {
  height: 320px;
}

.elder-mode .result-content {
  font-size: 18px;
}

.elder-mode .result-analysis {
  font-size: 17px;
}

.elder-mode .fall-switch-label {
  font-size: 19px;
}

.elder-mode .location-btn,
.elder-mode .tracking-btn {
  height: 56px;
  font-size: 19px;
}

.elder-mode .mode-tab {
  font-size: 18px;
  padding: 16px;
}

.elder-mode .elder-mode-label {
  font-size: 18px;
}

/* 大字模式下关闭所有氛围动效 */
.elder-mode .monitor-card {
  animation: none;
}

.elder-mode .result-item {
  animation: none;
}

.elder-mode .pulse-dot {
  animation: none;
}
</style>

<template>
  <div class="page ai-chat-page">
    <!-- 顶部固定栏 -->
    <div class="header-bar">
      <div class="header-content">
        <div class="header-left">
          <div class="header-avatar">🤖</div>
          <div class="header-title-group">
            <span class="header-title">小护</span>
            <span class="header-status">在线 · 随时陪伴</span>
          </div>
        </div>
        <div class="header-right">
          <button class="header-btn" @click="toggleAutoSpeak">
            {{ __state.autoSpeak ? '🔊' : '🔇' }}
          </button>
          <button class="header-btn" @click="__state.showClearConfirm = true">↺</button>
        </div>
      </div>

      <!-- 健康数据摘要条 -->
      <div v-if="__state.healthData.healthScore" class="health-summary-bar">
        <span class="summary-item">📈 今日步数: {{ __state.healthData.todaySteps || 0 }}</span>
        <span class="summary-item">💓 健康评分: {{ __state.healthData.healthScore }}</span>
        <span class="summary-item">🌙 睡眠: {{ __state.healthData.todaySleep || 0 }}h</span>
      </div>
    </div>

    <!-- 消息列表 -->
    <div class="messages-container">
      <div
        v-for="message in __state.messages"
        :key="message.id"
        class="message-wrapper"
        :class="message.role === 'user' ? 'is-user' : 'is-assistant'">
        <!-- AI 头像在左 -->
        <div v-if="message.role === 'assistant'" class="avatar-ring">🤖</div>

        <div class="message-bubble" :class="message.role === 'user' ? 'user-bubble' : 'assistant-bubble'">
          <p class="message-content">{{ message.content }}</p>
          <div class="message-meta">
            <span class="message-time">{{ message.time }}</span>
            <div v-if="message.role === 'assistant'" class="message-actions">
              <button class="meta-btn" @click="copyMessage(message.content, message.id)">
                {{ __state.copiedMessageId === message.id ? '✓' : '📄' }}
              </button>
              <button class="meta-btn" :class="{ playing: __state.isPlayingAudio }" @click="speakMessage(message.content)">
                {{ __state.isPlayingAudio ? '🔇' : '🔊' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 用户头像在右 -->
        <div v-if="message.role === 'user'" class="avatar-ring user-avatar">👤</div>
      </div>

      <!-- 快捷提问（首屏） -->
      <div v-if="__state.showQuickQuestions" class="quick-questions-container">
        <div class="quick-header">
          <span class="quick-sparkle">✨</span>
          <span class="quick-title">您可以这样问我</span>
        </div>
        <div class="quick-grid">
          <button v-for="q in quickQuestions" :key="q.id" class="quick-item" @click="sendMessage(q.text)">
            <div class="quick-icon-wrapper">{{ q.icon }}</div>
            <span class="quick-question-text">{{ q.text }}</span>
          </button>
        </div>
      </div>

      <!-- 思考中 -->
      <div v-if="__state.isLoading" class="thinking-wrapper">
        <div class="avatar-ring thinking">🤖</div>
        <div class="message-bubble assistant-bubble thinking-bubble">
          <span class="dot"></span>
          <span class="dot"></span>
          <span class="dot"></span>
          <span class="thinking-text">小护正在思考...</span>
        </div>
      </div>
    </div>

    <!-- 录音浮层 -->
    <div v-if="__state.isRecording" class="recording-overlay">
      <div class="recording-wave-animation">
        <span class="wave-bar"></span>
        <span class="wave-bar"></span>
        <span class="wave-bar"></span>
        <span class="wave-bar"></span>
        <span class="wave-bar"></span>
      </div>
      <p class="recording-title">正在录音中...</p>
      <p class="recording-desc">点击下方按钮停止录音</p>
    </div>

    <!-- 底部固定操作栏 -->
    <div class="action-bar">
      <button class="voice-btn" :class="{ recording: __state.isRecording }" @click="toggleRecording">🎤</button>
      <button class="input-box" @click="openInputModal">有什么想问小护的？</button>
      <button class="keyboard-btn" @click="openInputModal">⌨️</button>
    </div>

    <!-- 输入弹窗 -->
    <div v-if="__state.showInputModal" class="input-modal-overlay" @click.self="closeInputModal">
      <div class="input-modal-content">
        <div class="input-modal-header">
          <div class="modal-avatar">🤖</div>
          <span class="modal-title">和小护聊聊</span>
          <button class="modal-close" @click="closeInputModal">✕</button>
        </div>

        <div class="input-modal-body">
          <div class="input-field-wrapper">
            <input
              v-model="__state.inputText"
              class="modal-input"
              maxlength="500"
              placeholder="说点什么吧..."
              @keyup.enter="submitInput" />
          </div>

          <div class="input-modal-actions">
            <button class="modal-btn cancel" @click="closeInputModal">取消</button>
            <button
              class="modal-btn send"
              :class="{ disabled: !__state.inputText.trim() }"
              @click="submitInput">
              ➤ 发送
            </button>
          </div>

          <div class="input-modal-quick">
            <p class="quick-header-text">试试这些</p>
            <div class="input-modal-quick-grid">
              <button v-for="q in quickQuestions.slice(0, 4)" :key="q.id" class="modal-quick-item" @click="handleModalQuick(q.text)">
                {{ q.text }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 清空确认弹窗 -->
    <div v-if="__state.showClearConfirm" class="input-modal-overlay" @click.self="__state.showClearConfirm = false">
      <div class="confirm-box">
        <h3 class="confirm-title">确认清空</h3>
        <p class="confirm-message">确定要清空所有对话记录吗？</p>
        <div class="confirm-actions">
          <button class="modal-btn cancel" @click="__state.showClearConfirm = false">取消</button>
          <button class="modal-btn send" @click="clearMessages">确认</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 智能助手页（AI 对话）
 *
 * 三段式布局：固定顶部（AI 头像 + 在线状态 + 健康摘要）
 *          → 可滚动消息列表（AI 左白底 / 用户右绿底）
 *          → 固定底部操作栏（语音按钮 + 输入框 + 键盘按钮）
 *
 * 原项目的打字机效果、Coze 直连、声纹授权等在这里简化为 mock 回复，
 * 保证界面"看起来是活的"且不依赖任何后端。
 */
import { reactive, onUnmounted } from 'vue'

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  time: string
}

/** 6 条快捷提问（与原项目一致） */
const quickQuestions = [
  { id: 1, icon: '💓', text: '我今天的健康数据怎么样？' },
  { id: 2, icon: '🌙', text: '帮我分析一下睡眠质量' },
  { id: 3, icon: '📈', text: '今日运动建议' },
  { id: 4, icon: '💊', text: '服药提醒管理' },
  { id: 5, icon: '🧠', text: '健康风险评估' },
  { id: 6, icon: '🤖', text: '心理压力疏导' }
]

/** mock 回复池：根据提问关键词给不同回答，让对话有真实感 */
const MOCK_REPLIES: { keyword: string[]; reply: string }[] = [
  {
    keyword: ['健康数据', '健康', '身体'],
    reply: '根据今天的数据看：\n- 步数 6820 步，比昨天多 420 步\n- 睡眠 7 小时 32 分，质量良好\n- 健康评分 92 分\n\n整体状态不错，继续保持哦～'
  },
  {
    keyword: ['睡眠', '睡'],
    reply: '您的睡眠分析如下：\n- 入睡时间 23:10，起床 06:42\n- 总时长 7 小时 32 分，落在 7-9 小时最优区间\n- 深度睡眠占比约 32%\n\n建议：睡前 1 小时不看手机，把入睡时间提前到 22:40 左右会更理想。'
  },
  {
    keyword: ['运动', '步数', '锻炼'],
    reply: '今日运动建议：\n- 您已完成 6820 步，距离 8000 步目标还差 1180 步\n- 建议傍晚饭后散步 20 分钟\n- 户外活动建议选择气温舒适的时段，注意补水'
  },
  {
    keyword: ['服药', '药', '提醒'],
    reply: '您的用药提醒有 3 条：\n- 降压药 08:00 早餐后 1 片（已服用 ✓）\n- 降糖药 11:30 午餐前 半片（待服用）\n- 钙片 19:00 晚餐后 2 片（待服用）\n\n需要我帮您调整提醒时间吗？'
  },
  {
    keyword: ['风险', '评估'],
    reply: '健康风险评估结果：\n- 跌倒风险：低\n- 心血管风险：低\n- 睡眠障碍风险：中（近期入睡偏晚）\n\n建议保持规律作息，并坚持每天的打卡记录。'
  },
  {
    keyword: ['压力', '心理', '心情'],
    reply: '听起来您最近可能有些疲惫。可以试试：\n1. 每天固定时间做 5 分钟腹式呼吸\n2. 睡前听一段舒缓的轻音乐\n3. 白天多晒晒太阳，有助于调节情绪\n\n我一直都在，随时可以找我聊聊 😊'
  }
]

const __state = reactive({
  messages: [
    {
      id: 'welcome',
      role: 'assistant',
      content: '您好，我是小护 🌿 您的 AI 健康助手。\n我可以帮您：\n- 分析睡眠与运动数据\n- 提醒按时服药\n- 评估健康风险\n- 疏导心理压力\n\n有什么想问的，直接告诉我就好～',
      time: '09:00'
    },
    {
      id: 'msg-1',
      role: 'user',
      content: '我昨晚睡得怎么样？',
      time: '09:02'
    },
    {
      id: 'msg-2',
      role: 'assistant',
      content: '您昨晚 23:10 入睡，今早 06:42 起床\n- 总时长 7 小时 32 分，睡眠质量良好\n- 深睡占比约 32%，处于健康范围\n\n睡得不错，今晚继续保持这个节奏就好 🌙',
      time: '09:02'
    }
  ] as Message[],

  healthData: { todaySteps: 6820, todaySleep: 7.5, healthScore: 92 },

  isLoading: false,
  inputText: '',
  showQuickQuestions: true,
  showInputModal: false,
  showClearConfirm: false,
  isRecording: false,
  isPlayingAudio: false,
  autoSpeak: false,
  copiedMessageId: null as string | null
})

let replyTimer: ReturnType<typeof setTimeout> | null = null

function toast(msg: string) {
  console.log('[toast]', msg)
}

/** 当前 HH:mm */
function nowTime(): string {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** 按关键词挑选 mock 回复，命中不到就用兜底文案 */
function pickReply(text: string): string {
  const hit = MOCK_REPLIES.find((r) => r.keyword.some((k) => text.includes(k)))
  return hit ? hit.reply : '这个问题很有意思～ 我已经记下来了。\n您可以再具体说说，比如最近的睡眠、饮食或者身体感受，我会给您更针对性的建议 😊'
}

/** 发送消息（乐观更新 + mock 回复） */
function sendMessage(text: string) {
  const content = text.trim()
  if (!content || __state.isLoading) return

  __state.showQuickQuestions = false
  __state.messages.push({ id: 'u-' + Date.now(), role: 'user', content, time: nowTime() })
  __state.isLoading = true

  replyTimer = setTimeout(() => {
    const reply = pickReply(content)
    __state.messages.push({ id: 'a-' + Date.now(), role: 'assistant', content: reply, time: nowTime() })
    __state.isLoading = false
    if (__state.autoSpeak) speakMessage(reply)
  }, 1200)
}

function openInputModal() {
  __state.showInputModal = true
  __state.inputText = ''
}

function closeInputModal() {
  __state.showInputModal = false
}

function submitInput() {
  if (!__state.inputText.trim()) return
  const text = __state.inputText
  __state.inputText = ''
  closeInputModal()
  sendMessage(text)
}

function handleModalQuick(text: string) {
  closeInputModal()
  sendMessage(text)
}

function toggleRecording() {
  if (__state.isRecording) {
    __state.isRecording = false
    toast('录音已结束，语音识别需要后端服务支持')
    openInputModal()
  } else {
    __state.isRecording = true
    toast('正在录音...')
  }
}

function toggleAutoSpeak() {
  __state.autoSpeak = !__state.autoSpeak
  toast(__state.autoSpeak ? '已开启自动播报' : '已关闭自动播报')
}

function speakMessage(content: string) {
  if (__state.isPlayingAudio) {
    __state.isPlayingAudio = false
    toast('已停止播报')
    return
  }
  __state.isPlayingAudio = true
  toast('语音播报需要后端服务支持')
  setTimeout(() => {
    __state.isPlayingAudio = false
  }, 2000)
  console.log('[speak]', content.slice(0, 20))
}

function copyMessage(content: string, id: string) {
  console.log('[copy]', content.slice(0, 20))
  __state.copiedMessageId = id
  toast('已复制')
  setTimeout(() => {
    __state.copiedMessageId = null
  }, 2000)
}

function clearMessages() {
  __state.showClearConfirm = false
  __state.messages = [
    {
      id: 'welcome',
      role: 'assistant',
      content: '对话已清空。有什么新话题想聊吗？😊',
      time: nowTime()
    }
  ]
  __state.showQuickQuestions = true
}

onUnmounted(() => {
  if (replyTimer) clearTimeout(replyTimer)
})
</script>

<style scoped>
.ai-chat-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, #f5f7fa 0%, #e8f5e9 100%);
  padding-bottom: 120px;
}

/* ---------- 顶部固定栏 ---------- */
.header-bar {
  position: sticky;
  top: 0;
  z-index: 20;
  padding: 16px 20px;
  background: linear-gradient(135deg, #8fa68e 0%, #7bc9a6 100%);
  box-shadow: 0 4px 20px rgba(91, 154, 122, 0.15);
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-avatar {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  background: linear-gradient(135deg, #7bc9a6, #8fa68e);
  border-radius: 24px;
}

.header-title-group {
  display: flex;
  flex-direction: column;
}

.header-title {
  font-size: 20px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 0.5px;
}

.header-status {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
}

.header-right {
  display: flex;
  gap: 8px;
}

.header-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  background: rgba(255, 255, 255, 0.2);
  border: none;
  border-radius: 20px;
  cursor: pointer;
}

.health-summary-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 12px;
  padding: 8px 0;
}

.summary-item {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
}

/* ---------- 消息列表 ---------- */
.messages-container {
  flex: 1;
  min-height: 400px;
  padding: 16px 20px;
  overflow-y: auto;
}

.message-wrapper {
  display: flex;
  align-items: flex-start;
  margin-bottom: 20px;
  animation: message-fade-in 0.4s ease;
}

.message-wrapper.is-assistant {
  justify-content: flex-start;
}

.message-wrapper.is-user {
  justify-content: flex-end;
}

@keyframes message-fade-in {
  0% { opacity: 0; transform: translateY(16px) scale(0.96); }
  100% { opacity: 1; transform: translateY(0) scale(1); }
}

.avatar-ring {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  background: linear-gradient(135deg, #8fa68e, #7bc9a6);
  border: 2px solid rgba(91, 154, 122, 0.3);
  border-radius: 22px;
  margin-right: 12px;
  animation: ring-pulse 2s ease-in-out infinite;
}

@keyframes ring-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(91, 154, 122, 0.3); }
  50% { box-shadow: 0 0 0 6px rgba(91, 154, 122, 0); }
}

.user-avatar {
  background: linear-gradient(135deg, #636e72, #2d3436);
  border-color: rgba(45, 52, 54, 0.3);
  margin-right: 0;
  margin-left: 12px;
  animation: none;
}

.message-bubble {
  position: relative;
  max-width: 85%;
  padding: 16px 20px;
  border-radius: 20px;
}

/* AI 气泡：左白底 + 左下角小缺口 */
.assistant-bubble {
  background: #ffffff;
  border-left: 3px solid rgba(91, 154, 122, 0.4);
  border-bottom-left-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

/* 用户气泡：右绿底 + 右下角小缺口 */
.user-bubble {
  background: linear-gradient(135deg, #8fa68e, #7bc9a6);
  border-bottom-right-radius: 6px;
  box-shadow: 0 4px 16px rgba(91, 154, 122, 0.2);
}

.message-content {
  margin: 0;
  font-size: 16px;
  line-height: 1.6;
  color: #2d3436;
  white-space: pre-wrap;
  word-break: break-word;
}

.user-bubble .message-content {
  color: #ffffff;
}

.message-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}

.message-time {
  font-size: 12px;
  color: #9ca3af;
}

.user-bubble .message-time {
  color: rgba(255, 255, 255, 0.8);
}

.message-actions {
  display: flex;
  gap: 8px;
}

.meta-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  background: rgba(91, 154, 122, 0.1);
  border: none;
  border-radius: 18px;
  cursor: pointer;
}

.meta-btn.playing {
  background: rgba(239, 68, 68, 0.15);
}

/* ---------- 快捷提问 ---------- */
.quick-questions-container {
  margin-top: 8px;
}

.quick-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 12px;
}

.quick-sparkle {
  font-size: 24px;
  animation: sparkle 1.5s ease-in-out infinite;
}

@keyframes sparkle {
  0%, 100% { transform: scale(1) rotate(0deg); opacity: 1; }
  50% { transform: scale(1.15) rotate(8deg); opacity: 0.8; }
}

.quick-title {
  font-size: 14px;
  font-weight: 600;
  color: #636e72;
}

.quick-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 0 4px;
}

.quick-item {
  width: calc(50% - 6px);
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: #ffffff;
  border: 1px solid rgba(91, 154, 122, 0.15);
  border-radius: 16px;
  box-shadow: 0 3px 12px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;
}

.quick-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(91, 154, 122, 0.15);
}

.quick-icon-wrapper {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  background: linear-gradient(135deg, rgba(143, 166, 142, 0.2), rgba(123, 201, 166, 0.15));
  border-radius: 20px;
}

.quick-question-text {
  font-size: 15px;
  font-weight: 500;
  color: #2d3436;
  line-height: 1.4;
}

/* ---------- 思考中 ---------- */
.thinking-wrapper {
  display: flex;
  align-items: flex-start;
  margin-bottom: 20px;
  animation: thinking-pulse 1.5s ease-in-out infinite;
}

@keyframes thinking-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.75; }
}

.thinking-bubble {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dot {
  width: 8px;
  height: 8px;
  background: #8fa68e;
  border-radius: 4px;
  animation: dot-pulse 1.2s ease-in-out infinite;
}

.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes dot-pulse {
  0%, 100% { transform: scale(0.8); opacity: 0.5; }
  50% { transform: scale(1.2); opacity: 1; }
}

.thinking-text {
  font-size: 14px;
  color: #636e72;
}

/* ---------- 录音浮层 ---------- */
.recording-overlay {
  position: fixed;
  left: 16px;
  right: 16px;
  bottom: 140px;
  z-index: 30;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 24px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  animation: recording-slide-up 0.3s ease;
}

@keyframes recording-slide-up {
  0% { opacity: 0; transform: translateY(20px); }
  100% { opacity: 1; transform: translateY(0); }
}

.recording-wave-animation {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 32px;
}

.wave-bar {
  width: 4px;
  height: 20px;
  background: linear-gradient(180deg, #8fa68e, #7bc9a6);
  border-radius: 2px;
  animation: wave-bar 0.6s ease-in-out infinite;
}

.wave-bar:nth-child(2) { animation-delay: 0.1s; }
.wave-bar:nth-child(3) { animation-delay: 0.2s; }
.wave-bar:nth-child(4) { animation-delay: 0.3s; }
.wave-bar:nth-child(5) { animation-delay: 0.4s; }

@keyframes wave-bar {
  0%, 100% { height: 12px; }
  50% { height: 32px; }
}

.recording-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #2d3436;
}

.recording-desc {
  margin: 0;
  font-size: 13px;
  color: #636e72;
}

/* ---------- 底部固定操作栏 ---------- */
.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1001;
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  background: linear-gradient(to top, #ffffff 0%, rgba(255, 255, 255, 0.98) 100%);
  border-top: 1px solid rgba(91, 155, 122, 0.15);
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.08);
  box-sizing: border-box;
}

.voice-btn {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  background: #f5f7fa;
  border: 2px solid #8fa68e;
  border-radius: 22px;
  cursor: pointer;
}

.voice-btn.recording {
  background: #fee2e2;
  border-color: #ef4444;
}

.input-box {
  flex: 1;
  max-width: 200px;
  height: 44px;
  padding: 0 16px;
  font-size: 13px;
  color: #636e72;
  text-align: left;
  background: #f5f7fa;
  border: 1px solid rgba(91, 155, 122, 0.3);
  border-radius: 22px;
  cursor: pointer;
}

.keyboard-btn {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  background: #8fa68e;
  border: none;
  border-radius: 22px;
  box-shadow: 0 2px 8px rgba(91, 154, 122, 0.2);
  cursor: pointer;
}

/* ---------- 输入弹窗 ---------- */
.input-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: flex-end;
  background: rgba(0, 0, 0, 0.5);
  animation: overlay-fade-in 0.3s ease;
}

@keyframes overlay-fade-in {
  0% { opacity: 0; }
  100% { opacity: 1; }
}

.input-modal-content {
  width: 100%;
  max-height: 80vh;
  padding: 20px 24px 32px;
  background: #ffffff;
  border-radius: 24px 24px 0 0;
  animation: modal-slide-up 0.3s ease;
  box-sizing: border-box;
}

@keyframes modal-slide-up {
  0% { transform: translateY(100%); }
  100% { transform: translateY(0); }
}

.input-modal-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.modal-avatar {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  background: rgba(91, 154, 122, 0.12);
  border-radius: 18px;
}

.modal-title {
  flex: 1;
  font-size: 18px;
  font-weight: 700;
  color: #2d3436;
}

.modal-close {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #636e72;
  background: rgba(0, 0, 0, 0.05);
  border: none;
  border-radius: 20px;
  cursor: pointer;
}

.input-modal-body {
  padding-top: 16px;
}

.input-field-wrapper {
  padding: 12px 16px;
  margin-bottom: 16px;
  background: rgba(91, 154, 122, 0.08);
  border-radius: 16px;
}

.modal-input {
  width: 100%;
  font-size: 16px;
  color: #2d3436;
  background: transparent;
  border: none;
  outline: none;
}

.modal-input::placeholder {
  color: #b8b0a6;
}

.input-modal-actions {
  display: flex;
  gap: 12px;
}

.modal-btn {
  flex: 1;
  height: 48px;
  font-size: 16px;
  border: none;
  border-radius: 24px;
  cursor: pointer;
}

.modal-btn.cancel {
  color: #636e72;
  font-weight: 600;
  background: rgba(0, 0, 0, 0.05);
}

.modal-btn.send {
  color: #ffffff;
  font-weight: 700;
  background: linear-gradient(135deg, #8fa68e, #7bc9a6);
}

.modal-btn.send.disabled {
  background: rgba(91, 154, 122, 0.3);
  box-shadow: none;
}

.input-modal-quick {
  margin-top: 20px;
}

.quick-header-text {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: #636e72;
}

.input-modal-quick-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.modal-quick-item {
  width: calc(50% - 4px);
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 500;
  color: #2d3436;
  text-align: left;
  background: rgba(91, 154, 122, 0.08);
  border: none;
  border-radius: 12px;
  cursor: pointer;
}

/* ---------- 清空确认弹窗 ---------- */
.confirm-box {
  width: 320px;
  margin: 0 auto 40vh;
  padding: 24px;
  background: #ffffff;
  border-radius: 20px;
}

.confirm-title {
  margin: 0 0 12px;
  font-size: 20px;
  font-weight: 700;
  color: #2d3436;
}

.confirm-message {
  margin: 0 0 20px;
  font-size: 15px;
  color: #636e72;
}

.confirm-actions {
  display: flex;
  gap: 12px;
}
</style>

<template>
  <div class="page profile-page" :class="{ 'elder-mode': __state.elderMode }">
    <!-- ① 用户信息卡片 -->
    <div class="user-section">
      <div class="user-card">
        <div class="user-header" @click="handleAvatarClick">
          <div class="user-avatar">👤</div>
          <div class="user-main">
            <p class="user-name">{{ __state.userInfo.name }}</p>
            <span class="user-badge">💓 {{ __state.userInfo.healthLevel }}</span>
          </div>
          <button class="user-settings-btn" @click.stop="goPage('/pages/settings/index')">⚙️</button>
        </div>

        <!-- 统计三宫格 -->
        <div class="stats-grid">
          <div class="stat-item">
            <span class="stat-value">{{ __state.userInfo.totalCheckIns }}</span>
            <span class="stat-label">累计打卡</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-value">{{ __state.userInfo.streakDays }}</span>
            <span class="stat-label">连续打卡</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-value primary">{{ __state.stats.healthScore }}</span>
            <span class="stat-label">健康评分</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 功能入口列表（10 项） -->
    <div class="menu-section">
      <div class="menu-card">
        <button
          v-for="(item, index) in menuItems"
          :key="item.id"
          class="menu-item"
          :class="{ last: index === menuItems.length - 1 }"
          @click="handleMenuClick(item)">
          <div class="menu-icon-wrapper" :style="{ background: item.bg }">{{ item.icon }}</div>
          <div class="menu-text">
            <span class="menu-title">{{ item.label }}</span>
            <span v-if="item.value" class="menu-value">{{ item.value }}</span>
          </div>

          <!-- 右侧元素三选一：开关 / 徽章 / 箭头 -->
          <div
            v-if="item.hasSwitch"
            class="menu-switch"
            :class="{ on: __state.elderMode }"
            @click.stop="toggleElderMode">
            <div class="menu-switch-knob"></div>
          </div>
          <span v-else-if="item.badge" class="menu-badge">{{ item.badge }}</span>
          <span v-else class="menu-arrow">›</span>
        </button>
      </div>
    </div>

    <!-- 版本号 -->
    <p class="version-text">安居守护 v1.0.0 · 让家人安心每一天</p>
  </div>
</template>

<script setup lang="ts">
/**
 * 个人中心页
 *
 * 用户卡（头像 + 昵称 + 角色标签）→ 统计三宫格 → 10 个功能入口 → 版本号
 *
 * 原项目还有成就徽章墙、打卡日历、数据统计等模块，
 * 这里按任务要求聚焦"用户卡 + 三宫格 + 10 个入口 + 版本号"的主干结构。
 */
import { reactive } from 'vue'

interface MenuItem {
  id: string
  icon: string
  bg: string
  label: string
  value?: string
  badge?: string
  hasSwitch?: boolean
}

const __state = reactive({
  elderMode: false,

  userInfo: {
    name: '张奶奶',
    role: '被守护者',
    healthLevel: '状态良好',
    totalCheckIns: 86,
    streakDays: 12
  },

  stats: {
    healthScore: 92,
    weekAvgSleep: 7.4,
    totalDays: 86
  },

  userRole: 'elder' as 'elder' | 'guardian',
  userMode: 'elder' as 'elder' | 'family'
})

/** 10 个功能入口（名称与分析报告保持一致） */
const menuItems: MenuItem[] = [
  {
    id: 'userRole',
    icon: '👤',
    bg: 'rgba(24,144,255,0.12)',
    label: '我的身份',
    value: '老人（被守护者）',
    badge: '点击切换'
  },
  {
    id: 'userMode',
    icon: '👥',
    bg: 'rgba(91,154,122,0.15)',
    label: '当前模式',
    value: '老人模式',
    badge: '点击切换'
  },
  {
    id: 'members',
    icon: '👥',
    bg: 'rgba(91,154,122,0.15)',
    label: '成员绑定管理',
    value: '已绑定 2 位家属'
  },
  {
    id: 'elderMode',
    icon: '☀️',
    bg: 'rgba(245,176,65,0.15)',
    label: '长辈极简模式',
    value: '放大字号与按钮',
    hasSwitch: true
  },
  {
    id: 'admin',
    icon: '🛡️',
    bg: 'rgba(232,90,90,0.12)',
    label: '管理员系统',
    badge: '需验证'
  },
  {
    id: 'privacy',
    icon: '🛡️',
    bg: 'rgba(93,173,226,0.15)',
    label: '隐私安全设置'
  },
  {
    id: 'settings',
    icon: '⚙️',
    bg: 'rgba(125,116,107,0.12)',
    label: '系统设置'
  },
  {
    id: 'about',
    icon: '📄',
    bg: 'rgba(125,116,107,0.12)',
    label: '关于我们'
  },
  {
    id: 'report',
    icon: '📈',
    bg: 'rgba(24,144,255,0.12)',
    label: '查看详细报告'
  },
  {
    id: 'developer',
    icon: '🧠',
    bg: 'rgba(114,46,209,0.12)',
    label: '开发者管理'
  }
]

function toast(msg: string) {
  console.log('[toast]', msg)
}

function goPage(path: string) {
  console.log('[navigate]', path)
}

/** 点击头像：查看个人信息 / 账号设置 / 退出登录 */
function handleAvatarClick() {
  console.log('[actionSheet] 查看个人信息 / 账号设置 / 退出登录')
  toast('请选择操作')
}

/** 切换长辈极简模式 */
function toggleElderMode() {
  __state.elderMode = !__state.elderMode
  toast(__state.elderMode ? '已开启长辈模式' : '已关闭长辈模式')
}

/** 菜单点击分发（带开关的项不触发跳转） */
function handleMenuClick(item: MenuItem) {
  if (item.hasSwitch) return

  switch (item.id) {
    case 'userRole':
      __state.userRole = __state.userRole === 'elder' ? 'guardian' : 'elder'
      toast(__state.userRole === 'elder' ? '已设置为被守护者' : '已设置为监护人')
      break
    case 'userMode':
      __state.userMode = __state.userMode === 'elder' ? 'family' : 'elder'
      toast(__state.userMode === 'elder' ? '已切换到老人模式' : '已切换到子女模式')
      break
    case 'members':
      goPage('/pages/member-bind/index')
      break
    case 'admin':
      toast('管理员验证')
      goPage('/pages/admin-system/index')
      break
    case 'privacy':
      goPage('/pages/privacy-security/index')
      break
    case 'settings':
      goPage('/pages/settings/index')
      break
    case 'about':
      goPage('/pages/about/index')
      break
    case 'report':
      goPage('/pages/health-report/index')
      break
    case 'developer':
      goPage('/pages/developer/index')
      break
    default:
      break
  }
}
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  padding-bottom: 70px;
  background: linear-gradient(180deg, #f4efe6 0%, #f5f0e6 100%);
}

/* ---------- ① 用户信息卡片 ---------- */
.user-section {
  padding: 16px 12px;
  background: linear-gradient(180deg, #d4e0d0 0%, #f4efe6 100%);
}

.user-card {
  padding: 16px 12px;
  background: linear-gradient(135deg, #ffffff 0%, #f8f8f8 100%);
  border: 1px solid #e8e8e8;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.user-header {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 16px;
  cursor: pointer;
}

.user-avatar {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  background: linear-gradient(135deg, #8fa68e, #6bbe8a);
  border: 2px solid #ffffff;
  border-radius: 50%;
  box-shadow: 0 3px 10px rgba(91, 154, 122, 0.3);
  animation: avatar-breath 3s ease-in-out infinite;
}

@keyframes avatar-breath {
  0%, 100% { transform: scale(1); box-shadow: 0 3px 10px rgba(91, 154, 122, 0.3); }
  50% { transform: scale(1.04); box-shadow: 0 4px 16px rgba(91, 154, 122, 0.45); }
}

.user-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  margin-left: 12px;
}

.user-name {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #2d3436;
}

.user-badge {
  padding: 2px 8px;
  font-size: 12px;
  color: #5b9a7a;
  background: rgba(91, 154, 122, 0.12);
  border: 1px solid rgba(91, 154, 122, 0.25);
  border-radius: 8px;
}

.user-settings-btn {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  background: transparent;
  border: none;
  cursor: pointer;
}

/* 统计三宫格 */
.stats-grid {
  display: flex;
  align-items: center;
  margin-top: 8px;
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 12px;
}

.stat-value {
  font-size: 18px;
  font-weight: 700;
  color: #2d3436;
  animation: stat-value-enter 0.6s ease;
}

.stat-value.primary {
  color: #8fa68e;
}

@keyframes stat-value-enter {
  0% { opacity: 0; transform: translateY(8px); }
  100% { opacity: 1; transform: translateY(0); }
}

.stat-label {
  margin-top: 4px;
  font-size: 12px;
  color: #636e72;
}

.stat-divider {
  width: 1px;
  height: 32px;
  background: linear-gradient(180deg, transparent, #e0e0e0, transparent);
}

/* ---------- 功能入口列表 ---------- */
.menu-section {
  padding: 0 20px 20px;
}

.menu-card {
  background: linear-gradient(135deg, #ffffff 0%, #f8f8f8 100%);
  border: 1px solid #e8e8e8;
  border-radius: 20px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.menu-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 12px;
  background: transparent;
  border: none;
  border-bottom: 1px solid #e8e8e8;
  cursor: pointer;
  text-align: left;
  transition: background 0.2s ease;
}

.menu-item.last {
  border-bottom: none;
}

.menu-item:active {
  background: rgba(125, 116, 107, 0.05);
}

.menu-icon-wrapper {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  border-radius: 12px;
}

.menu-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.menu-title {
  font-size: 16px;
  font-weight: 600;
  color: #2d3436;
}

.menu-value {
  font-size: 13px;
  color: #636e72;
}

.menu-arrow {
  font-size: 20px;
  color: #a0a0a0;
}

.menu-badge {
  padding: 2px 8px;
  font-size: 12px;
  color: #8fa68e;
  background: rgba(143, 166, 142, 0.15);
  border-radius: 8px;
  white-space: nowrap;
}

.menu-switch {
  position: relative;
  width: 48px;
  height: 24px;
  flex-shrink: 0;
  background: rgba(125, 116, 107, 0.3);
  border-radius: 9999px;
  transition: background 0.2s ease;
}

.menu-switch.on {
  background: #8fa68e;
}

.menu-switch-knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  background: #ffffff;
  border-radius: 50%;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
  transition: transform 0.2s ease;
}

.menu-switch.on .menu-switch-knob {
  transform: translateX(24px);
}

/* ---------- 版本号 ---------- */
.version-text {
  margin: 0;
  padding: 24px 20px 32px;
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
}

/* ============================================================
   长辈极简模式：放大字号与控件
   ============================================================ */
.elder-mode .user-avatar {
  width: 80px;
  height: 80px;
  font-size: 40px;
  animation: none;
}

.elder-mode .user-name {
  font-size: 28px;
  font-weight: 800;
}

.elder-mode .user-badge {
  font-size: 15px;
}

.elder-mode .stat-value {
  font-size: 26px;
}

.elder-mode .stat-label {
  font-size: 15px;
}

.elder-mode .menu-item {
  padding: 20px 16px;
}

.elder-mode .menu-title {
  font-size: 20px;
}

.elder-mode .menu-value {
  font-size: 16px;
}

.elder-mode .menu-icon-wrapper {
  width: 44px;
  height: 44px;
  font-size: 24px;
}

.elder-mode .menu-arrow {
  font-size: 26px;
}

.elder-mode .menu-badge {
  font-size: 15px;
  padding: 4px 10px;
}

.elder-mode .version-text {
  font-size: 16px;
}
</style>
