/**
 * API 接口层
 * ----------------------------------------------------------------------------
 * 与后端 130+ 接口一一对应。
 * 命名规范：模块名 + 动作，如 checkinApi.check()、guardianApi.sos()
 */
import { http } from './request'
import type {
  UserInfo,
  CheckinRecord,
  TodayCheckin,
  CheckinStats,
  HealthScore,
  AlertItem,
  DeviceItem,
  MedicineReminder,
  DietRecord,
  ExerciseRecord,
  ChatResult,
  PageResult,
  GuardianStatus,
  DashboardOverview
} from '@/types'

// ============================================================================
// 认证
// ============================================================================
export const authApi = {
  /** 发送验证码 */
  sendCode: (phone: string) => http.post<{ sent: boolean; devMode: boolean; devCode?: string }>(
    '/api/auth/send-code', { phone }
  ),

  /** 登录（首次自动注册） */
  login: (phone: string, code: string) => http.post<{
    token: string
    isNewUser: boolean
    user: UserInfo
  }>('/api/auth/login', { phone, code }),

  /** 登出 */
  logout: () => http.post('/api/auth/logout'),

  /** 当前用户 */
  me: () => http.get<UserInfo>('/api/auth/user'),

  /** 更新资料 */
  updateProfile: (data: Record<string, unknown>) => http.post<UserInfo>('/api/auth/user/update', data),

  /** 微信登录（占位，需真实 AppID） */
  /**
   * 微信一键登录
   *
   * 传 uni.login() 拿到的 code，后端换 openid 并签发 token。
   * available=false 表示后端没配 AppID，此时应提示用户改用手机号登录。
   */
  wechatLogin: (code: string) => http.post<{
    available: boolean
    reason?: string
    token?: string
    isNewUser?: boolean
    user?: UserInfo
    expireDays?: number
  }>('/api/auth/wechat-login', { code }),

  /** 生成邀请码 */
  generateInvite: () => http.post<{ inviteCode: string; elderId: string }>('/api/auth/generate-invite'),

  /** 校验邀请码 */
  verifyInvite: (code: string) => http.get<{ code: string; valid: boolean }>(
    '/api/auth/verify-invite', { code }
  ),

  /** 单独校验验证码（分步注册用） */
  verifyCode: (phone: string, code: string) => http.post<{
    valid: boolean
    phone: string
    isRegistered: boolean
  }>('/api/auth/verify-code', { phone, code }),

  /** 注册（与登录等价，可带资料） */
  register: (phone: string, code: string, name?: string, role?: string) =>
    http.post<{ token: string; isNewUser: boolean; user: UserInfo }>(
      '/api/auth/register', { phone, code, name, role }
    ),

  /** 家属列表（不传 userId 返回全部相关绑定） */
  familyMembers: (userId?: string) => http.get<{
    userId: string
    list: Array<UserInfo & { bindingId: string; relation: string; status: string }>
    total: number
  }>('/api/auth/family-members', { userId }),

  /** 绑定家属：传邀请码，或传手机号发起邀请 */
  bindFamily: (payload: { inviteCode?: string; familyPhone?: string }) =>
    http.post<{ bindingId: string; inviteCode?: string; status: string }>(
      '/api/auth/bind-family', payload
    ),

  /** 解绑家属 */
  unbindFamily: (payload: { bindingId?: string; familyId?: string; elderId?: string }) =>
    http.post('/api/auth/unbind-family', payload)
}

// ============================================================================
// 作息打卡
// ============================================================================
export const checkinApi = {
  /** 打卡（sleep / wake） */
  check: (type: 'sleep' | 'wake') => http.post<{
    type: string
    time: string
    date: string
  }>('/api/checkin/check', { type }),

  /** 今日打卡状态 */
  today: () => http.get<TodayCheckin>('/api/checkin/today'),

  /** 历史记录 */
  records: (days = 30) => http.get<{ list: CheckinRecord[]; total: number }>(
    '/api/checkin/records', { days }
  ),

  /** 打卡统计 */
  stats: () => http.get<CheckinStats>('/api/checkin/stats'),

  /** 健康评分（五项加权） */
  healthScore: () => http.get<HealthScore>('/api/checkin/health-score')
}

// ============================================================================
// 居家守护
// ============================================================================
export const guardianApi = {
  /** SOS 求救 */
  sos: (payload: { reason?: string; location?: string; lng?: number; lat?: number }) =>
    http.post<{ alertId: string; notified: number; message: string }>('/api/guardian/sos', payload),

  /** 长辈状态 */
  status: (userId?: string) => http.get<GuardianStatus>('/api/guardian/status', { userId }),

  /** 设备状态 */
  device: (userId?: string) => http.get<{ list: DeviceItem[]; total: number; onlineCount: number }>(
    '/api/guardian/device', { userId }
  ),

  /** 告警列表 */
  alerts: (params?: { handled?: boolean; page?: number; pageSize?: number }) =>
    http.get<PageResult<AlertItem>>('/api/guardian/alerts', params),

  /** 处理告警 */
  handleAlert: (id: string, notes?: string) =>
    http.post('/api/guardian/alert/handle', { id, notes }),

  /** 创建告警 */
  createAlert: (data: { alertType: string; severity?: string; title: string; description?: string }) =>
    http.post<{ id: string }>('/api/guardian/alert/create', data),

  /** 告警统计 */
  alertStats: () => http.get<{
    weekTotal: number
    weekUnhandled: number
    monthTotal: number
    bySeverity: Record<string, number>
  }>('/api/guardian/alert/stats'),

  // ---------------- 以下是补充接口 ----------------

  /** 守护首页汇总（长辈列表 + 告警数 + 设备数，一次拿全） */
  dashboard: () => http.get<{
    elders: Array<{
      userId: string
      name: string
      avatar?: string
      role: string
      status: string
      statusText: string
      healthScore: number | null
      unhandledAlerts: number
      safe: boolean
      sleepTime: string | null
      wakeTime: string | null
    }>
    elderCount: number
    totalUnhandledAlerts: number
    onlineDevices: number
  }>('/api/guardian/dashboard'),

  /** 指定长辈状态（路径参数版） */
  statusOf: (userId: string) => http.get<GuardianStatus>(`/api/guardian/status/${userId}`),

  /** 风险评估 */
  risk: (userId: string) => http.get<{
    userId: string
    name: string
    riskScore: number
    level: 'low' | 'medium' | 'high'
    levelText: string
    factors: string[]
    healthScore: number | null
    weekCheckinDays: number
    onlineDevices: number
    suggestion: string
  }>(`/api/guardian/risk/${userId}`),

  /** 紧急联系人列表 */
  contacts: () => http.get<{
    list: Array<{ id: string; name: string; phone: string; relation: string; primary?: string }>
    total: number
  }>('/api/guardian/contacts'),

  /** 添加紧急联系人 */
  addContact: (data: { name: string; phone: string; relation?: string }) =>
    http.post<{ contact: Record<string, string>; total: number }>('/api/guardian/contacts/add', data),

  /** 更新紧急联系人 */
  updateContact: (data: { id: string; name?: string; phone?: string; relation?: string }) =>
    http.post('/api/guardian/contacts/update', data),

  /** 删除紧急联系人 */
  removeContact: (id: string) => http.post('/api/guardian/contacts/remove', { id }),

  /** 某长辈的家属列表 */
  familyOf: (userId: string) => http.get<{
    elderId: string
    list: Array<{ bindingId: string; userId: string; name: string; phone: string; status: string }>
    total: number
  }>(`/api/guardian/family/${userId}`),

  /** SOS 历史 */
  sosHistory: (userId: string, page = 1, pageSize = 20) =>
    http.get<PageResult<AlertItem>>(`/api/guardian/sos/history/${userId}`, { page, pageSize }),

  /** 活动上报（用于「长时间未移动」判断） */
  reportActivity: (type = 'move') => http.post('/api/guardian/activity', { type }),

  /** 新增告警（路径别名） */
  addAlert: (data: { alertType: string; severity?: string; title: string; description?: string }) =>
    http.post<{ id: string }>('/api/guardian/alerts/add', data),

  /** 解决告警（路径别名） */
  resolveAlert: (id: string, notes?: string) =>
    http.post('/api/guardian/alerts/resolve', { id, notes })
}

// ============================================================================
// AI
// ============================================================================
export const aiApi = {
  /** 对话 */
  chat: (message: string, conversationId?: string, context?: Record<string, unknown>) =>
    http.post<ChatResult>('/api/ai/chat', { message, conversationId, context }),

  /** 增强对话（强制注入健康上下文 + 推荐追问） */
  chatEnhanced: (message: string, conversationId?: string, context?: Record<string, unknown>) =>
    http.post<ChatResult & { followUp: string[]; hasEmoji: boolean }>(
      '/api/ai/chat/enhanced', { message, conversationId, context }
    ),

  /** 数据健康分析 */
  analyze: (data?: Record<string, unknown>) => http.post<{
    analysis: string
    provider: string
    healthScore?: number
    dimensions?: Record<string, number>
  }>('/api/ai/analyze', data ? { data } : {}),

  /** 睡眠分析 */
  sleepAnalysis: (sleepHours: number, wakeTime?: string, sleepTime?: string) =>
    http.post<{ sleepHours: number; score: number; level: string; suggestion: string }>(
      '/api/ai/sleep-analysis', { sleepHours, wakeTime, sleepTime }
    ),

  /** 文本转语音（当前未接入云端，返回 available=false） */
  tts: (text: string) => http.post<{
    available: boolean
    reason: string
    text: string
    fallback: string
  }>('/api/ai/tts', { text }),

  /** 每日报告 */
  dailyReport: () => http.get<HealthScore & { dimensions: Record<string, number> }>(
    '/api/ai/report/daily'
  ),

  /** 每周报告 */
  weeklyReport: () => http.get<HealthScore & { range: string; dimensions: Record<string, number> }>(
    '/api/ai/report/weekly'
  )
}

// ============================================================================
// 用药
// ============================================================================
export const medicineApi = {
  /** 提醒列表 */
  reminders: (isActive?: boolean) =>
    http.get<{ list: MedicineReminder[]; total: number }>('/api/medicine/reminders', { isActive }),

  /** 新增提醒 */
  addReminder: (data: Partial<MedicineReminder>) => http.post<MedicineReminder>(
    '/api/medicine/reminder', data as Record<string, unknown>
  ),

  /** 更新提醒 */
  updateReminder: (data: Partial<MedicineReminder> & { id: string }) =>
    http.post<MedicineReminder>('/api/medicine/reminder/update', data as Record<string, unknown>),

  /** 删除提醒（软删除） */
  removeReminder: (id: string) => http.del(`/api/medicine/reminder/${id}`),

  /** 服药打卡 */
  record: (data: { reminderId?: string; medicineName: string; status?: string; notes?: string }) =>
    http.post('/api/medicine/record', data),

  /** 今日服药情况 */
  today: () => http.get<{
    taken: number
    pending: number
    total: number
    periods: Record<string, unknown>
  }>('/api/medicine/today'),

  /** 服药历史 */
  history: (days = 30) => http.get<{ list: unknown[] }>('/api/medicine/history', { days }),

  /** 依从率统计（后端路径是 /stats） */
  adherence: (days = 30) => http.get<{
    taken: number
    missed: number
    skipped: number
    rate: number
  }>('/api/medicine/stats', { days })
}

// ============================================================================
// 饮食
// ============================================================================
export const dietApi = {
  /** 今日饮食（后端按餐次分组返回 meals，不是 list） */
  today: () => http.get<{
    date: string
    meals: Record<string, DietRecord[]>
    totalCalories?: number
    nutrition?: Record<string, number>
  }>('/api/diet/today'),
  record: (data: Partial<DietRecord>) => http.post<DietRecord>('/api/diet/record', data as Record<string, unknown>),
  history: (days = 30) => http.get<{ list: DietRecord[] }>('/api/diet/history', { days }),
  weekly: () => http.get<{ list: unknown[] }>('/api/diet/weekly'),
  nutrition: (days = 7) => http.get<Record<string, number>>('/api/diet/nutrition', { days }),
  preferences: () => http.get<{ list: string[] }>('/api/diet/preferences'),
  setPreferences: (list: string[]) => http.post('/api/diet/preferences', { list }),
  advice: () => http.get<{ advice: string }>('/api/diet/advice'),
  stats: () => http.get<Record<string, number>>('/api/diet/stats')
}

// ============================================================================
// 运动
// ============================================================================
export const exerciseApi = {
  /** 今日运动（后端返回 totalSteps / totalDuration 等汇总字段 + targets 目标） */
  today: () => http.get<{
    date: string
    records: ExerciseRecord[]
    total: number
    totalSteps: number
    totalDuration: number
    totalCalories: number
    totalDistance: number
    targets?: { steps: number; duration: number; calories: number }
    progress?: Record<string, number>
  }>('/api/exercise/today'),
  record: (data: Partial<ExerciseRecord>) =>
    http.post<ExerciseRecord>('/api/exercise/record', data as Record<string, unknown>),
  history: (days = 30) => http.get<{ list: ExerciseRecord[] }>('/api/exercise/history', { days }),
  weekly: () => http.get<{ list: unknown[] }>('/api/exercise/weekly'),
  monthly: () => http.get<{ list: unknown[] }>('/api/exercise/monthly'),
  types: () => http.get<{ list: unknown[] }>('/api/exercise/types'),
  goals: () => http.get<{ steps: number; minutes: number; calories: number; persisted?: boolean }>(
    '/api/exercise/goals'
  ),
  setGoals: (goals: Record<string, number>) => http.post('/api/exercise/goals', goals),
  stats: () => http.get<Record<string, number>>('/api/exercise/stats')
}

// ============================================================================
// 监测
// ============================================================================
export const monitorApi = {
  voiceAnalyze: (text: string) => http.post<{ analysis: string; provider: string }>(
    '/api/monitor/voice-analyze', { text }
  ),
  imageAnalyze: (desc: string) => http.post<{ analysis: string; provider: string }>(
    '/api/monitor/image-analyze', { description: desc }
  ),
  textAnalyze: (text: string) => http.post<{ analysis: string; provider: string }>(
    '/api/monitor/text-analyze', { text }
  ),
  fallDetect: (detected: boolean, confidence: number) =>
    http.post('/api/monitor/fall-detect', { detected, confidence }),
  /** 监测历史（后端路径含 userId） */
  history: (userId: string, page = 1, pageSize = 20) => http.get<PageResult<unknown>>(
    `/api/monitor/history/${userId}`, { page, pageSize }
  ),
  latest: () => http.get<Record<string, unknown>>('/api/monitor/latest'),
  stats: () => http.get<Record<string, number>>('/api/monitor/stats')
}

// ============================================================================
// 统计
// ============================================================================
export const statisticsApi = {
  overview: () => http.get<DashboardOverview>('/api/statistics/overview'),
  weekly: () => http.get<Record<string, unknown>>('/api/statistics/weekly'),
  monthly: () => http.get<Record<string, unknown>>('/api/statistics/monthly'),
  trend: (days = 7) => http.get<{ dates: string[]; values: number[] }>('/api/statistics/trend', { days })
}

// ============================================================================
// 设备
// ============================================================================
export const deviceApi = {
  list: () => http.get<{ list: DeviceItem[]; total: number }>('/api/device/list'),
  detail: (id: string) => http.get<DeviceItem>('/api/device/detail', { id }),
  bind: (data: { deviceName: string; deviceType: string; deviceMac?: string }) =>
    http.post<DeviceItem>('/api/device/bind', data),
  connect: (id: string) => http.post('/api/device/connect', { id }),
  disconnect: (id: string) => http.post('/api/device/disconnect', { id }),
  unbind: (id: string) => http.del('/api/device/unbind', { id }),
  status: (id: string, status: string) => http.post('/api/device/status', { id, status }),
  alerts: () => http.get<PageResult<AlertItem>>('/api/device/alerts')
}

// ============================================================================
// 家庭
// ============================================================================
export const familyApi = {
  members: () => http.get<{ list: UserInfo[]; total: number }>('/api/family/members'),
  pending: () => http.get<{ list: unknown[] }>('/api/family/pending'),
  invite: () => http.get<{ inviteCode: string }>('/api/family/invite'),
  add: (phone: string, relation?: string) => http.post('/api/family/add', { phone, relation }),
  accept: (id: string) => http.post('/api/family/accept', { id }),
  reject: (id: string) => http.post('/api/family/reject', { id }),
  remove: (id: string) => http.del('/api/family/remove', { id }),
  setPermission: (id: string, level: string) => http.post('/api/family/permission', { id, level })
}

// ============================================================================
// 提醒
// ============================================================================
export const reminderApi = {
  /** 提醒配置列表（后端路径含 userId） */
  list: (userId: string) => http.get<{ list: unknown[] }>(`/api/reminder/configs/${userId}`),
  create: (data: Record<string, unknown>) => http.post('/api/reminder/configs/create', data),
  update: (data: Record<string, unknown>) => http.post('/api/reminder/configs/update', data),
  remove: (id: string) => http.del('/api/reminder/configs/delete', { id }),
  toggle: (id: string, enabled: boolean) => http.post('/api/reminder/toggle', { id, enabled }),
  today: () => http.get<{ list: unknown[] }>('/api/reminder/today'),
  /** 打卡提醒配置 */
  checkinConfig: () => http.get<Record<string, unknown>>('/api/reminder/checkin-reminder'),
  setCheckinConfig: (data: Record<string, unknown>) =>
    http.post('/api/reminder/checkin-reminder', data)
}

// ============================================================================
// 位置
// ============================================================================
export const locationApi = {
  /** 上报当前位置 */
  current: (lng: number, lat: number) => http.post<Record<string, unknown>>(
    '/api/location/current', { lng, lat }
  ),
  /** 轨迹历史（后端路径含 userId） */
  history: (userId: string, days = 7) =>
    http.get<{ list: unknown[] }>(`/api/location/history/${userId}`, { days }),
  /** 安全区列表 */
  safeZones: (userId: string) => http.get<{ list: unknown[] }>(`/api/location/safe-zones/${userId}`),
  setSafeZone: (data: { lng: number; lat: number; radius: number; name?: string }) =>
    http.post('/api/location/safe-zone', data),
  removeSafeZone: (id: string) => http.del('/api/location/safe-zone', { id }),
  /** 是否在安全区内（后端返回 inSafeZone，字段名与直觉不同） */
  check: (lng: number, lat: number) => http.post<{
    lat: number
    lng: number
    inSafeZone: boolean
    checked: boolean
    message?: string
    distance?: number
    zones?: unknown[]
  }>('/api/location/safe-zone-check', { lng, lat })
}

// ============================================================================
// 天气 / 微信运动 / 反馈
// ============================================================================
export const weatherApi = {
  /** 实时天气（后端路径是 live） */
  current: () => http.get<Record<string, unknown>>('/api/weather/live'),
  forecast: () => http.get<{ list: unknown[] }>('/api/weather/forecast'),
  advice: () => http.get<{ advice: string }>('/api/weather/advice')
}

export const werunApi = {
  steps: (date?: string) => http.get<{ steps: number }>('/api/werun/steps', { date }),
  sync: (data: { steps: number; date: string }[]) => http.post('/api/werun/sync', { data })
}

export const feedbackApi = {
  submit: (content: string, contact?: string) => http.post('/api/feedback/submit', { content, contact }),
  list: () => http.get<{ list: unknown[] }>('/api/feedback/history')
}

// ============================================================================
// 地图（腾讯位置服务）
// ============================================================================
export const mapApi = {
  /** 坐标转地址 */
  regeo: (lat: number, lng: number) => http.post<{
    lat: number
    lng: number
    address: string
    formatted: string
    province?: string
    city?: string
    district?: string
    street?: string
    resolved: boolean
    mapConfigured?: boolean
    reason?: string
  }>('/api/map/regeo', { lat, lng }),

  /** 地址转坐标 */
  geocode: (address: string) => http.post<{
    lat?: number
    lng?: number
    address: string
    resolved: boolean
    reason?: string
  }>('/api/map/geocode', { address }),

  /** 周边搜索（医院 / 药店 / 公园） */
  nearby: (keyword: string, lat: number, lng: number, radius = 2000) =>
    http.get<{
      keyword: string
      radius: number
      list: Array<{
        id: string
        title: string
        address: string
        category: string
        distance: number
        lat: number
        lng: number
        phone?: string
      }>
      total: number
      mapConfigured: boolean
      hint?: string
    }>('/api/map/nearby', { keyword, lat, lng, radius }),

  /** 两点距离（本地算，不耗配额） */
  distance: (lat1: number, lng1: number, lat2: number, lng2: number) =>
    http.post<{ meters: number; text: string }>('/api/map/distance', { lat1, lng1, lat2, lng2 })
}

// ============================================================================
// 识图（腾讯云 OCR + Coze 多模态）
// ============================================================================
export const imageApi = {
  /** 纯文字识别（药品说明书 / 体检单 / 化验单） */
  ocr: (image: string) => http.post<{
    lines: string[]
    text: string
    count: number
    provider: string
    ocrConfigured: boolean
    remainingToday: number
    reason?: string
  }>('/api/image/ocr', { image }),

  /** 识图 + AI 解释 —— 「拍药盒问怎么吃」 */
  analyze: (image: string | null, question?: string) => http.post<{
    ocr: { lines: string[]; text: string; count: number }
    hasText: boolean
    analysis: string
    provider: string
    responseTime?: number
    remainingToday: number
  }>('/api/image/analyze', { image, question }),

  /** 跌倒风险判断 */
  fallDetect: (image: string | null, description?: string) => http.post<{
    detected: boolean
    confidence: number
    analysis: string
    provider: string
  }>('/api/image/fall-detect', { image, description }),

  /** 今日剩余识别次数 */
  quota: () => http.get<{
    remainingToday: number
    ocrConfigured: boolean
    mapConfigured: boolean
  }>('/api/image/quota')
}
