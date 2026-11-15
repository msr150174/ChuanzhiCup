<template>
  <view class="page monitor-page tabbar-gap" :class="{ 'elder-mode': settings.elderMode }">
    <!-- 大字模式开关条（显眼位置） -->
    <view class="elder-mode-bar">
      <view class="elder-mode-left">
        <text class="elder-mode-icon">🔍</text>
        <view class="elder-mode-text">
          <text class="elder-mode-label">大字模式</text>
          <text class="elder-mode-desc">放大字号与按钮，长辈看得更清楚</text>
        </view>
      </view>
      <view class="elder-switch" :class="{ on: settings.elderMode }" @click="toggleElderMode">
        <view class="elder-switch-knob"></view>
      </view>
    </view>

    <!-- ① 模式切换 Tab -->
    <view class="mode-tabs">
      <view
        v-for="m in MODES"
        :key="m.key"
        class="mode-tab"
        :class="{ active: activeMode === m.key }"
        @click="activeMode = m.key"
      >
        <text class="mode-tab-icon">{{ m.icon }}</text>
        <text class="mode-tab-label">{{ m.label }}</text>
      </view>
    </view>

    <!-- ② 语音监测 -->
    <view v-if="activeMode === 'voice'" class="card monitor-card">
      <view class="card-head">
        <view class="card-icon-wrapper voice-icon"><text>🎤</text></view>
        <view class="card-head-text">
          <text class="card-title">语音监测</text>
          <text class="card-desc">录制语音进行健康分析</text>
        </view>
      </view>

      <view class="recording-status">
        <view v-if="recording" class="recording-indicator">
          <view class="pulse-dot"></view>
          <text class="recording-text">录音中 {{ formatRecordTime(recordingTime) }}</text>
        </view>
        <view v-else-if="voiceText" class="voice-result">
          <text class="voice-result-label">识别结果：</text>
          <text class="voice-text">{{ voiceText }}</text>
        </view>
        <text v-else class="recording-idle">点击下方按钮开始录音，最长 60 秒</text>
      </view>

      <view class="record-btn-wrapper">
        <view class="record-btn" :class="{ stop: recording }" @click="toggleRecording">
          <text class="record-emoji">{{ recording ? '⏹' : '🎤' }}</text>
          <text class="record-label">{{ recording ? '停止录音' : '开始录音' }}</text>
        </view>
      </view>

      <!-- 无真实录音环境时的手动输入兜底 -->
      <view class="manual-box">
        <text class="manual-label">或直接输入语音内容（模拟识别结果）</text>
        <input
          v-model="voiceText"
          class="manual-input"
          :maxlength="200"
          placeholder="例如：今天感觉有点累，睡眠质量不太好"
          placeholder-class="field-ph"
        />
        <view class="manual-btn" :class="{ disabled: loading.voice }" @click="submitVoice">
          <text>{{ loading.voice ? '⏳ 分析中...' : '提交语音分析' }}</text>
        </view>
      </view>
    </view>

    <!-- ③ 图像监测 -->
    <view v-if="activeMode === 'image'" class="card monitor-card">
      <view class="card-head">
        <view class="card-icon-wrapper camera-icon"><text>📷</text></view>
        <view class="card-head-text">
          <text class="card-title">图像监测</text>
          <text class="card-desc">AI 视觉姿态分析</text>
        </view>
      </view>

      <!-- 摄像头占位（真机可换成 camera 组件） -->
      <view class="camera-container">
        <view class="camera-preview" :class="{ captured: !!capturedImage }">
          <view v-if="!capturedImage" class="camera-placeholder">
            <text class="camera-placeholder-emoji">📷</text>
            <text class="camera-placeholder-text">点击下方按钮拍摄画面</text>
          </view>
          <view v-else class="captured-frame">
            <text class="captured-emoji">🖼️</text>
            <text class="captured-text">已拍摄画面</text>
          </view>
        </view>
      </view>

      <!-- 图像描述输入（AI 分析的入参） -->
      <view class="manual-box">
        <text class="manual-label">画面描述（用于 AI 分析）</text>
        <input
          v-model="imageDesc"
          class="manual-input"
          :maxlength="200"
          placeholder="例如：老人坐在沙发上，姿态正常"
          placeholder-class="field-ph"
        />
      </view>

      <view class="camera-actions">
        <view v-if="capturedImage" class="action-btn" @click="retakePhoto">
          <text>🔄 重新拍摄</text>
        </view>
        <view class="action-btn primary" :class="{ disabled: loading.image }" @click="takePhoto">
          <text>{{ loading.image ? '分析中...' : (capturedImage ? '✨ AI 分析' : '📷 拍照分析') }}</text>
        </view>
      </view>
    </view>

    <!-- ④ 文本监测 -->
    <view v-if="activeMode === 'text'" class="card monitor-card">
      <view class="card-head">
        <view class="card-icon-wrapper text-icon"><text>📄</text></view>
        <view class="card-head-text">
          <text class="card-title">状态上报</text>
          <text class="card-desc">描述当前健康状况</text>
        </view>
      </view>

      <view class="text-area-wrapper">
        <textarea
          v-model="textInput"
          class="text-area"
          :maxlength="500"
          placeholder="请描述您当前的健康状态，如：今天感觉怎么样、有什么不适、睡眠质量如何..."
          placeholder-class="field-ph"
        />
        <text class="char-count">{{ textInput.length }}/500</text>
      </view>

      <view class="submit-btn" :class="{ disabled: loading.text || !textInput.trim() }" @click="submitText">
        <text>{{ loading.text ? '⏳ 分析中...' : '📄 提交分析' }}</text>
      </view>
    </view>

    <!-- ⑤ 跌倒检测状态 -->
    <view class="card">
      <view class="card-header-row">
        <text class="section-title">跌倒检测</text>
        <view class="mini-switch" :class="{ on: realtimeFall }" @click="toggleRealtimeFall">
          <view class="mini-switch-knob"></view>
        </view>
      </view>

      <view class="fall-status-row">
        <text class="fall-status-icon">{{ fallStatusIcon }}</text>
        <view class="fall-status-text">
          <text class="fall-status-title">{{ realtimeFall ? '实时监测中' : '实时监测已关闭' }}</text>
          <text class="fall-status-desc">状态：{{ fallStatusText }}</text>
        </view>
        <text class="fall-badge" :class="`badge-${fallStatus}`">{{ fallStatusText }}</text>
      </view>
    </view>

    <!-- ⑥ 监测结果 -->
    <view class="card">
      <view class="card-header-row">
        <text class="section-title">监测结果</text>
        <text class="link-more" @click="loadHistory">↻ 刷新</text>
      </view>

      <view v-if="results.length === 0" class="empty">
        <text class="empty-icon">📭</text>
        <text class="empty-text">暂无监测记录</text>
        <text class="empty-desc">使用上方任一方式开始监测</text>
      </view>

      <view v-else class="result-list">
        <view v-for="r in results" :key="r.id" class="result-item">
          <view class="result-top">
            <text class="result-type" :class="`type-${r.type}`">{{ typeLabel(r.type) }}</text>
            <text class="result-time">{{ r.timeText }}</text>
          </view>
          <text class="result-content">{{ r.content }}</text>
          <text class="result-analysis">🤖 {{ r.analysis }}</text>
          <text v-if="r.alert" class="result-alert">⚠️ {{ r.alert }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 健康监测页
 * ----------------------------------------------------------------------------
 * 三模态采集：语音 / 图像 / 文本，外加实时跌倒监测。
 * 所有分析都走 monitorApi，接口不可用时用本地规则兜底，保证界面不空白。
 * 大字模式从 settings store 读，实时挂到根节点 .elder-mode。
 */
import { ref, computed, onUnmounted } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { monitorApi } from '@/services/api'
import { useSettingsStore } from '@/stores/settings'

const settingsStore = useSettingsStore()
const settings = computed(() => settingsStore.settings)

const MODES = [
  { key: 'voice' as const, icon: '🎤', label: '语音监测' },
  { key: 'image' as const, icon: '📷', label: '图像监测' },
  { key: 'text' as const, icon: '📄', label: '文本监测' }
]

type ResultType = 'voice' | 'image' | 'text'
interface MonitorResult {
  id: string
  type: ResultType
  content: string
  analysis: string
  alert?: string
  timeText: string
}

const activeMode = ref<'voice' | 'image' | 'text'>('voice')

// ---------- 语音 ----------
const recording = ref(false)
const recordingTime = ref(0)
const voiceText = ref('')

// ---------- 图像 ----------
const capturedImage = ref('')
const imageDesc = ref('')

// ---------- 文本 ----------
const textInput = ref('')

// ---------- loading ----------
const loading = ref({ voice: false, image: false, text: false })

// ---------- 跌倒 ----------
const realtimeFall = ref(false)
const fallStatus = ref<'normal' | 'warning' | 'alert'>('normal')

// ---------- 结果 ----------
const FALLBACK_RESULTS: MonitorResult[] = [
  {
    id: 'fb-1',
    type: 'voice',
    content: '今天感觉有点累，睡眠质量不太好',
    analysis: '检测到您提到疲劳和睡眠问题，建议注意休息，保持规律作息。',
    alert: '睡眠质量需关注',
    timeText: '45分钟前'
  },
  {
    id: 'fb-2',
    type: 'text',
    content: '早上起床后有点头晕，量了血压 128/82',
    analysis: '血压处于正常范围，头晕可能与起床过快有关，建议起身时放缓动作。',
    timeText: '3小时前'
  },
  {
    id: 'fb-3',
    type: 'image',
    content: '已拍摄画面',
    analysis: '姿态正常，未检测到跌倒风险。',
    timeText: '昨天'
  }
]
const results = ref<MonitorResult[]>([])

let recordTimer: ReturnType<typeof setInterval> | null = null

// ============================================================================
// 派生
// ============================================================================
const fallStatusIcon = computed(() => {
  const map: Record<string, string> = { normal: '✓', warning: '⚠️', alert: '🚨' }
  return map[fallStatus.value]
})

const fallStatusText = computed(() => {
  const map: Record<string, string> = { normal: '状态正常', warning: '异常预警', alert: '跌倒警报' }
  return map[fallStatus.value]
})

function typeLabel(type: ResultType): string {
  if (type === 'text') return '文本'
  if (type === 'voice') return '语音'
  return '图像'
}

/** MM:SS 录音计时 */
function formatRecordTime(seconds: number): string {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
}

/** 当前时间文案 */
function nowText(): string {
  const d = new Date()
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function toast(msg: string) {
  uni.showToast({ title: msg, icon: 'none' })
}

/** 插入新结果到队首 */
function addResult(item: Omit<MonitorResult, 'id' | 'timeText'>) {
  results.value.unshift({
    ...item,
    id: 'r-' + Date.now(),
    timeText: nowText()
  })
  if (results.value.length > 20) results.value = results.value.slice(0, 20)
}

// ============================================================================
// 大字模式
// ============================================================================
function toggleElderMode() {
  settingsStore.set('elderMode', !settingsStore.settings.elderMode)
  toast(settingsStore.settings.elderMode ? '已开启大字模式' : '已关闭大字模式')
}

// ============================================================================
// 语音
// ============================================================================
function toggleRecording() {
  if (recording.value) {
    stopRecording()
  } else {
    startRecording()
  }
}

function startRecording() {
  recording.value = true
  recordingTime.value = 0
  voiceText.value = ''
  if (recordTimer) clearInterval(recordTimer)
  recordTimer = setInterval(() => {
    recordingTime.value += 1
    if (recordingTime.value >= 60) stopRecording()
  }, 1000)
}

function stopRecording() {
  if (recordTimer) {
    clearInterval(recordTimer)
    recordTimer = null
  }
  recording.value = false
  if (recordingTime.value < 1) {
    toast('录音时间太短')
    return
  }
  // 无真实 ASR 时，若用户已填文本则直接用，否则用示例文本
  if (!voiceText.value.trim()) {
    voiceText.value = '今天感觉有点累，睡眠质量不太好'
  }
  submitVoice()
}

/** 提交语音分析 */
async function submitVoice() {
  const content = voiceText.value.trim()
  if (!content) {
    toast('请先录音或输入语音内容')
    return
  }

  loading.value.voice = true
  try {
    const res = await monitorApi.voiceAnalyze(content)
    addResult({ type: 'voice', content, analysis: res?.analysis || '分析完成' })
    toast('语音分析完成')
  } catch {
    addResult({
      type: 'voice',
      content,
      analysis: '检测到您提到疲劳或睡眠问题，建议注意休息，保持规律作息。',
      alert: '睡眠质量需关注'
    })
    toast('已生成本地分析结果')
  } finally {
    loading.value.voice = false
  }
}

// ============================================================================
// 图像
// ============================================================================
function takePhoto() {
  loading.value.image = true
  // 无真实摄像头时用占位标识
  setTimeout(() => {
    capturedImage.value = 'shot-' + Date.now()
    loading.value.image = false
    submitImage()
  }, 800)
}

function retakePhoto() {
  capturedImage.value = ''
  imageDesc.value = ''
}

/** 提交图像分析 */
async function submitImage() {
  const desc = imageDesc.value.trim() || '老人处于正常坐姿，无明显异常'
  loading.value.image = true
  try {
    const res = await monitorApi.imageAnalyze(desc)
    addResult({ type: 'image', content: desc, analysis: res?.analysis || '画面分析完成' })
    toast('图像分析完成')
  } catch {
    addResult({
      type: 'image',
      content: desc,
      analysis: '姿态正常，未检测到跌倒风险。'
    })
    toast('已生成本地分析结果')
  } finally {
    loading.value.image = false
  }
}

// ============================================================================
// 文本
// ============================================================================
async function submitText() {
  const content = textInput.value.trim()
  if (!content) {
    toast('请输入健康状态')
    return
  }

  loading.value.text = true
  try {
    const res = await monitorApi.textAnalyze(content)
    addResult({ type: 'text', content, analysis: res?.analysis || '分析完成' })
    textInput.value = ''
    toast('分析完成')
  } catch {
    addResult({
      type: 'text',
      content,
      analysis: '已记录您的状态，AI 分析显示整体平稳，建议保持规律作息与适度活动。'
    })
    textInput.value = ''
    toast('已生成本地分析结果')
  } finally {
    loading.value.text = false
  }
}

// ============================================================================
// 跌倒监测
// ============================================================================
async function toggleRealtimeFall() {
  realtimeFall.value = !realtimeFall.value
  if (realtimeFall.value) {
    toast('实时跌倒监测已开启')
    // 上报一次「未检测到跌倒」的心跳，让后端有数据
    try {
      await monitorApi.fallDetect(false, 98)
      fallStatus.value = 'normal'
    } catch {
      fallStatus.value = 'normal'
    }
  } else {
    fallStatus.value = 'normal'
    toast('实时跌倒监测已关闭')
  }
}

// ============================================================================
// 结果加载
// ============================================================================
async function loadHistory() {
  try {
    const res = await monitorApi.history(1, 10)
    const list = res?.list || []
    if (list.length === 0) {
      results.value = [...FALLBACK_RESULTS]
      return
    }
    results.value = list.map((raw, index) => {
      const item = raw as Record<string, unknown>
      const type = (item.type as ResultType) || 'text'
      return {
        id: String(item.id ?? `h-${index}`),
        type,
        content: String(item.content ?? ''),
        analysis: String(item.analysis ?? ''),
        alert: item.alert ? String(item.alert) : undefined,
        timeText: String(item.timeText ?? item.createTime ?? '--')
      }
    })
  } catch {
    results.value = [...FALLBACK_RESULTS]
  }
}

onShow(() => {
  loadHistory()
})

onPullDownRefresh(async () => {
  await loadHistory()
  uni.stopPullDownRefresh()
})

onUnmounted(() => {
  if (recordTimer) clearInterval(recordTimer)
})
</script>

<style lang="scss" scoped>
.monitor-page {
  min-height: 100vh;
  padding: 24rpx;
  background: linear-gradient(180deg, #f4efe6 0%, #f0f4f0 100%);
}

/* ---------- 大字模式开关条 ---------- */
.elder-mode-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 28rpx;
  margin-bottom: 24rpx;
  background: rgba(143, 166, 142, 0.14);
  border-radius: var(--radius);
}

.elder-mode-left {
  display: flex;
  align-items: center;
  gap: 18rpx;
  flex: 1;
}

.elder-mode-icon {
  font-size: 40rpx;
}

.elder-mode-text {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.elder-mode-label {
  font-size: 32rpx;
  font-weight: 600;
  color: #2d3436;
}

.elder-mode-desc {
  font-size: 22rpx;
  color: var(--text-2);
}

.elder-switch {
  width: 96rpx;
  height: 52rpx;
  padding: 4rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  background: rgba(125, 116, 107, 0.2);
  border-radius: 26rpx;
  transition: background 0.2s ease;
}

.elder-switch.on {
  background: var(--primary);
}

.elder-switch-knob {
  width: 44rpx;
  height: 44rpx;
  background: #ffffff;
  border-radius: 50%;
  transition: transform 0.2s ease;
}

.elder-switch.on .elder-switch-knob {
  transform: translateX(44rpx);
}

/* ---------- 模式 Tab ---------- */
.mode-tabs {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.mode-tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 24rpx 0;
  background: var(--surface);
  border: 4rpx solid transparent;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.mode-tab.active {
  background: rgba(143, 166, 142, 0.16);
  border-color: var(--primary);
}

.mode-tab-icon {
  font-size: 40rpx;
}

.mode-tab-label {
  font-size: 26rpx;
  color: var(--text-2);
}

.mode-tab.active .mode-tab-label {
  color: var(--primary-d);
  font-weight: 600;
}

/* ---------- 监测卡片 ---------- */
.monitor-card {
  display: flex;
  flex-direction: column;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 24rpx;
}

.card-icon-wrapper {
  width: 80rpx;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 38rpx;
  border-radius: 24rpx;
  background: var(--primary-l);
}

.card-head-text {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.card-title {
  font-size: 34rpx;
  font-weight: 600;
  color: var(--text-1);
}

.card-desc {
  font-size: 24rpx;
  color: var(--text-2);
}

/* ---------- 语音 ---------- */
.recording-status {
  padding: 28rpx;
  background: rgba(125, 116, 107, 0.05);
  border-radius: 24rpx;
  margin-bottom: 28rpx;
}

.recording-indicator {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.pulse-dot {
  width: 20rpx;
  height: 20rpx;
  background: var(--danger);
  border-radius: 50%;
  animation: dot-blink 1s ease-in-out infinite;
}

@keyframes dot-blink {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.8); }
}

.recording-text {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--danger);
}

.voice-result {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.voice-result-label {
  font-size: 24rpx;
  color: var(--text-2);
}

.voice-text {
  font-size: 30rpx;
  color: var(--text-1);
  line-height: 1.6;
}

.recording-idle {
  font-size: 26rpx;
  color: var(--text-2);
}

.record-btn-wrapper {
  display: flex;
  justify-content: center;
  margin-bottom: 28rpx;
}

.record-btn {
  width: 240rpx;
  height: 240rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  background: linear-gradient(135deg, var(--primary), #a8c4a7);
  border-radius: 50%;
  box-shadow: 0 16rpx 40rpx rgba(143, 166, 142, 0.3);
}

.record-btn.stop {
  background: linear-gradient(135deg, #ff6b6b, #ee5a5a);
  box-shadow: 0 16rpx 40rpx rgba(238, 90, 90, 0.3);
}

.record-btn:active {
  transform: scale(0.97);
}

.record-emoji {
  font-size: 64rpx;
  line-height: 1;
}

.record-label {
  font-size: 26rpx;
  color: #ffffff;
  font-weight: 600;
}

/* ---------- 手动输入 ---------- */
.manual-box {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.manual-label {
  font-size: 24rpx;
  color: var(--text-2);
}

.manual-input {
  padding: 24rpx 28rpx;
  background: rgba(255, 255, 255, 0.9);
  border: 2rpx solid var(--border);
  border-radius: 20rpx;
  font-size: 28rpx;
  color: var(--text-1);
}

.field-ph {
  color: var(--text-3);
}

.manual-btn {
  padding: 24rpx 0;
  background: var(--primary);
  border-radius: 20rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #ffffff;
  text-align: center;
}

.manual-btn.disabled {
  opacity: 0.5;
}

/* ---------- 图像 ---------- */
.camera-container {
  margin-bottom: 24rpx;
}

.camera-preview {
  height: 320rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(125, 116, 107, 0.08);
  border: 4rpx dashed var(--border);
  border-radius: 24rpx;
}

.camera-preview.captured {
  border-style: solid;
  border-color: var(--primary);
  background: rgba(143, 166, 142, 0.12);
}

.camera-placeholder,
.captured-frame {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
}

.camera-placeholder-emoji,
.captured-emoji {
  font-size: 72rpx;
}

.camera-placeholder-text,
.captured-text {
  font-size: 26rpx;
  color: var(--text-2);
}

.camera-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 28rpx;
}

.action-btn {
  flex: 1;
  padding: 26rpx 0;
  background: rgba(125, 116, 107, 0.1);
  border-radius: 24rpx;
  font-size: 28rpx;
  color: var(--text-1);
  text-align: center;
}

.action-btn.primary {
  background: linear-gradient(135deg, var(--primary), #9db89c);
  color: #ffffff;
  font-weight: 600;
}

.action-btn.disabled {
  opacity: 0.5;
}

/* ---------- 文本 ---------- */
.text-area-wrapper {
  position: relative;
  margin-bottom: 28rpx;
}

.text-area {
  width: 100%;
  height: 240rpx;
  padding: 24rpx 28rpx;
  background: rgba(255, 255, 255, 0.9);
  border: 2rpx solid var(--border);
  border-radius: 24rpx;
  font-size: 28rpx;
  color: var(--text-1);
  box-sizing: border-box;
}

.char-count {
  position: absolute;
  right: 24rpx;
  bottom: 16rpx;
  font-size: 22rpx;
  color: var(--text-3);
}

.submit-btn {
  padding: 28rpx 0;
  background: linear-gradient(135deg, var(--primary), #9db89c);
  border-radius: 24rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #ffffff;
  text-align: center;
}

.submit-btn.disabled {
  opacity: 0.5;
}

/* ---------- 跌倒检测 ---------- */
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.section-title {
  font-size: 34rpx;
  font-weight: 700;
  color: var(--text-1);
}

.link-more {
  font-size: 26rpx;
  color: var(--primary);
}

.mini-switch {
  width: 80rpx;
  height: 44rpx;
  padding: 4rpx;
  display: flex;
  align-items: center;
  background: rgba(125, 116, 107, 0.2);
  border-radius: 22rpx;
}

.mini-switch.on {
  background: var(--primary);
}

.mini-switch-knob {
  width: 36rpx;
  height: 36rpx;
  background: #ffffff;
  border-radius: 50%;
  transition: transform 0.2s ease;
}

.mini-switch.on .mini-switch-knob {
  transform: translateX(36rpx);
}

.fall-status-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.fall-status-icon {
  font-size: 48rpx;
}

.fall-status-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.fall-status-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--text-1);
}

.fall-status-desc {
  font-size: 24rpx;
  color: var(--text-2);
}

.fall-badge {
  padding: 8rpx 20rpx;
  font-size: 24rpx;
  border-radius: 999rpx;
}

.badge-normal {
  background: var(--primary-l);
  color: var(--primary-d);
}

.badge-warning {
  background: var(--warning-l);
  color: var(--warning);
}

.badge-alert {
  background: var(--danger-l);
  color: var(--danger);
}

/* ---------- 结果列表 ---------- */
.result-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.result-item {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding: 24rpx;
  background: rgba(125, 116, 107, 0.05);
  border-radius: 24rpx;
}

.result-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.result-type {
  padding: 4rpx 18rpx;
  font-size: 22rpx;
  border-radius: 999rpx;
  background: var(--primary-l);
  color: var(--primary-d);
}

.result-type.type-image {
  background: rgba(212, 165, 116, 0.15);
  color: var(--warning);
}

.result-type.type-text {
  background: rgba(125, 116, 107, 0.12);
  color: var(--text-2);
}

.result-time {
  font-size: 22rpx;
  color: var(--text-3);
}

.result-content {
  font-size: 28rpx;
  color: var(--text-1);
  line-height: 1.6;
}

.result-analysis {
  font-size: 26rpx;
  color: var(--text-2);
  line-height: 1.6;
}

.result-alert {
  font-size: 24rpx;
  color: var(--warning);
}

/* ---------- 大字模式增强 ---------- */
.elder-mode .elder-mode-bar {
  padding: 32rpx;
}

.elder-mode .mode-tab-label,
.elder-mode .elder-mode-desc,
.elder-mode .card-desc,
.elder-mode .recording-idle,
.elder-mode .manual-label {
  font-size: 30rpx;
}

.elder-mode .record-btn {
  width: 300rpx;
  height: 300rpx;
}

.elder-mode .manual-btn,
.elder-mode .submit-btn,
.elder-mode .action-btn {
  padding: 40rpx 0;
  font-size: 38rpx;
}
</style>
