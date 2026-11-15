/**
 * 业务工具函数
 */

// ============================================================================
// 健康评分
// ============================================================================

/**
 * 睡眠时长评分（移植原项目算法）
 *
 * 7-9 小时 → 95-100 分（最优）
 * 6-7 小时 → 70-90 分（一般）
 * > 9 小时 → 80 分（过长）
 * < 6 小时 → 最多 50 分（不足）
 */
export function sleepDurationScore(hours: number): number {
  if (!hours || hours <= 0) return 0
  if (hours >= 7 && hours <= 9) return Math.min(95 + (hours - 7) * 2, 100)
  if (hours >= 6 && hours < 7) return 70 + (hours - 6) * 20
  if (hours > 9) return 80
  return Math.max(50, hours * 10)
}

/** 评分等级文案 */
export function scoreLevel(score: number): string {
  if (score >= 90) return '优秀'
  if (score >= 75) return '良好'
  if (score >= 60) return '一般'
  return '需关注'
}

/** 评分对应颜色 */
export function scoreColor(score: number): string {
  if (score >= 90) return '#8FA68E'
  if (score >= 75) return '#A8C4A7'
  if (score >= 60) return '#D4A574'
  return '#E55A5A'
}

/**
 * 原始评分环（270° 量程）的 conic-gradient 字符串
 */
export function ringGradient(score: number, color = 'var(--primary)'): string {
  const deg = Math.max(0, Math.min(100, score)) * 2.7
  return `conic-gradient(${color} 0deg ${deg}deg, #E8E3D8 ${deg}deg 270deg, transparent 270deg 360deg)`
}

// ============================================================================
// 告警
// ============================================================================

/** 告警严重程度 → 颜色 */
export function severityColor(severity: string): string {
  switch (severity) {
    case 'critical': return '#E55A5A'
    case 'high': return '#E55A5A'
    case 'medium': return '#D4A574'
    default: return '#8FA68E'
  }
}

/** 告警类型 → 图标 */
export function alertIcon(type: string): string {
  switch (type) {
    case 'sos': return '🆘'
    case 'fall': return '⚠️'
    case 'abnormal': return '📈'
    case 'feedback': return '💬'
    default: return '🔔'
  }
}

// ============================================================================
// 用药
// ============================================================================

/**
 * 按小时划分用药时段
 *
 * hour < 12 → 晨起
 * hour < 18 → 午后
 * 否则      → 睡前
 */
export function medicinePeriod(hour: number): { key: string; label: string } {
  if (hour < 12) return { key: 'morning', label: '晨起' }
  if (hour < 18) return { key: 'afternoon', label: '午后' }
  return { key: 'evening', label: '睡前' }
}

/** 当前时段 */
export function currentPeriod() {
  return medicinePeriod(new Date().getHours())
}

/** 频次文案 */
export function frequencyText(freq: string): string {
  switch (freq) {
    case 'daily': return '每日一次'
    case 'twice': return '每日两次'
    case 'three_times': return '每日三次'
    case 'weekly': return '每周一次'
    default: return freq
  }
}

// ============================================================================
// 饮食
// ============================================================================

/** 餐次文案 */
export function mealText(mealType: string): string {
  switch (mealType) {
    case 'breakfast': return '早餐'
    case 'lunch': return '午餐'
    case 'dinner': return '晚餐'
    case 'snack': return '加餐'
    default: return mealType
  }
}

/** 餐次图标 */
export function mealIcon(mealType: string): string {
  switch (mealType) {
    case 'breakfast': return '🌅'
    case 'lunch': return '☀️'
    case 'dinner': return '🌙'
    default: return '🍎'
  }
}

// ============================================================================
// 运动
// ============================================================================

/** 运动强度文案 */
export function intensityText(intensity: string): string {
  switch (intensity) {
    case 'low': return '轻度'
    case 'moderate': return '中等'
    case 'high': return '高强度'
    default: return intensity
  }
}

/** 步数评价 */
export function stepComment(steps: number): string {
  if (steps >= 10000) return '运动量很充足'
  if (steps >= 6000) return '达标啦'
  if (steps >= 3000) return '还需努力'
  return '今天动得太少啦'
}

// ============================================================================
// 通用
// ============================================================================

/** 手机号脱敏（本地展示用） */
export function maskPhone(phone: string): string {
  if (!phone || phone.length < 11) return phone || ''
  return phone.substring(0, 3) + '****' + phone.substring(7)
}

/** 手机号校验 */
export function isPhone(phone: string): boolean {
  return /^1[3-9]\d{9}$/.test(phone)
}

/** 金额格式化 */
export function formatMoney(n: number | string | null | undefined): string {
  const v = Number(n || 0)
  return v.toFixed(2)
}

/**
 * Haversine 球面距离（米）
 *
 * 用于判断是否在安全区内
 */
export function distance(
  lat1: number, lng1: number,
  lat2: number, lng2: number
): number {
  const R = 6371000
  const dLat = ((lat2 - lat1) * Math.PI) / 180
  const dLng = ((lng2 - lng1) * Math.PI) / 180
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLng / 2) *
      Math.sin(dLng / 2)
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

/** 距离文案 */
export function distanceText(meters: number): string {
  if (meters < 1000) return `${Math.round(meters)}米`
  return `${(meters / 1000).toFixed(1)}公里`
}

/** 生成 UUID */
export function uuid(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}
