<script setup lang="ts">
import { Lock, Message, User } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const sending = ref(false)
const submitting = ref(false)
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const form = reactive({
  phone: '',
  code: '',
})

const phoneValid = computed(() => /^1[3-9]\d{9}$/.test(form.phone))

const rules: FormRules<typeof form> = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的 11 位手机号', trigger: ['blur', 'input'] },
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码为 6 位数字', trigger: 'blur' },
  ],
}

function startCountdown() {
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

async function sendCode() {
  if (!phoneValid.value) {
    ElMessage.warning('请先输入正确的手机号')
    return
  }

  sending.value = true
  try {
    await authApi.sendCode(form.phone)
    ElMessage.success('验证码已发送，开发模式下可查看后端控制台')
    startCountdown()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    sending.value = false
  }
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const result = await authApi.login(form.phone, form.code)
    if (!result.user.isAdmin) {
      userStore.logout()
      ElMessage.error('该账号不是管理员')
      return
    }

    userStore.setSession(result)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.push(redirect.startsWith('/') ? redirect : '/dashboard')
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-card">
      <div class="login-logo">🌿</div>
      <h1>AI 节律守护</h1>
      <p class="login-subtitle">运营管理后台</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="handleLogin">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" :prefix-icon="User" placeholder="请输入管理员手机号" size="large" clearable />
        </el-form-item>
        <el-form-item label="验证码" prop="code">
          <div class="code-row">
            <el-input v-model="form.code" :prefix-icon="Lock" placeholder="请输入 6 位验证码" size="large" maxlength="6" />
            <el-button
              type="primary"
              size="large"
              :loading="sending"
              :disabled="!phoneValid || countdown > 0"
              @click="sendCode"
            >
              {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" class="login-button" :loading="submitting" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <p class="login-note">
        <el-icon><Message /></el-icon>
        仅管理员可登录，非管理员账号会被拒绝
      </p>
    </section>

    <section class="brand-panel">
      <div class="brand-mark">🌿</div>
      <h2>让每一次健康守护<br />都安静而可靠</h2>
      <p>聚焦长辈节律、设备状态与风险告警，帮助运营人员快速响应。</p>
      <div class="brand-features">
        <span>温暖清爽</span>
        <span>实时告警</span>
        <span>设备联动</span>
      </div>
    </section>
  </main>
</template>
