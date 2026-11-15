# AI 节律守护 · 运营管理后台

老年人健康守护小程序的**桌面端管理后台**。

用 **Coze AI** 依据提示词文档生成，已改造成连**真实 Spring Boot 后端**（数据来自 MySQL）。

---

## 快速开始

### 前置：先启动后端

后台的数据全部来自 Spring Boot 后端，**必须先启动它**：

```powershell
cd D:\msr150174\项目\智能无感\转换-Java后端\guardian-server
.\start.bat
```

看到 `status : UP` 再继续。

验证：浏览器打开 <http://localhost:3000/api/health>

### 启动后台

```powershell
npm install     # 首次
npm run dev
```

**打开** <http://localhost:5177>

### 登录

| 项 | 值 |
|---|---|
| 手机号 | `13800000001`（已设为管理员） |
| 验证码 | **看后端控制台**（开发模式不真发短信） |

后端控制台会打印：

```
【开发模式】手机号 13800000001 的验证码为 268549（300 秒内有效）
```

> ⚠️ 验证码 60 秒频率限制，连续测试请换手机号。

---

## 技术栈

| 类别 | 技术 | 版本 |
|---|---|---|
| 框架 | Vue 3 | 3.5 |
| 语法 | `<script setup>` + TypeScript | 6.0 |
| 构建 | Vite | 8.3 |
| UI | Element Plus | 2.14 |
| 路由 | Vue Router | 4 |
| 状态 | Pinia | 2.2 |
| 请求 | Axios | 1.20 |
| 图表 | ECharts | 6.1 |
| 样式 | SCSS | 1.105 |

---

## 页面

| 路由 | 页面 | 功能 |
|---|---|---|
| `/login` | 登录 | 手机号 + 验证码，检查 `isAdmin` |
| `/dashboard` | 数据总览 | 7 个指标卡片 + 近 7 天趋势折线图 |
| `/users` | 用户管理 | 搜索/筛选、设为管理员、禁用/启用、查看详情 |
| `/alerts` | 告警处置 | 按状态和级别筛选、填写处理说明 |
| `/feedback` | 反馈处理 | 回复用户反馈 |
| `/devices` | 设备管理 | 设备列表与所属用户 |
| `/system` | 系统监控 | MySQL / Redis / JVM 状态 |

---

## 目录结构

```
后台管理-Coze版/
├── .vscode/                VSCode 配置
├── index.html
├── package.json
├── tsconfig*.json
├── vite.config.ts          ★ 含 /api 代理配置
└── src/
    ├── api/index.ts        ★ Axios 封装 + 字段适配层
    ├── layouts/
    │   └── AdminLayout.vue  侧边栏 + 顶栏
    ├── views/
    │   ├── Login.vue
    │   ├── Dashboard.vue
    │   ├── Users.vue
    │   ├── Alerts.vue
    │   ├── Feedback.vue
    │   ├── Devices.vue
    │   └── System.vue
    ├── router/index.ts     路由 + 登录守卫
    ├── stores/user.ts      Pinia
    ├── types/index.ts      TypeScript 类型
    ├── utils/format.ts     格式化工具
    ├── styles/index.scss   设计令牌与全局样式
    ├── App.vue
    └── main.ts
```

---

## 与真实后端的对接

### 代理配置

`vite.config.ts` 把 `/api` 转发到后端：

```ts
server: {
  port: 5177,
  strictPort: true,
  proxy: {
    '/api': { target: 'http://localhost:3000', changeOrigin: true },
  },
}
```

**所以前端代码里 `baseURL: '/api'`，没有跨域问题。**

### 字段适配层

Coze 生成时假设了一套字段名，和真实后端**不完全一致**。
为了**不改 7 个页面组件**，`src/api/index.ts` 里做了一层适配：

| 位置 | Coze 假设 | 真实后端 | 适配方式 |
|---|---|---|---|
| 用户角色 | `ELDER` / `FAMILY` | `elder` / `family` | 大写转换 |
| 用户 ID | `number` | `string`（UUID） | 类型放宽 |
| 分页键 | `records` | `list` | `toPaged()` 映射 |
| 趋势键 | `newUsers` | `registrations` | 改名 |
| 告警级别 | `URGENT`/`HIGH`/… | `critical`/`high`/… | 双向映射 |
| 设备状态 | `ONLINE`/`OFFLINE` | `active`/`inactive` | 映射 |
| 打卡字段 | `checkinCount30d` | `checkinDays` | 改名 |
| 待处理告警 | `pendingAlerts` | `unhandledAlerts` | 改名 |

**要看后端原始返回**：直接调 <http://localhost:3000/api/admin/dashboard>（需带 token）。

---

## 接口清单

全部需要管理员身份（`users.is_admin = 1`），非管理员返回 `code=403`。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/send-code` | 发送验证码 |
| POST | `/api/auth/login` | 登录 |
| GET | `/api/admin/dashboard` | 核心指标 |
| GET | `/api/admin/trend` | 趋势数据 |
| GET | `/api/admin/users` | 用户列表（分页/搜索/筛选） |
| GET | `/api/admin/users/{id}` | 用户详情 |
| POST | `/api/admin/users/set-admin` | 设为/取消管理员 |
| POST | `/api/admin/users/set-active` | 启用/禁用 |
| GET | `/api/admin/alerts` | 告警列表 |
| POST | `/api/admin/alerts/handle` | 处理告警 |
| GET | `/api/admin/feedback` | 反馈列表 |
| POST | `/api/admin/feedback/reply` | 回复反馈 |
| GET | `/api/admin/devices` | 设备列表 |
| GET | `/api/admin/system` | 系统监控 |

### 统一响应格式

```json
{ "code": 200, "msg": "ok", "data": { } }
```

**HTTP 状态码恒为 200**，判断成功看 `code === 200`。

---

## 设计令牌

| 名称 | 值 |
|---|---|
| 主色 | `#8FA68E` |
| 主色深 | `#5B9A7A` |
| 背景 | `#F4EFE6` |
| 卡片 | `#FFF9EF` |
| 主文字 | `#2F2A24` |
| 次文字 | `#7D746B` |
| 警告 | `#D4A574` |
| 危险 | `#E55A5A` |
| 圆角 | `12px` |

与小程序端保持一致的暖米绿风格。

---

## 常见问题

### 页面数据全是 0 / 请求失败

**后端没启动。** 先跑 `start.bat`，确认 <http://localhost:3000/api/health> 返回 `status: UP`。

### 登录提示「该账号不是管理员」

这个手机号在 `users` 表里 `is_admin = 0`。改一下：

```sql
USE znwg;
UPDATE users SET is_admin = 1 WHERE phone = '你的手机号';
```

### 端口 5177 被占用

`vite.config.ts` 里设了 `strictPort: true`，会直接报错而不是偷偷换端口。
关掉占用进程，或改 `server.port`。

### VSCode 里 Vue 文件报错

装 **Vue (Official)** 扩展（`Vue.volar`）。

---

## 构建部署

```powershell
npm run build      # 产物在 dist/
npm run preview    # 本地预览产物
```

**部署时**把 `dist/` 交给 Nginx，并把 `/api` 反向代理到后端 3000 端口。

---

## 待处理

| 事项 | 说明 |
|---|---|
| 用户详情里的设备列表 | 后端 `/admin/users/{id}` 只返回设备数量，没返回设备数组，页面显示 0 台 |
| 反馈按状态筛选 | 后端不支持 `handled` 参数，目前在前端过滤 |
| 新增用户 | 后端暂无此接口，需先补 |
