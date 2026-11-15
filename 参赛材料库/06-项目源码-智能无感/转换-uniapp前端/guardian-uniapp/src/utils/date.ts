/**
 * 日期时间工具
 */

/** 格式化日期为 YYYY-MM-DD */
export function formatDate(d: Date | string): string {
  const date = typeof d === 'string' ? new Date(d) : d
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 格式化日期为中文，如 2026年06月22日 */
export function formatDateCN(d: Date | string): string {
  const date = typeof d === 'string' ? new Date(d) : d
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

/** 格式化时间为 HH:mm */
export function formatTime(d: Date | string): string {
  const date = typeof d === 'string' ? new Date(d) : d
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

/** 格式化日期时间为 MM-DD HH:mm */
export function formatDateTime(d: Date | string): string {
  const date = typeof d === 'string' ? new Date(d) : d
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${m}-${day} ${formatTime(date)}`
}

/**
 * 根据当前时间返回问候语
 */
export function greeting(): string {
  const h = new Date().getHours()
  if (h < 5) return '夜深了，注意休息'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  if (h < 22) return '晚上好'
  return '夜深了，早点休息'
}

/**
 * 星期几（1-7，周一为 1）
 */
export function weekdayCN(d: Date): string {
  const names = ['日', '一', '二', '三', '四', '五', '六']
  return names[d.getDay()]
}

/**
 * 计算两个 HH:mm 之间的小时数（跨天自动 +24）
 */
export function hoursBetween(sleepTime: string, wakeTime: string): number {
  if (!sleepTime || !wakeTime) return 0
  try {
    const [sh, sm] = sleepTime.split(':').map(Number)
    const [wh, wm] = wakeTime.split(':').map(Number)
    let sleepMin = sh * 60 + sm
    let wakeMin = wh * 60 + wm
    if (wakeMin < sleepMin) wakeMin += 24 * 60
    return Math.round(((wakeMin - sleepMin) / 60) * 100) / 100
  } catch {
    return 0
  }
}

/**
 * 睡眠时长文案：7.5 → "7小时30分"
 */
export function durationText(hours: number | null): string {
  if (!hours || hours <= 0) return '—'
  const h = Math.floor(hours)
  const m = Math.round((hours - h) * 60)
  if (h === 0) return `${m}分钟`
  if (m === 0) return `${h}小时`
  return `${h}小时${m}分`
}

/**
 * 相对时间：刚刚 / 5分钟前 / 2小时前 / 3天前
 */
export function relativeTime(d: Date | string): string {
  const date = typeof d === 'string' ? new Date(d) : d
  const diff = Date.now() - date.getTime()
  const min = Math.floor(diff / 60000)

  if (min < 1) return '刚刚'
  if (min < 60) return `${min}分钟前`

  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour}小时前`

  const day = Math.floor(hour / 24)
  if (day < 30) return `${day}天前`

  return formatDate(date)
}

/**
 * 本周日期（今天前后各 3 天，共 7 天）
 *
 * ★ 注意：这不是自然周（周一到周日），是原项目的行为
 */
export function weekRange(): Array<{ date: Date; dateStr: string; label: string; isToday: boolean }> {
  const out = []
  const today = new Date()
  for (let i = -3; i <= 3; i++) {
    const d = new Date(today)
    d.setDate(today.getDate() + i)
    out.push({
      date: d,
      dateStr: formatDate(d),
      label: i === 0 ? '今天' : weekdayCN(d),
      isToday: i === 0
    })
  }
  return out
}

/** 月份天数 */
export function daysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate()
}
