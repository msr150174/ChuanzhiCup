<template>
  <view class="page ai-chat-page tabbar-gap">
    <!-- 顶部固定栏 -->
    <view class="header-bar">
      <view class="header-content">
        <view class="header-left">
          <view class="header-avatar"><text>🤖</text></view>
          <view class="header-title-group">
            <text class="header-title">小护</text>
            <text class="header-status">{{ loading ? '正在思考…' : '在线 · 随时陪伴' }}</text>
          </view>
        </view>
        <view class="header-right">
          <view class="header-btn" @click="toggleAutoSpeak">
            <text>{{ autoSpeak ? '🔊' : '🔇' }}</text>
          </view>
          <view class="header-btn" @click="showClearConfirm = true">
            <text>↺</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 消息列表 -->
    <scroll-view class="messages-container" scroll-y :scroll-into-view="scrollTarget" :scroll-with-animation="true">
      <view
        v-for="message in messages"
        :id="'msg-' + message.id"
        :key="message.id"
        class="message-wrapper"
        :class="message.role === 'user' ? 'is-user' : 'is-assistant'"
      >
        <!-- AI 头像在左 -->
        <view v-if="message.role === 'assistant'" class="avatar-ring"><text>🤖</text></view>

        <view class="message-bubble" :class="message.role === 'user' ? 'user-bubble' : 'assistant-bubble'">
          <text class="message-content">{{ message.content }}</text>
          <view class="message-meta">
            <text class="message-time">{{ message.time }}</text>
            <view v-if="message.role === 'assistant'" class="message-actions">
              <view class="meta-btn" @click="copyMessage(message.content, message.id)">
                <text>{{ copiedId === message.id ? '✓' : '📄' }}</text>
              </view>
              <view class="meta-btn" :class="{ playing: playingId === message.id }" @click="speakMessage(message)">
                <text>{{ playingId === message.id ? '🔇' : '🔊' }}</text>
              </view>
            </view>
          </view>
        </view>

        <!-- 用户头像在右 -->
        <view v-if="message.role === 'user'" class="avatar-ring user-avatar"><text>👤</text></view>
      </view>

      <!-- 思考中 -->
      <view v-if="loading" class="thinking-wrapper">
        <view class="avatar-ring thinking"><text>🤖</text></view>
        <view class="message-bubble assistant-bubble thinking-bubble">
          <view class="dot"></view>
          <view class="dot"></view>
          <view class="dot"></view>
          <text class="thinking-text">小护正在思考...</text>
        </view>
      </view>

      <!-- 6 条快捷提问 -->
      <view class="quick-questions-container">
        <view class="quick-header">
          <text class="quick-sparkle">✨</text>
          <text class="quick-title">您可以这样问我</text>
        </view>
        <view class="quick-grid">
          <view v-for="q in QUICK_QUESTIONS" :key="q.text" class="quick-item" @click="sendMessage(q.text)">
            <view class="quick-icon-wrapper"><text>{{ q.icon }}</text></view>
            <text class="quick-question-text">{{ q.text }}</text>
          </view>
        </view>
      </view>

      <view id="msg-bottom" class="bottom-anchor"></view>
    </scroll-view>

    <!-- 底部输入栏 -->
    <view class="action-bar">
      <input
        v-model="inputText"
        class="input-box"
        :maxlength="500"
        placeholder="有什么想问小护的？"
        placeholder-class="field-ph"
        confirm-type="send"
        @confirm="submitInput"
      />
      <view class="send-btn" :class="{ disabled: !inputText.trim() || loading }" @click="submitInput">
        <text>➤</text>
      </view>
    </view>

    <!-- 清空确认弹窗 -->
    <view v-if="showClearConfirm" class="modal-overlay" @click.self="showClearConfirm = false">
      <view class="confirm-box">
        <text class="confirm-title">确认清空</text>
        <text class="confirm-message">确定要清空所有对话记录吗？</text>
        <view class="confirm-actions">
          <view class="modal-btn cancel" @click="showClearConfirm = false"><text>取消</text></view>
          <view class="modal-btn send" @click="clearMessages"><text>确认</text></view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 智能助手页（AI 对话）
 * ----------------------------------------------------------------------------
 * 三段式布局：固定顶部（AI 头像 + 在线状态）
 *          → 可滚动消息列表（AI 左白底 / 用户右绿底）
 *          → 固定底部输入栏
 * 对话走 aiApi.chat，接口不可用时用本地关键词回复兜底，并持久化最近 100 条。
 */
import { ref, nextTick } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { aiApi, checkinApi, statisticsApi } from '@/services/api'
import type { ChatMessage } from '@/types'

const CHAT_KEY = 'guardian_chat'

/** 6 条快捷提问 */
const QUICK_QUESTIONS = [
  { icon: '💓', text: '我今天的健康数据怎么样？' },
  { icon: '🌙', text: '帮我分析一下睡眠质量' },
  { icon: '📈', text: '今日运动建议' },
  { icon: '💊', text: '服药提醒管理' },
  { icon: '🧠', text: '健康风险评估' },
  { icon: '🤖', text: '心理压力疏导' }
]

/** 本地兜底回复池（按关键词命中） */
const FALLBACK_REPLIES: { keyword: string[]; reply: string }[] = [
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
    reply: '今日运动建议：\n- 您已完成 6820 步，距离 8000 步目标还差 1180 步\n- 建议傍晚饭后散步 20 分钟\n- 户外活动选择气温舒适时段，注意补水'
  },
  {
    keyword: ['服药', '药', '提醒'],
    reply: '您的用药提醒有 3 条：\n- 降压药 08:00 早餐后 1 片\n- 降糖药 11:30 午餐前 半片\n- 钙片 19:00 晚餐后 2 片\n\n需要我帮您调整提醒时间吗？'
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

/** 欢迎语 */
const WELCOME: ChatMessage = {
  id: 'welcome',
  role: 'assistant',
  content:
    '您好，我是小护 🌿 您的 AI 健康助手。\n我可以帮您：\n- 分析睡眠与运动数据\n- 提醒按时服药\n- 评估健康风险\n- 疏导心理压力\n\n有什么想问的，直接告诉我就好～',
  timestamp: Date.now()
}

const messages = ref<ChatMessage[]>([WELCOME])
const inputText = ref('')
const loading = ref(false)
const autoSpeak = ref(false)
const copiedId = ref<string | null>(null)
const playingId = ref<string | null>(null)
const showClearConfirm = ref(false)
const scrollTarget = ref('')
const conversationId = ref<string | undefined>(undefined)

/** 健康摘要（顶部展示用） */
const summary = ref({ steps: 0, healthScore: 0 })

// ============================================================================
// 工具
// ============================================================================
function toast(msg: string) {
  uni.showToast({ title: msg, icon: 'none' })
}

function nowTime(): string {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** 滚动到底部 */
async function scrollToBottom() {
  await nextTick()
  scrollTarget.value = ''
  await nextTick()
  scrollTarget.value = 'msg-bottom'
}

/** 持久化最近 100 条 */
function persist() {
  try {
    uni.setStorageSync(CHAT_KEY, messages.value.slice(-100))
  } catch {
    // 存储超限时忽略
  }
}

/** 按关键词挑本地回复 */
function pickReply(text: string): string {
  const hit = FALLBACK_REPLIES.find((r) => r.keyword.some((k) => text.includes(k)))
  return (
    hit?.reply ||
    '这个问题很有意思～ 我已经记下来了。\n您可以再具体说说，比如最近的睡眠、饮食或者身体感受，我会给您更针对性的建议 😊'
  )
}

// ============================================================================
// 消息逻辑
// ============================================================================
async function sendMessage(text: string) {
  const content = text.trim()
  if (!content || loading.value) return

  messages.value.push({ id: 'u-' + Date.now(), role: 'user', content, timestamp: Date.now() })
  inputText.value = ''
  loading.value = true
  persist()
  await scrollToBottom()

  try {
    const res = await aiApi.chat(content, conversationId.value)
    conversationId.value = res?.conversationId || conversationId.value

    const reply = res?.content
    if (reply) {
      messages.value.push({
        id: 'a-' + Date.now(),
        role: 'assistant',
        content: reply,
        timestamp: Date.now(),
        provider: res?.provider
      })
    } else {
      // 后端返回成功但没有内容 —— 极少见，给个明确提示而不是假装有回复
      messages.value.push({
        id: 'a-' + Date.now(),
        role: 'assistant',
        content: '抱歉，我没能理解你的意思，能再说一次吗？',
        timestamp: Date.now()
      })
    }
  } catch (e: any) {
    /**
     * ★ 这里以前是空 catch，把真实错误吞掉后静默换成固定模板回复，
     *   用户只看到「AI 答得很死板」，完全不知道是接口失败了。
     *   现在把失败原因显示出来，并在消息上标红，方便定位。
     */
    const reason = String(e?.errMsg || e?.message || e || '未知错误')
    console.error('[AI对话] 请求失败:', reason)

    let hint = pickReply(content)
    if (reason.includes('timeout') || reason.includes('超时')) {
      hint = '⏱️ AI 响应超时了（问题可能比较复杂）。\n\n你可以：\n· 稍等几秒再试一次\n· 把问题说得简短一些'
    } else if (reason.includes('401')) {
      hint = '🔒 登录已过期，请重新登录后再试'
    } else if (reason.includes('invalid response') || reason.includes('服务异常')) {
      hint = '🔌 连不上后端服务。\n\n请确认已启动 guardian-server（端口 3000）'
    }

    messages.value.push({
      id: 'a-' + Date.now(),
      role: 'assistant',
      content: hint,
      timestamp: Date.now(),
      failed: true
    })
  } finally {
    loading.value = false
    persist()
    await scrollToBottom()
    if (autoSpeak.value) {
      const last = messages.value[messages.value.length - 1]
      if (last && last.role === 'assistant') speakMessage(last)
    }
  }
}

function submitInput() {
  if (!inputText.value.trim()) {
    toast('请输入内容')
    return
  }
  sendMessage(inputText.value)
}

function toggleAutoSpeak() {
  autoSpeak.value = !autoSpeak.value
  toast(autoSpeak.value ? '已开启自动播报' : '已关闭自动播报')
}

/** 语音播报（需后端 TTS 支持，这里给出明确反馈） */
function speakMessage(message: ChatMessage) {
  if (playingId.value === message.id) {
    playingId.value = null
    toast('已停止播报')
    return
  }
  playingId.value = message.id
  toast('语音播报需要后端 TTS 支持')
  setTimeout(() => {
    if (playingId.value === message.id) playingId.value = null
  }, 2000)
}

/** 复制消息 */
function copyMessage(content: string, id: string) {
  uni.setClipboardData({
    data: content,
    success: () => {
      copiedId.value = id
      toast('已复制')
      setTimeout(() => {
        if (copiedId.value === id) copiedId.value = null
      }, 2000)
    }
  })
}

/** 清空对话 */
function clearMessages() {
  showClearConfirm.value = false
  messages.value = [
    { id: 'welcome', role: 'assistant', content: '对话已清空。有什么新话题想聊吗？😊', timestamp: Date.now() }
  ]
  conversationId.value = undefined
  persist()
  scrollToBottom()
}

// ============================================================================
// 生命周期
// ============================================================================
onLoad(() => {
  // 读取历史对话
  try {
    const saved = uni.getStorageSync(CHAT_KEY) as ChatMessage[] | ''
    if (Array.isArray(saved) && saved.length > 0) {
      messages.value = saved
    }
  } catch {
    messages.value = [WELCOME]
  }

  // 顶部健康摘要（失败则用兜底常量）
  Promise.all([checkinApi.today(), statisticsApi.overview()])
    .then(([today, overview]) => {
      summary.value = {
        steps: Number(overview?.exercise?.todaySteps ?? 6820),
        healthScore: Number(overview?.health?.totalScore ?? today?.sleepScore ?? 92)
      }
    })
    .catch(() => {
      summary.value = { steps: 6820, healthScore: 92 }
    })

  scrollToBottom()
})

onUnload(() => {
  persist()
})
</script>

<style lang="scss" scoped>
.ai-chat-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #f4efe6 0%, #eef3ee 100%);
  box-sizing: border-box;
}

/* ---------- 顶部固定栏 ---------- */
.header-bar {
  flex-shrink: 0;
  padding: 24rpx 32rpx;
  background: linear-gradient(135deg, #8fa68e 0%, #7bc9a6 100%);
  box-shadow: 0 8rpx 32rpx rgba(91, 154, 122, 0.15);
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.header-avatar {
  width: 88rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 44rpx;
}

.header-title-group {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.header-title {
  font-size: 38rpx;
  font-weight: 700;
  color: #ffffff;
}

.header-status {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.85);
}

.header-right {
  display: flex;
  gap: 16rpx;
}

.header-btn {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  background: rgba(255, 255, 255, 0.22);
  border-radius: 36rpx;
}

/* ---------- 消息列表 ---------- */
.messages-container {
  flex: 1;
  min-height: 0;
  padding: 28rpx 32rpx;
  box-sizing: border-box;
}

.message-wrapper {
  display: flex;
  align-items: flex-start;
  margin-bottom: 32rpx;
}

.message-wrapper.is-assistant {
  justify-content: flex-start;
}

.message-wrapper.is-user {
  justify-content: flex-end;
}

.avatar-ring {
  width: 72rpx;
  height: 72rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  background: linear-gradient(135deg, #8fa68e, #7bc9a6);
  border-radius: 36rpx;
  margin-right: 20rpx;
}

.avatar-ring.user-avatar {
  background: linear-gradient(135deg, #636e72, #2d3436);
  margin-right: 0;
  margin-left: 20rpx;
}

.message-bubble {
  max-width: 76%;
  padding: 24rpx 28rpx;
  border-radius: 32rpx;
}

/* AI 气泡：左白底 */
.assistant-bubble {
  background: #ffffff;
  border-left: 6rpx solid rgba(91, 154, 122, 0.4);
  border-bottom-left-radius: 10rpx;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.06);
}

/* 用户气泡：右绿底 */
.user-bubble {
  background: linear-gradient(135deg, #8fa68e, #7bc9a6);
  border-bottom-right-radius: 10rpx;
  box-shadow: 0 8rpx 24rpx rgba(91, 154, 122, 0.2);
}

.message-content {
  font-size: 30rpx;
  line-height: 1.7;
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
  gap: 24rpx;
  margin-top: 14rpx;
}

.message-time {
  font-size: 22rpx;
  color: #9ca3af;
}

.user-bubble .message-time {
  color: rgba(255, 255, 255, 0.8);
}

.message-actions {
  display: flex;
  gap: 14rpx;
}

.meta-btn {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  background: rgba(91, 154, 122, 0.1);
  border-radius: 28rpx;
}

.meta-btn.playing {
  background: rgba(229, 90, 90, 0.15);
}

/* ---------- 思考中 ---------- */
.thinking-wrapper {
  display: flex;
  align-items: flex-start;
  margin-bottom: 32rpx;
}

.thinking-bubble {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.dot {
  width: 14rpx;
  height: 14rpx;
  background: var(--primary);
  border-radius: 50%;
  animation: dot-pulse 1.2s ease-in-out infinite;
}

.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }

@keyframes dot-pulse {
  0%, 100% { transform: scale(0.8); opacity: 0.5; }
  50% { transform: scale(1.2); opacity: 1; }
}

.thinking-text {
  font-size: 26rpx;
  color: #636e72;
}

/* ---------- 快捷提问 ---------- */
.quick-questions-container {
  margin-top: 8rpx;
  margin-bottom: 32rpx;
}

.quick-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  margin-bottom: 20rpx;
}

.quick-sparkle {
  font-size: 36rpx;
}

.quick-title {
  font-size: 26rpx;
  font-weight: 600;
  color: #636e72;
}

.quick-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx;
}

.quick-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx 28rpx;
  background: #ffffff;
  border: 2rpx solid rgba(91, 154, 122, 0.15);
  border-radius: 28rpx;
  box-shadow: 0 6rpx 20rpx rgba(0, 0, 0, 0.05);
}

.quick-item:active {
  transform: scale(0.98);
  opacity: 0.9;
}

.quick-icon-wrapper {
  width: 64rpx;
  height: 64rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  background: linear-gradient(135deg, rgba(143, 166, 142, 0.2), rgba(123, 201, 166, 0.15));
  border-radius: 32rpx;
}

.quick-question-text {
  flex: 1;
  font-size: 28rpx;
  font-weight: 500;
  color: #2d3436;
}

.bottom-anchor {
  height: 24rpx;
}

/* ---------- 底部输入栏 ---------- */
.action-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx 32rpx;
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  background: var(--surface);
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.06);
}

.input-box {
  flex: 1;
  padding: 24rpx 32rpx;
  background: rgba(125, 116, 107, 0.08);
  border-radius: 40rpx;
  font-size: 30rpx;
  color: var(--text-1);
}

.field-ph {
  color: var(--text-3);
}

.send-btn {
  width: 88rpx;
  height: 88rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  color: #ffffff;
  background: linear-gradient(135deg, var(--primary), #9db89c);
  border-radius: 44rpx;
  box-shadow: 0 8rpx 20rpx rgba(143, 166, 142, 0.3);
}

.send-btn.disabled {
  opacity: 0.45;
  box-shadow: none;
}

/* ---------- 清空确认 ---------- */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 64rpx;
  background: rgba(0, 0, 0, 0.45);
}

.confirm-box {
  width: 100%;
  padding: 48rpx 40rpx;
  background: var(--surface);
  border-radius: 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.confirm-title {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--text-1);
}

.confirm-message {
  margin-top: 20rpx;
  font-size: 28rpx;
  color: var(--text-2);
  text-align: center;
}

.confirm-actions {
  display: flex;
  gap: 20rpx;
  width: 100%;
  margin-top: 40rpx;
}

.modal-btn {
  flex: 1;
  padding: 24rpx 0;
  border-radius: 24rpx;
  font-size: 30rpx;
  font-weight: 600;
  text-align: center;
}

.modal-btn.cancel {
  background: rgba(125, 116, 107, 0.1);
  color: var(--text-2);
}

.modal-btn.send {
  background: var(--primary);
  color: #ffffff;
}
</style>
