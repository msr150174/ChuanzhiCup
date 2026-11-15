import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type {
  AlertItem,
  AlertQuery,
  DashboardStats,
  DeviceItem,
  FeedbackItem,
  FeedbackQuery,
  LoginResult,
  Paged,
  Role,
  SystemInfo,
  TrendData,
  UserDetail,
  UserListItem,
  UserQuery,
} from '@/types'

/**
 * 对接真实后端（Spring Boot，端口 3000，上下文 /api）
 *
 * ★ 这个文件里做了一层「字段适配」：
 *   Coze 生成的前端假设了一套字段（大写角色、records 分页键、newUsers 趋势键），
 *   而真实后端用的是另一套（小写角色、list 分页键、registrations 趋势键）。
 *   与其改 7 个页面组件，不如在这里把两边对齐 —— 组件代码零改动。
 *
 * 后端约定：
 *   - 响应恒为 { code, msg, data }，HTTP 状态码永远是 200
 *   - 成功看 code === 200
 *   - 所有 /admin/* 需要 users.is_admin = 1
 */

interface RequestConfig extends AxiosRequestConfig {
  _silent?: boolean
}

interface BusinessResult<T> {
  code: number
  msg: string
  data: T
}

export const TOKEN_KEY = 'guardian_admin_token'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const body = response.data as BusinessResult<unknown>
    const requestConfig = response.config as RequestConfig

    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) {
        return response
      }

      if (body.code === 401) {
        localStorage.removeItem(TOKEN_KEY)
        if (window.location.pathname !== '/login') {
          if (!requestConfig._silent) {
            ElMessage.error(body.msg || '登录已过期，请重新登录')
          }
          window.location.href = '/login'
        }
        return Promise.reject(new Error(body.msg || '登录已过期，请重新登录'))
      }

      if (body.code === 403) {
        if (!requestConfig._silent) {
          ElMessage.error(body.msg || '无权限')
        }
        return Promise.reject(new Error(body.msg || '无权限'))
      }

      if (!requestConfig._silent) {
        ElMessage.error(body.msg || '请求失败')
      }
      return Promise.reject(new Error(body.msg || '请求失败'))
    }

    return response
  },
  (error: unknown) => {
    if (axios.isAxiosError(error)) {
      ElMessage.error(error.message || '网络异常，请稍后重试')
    } else {
      ElMessage.error('网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

async function get<T>(url: string, params?: object, config?: RequestConfig): Promise<T> {
  const res = await request.get<BusinessResult<T>>(url, { params, ...config })
  return res.data.data
}

async function post<T>(url: string, data?: object, config?: RequestConfig): Promise<T> {
  const res = await request.post<BusinessResult<T>>(url, data, config)
  return res.data.data
}

// ============================================================================
// 字段适配
// ============================================================================

/** 后端 role（小写）→ 前端期望（大写） */
function toRole(role: unknown): Role {
  const r = String(role || '').toLowerCase()
  return r === 'family' ? 'FAMILY' : 'ELDER'
}

/** 后端 status（active/inactive）→ 前端期望（ONLINE/OFFLINE） */
function toDeviceStatus(status: unknown): 'ONLINE' | 'OFFLINE' {
  return String(status || '').toLowerCase() === 'active' ? 'ONLINE' : 'OFFLINE'
}

/** 后端 severity（critical/high/medium/low）→ 前端期望（URGENT/HIGH/MEDIUM/LOW） */
function toSeverity(sev: unknown): 'URGENT' | 'HIGH' | 'MEDIUM' | 'LOW' {
  const s = String(sev || '').toLowerCase()
  if (s === 'critical') return 'URGENT'
  if (s === 'high') return 'HIGH'
  if (s === 'low') return 'LOW'
  return 'MEDIUM'
}

/** 前端 severity → 后端 */
function fromSeverity(sev: unknown): string {
  const s = String(sev || '').toUpperCase()
  if (s === 'URGENT') return 'critical'
  if (s === 'HIGH') return 'high'
  if (s === 'LOW') return 'low'
  return 'medium'
}

/** 后端分页 { list, total } → 前端期望 { records, total } */
function toPaged<TOut>(
  data: any,
  map: (row: any) => TOut,
  fallbackPage = 1,
  fallbackSize = 20
): Paged<TOut> {
  const rows = Array.isArray(data?.list) ? data.list : []
  return {
    records: rows.map(map),
    total: Number(data?.total ?? rows.length),
    page: Number(data?.page ?? fallbackPage),
    pageSize: Number(data?.pageSize ?? fallbackSize),
  }
}

/** 'true' | 'false' | '' → boolean | undefined */
function toBool(v: unknown): boolean | undefined {
  if (v === 'true' || v === true) return true
  if (v === 'false' || v === false) return false
  return undefined
}

// ============================================================================
// 认证
// ============================================================================

export const authApi = {
  sendCode: (phone: string) =>
    post<{ sent: boolean; devCode?: string }>('/auth/send-code', { phone }, { _silent: true }),

  login: async (phone: string, code: string): Promise<LoginResult> => {
    const raw = await post<any>('/auth/login', { phone, code }, { _silent: true })
    return {
      token: raw.token,
      user: {
        id: raw.user?.id,
        name: raw.user?.name,
        phone: raw.user?.phone,
        role: toRole(raw.user?.role),
        isAdmin: !!raw.user?.isAdmin,
        isActive: raw.user?.isActive !== false,
      },
    }
  },

  /** 退出登录（通知后端清掉 Redis 里的 token） */
  logout: () => post<unknown>('/auth/logout', {}, { _silent: true }),
}

// ============================================================================
// 数据总览
// ============================================================================

export const dashboardApi = {
  stats: async (): Promise<DashboardStats> => {
    const raw = await get<any>('/admin/dashboard')
    const o = raw?.overview || {}
    return {
      totalUsers: o.totalUsers ?? 0,
      elders: o.elders ?? 0,
      families: o.families ?? 0,
      newThisWeek: o.newThisWeek ?? 0,
      weekAlerts: o.weekAlerts ?? 0,
      todayCheckins: o.todayCheckins ?? 0,
      admins: o.admins ?? 0,
    }
  },

  trend: async (days: number): Promise<TrendData> => {
    const raw = await get<any>('/admin/trend', { days })
    return {
      days,
      dates: raw?.dates || [],
      // 后端叫 registrations，前端期望 newUsers
      newUsers: raw?.registrations || [],
      alerts: raw?.alerts || [],
    }
  },
}

// ============================================================================
// 用户管理
// ============================================================================

function mapUser(row: any): UserListItem {
  return {
    id: row.id,
    name: row.name || '—',
    phone: row.phone || '',
    role: toRole(row.role),
    isAdmin: !!row.isAdmin,
    isActive: row.isActive !== false,
    createTime: row.createTime || row.createdAt || '',
  }
}

export const userApi = {
  list: async (query: UserQuery): Promise<Paged<UserListItem>> => {
    const raw = await get<any>('/admin/users', {
      keyword: query.keyword || undefined,
      role: query.role ? String(query.role).toLowerCase() : undefined,
      isAdmin: toBool(query.isAdmin),
      isActive: toBool(query.isActive),
      page: query.page,
      pageSize: query.pageSize,
    })
    return toPaged(raw, mapUser, query.page, query.pageSize)
  },

  detail: async (id: string | number): Promise<UserDetail> => {
    const raw = await get<any>(`/admin/users/${id}`)
    return {
      id: raw.id,
      name: raw.name || '—',
      phone: raw.phone || '',
      role: toRole(raw.role),
      isAdmin: !!raw.isAdmin,
      isActive: raw.isActive !== false,
      createTime: raw.createTime || '',
      address: raw.address || '',
      // 后端叫 checkinDays，前端期望 checkinCount30d
      checkinCount30d: raw.checkinDays ?? 0,
      // 后端只给 deviceCount 一个数字，前端要数组 —— 给空数组，页面显示 0 台
      devices: [],
      pendingAlerts: raw.unhandledAlerts ?? 0,
    }
  },

  setAdmin: (userId: string | number, isAdmin: boolean) =>
    post<{ id: string; isAdmin: boolean }>('/admin/users/set-admin', { userId, isAdmin }),

  setActive: (userId: string | number, isActive: boolean) =>
    post<{ id: string; isActive: boolean }>('/admin/users/set-active', { userId, isActive }),
}

// ============================================================================
// 告警处置
// ============================================================================

function mapAlert(row: any): AlertItem {
  return {
    id: row.id,
    severity: toSeverity(row.severity),
    alertType: row.alertType || '',
    title: row.title || '',
    description: row.description || '',
    userId: row.userId,
    userName: row.userName || '—',
    userPhone: row.userPhone || '',
    occurredAt: row.occurredAt || '',
    handled: !!row.handled,
    handledAt: row.handledAt || null,
    notes: row.notes || null,
  }
}

export const alertApi = {
  list: async (query: AlertQuery): Promise<Paged<AlertItem>> => {
    const raw = await get<any>('/admin/alerts', {
      handled: toBool(query.handled),
      severity: query.severity ? fromSeverity(query.severity) : undefined,
      page: query.page,
      pageSize: query.pageSize,
    })
    return toPaged(raw, mapAlert, query.page, query.pageSize)
  },

  handle: (alertId: string | number, notes: string) =>
    post<{ id: string; handled: boolean }>('/admin/alerts/handle', { alertId, notes }),
}

// ============================================================================
// 反馈处理
// ============================================================================

function mapFeedback(row: any): FeedbackItem {
  return {
    id: row.id,
    userId: row.userId,
    userName: row.userName || '—',
    userPhone: row.userPhone || '',
    title: row.title || '',
    content: row.content || '',
    // 后端 severity 是字符串（low/medium），前端要数字星级
    severity: Number(row.severity) || 3,
    createTime: row.createTime || '',
    handled: !!row.handled,
    reply: row.notes || null,
  }
}

export const feedbackApi = {
  list: async (query: FeedbackQuery): Promise<Paged<FeedbackItem>> => {
    const raw = await get<any>('/admin/feedback', {
      page: query.page,
      pageSize: query.pageSize,
    })
    const paged = toPaged(raw, mapFeedback, query.page, query.pageSize)

    // handled 筛选后端不支持，在前端过滤（反馈量小，够用）
    const want = toBool(query.handled)
    if (want !== undefined) {
      paged.records = paged.records.filter((r) => r.handled === want)
      paged.total = paged.records.length
    }
    return paged
  },

  reply: (feedbackId: string | number, reply: string) =>
    post<{ id: string; handled: boolean; reply: string }>('/admin/feedback/reply', {
      feedbackId,
      reply,
    }),
}

// ============================================================================
// 设备管理
// ============================================================================

function mapDevice(row: any): DeviceItem {
  return {
    id: row.id,
    deviceName: row.deviceName || '—',
    deviceType: row.deviceType || '',
    userName: row.userName || '—',
    userId: row.userId,
    status: toDeviceStatus(row.status),
    lastSync: row.lastSync || '',
    createTime: row.createTime || '',
  }
}

export const deviceApi = {
  list: async (): Promise<{ records: DeviceItem[]; total: number }> => {
    const raw = await get<any>('/admin/devices')
    const rows = Array.isArray(raw?.list) ? raw.list : []
    return { records: rows.map(mapDevice), total: Number(raw?.total ?? rows.length) }
  },
}

// ============================================================================
// 系统监控
// ============================================================================

export const systemApi = {
  info: async (): Promise<SystemInfo> => {
    const raw = await get<any>('/admin/system')
    const jvm = raw?.jvm || {}
    return {
      database: {
        name: 'MySQL',
        version: raw?.mysql?.connected ? '已连接（znwg）' : '未连接',
        connected: !!raw?.mysql?.connected,
        users: raw?.mysql?.users ?? 0,
      },
      cache: {
        name: 'Redis',
        version: raw?.redis?.connected ? '已连接' : '未连接',
        connected: !!raw?.redis?.connected,
        keyCount: raw?.redis?.keyCount ?? 0,
      },
      runtime: {
        javaVersion: jvm.javaVersion || '',
        maxMemoryMB: jvm.maxMemoryMB ?? 0,
        usedMemoryMB: jvm.usedMemoryMB ?? 0,
        freeMemoryMB: jvm.freeMemoryMB ?? 0,
        processors: jvm.processors ?? 0,
        uptimeSeconds: jvm.uptimeSeconds ?? 0,
      },
    }
  },
}

export { request }
