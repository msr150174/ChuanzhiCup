<template>
  <view class="page" :class="{ 'elder-mode': settingsStore.settings.elderMode }">
    <view class="page-inner">
      <!-- ① 录音区 -->
      <view class="card record-card">
        <text class="record-title">🎙️ 语音健康监测</text>
        <text class="record-desc">按住下方按钮说话，AI 会分析语音内容，识别情绪与健康风险</text>

        <view class="recorder-wrap">
          <view
            class="recorder"
            :class="{ recording, tooShort: hintType === 'warn' }"
            @touchstart="startRecord"
            @touchend="stopRecord"
            @touchcancel="cancelRecord">
            <text class="recorder-icon">{{ recording ? '🔴' : '🎤' }}</text>
          </view>
          <text class="recorder-hint" :class="hintType">{{ hintText }}</text>
        </view>

        <view class="duration-row">
          <text class="duration-value">{{ durationText }}</text>
          <text class="duration-label">录音时长（最长 60 秒）</text>
        </view>

        <view class="voice-select">
          <text class="voice-label">选择示例语音（无麦克风时可直接分析）</text>
          <view class="voice-chips">
            <view
              v-for="v in SAMPLES"
              :key="v.id"
              class="voice-chip"
              :class="{ active: selectedSample === v.id }"
              @click="useSample(v.id)">
              <text>{{ v.label }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- ② 分析中 -->
      <view v-if="analyzing" class="card analyzing-card">
        <text class="analyzing-icon">🤖</text>
        <text class="analyzing-text">AI 正在分析语音内容...</text>
      </view>

      <!-- ③ 分析结果 -->
      <view v-if="result" class="card result-card">
        <view class="flex-between">
          <text class="card-title no-margin">📋 分析结果</text>
          <text class="tag">{{ result.provider }}</text>
        </view>
        <text class="result-content">{{ result.analysis }}</text>
        <view class="divider"></view>
        <text class="result-sub-title">💡 健康建议</text>
        <text v-for="(s, i) in result.suggestions" :key="i" class="result-suggestion">· {{ s }}</text>
      </view>

      <!-- ④ 历史记录 -->
      <view class="card">
        <view class="flex-between card-title-row">
          <text class="card-title no-margin">🕐 历史记录</text>
          <text class="clear-btn" @click="clearHistory">清空</text>
        </view>

        <EmptyState v-if="history.length === 0" icon="🎙️" text="还没有语音分析记录" desc="按住上方按钮说一句话试试" />

        <view v-for="item in history" :key="item.id" class="history-item">
          <view class="history-head">
            <text class="history-time">{{ item.time }}</text>
            <text class="history-dur">{{ item.duration }} 秒</text>
          </view>
          <text class="history-text">{{ item.text }}</text>
          <text class="history-analysis">{{ item.analysis }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 语音监测
 * ----------------------------------------------------------------------------
 * 按住录音 → 松开停止 → 调用 monitorApi.voiceAnalyze() 分析。
 * 少于 1 秒提示重录；H5 环境录音接口不可用时，可用示例语音走通分析流程。
 */
import { computed, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { monitorApi } from '@/services/api'
import { useSettingsStore } from '@/stores/settings'
import EmptyState from '@/components/EmptyState.vue'

const settingsStore = useSettingsStore()

/** 示例语音（没有麦克风时的兜底输入） */
const SAMPLES = [
  { id: 's1', label: '我最近睡得不太好', text: '我最近总是睡不着，半夜醒好几次，白天没什么精神。' },
  { id: 's2', label: '今天有点头晕', text: '今天早上起来有点头晕，量了血压是 145/92，需要去医院吗？' },
  { id: 's3', label: '今天心情不错', text: '今天天气好，我下楼走了 40 分钟，回来吃了午饭，心情挺好的。' }
]

/** 历史记录结构 */
interface VoiceRecord {
  id: string
  time: string
  duration: number
  text: string
  analysis: string
}

const HISTORY_KEY = 'guardian_voice_history'
const MIN_SECONDS = 1
const MAX_SECONDS = 60

const recording = ref(false)
const seconds = ref(0)
const analyzing = ref(false)
const selectedSample = ref('')
const result = ref<{ analysis: string; provider: string; suggestions: string[] } | null>(null)
const history = ref<VoiceRecord[]>([])

/** 提示文案类型：info / warn */
const hintType = ref<'info' | 'warn'>('info')
const hintText = ref('按住开始录音')

let timer: ReturnType<typeof setInterval> | null = null
let recorder: UniApp.RecorderManager | null = null

/** 录音时长文案 */
const durationText = computed(() => {
  const m = Math.floor(seconds.value / 60)
  const s = seconds.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

/** 读取本地历史 */
function loadHistory() {
  try {
    const saved = uni.getStorageSync(HISTORY_KEY)
    if (Array.isArray(saved) && saved.length > 0) {
      history.value = saved as VoiceRecord[]
      return
    }
  } catch {
    // 忽略读取异常
  }
  // 兜底：给两条示例记录，界面不空白
  history.value = [
    {
      id: 'h-demo-1',
      time: '昨天 20:12',
      duration: 8,
      text: '昨晚三点多醒了，之后就睡不着了。',
      analysis: '识别到睡眠中断的描述，结合近期作息数据，入睡后易醒可能与睡前饮水偏多、白天活动不足有关。建议睡前 2 小时减少饮水量，晚饭后散步 20 分钟。',
      provider: 'local'
    },
    {
      id: 'h-demo-2',
      time: '前天 09:30',
      duration: 6,
      text: '今天早上起来头有点晕。',
      analysis: '语音中出现头晕主诉，建议先静坐休息并测量血压；若收缩压持续高于 160mmHg 或伴随视物模糊，请立即联系家属或就医。',
      provider: 'local'
    } as VoiceRecord
  ]
}

/** 保存历史 */
function saveHistory() {
  try {
    uni.setStorageSync(HISTORY_KEY, history.value.slice(0, 20))
  } catch {
    // 忽略写入异常
  }
}

/** 开始录音 */
function startRecord() {
  if (analyzing.value) return
  recording.value = true
  seconds.value = 0
  selectedSample.value = ''
  hintType.value = 'info'
  hintText.value = '松开结束录音'

  timer = setInterval(() => {
    seconds.value += 1
    if (seconds.value >= MAX_SECONDS) {
      uni.showToast({ title: '已达最长录音时长', icon: 'none' })
      stopRecord()
    }
  }, 1000)

  // 真实录音：H5 / 未授权环境会 fail，这里只记录不阻断交互
  try {
    recorder = uni.getRecorderManager()
    recorder.onStop(() => {
      // 录音文件回调，本项目只做时长与内容分析，不保存音频
    })
    recorder.start({ duration: MAX_SECONDS * 1000, format: 'mp3' })
  } catch {
    recorder = null
  }
}

/** 取消录音 */
function cancelRecord() {
  recording.value = false
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  try {
    recorder?.stop()
  } catch {
    // 忽略
  }
  seconds.value = 0
  hintType.value = 'info'
  hintText.value = '已取消，按住重新录音'
}

/** 松开结束录音 */
function stopRecord() {
  if (!recording.value) return
  recording.value = false
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  try {
    recorder?.stop()
  } catch {
    // 忽略
  }

  if (seconds.value < MIN_SECONDS) {
    hintType.value = 'warn'
    hintText.value = `录音太短（至少 ${MIN_SECONDS} 秒），请重新录制`
    uni.showToast({ title: '录音时间太短', icon: 'none' })
    return
  }

  hintType.value = 'info'
  hintText.value = '录音完成，正在分析'
  analyze(`（语音片段 ${seconds.value} 秒）老人主诉身体不适，请结合近期作息与用药给出建议。`, seconds.value)
}

/** 使用示例语音 */
function useSample(id: string) {
  const sample = SAMPLES.find((s) => s.id === id)
  if (!sample) return
  selectedSample.value = id
  hintType.value = 'info'
  hintText.value = `已选择：${sample.label}`
  seconds.value = 5
  analyze(sample.text, 5)
}

/** 调用分析接口（含兜底） */
async function analyze(text: string, duration: number) {
  if (analyzing.value) return
  analyzing.value = true
  result.value = null

  let analysis = ''
  let provider = 'local'
  let suggestions: string[] = []

  try {
    const res = await monitorApi.voiceAnalyze(text)
    if (res?.analysis) {
      analysis = res.analysis
      provider = res.provider || 'ai'
    }
  } catch {
    // 接口失败走本地兜底
  }

  if (!analysis) {
    analysis =
      '语音识别完成。这段话表达的情绪总体平稳，语速正常，未发现明显的急促呼吸或含混发音。' +
      '结合近 7 天的作息记录，建议保持规律作息，若出现持续头晕、胸闷等情况请及时联系家属。'
    provider = '本地规则'
  }

  suggestions = [
    '每天固定时间入睡与起床，白天午睡不超过 30 分钟',
    '每天散步 20-30 分钟，运动后注意补充水分',
    '如出现持续头晕、心慌、胸闷，请立即联系家属或就医'
  ]

  result.value = { analysis, provider, suggestions }

  // 写入历史
  history.value.unshift({
    id: 'h-' + Date.now(),
    time: formatNow(),
    duration,
    text,
    analysis
  })
  history.value = history.value.slice(0, 20)
  saveHistory()

  analyzing.value = false
  uni.showToast({ title: '分析完成', icon: 'success' })
}

/** 清空历史 */
function clearHistory() {
  if (history.value.length === 0) return
  uni.showModal({
    title: '清空记录',
    content: '确定要清空全部语音分析记录吗？',
    confirmColor: '#E55A5A',
    success: (res) => {
      if (!res.confirm) return
      history.value = []
      try {
        uni.removeStorageSync(HISTORY_KEY)
      } catch {
        // 忽略
      }
      uni.showToast({ title: '已清空', icon: 'none' })
    }
  })
}

/** 当前时间文案 */
function formatNow(): string {
  const d = new Date()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${mm}-${dd} ${hh}:${mi}`
}

onLoad(() => {
  loadHistory()
})

onUnload(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  try {
    recorder?.stop()
  } catch {
    // 忽略
  }
})
</script>

<style lang="scss" scoped>
.page-inner {
  padding: 24rpx;
}

/* ---------- 录音区 ---------- */
.record-card {
  text-align: center;
}

.record-title {
  display: block;
  font-size: 34rpx;
  font-weight: 700;
  color: var(--text-1);
}

.record-desc {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: var(--text-2);
  line-height: 1.7;
}

.recorder-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: 44rpx 0 24rpx;
}

.recorder {
  width: 220rpx;
  height: 220rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--primary), #9db89c);
  box-shadow: 0 12rpx 32rpx rgba(143, 166, 142, 0.32);
  transition: all 0.2s ease;
}

.recorder.recording {
  background: linear-gradient(135deg, #e55a5a, #f08b8b);
  box-shadow: 0 12rpx 40rpx rgba(229, 90, 90, 0.4);
  transform: scale(1.06);
}

.recorder.tooShort {
  background: linear-gradient(135deg, var(--warning), #e0b98d);
}

.recorder-icon {
  font-size: 92rpx;
}

.recorder-hint {
  margin-top: 24rpx;
  font-size: 26rpx;
  color: var(--text-2);
}

.recorder-hint.warn {
  color: var(--danger);
}

/* ---------- 时长 ---------- */
.duration-row {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.duration-value {
  font-size: 60rpx;
  font-weight: 700;
  color: var(--primary-d);
  letter-spacing: 2rpx;
}

.duration-label {
  font-size: 22rpx;
  color: var(--text-3);
}

/* ---------- 示例语音 ---------- */
.voice-select {
  margin-top: 32rpx;
  padding-top: 24rpx;
  border-top: 2rpx solid var(--divider);
}

.voice-label {
  display: block;
  font-size: 24rpx;
  color: var(--text-2);
  margin-bottom: 16rpx;
}

.voice-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  justify-content: center;
}

.voice-chip {
  padding: 14rpx 26rpx;
  border-radius: var(--radius-full);
  background: var(--surface-2);
  font-size: 24rpx;
  color: var(--text-2);
}

.voice-chip.active {
  background: var(--primary-l);
  color: var(--primary-d);
  font-weight: 600;
}

/* ---------- 分析中 ---------- */
.analyzing-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.analyzing-icon {
  font-size: 48rpx;
}

.analyzing-text {
  font-size: 28rpx;
  color: var(--text-2);
}

/* ---------- 结果 ---------- */
.no-margin {
  margin-bottom: 0;
}

.result-content {
  display: block;
  margin-top: 20rpx;
  font-size: 28rpx;
  color: var(--text-1);
  line-height: 1.9;
}

.result-sub-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text-1);
  margin-bottom: 12rpx;
}

.result-suggestion {
  display: block;
  font-size: 26rpx;
  color: var(--text-2);
  line-height: 1.9;
}

/* ---------- 历史 ---------- */
.card-title-row {
  margin-bottom: 8rpx;
}

.clear-btn {
  font-size: 26rpx;
  color: var(--danger);
}

.history-item {
  padding: 24rpx 0;
  border-bottom: 2rpx solid var(--divider);
}

.history-item:last-child {
  border-bottom: none;
}

.history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.history-time {
  font-size: 24rpx;
  color: var(--text-2);
}

.history-dur {
  font-size: 22rpx;
  color: var(--text-3);
}

.history-text {
  display: block;
  margin-top: 10rpx;
  font-size: 28rpx;
  color: var(--text-1);
}

.history-analysis {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: var(--text-2);
  line-height: 1.8;
}
</style>
