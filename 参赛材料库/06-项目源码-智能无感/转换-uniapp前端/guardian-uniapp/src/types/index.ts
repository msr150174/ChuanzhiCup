/**
 * 全局类型定义
 */

// ============================================================================
// 通用
// ============================================================================
export interface PageResult<T> {
  list: T[]
  total: number
  page?: number
  pageSize?: number
  hasMore?: boolean
}

// ============================================================================
// 用户
// ============================================================================
export interface UserInfo {
  id: string
  phone: string
  name: string
  avatar?: string
  /** elder 长辈 / family 家属 */
  role: 'elder' | 'family'
  gender?: string
  birthDate?: string
  address?: string
  emergencyPhone?: string
  isActive?: boolean
  createTime?: string
}

// ============================================================================
// 打卡
// ============================================================================
export interface CheckinRecord {
  id?: number
  date: string
  sleepTime: string | null
  wakeTime: string | null
  duration: number | null
  sleepScore: number | null
  wakeScore?: number | null
  /** none 未打卡 / sleeping 已睡未起 / done 已完成 */
  status?: 'none' | 'sleeping' | 'done'
}

export interface TodayCheckin {
  date: string
  sleepTime: string | null
  wakeTime: string | null
  sleepScore: number | null
  duration: number | null
  status: 'none' | 'sleeping' | 'done'
}

export interface CheckinStats {
  continuousDays: number
  weekDays: number
  totalDays: number
  weekTarget: number
}

// ============================================================================
// 健康评分
// ============================================================================
export interface HealthScore {
  userId?: string
  date?: string
  totalScore: number
  level: string
  suggestion: string
  sleepScore?: number
  medicineScore?: number
  exerciseScore?: number
  dietScore?: number
  monitorScore?: number
}

// ============================================================================
// 告警
// ============================================================================
export interface AlertItem {
  id: string
  type: string
  typeText: string
  severity: 'critical' | 'high' | 'medium' | 'low'
  severityText: string
  title: string
  description?: string
  occurredAt: string
  handled: boolean
  handledAt?: string
  notes?: string
}

// ============================================================================
// 设备
// ============================================================================
export interface DeviceItem {
  id: string
  name: string
  type: string
  status: string
  statusText: string
  lastSync?: string
  /** 电量（前端展示用，后端可扩展） */
  battery?: number
}

// ============================================================================
// 长辈状态
// ============================================================================
export interface GuardianStatus {
  userId: string
  name: string
  avatar?: string
  elderStatus: {
    code: 'sleeping' | 'awake' | 'unknown'
    text: string
  }
  checkin: {
    sleepTime: string | null
    wakeTime: string | null
    sleepScore: number | null
  }
  unhandledAlerts: number
  safe: boolean
}

// ============================================================================
// 用药
// ============================================================================
export interface MedicineReminder {
  id: string
  userId?: string
  medicineName: string
  dosage: string
  /** daily / twice / three_times */
  frequency: string
  /** 提醒时间点，如 ["08:00", "20:00"] */
  times: string[]
  startDate: string
  endDate?: string | null
  notes?: string
  isActive: boolean
}

// ============================================================================
// 饮食
// ============================================================================
export interface DietRecord {
  id?: string
  date: string
  /** breakfast / lunch / dinner / snack */
  mealType: string
  mealTime: string
  foods: string[]
  calories?: number
  protein?: number
  carbs?: number
  fat?: number
  notes?: string
}

// ============================================================================
// 运动
// ============================================================================
export interface ExerciseRecord {
  id?: string
  date: string
  exerciseType: string
  duration: number
  steps?: number
  calories?: number
  distance?: number
  /** low / moderate / high */
  intensity?: string
  notes?: string
}

// ============================================================================
// AI
// ============================================================================
export interface ChatResult {
  content: string
  conversationId: string
  /** ollama / coze / rule / cache */
  provider: string
  responseTime?: number
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: number
  /** 回复来源：ollama / coze / rule / cache（仅助手消息有） */
  provider?: string
  /** 标记这条是请求失败后的提示，界面上可以特殊显示 */
  failed?: boolean
}

// ============================================================================
// 统计
// ============================================================================
export interface DashboardOverview {
  checkin: {
    continuousDays: number
    weekDays: number
    todayStatus: string
  }
  health: {
    totalScore: number
    level: string
  }
  exercise: {
    todaySteps: number
    weekMinutes: number
  }
  diet: {
    todayCalories: number
    recordDays: number
  }
  medicine: {
    todayTaken: number
    todayTotal: number
    adherenceRate: number
  }
}

// ============================================================================
// 组件 Props
// ============================================================================
export interface StatCardProps {
  icon?: string
  title?: string
  value?: string | number
  unit?: string
  accent?: string
  trend?: string
}
