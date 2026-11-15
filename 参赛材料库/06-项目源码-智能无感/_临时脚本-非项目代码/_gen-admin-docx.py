# -*- coding: utf-8 -*-
"""生成「智能无感 · 后台管理 · Coze 开发提示词」DOCX。"""
import json
import os

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Pt, RGBColor, Cm
from docx.oxml.ns import qn

ROOT = r"D:\msr150174\项目\智能无感"
DOCS = os.path.join(ROOT, "文档")
OUT = os.path.join(DOCS, "智能无感-后台管理-Coze开发提示词.docx")

with open(os.path.join(DOCS, "智能无感-后台管理-原型规格.json"), encoding="utf-8") as f:
    SPEC = json.load(f)

PRIMARY = RGBColor(0x5B, 0x9A, 0x7A)
DARK = RGBColor(0x2F, 0x2A, 0x24)
GRAY = RGBColor(0x7D, 0x74, 0x6B)


def set_font(run, name="微软雅黑", size=10.5, bold=False, color=None):
    run.font.name = name
    run.font.size = Pt(size)
    run.bold = bold
    if color:
        run.font.color.rgb = color
    # 中文字体必须显式设 eastAsia，否则渲染可能回退
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)


def H(doc, text, level=1):
    h = doc.add_heading(text, level=level)
    for r in h.runs:
        set_font(r, size={0: 20, 1: 15, 2: 12.5, 3: 11}[level],
                 bold=True, color=PRIMARY if level <= 2 else DARK)
    return h


def P(doc, text, size=10.5, bold=False, color=None, align=None, indent=0):
    p = doc.add_paragraph()
    p.paragraph_format.word_wrap = True
    if align:
        p.alignment = align
    if indent:
        p.paragraph_format.left_indent = Cm(indent)
    r = p.add_run(text)
    set_font(r, size=size, bold=bold, color=color)
    return p


def CODE(doc, text):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.5)
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(6)
    r = p.add_run(text)
    set_font(r, name="Consolas", size=8.5, color=RGBColor(0x1F, 0x4E, 0x39))
    # 长行自动换行，避免超出页宽被裁切
    p.paragraph_format.word_wrap = True
    return p


def TABLE(doc, headers, rows, widths=None):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Light Grid Accent 1"
    # 关掉 autofit，否则 Word/LibreOffice 会自动均分列宽，设了也不生效
    t.autofit = False

    # 单元格默认左右各有约 0.19cm 内边距，3 列就吃掉 1.1cm，
    # 会让表格超出右边界。这里统一收窄到 0.08cm。
    from docx.oxml.ns import qn as _qn
    from docx.oxml import OxmlElement
    tblPr = t._tbl.tblPr
    mar = OxmlElement("w:tblCellMar")
    for side, val in (("left", 45), ("right", 45), ("top", 20), ("bottom", 20)):
        e = OxmlElement(f"w:{side}")
        e.set(_qn("w:w"), str(val))
        e.set(_qn("w:type"), "dxa")
        mar.append(e)
    tblPr.append(mar)

    for i, h in enumerate(headers):
        cell = t.rows[0].cells[i]
        cell.text = ""
        r = cell.paragraphs[0].add_run(h)
        set_font(r, size=10, bold=True)
    for row in rows:
        cells = t.add_row().cells
        for i, v in enumerate(row):
            cells[i].text = ""
            r = cells[i].paragraphs[0].add_run(str(v))
            set_font(r, size=9.5)
    if widths:
        # 必须同时设 column.width 和每个 cell.width，只设其中一个不生效
        for i, w in enumerate(widths):
            t.columns[i].width = Cm(w)
            for row in t.rows:
                row.cells[i].width = Cm(w)
    return t


doc = Document()

# 页边距 + 纸张改成 A4
# 默认模板是 Letter(21.59×27.94cm)，国内文档应该用 A4(21.0×29.7cm)
from docx.shared import Mm
for s in doc.sections:
    s.page_width = Mm(210)
    s.page_height = Mm(297)
    s.left_margin = Cm(2.0)
    s.right_margin = Cm(2.0)
    s.top_margin = Cm(2.0)
    s.bottom_margin = Cm(2.0)

# A4 可用宽度 = 21.0 - 2.0 - 2.0 = 17.0 cm

# ============================== 封面 ==============================
# 标题拆两行：可用宽度只有 17.2cm，单行放不下 20pt 的中文长标题
title = doc.add_paragraph()
title.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = title.add_run("AI 节律守护 · 运营管理后台")
set_font(r, size=17, bold=True, color=PRIMARY)

sub = doc.add_paragraph()
sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = sub.add_run("Coze AI 开发提示词")
set_font(r, size=13, color=GRAY)

doc.add_paragraph()

meta = doc.add_paragraph()
meta.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = meta.add_run(
    f"版本 {SPEC['meta']['version']}    |    "
    f"{len(SPEC['pages'])} 个页面    |    "
    f"{len(SPEC['apiList'])} 个后端接口    |    "
    "Vue 3 + Element Plus"
)
set_font(r, size=9.5, color=GRAY)

doc.add_paragraph()

# ============================== 提示词正文 ==============================
H(doc, "一、直接复制给 Coze 的提示词", 1)

P(doc, "把下面整段内容复制粘贴给 Coze AI，它会生成可运行的完整项目。",
  size=10.5, color=GRAY)

prompt_text = f"""请帮我生成一个「AI 节律守护 · 运营管理后台」的完整可运行项目。

【技术栈】
- Vue 3 + <script setup> + TypeScript
- Vite 构建，Element Plus 组件库
- Vue Router 4 路由，Pinia 状态管理
- Axios 请求，ECharts 5 图表
- SCSS 样式
- 禁止使用：React、Nuxt、SSR、Tailwind、Ant Design

【项目信息】
- 项目名：guardian-admin
- 开发端口：5177
- 后端地址：http://localhost:3000/api（已开发完成，{len(SPEC['apiList'])} 个接口全部可用，不需要 Mock）
- 代理配置：Vite proxy 把 /api 转发到 http://localhost:3000

【设计风格】
- 主色：{SPEC['designTokens']['primary']}（柔和绿）
- 背景：{SPEC['designTokens']['bg']}（暖米色）
- 卡片：{SPEC['designTokens']['card']}（白色）
- 侧边栏：{SPEC['designTokens']['surface']}
- 文字：主 {SPEC['designTokens']['text1']} / 次 {SPEC['designTokens']['text2']}
- 圆角：{SPEC['designTokens']['radius']}
- 整体风格：温暖、清爽、适合健康类产品，不要冷冰冰的蓝色后台

【布局】
左侧固定侧边栏（宽 220px）+ 顶部栏（高 60px）+ 内容区
侧边栏菜单：
  数据总览 /dashboard
  用户管理 /users
  告警处置 /alerts
  反馈处理 /feedback
  设备管理 /devices
  系统监控 /system

【统一响应格式】
所有接口返回 {{ code, msg, data }}，HTTP 状态码恒为 200。
判断成功看 code === 200，不要用 HTTP 状态码判断。

【登录页 /login】
- 独立布局，不用 AdminLayout
- 手机号输入框（校验 ^1[3-9]\\d{{9}}$）
- 验证码输入框 + 获取验证码按钮（60 秒倒计时）
- 调 POST /api/auth/send-code 和 POST /api/auth/login
- 登录成功后检查 user.isAdmin：
    isAdmin=true  → 存 token 到 localStorage['guardian_admin_token']，跳 /dashboard
    isAdmin=false → 提示「该账号不是管理员」并清除 token
- 开发模式下验证码打印在后端控制台

【数据总览 /dashboard】
- 7 个指标卡片（两行）：
    第一行 4 个：用户总数、长辈用户、家属用户、本周新增
    第二行 3 个：近 7 天告警、今日打卡、管理员数
- 一张 ECharts 折线图（高 320px）：近 7 天新增用户 + 告警数双线
- 数据源：GET /api/admin/dashboard 和 GET /api/admin/trend?days=7

【用户管理 /users】
- 筛选栏：关键词（手机号/姓名）、角色、身份（管理员/普通）、状态（启用/禁用）
- 表格列：姓名、手机号、角色、身份、状态、注册时间、操作
- 操作按钮：详情、设为管理员/取消管理员、禁用/启用
- 所有写操作必须二次确认（ElMessageBox）
- 分页用后端分页，pageSize 可选 10/20/50/100
- 点击「详情」弹窗显示：姓名、手机号、角色、近 30 天打卡、绑定设备、未处理告警、注册时间、地址

【告警处置 /alerts】
- 筛选栏：状态（未处理/已处理）、级别（紧急/高/中/低）
- 表格列：级别、类型、标题、描述、用户、手机号、发生时间、状态、操作
- 未处理的告警行可用淡红色背景区分
- 点「处理」弹窗填写处理说明，调 POST /api/admin/alerts/handle

【反馈处理 /feedback】
- 表格列：用户、手机号、标题、内容、评分、提交时间、状态、操作
- 点「回复」弹窗填写回复内容，调 POST /api/admin/feedback/reply

【设备管理 /devices】
- 表格列：设备名称、类型、所属用户、用户ID、状态、最后同步、绑定时间
- 数据源：GET /api/admin/devices

【系统监控 /system】
- 三组描述列表：数据库（MySQL）、缓存（Redis）、运行时（JVM）
- 显示连接状态、用户数、键数量、Java 版本、内存、CPU 核心、已运行时长
- 数据源：GET /api/admin/system
- 每 30 秒自动刷新（可开关）

【代码要求】
- 用 <script setup lang="ts">，不要 Options API
- 所有接口封装在 src/api/index.ts，组件里不直接写 axios
- 请求拦截器自动带上 Authorization: Bearer {{token}}
- 响应拦截器统一处理：401 清 token 跳登录，403 提示无权限，其他错误 ElMessage.error(msg)
- 表格分页用后端分页
- 禁止 any，类型定义放 src/types
- 代码完整可运行，不要 TODO 或占位符

【目录结构】
src/main.ts              入口
src/App.vue              根组件
src/router/index.ts      路由 + 登录守卫
src/layouts/AdminLayout.vue
src/views/Login.vue
src/views/Dashboard.vue
src/views/Users.vue
src/views/Alerts.vue
src/views/Feedback.vue
src/views/Devices.vue
src/views/System.vue
src/api/index.ts         Axios 封装
src/stores/user.ts
src/styles/index.scss

请直接输出所有文件的完整代码。"""

CODE(doc, prompt_text)

doc.add_page_break()

# ============================== 接口清单 ==============================
H(doc, "二、后端接口清单（已实测可用）", 1)

P(doc, "以下接口全部已在后端实现并验证通过，Coze 生成时直接调用即可。",
  size=10, color=GRAY)

rows = [[a["method"], a["path"], a["desc"]] for a in SPEC["apiList"]]
TABLE(doc, ["方法", "路径", "说明"], rows, widths=[1.6, 6.6, 8.6])

doc.add_paragraph()
P(doc, "所有 /admin/* 接口都需要管理员身份（users.is_admin = 1），"
       "非管理员访问返回 code=403。", size=10, color=GRAY)

doc.add_page_break()

# ============================== 页面规格 ==============================
H(doc, "三、页面规格明细", 1)

for pg in SPEC["pages"]:
    H(doc, f"{pg['title']}  {pg['path']}", 2)

    info = [["优先级", pg.get("priority", "-")],
            ["布局", pg.get("layout", "-")]]
    TABLE(doc, ["项", "值"], info, widths=[3.0, 13.8])
    doc.add_paragraph()

    if "elements" in pg:
        P(doc, "页面元素：", size=10, bold=True)
        for el in pg["elements"]:
            desc = el.get("content") or el.get("label") or el.get("text") or el.get("type")
            P(doc, f"· {el['type']}：{desc}", size=10, indent=0.5)

    if "sections" in pg:
        P(doc, "区块：", size=10, bold=True)
        for sec in pg["sections"]:
            line = f"· {sec['type']}"
            if sec.get("title"):
                line += f"  「{sec['title']}」"
            if sec.get("source"):
                line += f"   数据源 {sec['source']}"
            P(doc, line, size=10, indent=0.5)

            for item in sec.get("items", []):
                P(doc, f"    - {item.get('label', '')} ({item.get('key', '')})",
                  size=9.5, color=GRAY, indent=1.0)

            cols = sec.get("columns")
            if isinstance(cols, int):
                P(doc, f"    分 {cols} 列", size=9.5, color=GRAY, indent=1.0)
            elif isinstance(cols, list):
                for col in cols:
                    P(doc, f"    - 列 {col.get('label')} ({col.get('prop', '-')})",
                      size=9.5, color=GRAY, indent=1.0)

    if "interactions" in pg:
        P(doc, "交互：", size=10, bold=True)
        for it in pg["interactions"]:
            P(doc, f"· {it['trigger']} → {it['action']}", size=10, indent=0.5)

    doc.add_paragraph()

doc.add_page_break()

# ============================== 验收标准 ==============================
H(doc, "四、验收标准", 1)

P(doc, "生成的项目必须满足以下全部条件：", size=10, color=GRAY)
for i, ac in enumerate(SPEC["acceptanceCriteria"], 1):
    P(doc, f"{i}. {ac}", size=10)

doc.add_page_break()

# ============================== 关键规则 ==============================
H(doc, "五、关键规则（易错点）", 1)

for rule in SPEC["criticalRules"]:
    P(doc, rule.replace("★ ", ""), size=10.5)

doc.add_paragraph()

H(doc, "六、测试账号", 1)
TABLE(doc, ["项", "值"], [
    ["管理员手机号", SPEC["externalRefs"]["testAdmin"]["phone"]],
    ["验证码", "打印在后端控制台（开发模式）"],
    ["后端健康检查", SPEC["externalRefs"]["backendHealth"]],
    ["token 存储键", SPEC["externalRefs"]["adminTokenKey"]],
], widths=[4.0, 12.8])

doc.add_paragraph()
P(doc, "提示：验证码有 60 秒频率限制，连续测试时请更换手机号。",
  size=9.5, color=GRAY)

os.makedirs(DOCS, exist_ok=True)
doc.save(OUT)
print(f"已生成: {OUT}")
print(f"大小: {os.path.getsize(OUT)/1024:.0f} KB")
