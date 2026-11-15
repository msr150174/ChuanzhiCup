# -*- coding: utf-8 -*-
"""
生成「智能无感 · 后台管理」的 VTJ 设计稿元数据 JSON。

VTJ 的 DSL 结构（官方规范）：
  ProjectSchema → PageFile[] → BlockSchema → NodeSchema

本文件按该结构组织，并额外保留一份 uiSpec（页面语义描述），
方便人阅读和给 Coze 参考。
"""
import json
import os
import uuid

ROOT = r"D:\msr150174\项目\智能无感"
DOCS = os.path.join(ROOT, "文档")

TOKENS = {
    "primary": "#8FA68E",
    "primaryDark": "#5B9A7A",
    "bg": "#F4EFE6",
    "surface": "#FFF9EF",
    "card": "#FFFFFF",
    "text1": "#2F2A24",
    "text2": "#7D746B",
    "border": "#E8E3D8",
    "warning": "#D4A574",
    "danger": "#E55A5A",
}


def rid(p=""):
    return p + uuid.uuid4().hex[:10]


def node(name, props=None, children=None, events=None):
    """构造一个 NodeSchema"""
    return {
        "id": rid("n"),
        "name": name,
        "from": name,
        "props": props or {},
        "events": events or {},
        "directives": [],
        "children": children or [],
    }


# ============================================================================
# 页面 → 节点树
# ============================================================================
def build_login():
    return [
        node("ElCard", {"class": "login-card", "shadow": "always"}, [
            node("div", {"class": "logo"}, [node("text", {"content": "🌿"})]),
            node("h2", {"class": "title", "style": "text-align:center"}, "AI 节律守护 · 运营管理后台"),
            node("ElForm", {"model": "form", "labelPosition": "top"}, [
                node("ElFormItem", {"label": "手机号"}, [
                    node("ElInput", {"v-model": "form.phone", "placeholder": "请输入手机号",
                                     "maxlength": 11, "clearable": True}),
                ]),
                node("ElFormItem", {"label": "验证码"}, [
                    node("div", {"class": "code-row"}, [
                        node("ElInput", {"v-model": "form.code", "placeholder": "请输入验证码",
                                         "maxlength": 6}),
                        node("ElButton", {"type": "primary", "plain": True,
                                          ":disabled": "!phoneValid || counting",
                                          "@click": "sendCode"}, "获取验证码"),
                    ]),
                ]),
                node("ElButton", {"type": "primary", ":loading": "submitting",
                                  "style": "width:100%", "@click": "submit"}, "登 录"),
                node("p", {"class": "note"}, "⚠️ 仅管理员可登录"),
            ]),
        ]),
    ]


def build_dashboard():
    return [
        node("div", {"class": "page-dashboard"}, [
            node("ElRow", {":gutter": 16}, [
                node("ElCol", {":span": 6}, [stat_card("用户总数", "overview.totalUsers", "User", TOKENS["primary"])]),
                node("ElCol", {":span": 6}, [stat_card("长辈用户", "overview.elders", "Avatar", TOKENS["primaryDark"])]),
                node("ElCol", {":span": 6}, [stat_card("家属用户", "overview.families", "UserFilled", TOKENS["primary"])]),
                node("ElCol", {":span": 6}, [stat_card("本周新增", "overview.newThisWeek", "TrendCharts", TOKENS["primaryDark"])]),
            ]),
            node("ElRow", {":gutter": 16, "style": "margin-top:16px"}, [
                node("ElCol", {":span": 8}, [stat_card("近 7 天告警", "overview.weekAlerts", "Bell", TOKENS["danger"])]),
                node("ElCol", {":span": 8}, [stat_card("今日打卡", "overview.todayCheckins", "Calendar", TOKENS["primary"])]),
                node("ElCol", {":span": 8}, [stat_card("管理员数", "overview.admins", "Setting", TOKENS["text2"])]),
            ]),
            node("ElCard", {"style": "margin-top:16px"}, [
                node("template", {"#header": ""}, [node("span", {}, "近 7 天趋势")]),
                node("div", {"ref": "chartRef", "style": "height:320px"}),
            ]),
        ]),
    ]


def stat_card(label, value_expr, icon, color):
    return node("ElCard", {"class": "stat-card", "shadow": "hover"}, [
        node("div", {"class": "stat-inner"}, [
            node("ElIcon", {"class": "stat-icon", "style": f"color:{color}"}, [node("component", {":is": icon})]),
            node("div", {"class": "stat-text"}, [
                node("div", {"class": "stat-label"}, label),
                node("div", {"class": "stat-value"}, "{{ " + value_expr + " }}"),
            ]),
        ]),
    ])


def build_users():
    return [
        node("div", {"class": "page-users"}, [
            node("ElCard", {}, [
                node("ElForm", {":inline": True, "model": "query"}, [
                    node("ElFormItem", {}, [node("ElInput", {"v-model": "query.keyword",
                                                             "placeholder": "搜索手机号或姓名", "clearable": True})]),
                    node("ElFormItem", {}, [node("ElSelect", {"v-model": "query.role", "placeholder": "全部角色", "clearable": True}, [
                        node("ElOption", {"label": "长辈", "value": "elder"}),
                        node("ElOption", {"label": "家属", "value": "family"}),
                    ])]),
                    node("ElFormItem", {}, [node("ElSelect", {"v-model": "query.isAdmin", "placeholder": "全部身份", "clearable": True}, [
                        node("ElOption", {"label": "管理员", ":value": True}),
                        node("ElOption", {"label": "普通用户", ":value": False}),
                    ])]),
                    node("ElFormItem", {}, [
                        node("ElButton", {"type": "primary", "@click": "search"}, "查询"),
                        node("ElButton", {"@click": "reset"}, "重置"),
                    ]),
                ]),
            ]),
            node("ElCard", {"style": "margin-top:16px"}, [
                node("ElTable", {":data": "list", "v-loading": "loading", "stripe": True}, [
                    node("ElTableColumn", {"prop": "name", "label": "姓名", "width": 120}),
                    node("ElTableColumn", {"prop": "phone", "label": "手机号", "width": 140}),
                    node("ElTableColumn", {"prop": "role", "label": "角色", "width": 100}, [
                        node("template", {"#default": "scope"}, [
                            node("ElTag", {":type": "scope.row.role === 'elder' ? 'success' : 'primary'"}, "{{ scope.row.role === 'elder' ? '长辈' : '家属' }}"),
                        ]),
                    ]),
                    node("ElTableColumn", {"prop": "isAdmin", "label": "身份", "width": 100}, [
                        node("template", {"#default": "scope"}, [
                            node("ElTag", {":type": "scope.row.isAdmin ? 'danger' : 'info'"}, "{{ scope.row.isAdmin ? '管理员' : '普通' }}"),
                        ]),
                    ]),
                    node("ElTableColumn", {"prop": "isActive", "label": "状态", "width": 90}, [
                        node("template", {"#default": "scope"}, [
                            node("ElTag", {":type": "scope.row.isActive ? 'success' : 'info'"}, "{{ scope.row.isActive ? '启用' : '禁用' }}"),
                        ]),
                    ]),
                    node("ElTableColumn", {"prop": "createTime", "label": "注册时间", "width": 170}),
                    node("ElTableColumn", {"label": "操作", "width": 260, "fixed": "right"}, [
                        node("template", {"#default": "scope"}, [
                            node("ElButton", {"size": "small", "@click": "openDetail(scope.row)"}, "详情"),
                            node("ElButton", {":size": "small", "type": "warning", "v-if": "!scope.row.isAdmin",
                                              "@click": "setAdmin(scope.row, true)"}, "设为管理员"),
                            node("ElButton", {":size": "small", "type": "info", "v-if": "scope.row.isAdmin",
                                              "@click": "setAdmin(scope.row, false)"}, "取消管理员"),
                            node("ElButton", {":size": "small", "type": "danger", "v-if": "scope.row.isActive",
                                              "@click": "setActive(scope.row, false)"}, "禁用"),
                            node("ElButton", {":size": "small", "type": "success", "v-if": "!scope.row.isActive",
                                              "@click": "setActive(scope.row, true)"}, "启用"),
                        ]),
                    ]),
                ]),
                node("ElPagination", {"style": "margin-top:16px;justify-content:flex-end",
                                      ":current-page": "page", ":page-size": "pageSize",
                                      ":total": "total", "layout": "total, sizes, prev, pager, next, jumper",
                                      "@current-change": "onPageChange"}, ),
            ]),
            node("ElDialog", {"v-model": "detailVisible", "title": "用户详情", "width": "640px"}, [
                node("ElDescriptions", {":column": 2, "border": True}, [
                    node("ElDescriptionsItem", {"label": "姓名"}, "{{ detail.name }}"),
                    node("ElDescriptionsItem", {"label": "手机号"}, "{{ detail.phone }}"),
                    node("ElDescriptionsItem", {"label": "角色"}, "{{ detail.role }}"),
                    node("ElDescriptionsItem", {"label": "近 30 天打卡"}, "{{ detail.checkinDays }} 天"),
                    node("ElDescriptionsItem", {"label": "绑定设备"}, "{{ detail.deviceCount }} 台"),
                    node("ElDescriptionsItem", {"label": "未处理告警"}, "{{ detail.unhandledAlerts }} 条"),
                    node("ElDescriptionsItem", {"label": "注册时间"}, "{{ detail.createTime }}"),
                    node("ElDescriptionsItem", {"label": "地址"}, "{{ detail.address }}"),
                ]),
            ]),
        ]),
    ]


def build_alerts():
    return [
        node("div", {"class": "page-alerts"}, [
            node("ElCard", {}, [
                node("ElForm", {":inline": True, "model": "query"}, [
                    node("ElFormItem", {}, [node("ElSelect", {"v-model": "query.handled", "placeholder": "全部状态", "clearable": True}, [
                        node("ElOption", {"label": "未处理", ":value": False}),
                        node("ElOption", {"label": "已处理", ":value": True}),
                    ])]),
                    node("ElFormItem", {}, [node("ElSelect", {"v-model": "query.severity", "placeholder": "全部级别", "clearable": True}, [
                        node("ElOption", {"label": "紧急", "value": "critical"}),
                        node("ElOption", {"label": "高", "value": "high"}),
                        node("ElOption", {"label": "中", "value": "medium"}),
                        node("ElOption", {"label": "低", "value": "low"}),
                    ])]),
                    node("ElFormItem", {}, [
                        node("ElButton", {"type": "primary", "@click": "search"}, "查询"),
                        node("ElButton", {"@click": "reset"}, "重置"),
                    ]),
                ]),
            ]),
            node("ElCard", {"style": "margin-top:16px"}, [
                node("ElTable", {":data": "list", "v-loading": "loading", ":row-class-name": "rowClass"}, [
                    node("ElTableColumn", {"prop": "severity", "label": "级别", "width": 90}, [
                        node("template", {"#default": "scope"}, [
                            node("ElTag", {":type": "severityType(scope.row.severity)"}, "{{ severityText(scope.row.severity) }}"),
                        ]),
                    ]),
                    node("ElTableColumn", {"prop": "alertType", "label": "类型", "width": 110}),
                    node("ElTableColumn", {"prop": "title", "label": "标题", "minWidth": 160}),
                    node("ElTableColumn", {"prop": "description", "label": "描述", "minWidth": 220, "showOverflowTooltip": True}),
                    node("ElTableColumn", {"prop": "userName", "label": "用户", "width": 110}),
                    node("ElTableColumn", {"prop": "userPhone", "label": "手机号", "width": 130}),
                    node("ElTableColumn", {"prop": "occurredAt", "label": "发生时间", "width": 170}),
                    node("ElTableColumn", {"prop": "handled", "label": "状态", "width": 90}, [
                        node("template", {"#default": "scope"}, [
                            node("ElTag", {":type": "scope.row.handled ? 'success' : 'danger'"}, "{{ scope.row.handled ? '已处理' : '未处理' }}"),
                        ]),
                    ]),
                    node("ElTableColumn", {"label": "操作", "width": 120, "fixed": "right"}, [
                        node("template", {"#default": "scope"}, [
                            node("ElButton", {"size": "small", "type": "primary", "v-if": "!scope.row.handled",
                                              "@click": "openHandle(scope.row)"}, "处理"),
                        ]),
                    ]),
                ]),
                node("ElPagination", {"style": "margin-top:16px;justify-content:flex-end",
                                      ":current-page": "page", ":page-size": "pageSize",
                                      ":total": "total", "layout": "total, prev, pager, next",
                                      "@current-change": "onPageChange"}),
            ]),
            node("ElDialog", {"v-model": "handleVisible", "title": "处理告警", "width": "480px"}, [
                node("ElDescriptions", {":column": 1, "border": True}, [
                    node("ElDescriptionsItem", {"label": "告警标题"}, "{{ current.title }}"),
                    node("ElDescriptionsItem", {"label": "用户"}, "{{ current.userName }}"),
                    node("ElDescriptionsItem", {"label": "发生时间"}, "{{ current.occurredAt }}"),
                ]),
                node("ElInput", {"style": "margin-top:16px", "v-model": "notes", "type": "textarea",
                                 ":rows": 3, "placeholder": "例如：已电话联系家属，确认老人安全"}),
                node("template", {"#footer": ""}, [
                    node("ElButton", {"@click": "handleVisible = false"}, "取消"),
                    node("ElButton", {"type": "primary", "@click": "confirmHandle"}, "确认处理"),
                ]),
            ]),
        ]),
    ]


def build_simple_table(page_id, title, columns, source_desc):
    cols = []
    for c in columns:
        props = {"prop": c["prop"], "label": c["label"]}
        if "width" in c:
            props["width"] = c["width"]
        if c.get("minWidth"):
            props["minWidth"] = c["minWidth"]
        cols.append(node("ElTableColumn", props))
    return [
        node("div", {"class": f"page-{page_id}"}, [
            node("ElCard", {}, [
                node("template", {"#header": ""}, [node("span", {}, title)]),
                node("ElTable", {":data": "list", "v-loading": "loading", "stripe": True}, cols),
                node("ElPagination", {"style": "margin-top:16px;justify-content:flex-end",
                                      ":current-page": "page", ":page-size": "pageSize",
                                      ":total": "total", "layout": "total, prev, pager, next",
                                      "@current-change": "onPageChange"}),
            ]),
        ]),
    ]


def build_feedback():
    return [
        node("div", {"class": "page-feedback"}, [
            node("ElCard", {}, [
                node("ElTable", {":data": "list", "v-loading": "loading"}, [
                    node("ElTableColumn", {"prop": "userName", "label": "用户", "width": 110}),
                    node("ElTableColumn", {"prop": "userPhone", "label": "手机号", "width": 130}),
                    node("ElTableColumn", {"prop": "title", "label": "标题", "width": 160}),
                    node("ElTableColumn", {"prop": "content", "label": "内容", "minWidth": 260, "showOverflowTooltip": True}),
                    node("ElTableColumn", {"prop": "severity", "label": "评分", "width": 80}),
                    node("ElTableColumn", {"prop": "createTime", "label": "提交时间", "width": 170}),
                    node("ElTableColumn", {"prop": "handled", "label": "状态", "width": 90}, [
                        node("template", {"#default": "scope"}, [
                            node("ElTag", {":type": "scope.row.handled ? 'success' : 'warning'"}, "{{ scope.row.handled ? '已回复' : '待回复' }}"),
                        ]),
                    ]),
                    node("ElTableColumn", {"label": "操作", "width": 120, "fixed": "right"}, [
                        node("template", {"#default": "scope"}, [
                            node("ElButton", {"size": "small", "type": "primary", "v-if": "!scope.row.handled",
                                              "@click": "openReply(scope.row)"}, "回复"),
                        ]),
                    ]),
                ]),
                node("ElPagination", {"style": "margin-top:16px;justify-content:flex-end",
                                      ":current-page": "page", ":page-size": "pageSize",
                                      ":total": "total", "layout": "total, prev, pager, next",
                                      "@current-change": "onPageChange"}),
            ]),
            node("ElDialog", {"v-model": "replyVisible", "title": "回复反馈", "width": "480px"}, [
                node("ElDescriptions", {":column": 1, "border": True}, [
                    node("ElDescriptionsItem", {"label": "用户"}, "{{ current.userName }}"),
                    node("ElDescriptionsItem", {"label": "反馈内容"}, "{{ current.content }}"),
                ]),
                node("ElInput", {"style": "margin-top:16px", "v-model": "reply", "type": "textarea",
                                 ":rows": 4, "placeholder": "请输入回复内容"}),
                node("template", {"#footer": ""}, [
                    node("ElButton", {"@click": "replyVisible = false"}, "取消"),
                    node("ElButton", {"type": "primary", "@click": "confirmReply"}, "提交回复"),
                ]),
            ]),
        ]),
    ]


def build_system():
    return [
        node("div", {"class": "page-system"}, [
            node("ElCard", {}, [
                node("template", {"#header": ""}, [node("span", {}, "数据库")]),
                node("ElDescriptions", {":column": 3, "border": True}, [
                    node("ElDescriptionsItem", {"label": "连接状态"}, [
                        node("ElTag", {":type": "sys.mysql?.connected ? 'success' : 'danger'"},
                             "{{ sys.mysql?.connected ? '正常' : '异常' }}"),
                    ]),
                    node("ElDescriptionsItem", {"label": "用户数"}, "{{ sys.mysql?.users }}"),
                ]),
            ]),
            node("ElCard", {"style": "margin-top:16px"}, [
                node("template", {"#header": ""}, [node("span", {}, "缓存")]),
                node("ElDescriptions", {":column": 3, "border": True}, [
                    node("ElDescriptionsItem", {"label": "连接状态"}, [
                        node("ElTag", {":type": "sys.redis?.connected ? 'success' : 'danger'"},
                             "{{ sys.redis?.connected ? '正常' : '异常' }}"),
                    ]),
                    node("ElDescriptionsItem", {"label": "键数量"}, "{{ sys.redis?.keyCount }}"),
                ]),
            ]),
            node("ElCard", {"style": "margin-top:16px"}, [
                node("template", {"#header": ""}, [node("span", {}, "运行时")]),
                node("ElDescriptions", {":column": 3, "border": True}, [
                    node("ElDescriptionsItem", {"label": "Java 版本"}, "{{ sys.jvm?.javaVersion }}"),
                    node("ElDescriptionsItem", {"label": "最大内存"}, "{{ sys.jvm?.maxMemoryMB }} MB"),
                    node("ElDescriptionsItem", {"label": "已用内存"}, "{{ sys.jvm?.usedMemoryMB }} MB"),
                    node("ElDescriptionsItem", {"label": "可用内存"}, "{{ sys.jvm?.freeMemoryMB }} MB"),
                    node("ElDescriptionsItem", {"label": "CPU 核心"}, "{{ sys.jvm?.processors }}"),
                    node("ElDescriptionsItem", {"label": "已运行"}, "{{ sys.jvm?.uptimeSeconds }} 秒"),
                ]),
            ]),
        ]),
    ]


def build_device():
    return build_simple_table("devices", "设备列表", [
        {"prop": "deviceName", "label": "设备名称", "width": 160},
        {"prop": "deviceType", "label": "类型", "width": 120},
        {"prop": "userName", "label": "所属用户", "width": 120},
        {"prop": "userId", "label": "用户ID", "minWidth": 200},
        {"prop": "status", "label": "状态", "width": 100},
        {"prop": "lastSync", "label": "最后同步", "width": 170},
        {"prop": "createTime", "label": "绑定时间", "width": 170},
    ], "设备列表")


def build_layout():
    return [
        node("ElContainer", {"class": "admin-layout"}, [
            node("ElAside", {"width": "220px", "class": "sidebar"}, [
                node("div", {"class": "logo"}, [
                    node("span", {"class": "logo-icon"}, "🌿"),
                    node("span", {"class": "logo-text"}, "节律守护"),
                ]),
                node("ElMenu", {":default-active": "route.path", "router": True,
                                "background-color": TOKENS["surface"],
                                "text-color": TOKENS["text2"],
                                "active-text-color": TOKENS["primaryDark"]}, [
                    node("ElMenuItem", {"index": "/dashboard"}, [
                        node("ElIcon", {}, [node("DataLine")]),
                        node("template", {"#title": ""}, [node("span", {}, "数据总览")]),
                    ]),
                    node("ElMenuItem", {"index": "/users"}, [
                        node("ElIcon", {}, [node("User")]),
                        node("template", {"#title": ""}, [node("span", {}, "用户管理")]),
                    ]),
                    node("ElMenuItem", {"index": "/alerts"}, [
                        node("ElIcon", {}, [node("Bell")]),
                        node("template", {"#title": ""}, [node("span", {}, "告警处置")]),
                    ]),
                    node("ElMenuItem", {"index": "/feedback"}, [
                        node("ElIcon", {}, [node("ChatDotRound")]),
                        node("template", {"#title": ""}, [node("span", {}, "反馈处理")]),
                    ]),
                    node("ElMenuItem", {"index": "/devices"}, [
                        node("ElIcon", {}, [node("Cpu")]),
                        node("template", {"#title": ""}, [node("span", {}, "设备管理")]),
                    ]),
                    node("ElMenuItem", {"index": "/system"}, [
                        node("ElIcon", {}, [node("Monitor")]),
                        node("template", {"#title": ""}, [node("span", {}, "系统监控")]),
                    ]),
                ]),
            ]),
            node("ElContainer", {}, [
                node("ElHeader", {"height": "60px", "class": "header"}, [
                    node("div", {"class": "header-title"}, "{{ route.meta.title }}"),
                    node("div", {"class": "header-right"}, [
                        node("span", {"class": "username"}, "{{ userStore.user?.name }}"),
                        node("ElButton", {"text": True, "@click": "logout"}, "退出登录"),
                    ]),
                ]),
                node("ElMain", {"class": "content"}, [
                    node("router-view", {}),
                ]),
            ]),
        ]),
    ]


PAGE_BUILDERS = [
    ("login", "登录", "/login", "独立布局", build_login),
    ("dashboard", "数据总览", "/dashboard", "AdminLayout", build_dashboard),
    ("users", "用户管理", "/users", "AdminLayout", build_users),
    ("alerts", "告警处置", "/alerts", "AdminLayout", build_alerts),
    ("feedback", "反馈处理", "/feedback", "AdminLayout", build_feedback),
    ("devices", "设备管理", "/devices", "AdminLayout", build_device),
    ("system", "系统监控", "/system", "AdminLayout", build_system),
]

pages = []
for pid, title, path, layout, builder in PAGE_BUILDERS:
    pages.append({
        "id": rid("p"),
        "name": pid,
        "title": title,
        "type": "page",
        "dir": False,
        "children": [],
        "raw": False,
        "pure": False,
        "hidden": False,
        "layout": layout,
        "cache": True,
        "meta": {"path": path, "layout": layout},
        "dsl": {
            "id": rid("b"),
            "name": pid,
            "version": "1.0.0",
            "platform": "web",
            "nodes": builder(),
            "css": "",
            "props": [],
            "emits": [],
            "slots": [],
        },
    })

project = {
    "id": rid("proj"),
    "name": "guardian-admin",
    "title": "AI 节律守护 · 后台管理",
    "description": "老年人健康守护小程序的管理后台",
    "platform": "web",
    "version": "1.0.0",
    "pages": pages,
    "blocks": [
        {
            "id": rid("blk"),
            "name": "AdminLayout",
            "title": "后台布局",
            "type": "block",
            "fromType": "schema",
            "dsl": {
                "id": rid("b"),
                "name": "AdminLayout",
                "nodes": build_layout(),
                "css": "",
            },
        }
    ],
    "dependencies": [
        {"name": "vue", "version": "^3.4.21"},
        {"name": "element-plus", "version": "^2.4.0"},
        {"name": "echarts", "version": "^5.4.0"},
    ],
    "apis": [
        {"method": "POST", "path": "/api/auth/send-code", "desc": "发送验证码"},
        {"method": "POST", "path": "/api/auth/login", "desc": "登录"},
        {"method": "GET", "path": "/api/admin/dashboard", "desc": "核心指标"},
        {"method": "GET", "path": "/api/admin/trend", "desc": "趋势数据"},
        {"method": "GET", "path": "/api/admin/users", "desc": "用户列表"},
        {"method": "GET", "path": "/api/admin/users/{id}", "desc": "用户详情"},
        {"method": "POST", "path": "/api/admin/users/set-admin", "desc": "设为/取消管理员"},
        {"method": "POST", "path": "/api/admin/users/set-active", "desc": "启用/禁用账号"},
        {"method": "GET", "path": "/api/admin/alerts", "desc": "告警列表"},
        {"method": "POST", "path": "/api/admin/alerts/handle", "desc": "处理告警"},
        {"method": "GET", "path": "/api/admin/feedback", "desc": "反馈列表"},
        {"method": "POST", "path": "/api/admin/feedback/reply", "desc": "回复反馈"},
        {"method": "GET", "path": "/api/admin/devices", "desc": "设备列表"},
        {"method": "GET", "path": "/api/admin/system", "desc": "系统监控"},
    ],
    "meta": {
        "framework": "Vue 3 + Element Plus",
        "designTokens": TOKENS,
        "sidebar": [
            {"path": "/dashboard", "title": "数据总览"},
            {"path": "/users", "title": "用户管理"},
            {"path": "/alerts", "title": "告警处置"},
            {"path": "/feedback", "title": "反馈处理"},
            {"path": "/devices", "title": "设备管理"},
            {"path": "/system", "title": "系统监控"},
        ],
    },
    "config": {
        "port": 5177,
        "proxy": {"/api": "http://localhost:3000"},
    },
    "globals": {},
    "i18n": {},
    "env": [],
}

out = os.path.join(DOCS, "智能无感-后台管理-设计稿元数据.json")
with open(out, "w", encoding="utf-8") as f:
    json.dump(project, f, ensure_ascii=False, indent=2)

size = os.path.getsize(out)
total_nodes = 0


def count(ns):
    n = 0
    for x in ns:
        if not isinstance(x, dict):
            # 字符串形式的文本子节点
            n += 1
            continue
        n += 1
        n += count(x.get("children", []))
    return n


for p in pages:
    total_nodes += count(p["dsl"]["nodes"])
total_nodes += count(project["blocks"][0]["dsl"]["nodes"])

print(f"② 设计稿元数据: {out}")
print(f"   页面 {len(pages)} 个，区块 1 个，节点 {total_nodes} 个，{size/1024:.0f} KB")
