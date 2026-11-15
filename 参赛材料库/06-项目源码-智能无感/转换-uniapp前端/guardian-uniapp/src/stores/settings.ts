/**
 * 设置状态管理
 *
 * 管理大字模式、提醒开关等本地设置，用 uni.setStorageSync 持久化。
 */
import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

const SETTINGS_KEY = 'guardian_settings'

interface Settings {
  /** 大字模式（适老化） */
  elderMode: boolean
  /** 打卡提醒 */
  checkinReminder: boolean
  /** 用药提醒 */
  medicineReminder: boolean
  /** 告警推送 */
  alertPush: boolean
  /** 语音播报 */
  voiceBroadcast: boolean
  /** 数据可见范围 */
  dataScope: 'all' | 'health' | 'location'
}

const DEFAULT: Settings = {
  elderMode: false,
  checkinReminder: true,
  medicineReminder: true,
  alertPush: true,
  voiceBroadcast: false,
  dataScope: 'all'
}

export const useSettingsStore = defineStore('settings', () => {
  const saved = (uni.getStorageSync(SETTINGS_KEY) || {}) as Partial<Settings>
  const settings = ref<Settings>({ ...DEFAULT, ...saved })

  // 自动持久化
  watch(
    settings,
    (val) => {
      uni.setStorageSync(SETTINGS_KEY, val)
    },
    { deep: true }
  )

  function toggle(key: keyof Settings) {
    const cur = settings.value[key]
    if (typeof cur === 'boolean') {
      // @ts-expect-error 动态键赋值
      settings.value[key] = !cur
    }
  }

  function set<K extends keyof Settings>(key: K, value: Settings[K]) {
    settings.value[key] = value
  }

  function reset() {
    settings.value = { ...DEFAULT }
  }

  return { settings, toggle, set, reset }
})
