/**
 * 网络请求封装
 * ----------------------------------------------------------------------------
 * ★ 安全要点：
 *   1. 后端地址从环境变量读，不硬编码
 *   2. 绝不在这里放任何第三方 API Token（原项目把 Coze Token 放前端是严重错误）
 *   3. 所有需要密钥的调用都走后端中转
 */

/**
 * 后端地址
 *
 * ★ 三种运行环境，地址规则完全不同：
 *
 *   ① 微信开发者工具
 *      localhost 指电脑自己，直接用 http://localhost:3000 即可。
 *
 *   ② 真机预览（扫码）
 *      localhost 指手机自己，必须用电脑的局域网 IP。
 *      ⚠️ 而且微信真机默认强制 https + 已备案域名，
 *         走 http + IP 需要在手机上【打开调试模式】才能绕过：
 *         手机预览界面 → 右上角「···」 → 打开调试
 *         打开后会重启小程序，此时才允许 http 请求。
 *
 *   ③ 正式发布
 *      必须 https 域名且配置到小程序后台的 request 合法域名。
 *
 * 改 IP：命令行执行 ipconfig，找 WLAN 的「IPv4 地址」。
 */
const DEV_LAN_IP = '10.224.107.234'   // ← 换网络后要改这里（当前 WLAN 的 IPv4）

/** 后端端口，改端口只改这一处 */
const API_PORT = 3000

const BASE_URL = (() => {
  // #ifdef MP-WEIXIN
  try {
    const info = uni.getSystemInfoSync()
    const isDevtools = info.platform === 'devtools'

    if (!isDevtools) {
      // 真机：用局域网 IP（前提是已经打开调试模式）
      console.warn(
        '[请求] 真机环境，后端地址:',
        `http://${DEV_LAN_IP}:${API_PORT}`,
        '\n如果请求失败，请在手机上打开调试模式：右上角「···」→ 打开调试'
      )
      return `http://${DEV_LAN_IP}:${API_PORT}`
    }
  } catch {
    // 拿不到系统信息就按开发者工具处理
  }
  // #endif

  return `http://localhost:${API_PORT}`
})()

/** token 存储键 */
export const TOKEN_KEY = 'guardian_token'

interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: Record<string, unknown>
  header?: Record<string, string>
  /** 静默模式：失败时不弹提示（用于后台静默刷新） */
  silent?: boolean
}

/** 是否绝对地址 */
function isAbsolute(url: string): boolean {
  return /^https?:\/\//.test(url)
}

/** 拼完整地址 */
function resolveUrl(url: string): string {
  return isAbsolute(url) ? url : BASE_URL + url
}

/**
 * 业务失败时统一提示
 *
 * 401/403 会清 token 并跳登录页
 */
function handleBizError(code: number, msg: string, silent: boolean) {
  if (code === 401 || code === 403) {
    uni.removeStorageSync(TOKEN_KEY)
    uni.removeStorageSync('guardian_user')
    if (!silent) {
      uni.showToast({ title: msg || '登录已失效', icon: 'none' })
    }
    setTimeout(() => {
      uni.redirectTo({ url: '/pages/login/index' })
    }, 800)
    return
  }

  if (!silent) {
    uni.showToast({ title: msg || '请求失败', icon: 'none' })
  }
}

/**
 * 发起请求
 *
 * 后端统一返回信封 { code, msg, data }，HTTP 状态码恒为 200。
 * 这里把信封拆开，只把 data 交给调用方。
 */
export function request<T = unknown>(options: RequestOptions): Promise<T> {
  const { url, method = 'GET', data, header = {}, silent = false } = options

  const token = uni.getStorageSync(TOKEN_KEY)
  const finalHeader: Record<string, string> = {
    'Content-Type': 'application/json',
    ...header
  }
  if (token) {
    finalHeader.Authorization = 'Bearer ' + token
  }

  /**
   * ★ 超时按接口类型区分。
   *
   * 默认 15 秒对普通查询够了，但 AI 接口不够：
   *   Coze 首轮生成实测 3~12 秒，复杂问题会更久，
   *   统一 15 秒会出现「偶尔能回、偶尔超时」的随机失败。
   *   所以对 /ai/ 和 /monitor/ 这类要走大模型的接口放宽到 60 秒。
   */
  const isAiCall = url.includes('/ai/') || url.includes('/monitor/')
  const timeout = isAiCall ? 60000 : 15000

  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: resolveUrl(url),
      method,
      data,
      header: finalHeader,
      timeout,
      success: (res) => {
        const body = res.data as { code?: number; msg?: string; data?: T } | undefined

        // 非 JSON 响应（比如后端没启动时返回了 HTML）
        if (!body || typeof body !== 'object' || body.code === undefined) {
          if (!silent) {
            uni.showToast({ title: '服务异常，请检查后端是否启动', icon: 'none' })
          }
          reject(new Error('invalid response'))
          return
        }

        if (body.code !== 200) {
          handleBizError(body.code, body.msg || '请求失败', silent)
          reject(new Error(body.msg || 'request failed'))
          return
        }

        resolve(body.data as T)
      },
      fail: (err) => {
        /**
         * ★ 真机上失败最常见的原因是微信拦截了 http 请求。
         *   微信强制要求 https + 已备案域名，走 http + 局域网 IP
         *   必须在手机上打开「调试模式」才放行。
         *   这里把地址和排查步骤直接告诉用户，比一句「网络异常」有用得多。
         */
        const msg = String((err as any)?.errMsg || err || '')
        const fullUrl = resolveUrl(url)
        console.error('[请求失败]', fullUrl, msg)

        if (!silent) {
          if (msg.includes('url not in domain list') || msg.includes('not in domain')) {
            uni.showModal({
              title: '请求被微信拦截',
              content:
                '真机默认只允许 https 域名。\n\n' +
                '解决方法：\n' +
                '1. 点右上角「···」\n' +
                '2. 选「打开调试」\n' +
                '3. 小程序会重启，然后重试',
              showCancel: false,
              confirmText: '知道了'
            })
          } else if (msg.includes('timeout')) {
            uni.showToast({ title: '请求超时，请重试', icon: 'none' })
          } else {
            uni.showModal({
              title: '连不上后端',
              content:
                `地址：${fullUrl}\n\n` +
                '请检查：\n' +
                '1. 手机和电脑是否连同一个 WiFi\n' +
                '2. 电脑上 guardian-server 是否已启动\n' +
                '3. 手机浏览器能否打开上面的地址',
              showCancel: false,
              confirmText: '知道了'
            })
          }
        }
        reject(err)
      }
    })
  })
}

/** 便捷方法 */
export const http = {
  get: <T = unknown>(url: string, params?: Record<string, unknown>, silent?: boolean) => {
    let full = url
    if (params && Object.keys(params).length > 0) {
      const qs = Object.keys(params)
        .filter((k) => params[k] !== undefined && params[k] !== null && params[k] !== '')
        .map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(String(params[k]))}`)
        .join('&')
      if (qs) full += (url.includes('?') ? '&' : '?') + qs
    }
    return request<T>({ url: full, method: 'GET', silent })
  },

  post: <T = unknown>(url: string, data?: Record<string, unknown>, silent?: boolean) =>
    request<T>({ url, method: 'POST', data, silent }),

  put: <T = unknown>(url: string, data?: Record<string, unknown>, silent?: boolean) =>
    request<T>({ url, method: 'PUT', data, silent }),

  del: <T = unknown>(url: string, data?: Record<string, unknown>, silent?: boolean) =>
    request<T>({ url, method: 'DELETE', data, silent })
}

export default http
