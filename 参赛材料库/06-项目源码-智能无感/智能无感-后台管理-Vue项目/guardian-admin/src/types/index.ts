// 统一响应与业务类型定义

export interface ApiResult<T> {
  code: number
  msg: string
  data: T
}

export type Role = 'ELDER' | 'FAMILY'

export interface LoginUser {
  id: string
  name: string
  phone: string
  role: Role
  isAdmin: boolean
  isActive: boolean
}

export interface LoginResult {
  token: string
  user: LoginUser
}

export interface DashboardStats {
  totalUsers: number
  elders: number
  families: number
  newThisWeek: number
  weekAlerts: number
  todayCheckins: number
  admins: number
}

export interface TrendData {
  days: number
  dates: string[]
  newUsers: number[]
  alerts: number[]
}

export interface UserListItem {
  id: string
  name: string
  phone: string
  role: Role
  isAdmin: boolean
  isActive: boolean
  createTime: string
}

export interface UserDeviceBrief {
  id: string
  deviceName: string
  deviceType: string
  status: 'ONLINE' | 'OFFLINE'
}

export interface UserDetail extends LoginUser {
  createTime: string
  address: string
  checkinCount30d: number
  devices: UserDeviceBrief[]
  pendingAlerts: number
}

export interface Paged<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}

export interface UserQuery {
  keyword?: string
  role?: '' | Role
  isAdmin?: '' | 'true' | 'false'
  isActive?: '' | 'true' | 'false'
  page: number
  pageSize: number
}

export type Severity = 'URGENT' | 'HIGH' | 'MEDIUM' | 'LOW'

export interface AlertItem {
  id: string
  severity: Severity
  alertType: string
  title: string
  description: string
  userId: string
  userName: string
  userPhone: string
  occurredAt: string
  handled: boolean
  handledAt: string | null
  notes: string | null
}

export interface AlertQuery {
  handled?: '' | 'true' | 'false'
  severity?: '' | Severity
  page: number
  pageSize: number
}

export interface FeedbackItem {
  id: string
  userId: string
  userName: string
  userPhone: string
  title: string
  content: string
  severity: number
  createTime: string
  handled: boolean
  reply: string | null
}

export interface FeedbackQuery {
  handled?: '' | 'true' | 'false'
  page: number
  pageSize: number
}

export interface DeviceItem {
  id: string
  deviceName: string
  deviceType: string
  userName: string
  userId: string
  status: 'ONLINE' | 'OFFLINE'
  lastSync: string
  createTime: string
}

export interface SystemInfo {
  database: {
    name: string
    version: string
    connected: boolean
    users: number
  }
  cache: {
    name: string
    version: string
    connected: boolean
    keyCount: number
  }
  runtime: {
    javaVersion: string
    maxMemoryMB: number
    usedMemoryMB: number
    freeMemoryMB: number
    processors: number
    uptimeSeconds: number
  }
}
