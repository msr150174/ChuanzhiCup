import type { Severity } from '@/types'

export function formatDateTime(value: string | null | undefined): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

export function roleLabel(role: string): string {
  if (role === 'ELDER') return '长辈'
  if (role === 'FAMILY') return '家属'
  return role || '—'
}

export function severityLabel(severity: Severity): string {
  const labels: Record<Severity, string> = {
    URGENT: '紧急',
    HIGH: '高',
    MEDIUM: '中',
    LOW: '低',
  }
  return labels[severity]
}

export function severityTagType(severity: Severity): 'danger' | 'warning' | 'success' | 'info' {
  const types: Record<Severity, 'danger' | 'warning' | 'success' | 'info'> = {
    URGENT: 'danger',
    HIGH: 'warning',
    MEDIUM: 'info',
    LOW: 'success',
  }
  return types[severity]
}

export function alertTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    FALL: '跌倒',
    HEART: '心率',
    SOS: '紧急呼叫',
    NO_CHECKIN: '打卡异常',
    SLEEP: '睡眠',
    DEVICE_OFFLINE: '设备离线',
    BATTERY: '低电量',
  }
  return labels[type] || type
}

export function deviceTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    SLEEP_MAT: '无感睡眠带',
    RADAR: '毫米波雷达',
    WRISTBAND: '智能手环',
    BLOOD_PRESSURE: '智能血压计',
    SOS_BUTTON: '紧急呼叫器',
  }
  return labels[type] || type
}

export function formatUptime(seconds: number): string {
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const remainSeconds = seconds % 60
  return `${days}天 ${hours}小时 ${minutes}分 ${remainSeconds}秒`
}
