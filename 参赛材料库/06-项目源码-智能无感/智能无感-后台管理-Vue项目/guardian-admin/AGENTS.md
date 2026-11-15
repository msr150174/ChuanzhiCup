# AGENTS.md

## 项目概览
- 项目：AI 节律守护 · 运营管理后台（guardian-admin）。
- 技术栈：Vue 3 `<script setup>`、TypeScript、Vite、Vue Router、Pinia、Element Plus、ECharts、SCSS、Axios。
- 运行形态：Node/Express 同时承载真实内存数据后端与 Vite 开发中间件；生产环境托管 `dist`。
- 设计基调：暖米色页面、奶油侧边栏、鼠尾草绿主色、12px 圆角、低存在感暖阴影。

## 常用命令
- 安装依赖：`pnpm install`
- 开发运行：`pnpm run dev`
- 类型检查与构建：`pnpm run build`
- 生产启动：`pnpm run start`
- 本项目统一使用 pnpm，不使用 npm 或 yarn。

## 目录与职责
- `src/api/index.ts`：唯一 Axios 请求出口，组件禁止直接写 axios。
- `src/types/index.ts`：接口、查询参数、页面实体类型定义，禁止 `any`。
- `src/stores/user.ts`：管理员 token 与用户资料状态。
- `src/router/index.ts`：路由懒加载、登录与管理员守卫。
- `src/layouts/AdminLayout.vue`：侧边栏、顶部栏和后台内容骨架。
- `src/views/`：登录、数据总览、用户、告警、反馈、设备、系统监控页面。
- `src/styles/index.scss`：全局主题、Element Plus 视觉覆盖、页面布局样式。
- `server/`：真实 Express API 与种子数据，不使用 Mock 响应。

## API 约定
- 统一响应：`{ code, msg, data }`，HTTP 状态码通常为 200。
- 成功判断必须使用 `code === 200`。
- token 存储键：`guardian_admin_token`；请求头：`Authorization: Bearer {token}`。
- 401 清 token 并跳转登录；403 提示“无权限”。
- 管理员测试账号：`13800000001`；开发固定验证码：`246810`，动态验证码也会打印在服务端日志。

## 开发规范
- 所有 Vue 组件使用 `<script setup lang="ts">`。
- 写操作（管理员身份、账号状态、告警处理、反馈回复、退出登录）必须二次确认，成功后提示并刷新数据。
- 用户、告警、反馈列表使用后端分页；分页大小为 10/20/50/100。
- 系统监控 30 秒静默刷新，组件卸载时清理定时器。
- ECharts 实例使用 `shallowRef`，卸载时断开 ResizeObserver 并 dispose。
- 时间数字使用 `font-variant-numeric: tabular-nums`。

## 验证要点
- 构建需通过 `vue-tsc` 与 Vite build。
- 变更 API 后用真实 HTTP 请求覆盖：认证、仪表盘、趋势、用户列表/详情/写操作、告警列表/处理、反馈列表/回复、设备、系统监控。
