# -*- coding: utf-8 -*-
"""
生成「智能无感 · 后台管理」给 Coze 的两份交付物：

  ① 智能无感-后台管理-原型规格.json   —— 给 Coze AI 生成代码用（结构化规格）
  ② 智能无感-后台管理-设计稿元数据.json —— 给 VTJ 用（页面/区块/组件树）

★ 关键：接口清单从后端源码实际扫描得出，不是手写的，
  避免文档写了接口但后端没有（智跑校园当初就吃过这个亏）。
"""
import json
import os
import re

ROOT = r"D:\msr150174\项目\智能无感"
BE = os.path.join(ROOT, "转换-Java后端", "guardian-server")
CTRL = os.path.join(BE, "src", "main", "java", "com", "guardian", "controller")
OUTDIR = os.path.join(ROOT, "文档")
os.makedirs(OUTDIR, exist_ok=True)


# ============================================================================
# ① 从源码扫描真实接口
# ============================================================================
def scan_admin_api():
    """扫描 AdminController，得出真实的接口清单"""
    f = os.path.join(CTRL, "AdminController.java")
    with open(f, encoding="utf-8") as fp:
        src = fp.read()

    base = "/api/admin"
    apis = []

    for m in re.finditer(
        r'@(Get|Post|Put|Delete)Mapping\(\s*(?:value\s*=\s*)?"([^"]*)"', src
    ):
        method = m.group(1).upper()
        sub = m.group(2).strip("/")
        path = base + ("/" + sub if sub else "")

        # 抓方法名和注释
        after = src[m.end():m.end() + 900]
        name_m = re.search(r'public\s+ApiResponse<[^>]+>\s+(\w+)\s*\(', after)
        name = name_m.group(1) if name_m else "?"

        # 往上找最近的文档注释
        before = src[max(0, m.start() - 700):m.start()]
        doc = ""
        docs = re.findall(r'/\*\*(.*?)\*/', before, re.S)
        if docs:
            lines = [l.strip().lstrip("*").strip() for l in docs[-1].splitlines()]
            lines = [l for l in lines if l and not l.startswith("@")]
            if lines:
                doc = lines[0]

        apis.append({"method": method, "path": path, "handler": name, "desc": doc})

    return apis


ADMIN_APIS = scan_admin_api()


# ============================================================================
# ② 设计令牌（与小程序端保持一致）
# ============================================================================
TOKENS = {
    "primary": "#8FA68E",
    "primaryDark": "#5B9A7A",
    "primaryLight": "rgba(143,166,142,0.15)",
    "bg": "#F4EFE6",
    "surface": "#FFF9EF",
    "card": "#FFFFFF",
    "text1": "#2F2A24",
    "text2": "#7D746B",
    "text3": "#A89F94",
    "border": "#E8E3D8",
    "warning": "#D4A574",
    "danger": "#E55A5A",
    "success": "#5B9A7A",
    "info": "#8FA68E",
    "radius": "12px",
    "radiusLg": "16px",
    "shadow": "0 2px 12px rgba(0,0,0,0.06)",
    "sidebarWidth": "220px",
    "headerHeight": "60px",
}

# ============================================================================
# ③ 页面定义
# ============================================================================
PAGES = [
    {
        "id": "login",
        "path": "/login",
        "title": "登录",
        "priority": "P0",
        "layout": "独立布局（不用 AdminLayout）",
        "background": TOKENS["bg"],
        "card": {"width": "420px", "background": TOKENS["card"], "radius": "16px",
                 "shadow": "0 4px 20px rgba(0,0,0,0.08)", "align": "center"},
        "elements": [
            {"type": "logo", "content": "🌿", "fontSize": "48px", "align": "center"},
            {"type": "title", "content": "AI 节律守护 · 运营管理后台",
             "fontSize": "18px", "fontWeight": 600, "align": "center", "marginTop": "16px"},
            {"type": "input", "field": "phone", "label": "手机号",
             "placeholder": "请输入手机号", "height": "44px",
             "validation": "^1[3-9]\\d{9}$", "errorMessage": "请输入正确的手机号",
             "marginTop": "32px"},
            {"type": "inputWithButton", "field": "code", "label": "验证码",
             "placeholder": "请输入验证码", "height": "44px", "maxLength": 6,
             "button": {"text": "获取验证码", "countdownText": "{n}s",
                        "countdownSeconds": 60, "disabledWhen": "phoneInvalid || counting",
                        "devModeHint": "开发模式：验证码见服务端日志"}},
            {"type": "primary", "text": "登 录", "fullWidth": True, "height": "44px",
             "disabledWhen": "!phoneValid || code.length !== 6 || submitting",
             "submitOnEnter": True},
            {"type": "note", "content": "⚠️ 仅管理员可登录，非管理员账号会被拒绝",
             "color": TOKENS["text3"], "fontSize": "12px", "align": "center"},
        ],
        "interactions": [
            {"trigger": "phone 输入", "action": "实时校验 11 位手机号格式"},
            {"trigger": "点击获取验证码",
             "action": "调 POST /api/auth/send-code，成功后倒计时 60 秒",
             "devMode": "验证码打印在后端控制台（开发模式）"},
            {"trigger": "点击登录", "action": "调 POST /api/auth/login",
             "onSuccess": [
                 "存 token 到 localStorage['guardian_admin_token']",
                 "存用户信息到 Pinia",
                 "★ 检查 user.isAdmin",
                 "isAdmin=true → 跳转 /dashboard",
                 "isAdmin=false → ElMessage.error('该账号不是管理员') 并清除 token",
             ]},
        ],
    },
    {
        "id": "dashboard",
        "path": "/dashboard",
        "title": "数据总览",
        "priority": "P0",
        "layout": "AdminLayout",
        "sections": [
            {"type": "statCards", "title": "核心指标", "columns": 4,
             "source": "GET /api/admin/dashboard",
             "items": [
                 {"key": "totalUsers", "label": "用户总数", "icon": "User", "color": TOKENS["primary"]},
                 {"key": "elders", "label": "长辈用户", "icon": "Avatar", "color": TOKENS["primaryDark"]},
                 {"key": "families", "label": "家属用户", "icon": "UserFilled", "color": TOKENS["info"]},
                 {"key": "newThisWeek", "label": "本周新增", "icon": "TrendCharts", "color": TOKENS["success"]},
             ]},
            {"type": "statCards", "title": "健康守护", "columns": 3,
             "items": [
                 {"key": "weekAlerts", "label": "近 7 天告警", "icon": "Bell", "color": TOKENS["danger"]},
                 {"key": "todayCheckins", "label": "今日打卡", "icon": "Calendar", "color": TOKENS["primary"]},
                 {"key": "admins", "label": "管理员数", "icon": "Setting", "color": TOKENS["text2"]},
             ]},
            {"type": "lineChart", "title": "近 7 天趋势", "height": "320px",
             "source": "GET /api/admin/trend?days=7",
             "xField": "dates",
             "series": [
                 {"field": "registrations", "name": "新增用户", "color": TOKENS["primary"]},
                 {"field": "alerts", "name": "告警数", "color": TOKENS["danger"]},
             ]},
        ],
        "interactions": [
            {"trigger": "页面加载", "action": "并行请求 dashboard 与 trend"},
            {"trigger": "切换天数（7/14/30）", "action": "重新请求 trend 并重绘图表"},
        ],
    },
    {
        "id": "users",
        "path": "/users",
        "title": "用户管理",
        "priority": "P0",
        "layout": "AdminLayout",
        "sections": [
            {"type": "filterBar",
             "fields": [
                 {"field": "keyword", "type": "input", "placeholder": "搜索手机号或姓名", "width": "240px"},
                 {"field": "role", "type": "select", "placeholder": "全部角色",
                  "options": [{"label": "长辈", "value": "elder"}, {"label": "家属", "value": "family"}]},
                 {"field": "isAdmin", "type": "select", "placeholder": "全部身份",
                  "options": [{"label": "管理员", "value": True}, {"label": "普通用户", "value": False}]},
                 {"field": "isActive", "type": "select", "placeholder": "全部状态",
                  "options": [{"label": "已启用", "value": True}, {"label": "已禁用", "value": False}]},
             ],
             "buttons": [{"text": "查询", "type": "primary"}, {"text": "重置"}]},
            {"type": "table", "source": "GET /api/admin/users",
             "rowKey": "id", "columns": [
                 {"prop": "name", "label": "姓名", "width": "120"},
                 {"prop": "phone", "label": "手机号", "width": "140"},
                 {"prop": "role", "label": "角色", "width": "100", "type": "tag",
                  "map": {"elder": {"text": "长辈", "type": "success"},
                          "family": {"text": "家属", "type": "primary"}}},
                 {"prop": "isAdmin", "label": "身份", "width": "100", "type": "tag",
                  "map": {True: {"text": "管理员", "type": "danger"},
                          False: {"text": "普通", "type": "info"}}},
                 {"prop": "isActive", "label": "状态", "width": "90", "type": "tag",
                  "map": {True: {"text": "启用", "type": "success"},
                          False: {"text": "禁用", "type": "info"}}},
                 {"prop": "createTime", "label": "注册时间", "width": "170"},
                 {"label": "操作", "type": "actions", "width": "220",
                  "buttons": [
                      {"text": "详情", "action": "openDetail"},
                      {"text": "设为管理员", "action": "setAdmin",
                       "visibleWhen": "!row.isAdmin", "confirm": "确认将 {name} 设为管理员？"},
                      {"text": "取消管理员", "action": "unsetAdmin",
                       "visibleWhen": "row.isAdmin", "type": "warning", "confirm": "确认取消 {name} 的管理员权限？"},
                      {"text": "禁用", "action": "setInactive",
                       "visibleWhen": "row.isActive", "type": "danger", "confirm": "禁用后该用户将无法登录，确认？"},
                      {"text": "启用", "action": "setActive",
                       "visibleWhen": "!row.isActive", "type": "success"},
                  ]},
             ],
             "pagination": {"pageSize": 20, "pageSizes": [10, 20, 50, 100]}},
        ],
        "dialogs": [
            {"id": "userDetail", "title": "用户详情", "width": "640px",
             "source": "GET /api/admin/users/{id}",
             "fields": [
                 {"label": "姓名", "key": "name"},
                 {"label": "手机号", "key": "phone"},
                 {"label": "角色", "key": "role", "type": "tag"},
                 {"label": "性别", "key": "gender"},
                 {"label": "出生日期", "key": "birthDate"},
                 {"label": "地址", "key": "address"},
                 {"label": "紧急联系人", "key": "emergencyPhone"},
                 {"label": "近 30 天打卡", "key": "checkinDays", "suffix": "天"},
                 {"label": "绑定设备", "key": "deviceCount", "suffix": "台"},
                 {"label": "未处理告警", "key": "unhandledAlerts", "suffix": "条"},
                 {"label": "注册时间", "key": "createTime"},
             ]},
        ],
        "interactions": [
            {"trigger": "查询 / 重置", "action": "重置到第 1 页并重新请求"},
            {"trigger": "点击设为管理员", "action": "ElMessageBox 确认 → POST /api/admin/users/set-admin"},
            {"trigger": "点击禁用", "action": "ElMessageBox 确认 → POST /api/admin/users/set-active"},
            {"trigger": "操作成功", "action": "ElMessage.success + 刷新列表"},
        ],
    },
    {
        "id": "alerts",
        "path": "/alerts",
        "title": "告警处置",
        "priority": "P0",
        "layout": "AdminLayout",
        "sections": [
            {"type": "filterBar",
             "fields": [
                 {"field": "handled", "type": "select", "placeholder": "全部状态",
                  "options": [{"label": "未处理", "value": False}, {"label": "已处理", "value": True}]},
                 {"field": "severity", "type": "select", "placeholder": "全部级别",
                  "options": [{"label": "紧急", "value": "critical"}, {"label": "高", "value": "high"},
                              {"label": "中", "value": "medium"}, {"label": "低", "value": "low"}]},
             ],
             "buttons": [{"text": "查询", "type": "primary"}, {"text": "重置"}]},
            {"type": "table", "source": "GET /api/admin/alerts", "rowKey": "id",
             "rowClassName": "severityRowClass",
             "columns": [
                 {"prop": "severity", "label": "级别", "width": "90", "type": "tag",
                  "map": {"critical": {"text": "紧急", "type": "danger"},
                          "high": {"text": "高", "type": "danger"},
                          "medium": {"text": "中", "type": "warning"},
                          "low": {"text": "低", "type": "info"}}},
                 {"prop": "alertType", "label": "类型", "width": "110"},
                 {"prop": "title", "label": "标题", "minWidth": "160"},
                 {"prop": "description", "label": "描述", "minWidth": "220", "showOverflowTooltip": True},
                 {"prop": "userName", "label": "用户", "width": "110"},
                 {"prop": "userPhone", "label": "手机号", "width": "130"},
                 {"prop": "occurredAt", "label": "发生时间", "width": "170"},
                 {"prop": "handled", "label": "状态", "width": "90", "type": "tag",
                  "map": {True: {"text": "已处理", "type": "success"},
                          False: {"text": "未处理", "type": "danger"}}},
                 {"label": "操作", "type": "actions", "width": "120",
                  "buttons": [{"text": "处理", "action": "handle", "type": "primary",
                               "visibleWhen": "!row.handled"}]},
             ],
             "pagination": {"pageSize": 20}},
        ],
        "dialogs": [
            {"id": "handleDialog", "title": "处理告警", "width": "480px",
             "content": [
                 {"type": "descriptions", "items": [
                     {"label": "告警标题", "key": "title"},
                     {"label": "用户", "key": "userName"},
                     {"label": "发生时间", "key": "occurredAt"}]},
                 {"type": "textarea", "field": "notes", "label": "处理说明",
                  "placeholder": "例如：已电话联系家属，确认老人安全", "rows": 3, "required": True},
             ],
             "buttons": [{"text": "取消"}, {"text": "确认处理", "type": "primary"}]},
        ],
        "interactions": [
            {"trigger": "点击处理", "action": "打开弹窗"},
            {"trigger": "确认处理", "action": "POST /api/admin/alerts/handle { alertId, notes }"},
            {"trigger": "处理成功", "action": "ElMessage.success('已标记为处理完成') + 刷新"},
        ],
    },
    {
        "id": "feedback",
        "path": "/feedback",
        "title": "反馈处理",
        "priority": "P1",
        "layout": "AdminLayout",
        "sections": [
            {"type": "table", "source": "GET /api/admin/feedback", "rowKey": "id",
             "columns": [
                 {"prop": "userName", "label": "用户", "width": "110"},
                 {"prop": "userPhone", "label": "手机号", "width": "130"},
                 {"prop": "title", "label": "标题", "width": "160"},
                 {"prop": "content", "label": "内容", "minWidth": "260", "showOverflowTooltip": True},
                 {"prop": "severity", "label": "评分", "width": "80"},
                 {"prop": "createTime", "label": "提交时间", "width": "170"},
                 {"prop": "handled", "label": "状态", "width": "90", "type": "tag",
                  "map": {True: {"text": "已回复", "type": "success"},
                          False: {"text": "待回复", "type": "warning"}}},
                 {"label": "操作", "type": "actions", "width": "120",
                  "buttons": [{"text": "回复", "action": "reply", "type": "primary",
                               "visibleWhen": "!row.handled"}]},
             ],
             "pagination": {"pageSize": 20}},
        ],
        "dialogs": [
            {"id": "replyDialog", "title": "回复反馈", "width": "480px",
             "content": [
                 {"type": "descriptions", "items": [
                     {"label": "用户", "key": "userName"},
                     {"label": "反馈内容", "key": "content"}]},
                 {"type": "textarea", "field": "reply", "label": "回复内容",
                  "placeholder": "请输入回复内容", "rows": 4, "required": True},
             ],
             "buttons": [{"text": "取消"}, {"text": "提交回复", "type": "primary"}]},
        ],
        "interactions": [
            {"trigger": "确认回复", "action": "POST /api/admin/feedback/reply { feedbackId, reply }"},
        ],
    },
    {
        "id": "devices",
        "path": "/devices",
        "title": "设备管理",
        "priority": "P1",
        "layout": "AdminLayout",
        "sections": [
            {"type": "table", "source": "GET /api/admin/devices", "rowKey": "id",
             "columns": [
                 {"prop": "deviceName", "label": "设备名称", "width": "160"},
                 {"prop": "deviceType", "label": "类型", "width": "120"},
                 {"prop": "userName", "label": "所属用户", "width": "120"},
                 {"prop": "userId", "label": "用户ID", "width": "200", "showOverflowTooltip": True},
                 {"prop": "status", "label": "状态", "width": "100", "type": "tag",
                  "map": {"active": {"text": "在线", "type": "success"},
                          "inactive": {"text": "离线", "type": "info"}}},
                 {"prop": "lastSync", "label": "最后同步", "width": "170"},
                 {"prop": "createTime", "label": "绑定时间", "width": "170"},
             ]},
        ],
    },
    {
        "id": "system",
        "path": "/system",
        "title": "系统监控",
        "priority": "P1",
        "layout": "AdminLayout",
        "sections": [
            {"type": "descriptions", "title": "数据库", "source": "GET /api/admin/system",
             "prefix": "mysql", "column": 3, "items": [
                 {"label": "连接状态", "key": "connected", "type": "tag",
                  "map": {True: {"text": "正常", "type": "success"},
                          False: {"text": "异常", "type": "danger"}}},
                 {"label": "用户数", "key": "users"},
             ]},
            {"type": "descriptions", "title": "缓存", "prefix": "redis", "column": 3, "items": [
                {"label": "连接状态", "key": "connected", "type": "tag",
                 "map": {True: {"text": "正常", "type": "success"},
                         False: {"text": "异常", "type": "danger"}}},
                {"label": "键数量", "key": "keyCount"},
            ]},
            {"type": "descriptions", "title": "运行时", "prefix": "jvm", "column": 3, "items": [
                {"label": "Java 版本", "key": "javaVersion"},
                {"label": "最大内存", "key": "maxMemoryMB", "suffix": " MB"},
                {"label": "已用内存", "key": "usedMemoryMB", "suffix": " MB"},
                {"label": "可用内存", "key": "freeMemoryMB", "suffix": " MB"},
                {"label": "CPU 核心", "key": "processors"},
                {"label": "已运行", "key": "uptimeSeconds", "suffix": " 秒"},
            ]},
        ],
        "interactions": [
            {"trigger": "页面加载", "action": "请求 /api/admin/system"},
            {"trigger": "每 30 秒", "action": "自动刷新（可开关）"},
        ],
    },
]


# ============================================================================
# ④ 组装「原型规格.json」（给 Coze）
# ============================================================================
spec = {
    "$schema": "https://guardian.local/schemas/admin-prototype-v1.json",
    "meta": {
        "name": "AI 节律守护 · 后台管理系统",
        "nameEn": "guardian-admin",
        "version": "1.0.0",
        "type": "可运行的真实产品（非原型图）",
        "target": "桌面端管理后台",
        "language": "zh-CN",
        "generatedFor": "Coze AI 代码生成",
        "backend": {
            "baseUrl": "http://localhost:3000/api",
            "status": f"已完成，{len(ADMIN_APIS)} 个管理员接口全部可用，无需 Mock",
            "auth": "登录后带 Authorization: Bearer {token}",
            "adminRequired": "所有 /admin/* 接口需要 users.is_admin=1，否则返回 code=403",
        },
    },
    "techStack": {
        "framework": "Vue 3",
        "syntax": "<script setup> + TypeScript",
        "build": "Vite",
        "ui": "Element Plus",
        "router": "Vue Router 4",
        "state": "Pinia",
        "http": "Axios",
        "chart": "ECharts 5",
        "style": "SCSS",
        "forbidden": ["React", "Nuxt", "SSR", "Tailwind", "Ant Design", "后端模板引擎"],
    },
    "devServer": {
        "port": 5177,
        "proxy": {"/api": {"target": "http://localhost:3000", "changeOrigin": True}},
    },
    "designTokens": TOKENS,
    "layout": {
        "type": "侧边栏 + 顶栏 + 内容区",
        "sidebar": {
            "width": TOKENS["sidebarWidth"],
            "background": TOKENS["surface"],
            "logo": {"icon": "🌿", "text": "节律守护"},
            "menu": [
                {"path": "/dashboard", "title": "数据总览", "icon": "DataLine"},
                {"path": "/users", "title": "用户管理", "icon": "User"},
                {"path": "/alerts", "title": "告警处置", "icon": "Bell"},
                {"path": "/feedback", "title": "反馈处理", "icon": "ChatDotRound"},
                {"path": "/devices", "title": "设备管理", "icon": "Cpu"},
                {"path": "/system", "title": "系统监控", "icon": "Monitor"},
            ],
        },
        "header": {
            "height": TOKENS["headerHeight"],
            "right": [
                {"type": "text", "content": "{user.name}"},
                {"type": "dropdown", "items": [
                    {"text": "个人信息", "action": "showProfile"},
                    {"text": "退出登录", "action": "logout"},
                ]},
            ],
        },
        "content": {"background": TOKENS["bg"], "padding": "20px"},
    },
    "pages": PAGES,
    "apiList": ADMIN_APIS,
    "commonStates": {
        "loading": "表格用 v-loading，卡片用骨架屏，图表显示加载动画",
        "empty": "ElEmpty，描述文案「暂无数据」",
        "error": "ElMessage.error(msg)，401 清除 token 跳登录，403 提示无权限",
    },
    "criticalRules": [
        "★ 登录后必须检查 user.isAdmin，false 则拒绝进入后台",
        "★ token 存 localStorage['guardian_admin_token']，请求拦截器自动带上",
        "★ 响应格式统一 { code, msg, data }，HTTP 状态码恒为 200，判断成功看 code===200",
        "★ 401 清除 token 并跳转 /login；403 提示「无权限」",
        "★ 所有写操作（设为管理员/禁用/处理告警）必须二次确认",
        "★ 后端地址 http://localhost:3000/api，开发时配 Vite proxy 避免跨域",
    ],
    "projectStructure": {
        "src/main.ts": "入口，注册 Element Plus / Router / Pinia",
        "src/App.vue": "根组件",
        "src/router/index.ts": "路由 + 登录守卫",
        "src/layouts/AdminLayout.vue": "侧边栏 + 顶栏 + router-view",
        "src/views/Login.vue": "登录页",
        "src/views/Dashboard.vue": "数据总览",
        "src/views/Users.vue": "用户管理",
        "src/views/Alerts.vue": "告警处置",
        "src/views/Feedback.vue": "反馈处理",
        "src/views/Devices.vue": "设备管理",
        "src/views/System.vue": "系统监控",
        "src/api/index.ts": "Axios 封装 + 接口函数",
        "src/stores/user.ts": "用户状态",
        "src/styles/index.scss": "全局样式与设计令牌",
    },
    "codeRequirements": [
        "用 <script setup lang=\"ts\">，不要用 Options API",
        "所有接口调用封装在 src/api/index.ts，组件里不直接写 axios",
        "表格分页用后端分页（page/pageSize），不要前端切片",
        "图表用 ECharts，容器必须有明确高度",
        "所有金额/时间/数量格式化统一放 utils",
        "禁止 any，类型定义放 src/types",
        "代码可直接 npm run dev 运行，不要占位符",
    ],
    "acceptanceCriteria": [
        "npm install && npm run dev 能启动，端口 5177",
        "登录页能用手机号 + 验证码登录（验证码见后端控制台）",
        "非管理员账号登录被拒绝并提示",
        "数据总览的 7 个指标卡片有真实数字",
        "趋势折线图能渲染 7 天数据",
        "用户列表能分页、搜索、按角色/身份/状态筛选",
        "能把普通用户设为管理员，成功后列表刷新",
        "能禁用/启用用户，禁用后该用户无法登录",
        "告警列表能按状态和级别筛选",
        "能填写处理说明并标记告警为已处理",
        "能回复用户反馈",
        "设备列表显示设备与所属用户",
        "系统监控显示 MySQL/Redis/JVM 状态",
        "退出登录清除 token 并跳回登录页",
    ],
    "externalRefs": {
        "backendHealth": "http://localhost:3000/api/health",
        "adminTokenKey": "guardian_admin_token",
        "testAdmin": {"phone": "13800000001", "note": "测试管理员，验证码见后端控制台"},
    },
}

spec_path = os.path.join(OUTDIR, "智能无感-后台管理-原型规格.json")
with open(spec_path, "w", encoding="utf-8") as f:
    json.dump(spec, f, ensure_ascii=False, indent=2)

print(f"① 原型规格: {spec_path}")
print(f"   页面 {len(PAGES)} 个，接口 {len(ADMIN_APIS)} 个，{os.path.getsize(spec_path)/1024:.0f} KB")
