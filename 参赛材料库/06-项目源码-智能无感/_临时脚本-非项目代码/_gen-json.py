# -*- coding: utf-8 -*-
"""
把智能无感的 36 个 uni-app 页面转成 VTJ 的 ProjectSchema JSON。

依据：VTJ 官方 DSL 规范
  ProjectSchema = { id, name, platform, pages[], blocks[], dependencies[], apis[], meta, config, globals, i18n, env }
  PageFile      = { id, name, title, type:"page", dir, children[], dsl: BlockSchema, raw, pure, hidden, layout, cache, meta }
  BlockSchema   = { id, name, state, computed, watch, methods, lifeCycles, nodes[], css, props, emits, slots, inject, expose }
  NodeSchema    = { id, name, from, locked, invisible, props, events, directives, children, slot }

转换策略：
  1. 解析每个 .vue 的 <template>，把 uni-app 标签映射成 Web/Element Plus 标签
  2. 解析 <script setup>，抽取 reactive 状态、computed、函数
  3. 解析 <style scoped>，转成 css 字段
  4. 组装成 ProjectSchema
"""
import json
import os
import re
import uuid

FE = r"D:\msr150174\项目\智能无感\转换-uniapp前端\guardian-uniapp"
OUT = r"D:\msr150174\项目\智能无感\VTJ设计源码\智能无感-原型.json"

# ============================================================================
# 标签映射：uni-app → Web（VTJ 渲染到 H5 时用标准标签）
# ============================================================================
TAG_MAP = {
    "view": "div",
    "text": "span",
    "image": "img",
    "scroll-view": "div",
    "swiper": "div",
    "swiper-item": "div",
    "button": "button",
    "input": "input",
    "textarea": "textarea",
    "picker": "select",
    "switch": "input",
    "checkbox": "input",
    "radio": "input",
    "navigator": "a",
    "rich-text": "div",
    "canvas": "canvas",
    "video": "video",
    "audio": "audio",
    "map": "div",
    "web-view": "iframe",
    "form": "form",
    "label": "label",
    "slot": "slot",
    "template": "template",
    "block": "template",
}

# 页面名 → 中文标题
PAGE_TITLES = {
    "home": "节律守护", "guardian": "居家守护", "monitor": "健康监测",
    "ai-chat": "智能助手", "profile": "个人中心",
    "login": "登录", "register": "注册", "forgot-password": "找回密码",
    "guardian-panel": "守护面板", "health-report": "健康报告",
    "health-detail": "健康详情", "ai-sleep-analysis": "睡眠分析",
    "family": "家庭成员", "member-bind": "成员绑定", "elder-info": "长辈档案",
    "medicine-reminder": "用药提醒", "medication-history": "服药记录",
    "diet-record": "饮食记录", "exercise-record": "运动记录",
    "sos-map": "紧急求助", "alert-history": "告警历史",
    "emergency-contacts": "紧急联系人", "location-detail": "位置详情",
    "device": "我的设备", "bluetooth-connection": "蓝牙连接",
    "monitor-history": "监测历史", "voice-monitor": "语音监测",
    "video-monitor": "视频监测", "settings": "设置",
    "simple-mode": "长辈极简模式", "privacy-security": "隐私安全",
    "about": "关于我们", "user-agreement": "用户服务协议",
    "privacy-policy": "隐私政策", "voiceprint-agreement": "声纹授权协议",
    "debug-log": "调试日志",
}

TABBAR = ["home", "guardian", "monitor", "ai-chat", "profile"]


def rid(prefix=""):
    """生成短 ID（VTJ 内部用短字符串 id）"""
    return (prefix + uuid.uuid4().hex[:10])


# ============================================================================
# 模板解析
# ============================================================================

TAG_RE = re.compile(r"<(/?)([a-zA-Z][\w-]*)((?:\s+[^<>]*?)?)(/?)>", re.S)
ATTR_RE = re.compile(r"([:@]?[\w-]+)\s*=\s*(\"[^\"]*\"|'[^']*'|\{[^}]*\})")

# {{ expr }} 插值
INTERP_RE = re.compile(r"\{\{(.*?)\}\}", re.S)

# 这些标签内的文字是代码/样式，不能当正文
NO_TEXT_TAGS = {"script", "style"}


def parse_attrs(raw):
    """解析标签属性，返回 (props, events, directives)"""
    props, events, directives = [], [], []
    for m in ATTR_RE.finditer(raw):
        name, val = m.group(1), m.group(2)
        if val.startswith(('"', "'")):
            val = val[1:-1]
            is_expr = False
        else:
            val = val[1:-1]
            is_expr = True

        if name.startswith("@"):
            events.append({"name": name[1:], "handler": val})
        elif name.startswith("v-"):
            directives.append({"name": name, "value": val})
        elif name.startswith(":"):
            props.append({"name": name[1:], "value": val, "expression": is_expr})
        else:
            props.append({"name": name, "value": val, "expression": is_expr})
    return props, events, directives


def make_text_node(text):
    """
    把 <view> / <text> 之间的纯文本变成一个 text 节点。

    模板里的文字有两种形态：
      1. 静态文字   "暂无告警记录"
      2. 插值表达式 "{{ state.list.length }} 条"
    插值要转成动态绑定（expression=True），否则渲染出来就是一串花括号。
    """
    text = text.strip()
    if not text:
        return None

    node = {
        "id": rid("t"),
        "name": "text",
        "from": "text",
        "props": [],
        "events": [],
        "directives": [],
        "children": []
    }

    # 整段就是一个插值 → 纯动态绑定
    #
    # ★ 这里必须显式检查：不能用 INTERP_RE.fullmatch 直接判断。
    #   fullmatch 加上惰性 .*? 仍然允许跨过 }} 继续匹配到最后一组，
    #   于是 "{{ A }} {{ B }}" 会被误判成单个表达式 "A }} {{ B"，
    #   渲染出来就是一串残缺的花括号。
    whole = re.fullmatch(r"\{\{\s*(.*?)\s*\}\}", text, re.S)
    if whole:
        inner = whole.group(1)
        if "}}" not in inner and "{{" not in inner:
            node["props"].append({
                "name": "content",
                "value": inner.strip(),
                "expression": True
            })
            return node
        # 落到下面按混合内容处理
        whole = None

    # 混合内容（静态 + 插值）→ 拆成 parts
    if "{{" in text:
        parts, last = [], 0
        for m in INTERP_RE.finditer(text):
            if m.start() > last:
                seg = text[last:m.start()]
                if seg:
                    parts.append({"type": "static", "value": seg})
            parts.append({"type": "expression", "value": m.group(1).strip()})
            last = m.end()
        if last < len(text):
            parts.append({"type": "static", "value": text[last:]})

        # 兜底：若还有不成对的 {{ 或 }}，说明插值跨行被切断了。
        # 这种情况下把整段当成一个原始字符串，总比吐出一堆花括号强。
        leftover = any("{{" in p.get("value", "") or "}}" in p.get("value", "")
                       for p in parts if p["type"] == "static")

        node["props"].append({"name": "content", "value": "", "expression": False})
        if leftover or len(parts) == 0:
            node["props"].append({
                "name": "content",
                "value": re.sub(r"\{\{|\}\}", "", text).strip(),
                "expression": False
            })
        else:
            node["props"].append({"name": "parts", "value": parts, "expression": True})
        return node

    # 纯静态文字
    node["props"].append({"name": "content", "value": text, "expression": False})
    return node


def parse_template(tpl):
    """
    把 uni-app 模板解析成 NodeSchema 树。

    手写一个简单的栈式解析器：够用、无依赖、能看到每一步。
    """
    root = {"id": rid("n"), "name": "div", "from": "div", "props": [], "events": [],
            "directives": [], "children": []}
    stack = [root]
    pos = 0

    # 先剥掉注释
    tpl = re.sub(r"<!--.*?-->", "", tpl, flags=re.S)

    while True:
        m = TAG_RE.search(tpl, pos)
        if not m:
            break

        closing, tag, raw_attrs, self_close = m.group(1), m.group(2), m.group(3), m.group(4)

        if closing:
            # 闭合标签：把闭合前那段文字挂到当前节点上
            inner = tpl[pos:m.start()]
            if inner.strip() and tag not in NO_TEXT_TAGS:
                tn = make_text_node(inner)
                if tn:
                    stack[-1]["children"].append(tn)
            if len(stack) > 1:
                stack.pop()
            pos = m.end()
            continue

        # 收集标签之间的纯文本
        text_between = tpl[pos:m.start()]
        if text_between.strip() and stack[-1]["name"] not in NO_TEXT_TAGS:
            tn = make_text_node(text_between)
            if tn:
                stack[-1]["children"].append(tn)

        props, events, directives = parse_attrs(raw_attrs)
        mapped = TAG_MAP.get(tag, tag)

        node = {
            "id": rid("n"),
            "name": mapped,
            "from": "div" if mapped in ("div", "span", "template") else mapped,
            "props": props,
            "events": events,
            "directives": directives,
            "children": []
        }
        # 保留原始 uni-app 标签名，便于人工核对
        node["props"].append({"name": "__uniTag", "value": tag, "expression": False})

        stack[-1]["children"].append(node)

        if not self_close and tag not in ("input", "img", "br", "hr", "image"):
            stack.append(node)

        pos = m.end()

    # 尾部残余文本
    tail = tpl[pos:]
    if tail.strip() and not tail.lstrip().startswith("<"):
        tn = make_text_node(tail)
        if tn:
            stack[-1]["children"].append(tn)

    return root["children"]


# ============================================================================
# 脚本解析
# ============================================================================
REACTIVE_RE = re.compile(r"const\s+(\w+)\s*=\s*reactive\(\s*(\{.*?\})\s*\)", re.S)
COMPUTED_RE = re.compile(r"const\s+(\w+)\s*=\s*computed\(\s*\(\)\s*=>\s*(.*?)\s*\)", re.S)
FUNC_RE = re.compile(r"(?:async\s+)?function\s+(\w+)\s*\(([^)]*)\)\s*\{", re.S)
ARROW_RE = re.compile(r"const\s+(\w+)\s*=\s*(?:async\s*)?\(([^)]*)\)\s*=>")


def parse_script(script):
    """从 <script setup> 抽取状态 / 计算属性 / 方法"""
    state, computed_list, methods, lifecycles = [], [], [], []

    # reactive 状态 —— 提取字段名
    for m in REACTIVE_RE.finditer(script):
        var_name, body = m.group(1), m.group(2)
        fields = re.findall(r"(\w+)\s*:", body)
        state.append({
            "name": var_name,
            "type": "reactive",
            "fields": fields[:40]
        })

    # ref 状态
    for m in re.finditer(r"const\s+(\w+)\s*=\s*ref\(([^)]*)\)", script):
        state.append({"name": m.group(1), "type": "ref", "fields": []})

    # computed
    for m in COMPUTED_RE.finditer(script):
        computed_list.append({"name": m.group(1), "code": m.group(2)[:200]})

    # 函数
    for m in FUNC_RE.finditer(script):
        methods.append({"name": m.group(1), "params": m.group(2)[:80]})
    for m in ARROW_RE.finditer(script):
        if m.group(1) not in [x["name"] for x in methods]:
            methods.append({"name": m.group(1), "params": m.group(2)[:80]})

    # 生命周期
    for lc in ("onLoad", "onShow", "onUnload", "onPullDownRefresh", "onReachBottom"):
        if re.search(r"\b" + lc + r"\s*\(", script):
            lifecycles.append(lc)

    # 接口调用
    apis = sorted(set(re.findall(r"(\w+Api)\.(\w+)\s*\(", script)))
    api_list = [f"{a}.{b}" for a, b in apis]

    return state, computed_list, methods, lifecycles, api_list


# ============================================================================
# 主流程
# ============================================================================
def main():
    pages_dir = os.path.join(FE, "src", "pages")
    pages = []

    order = TABBAR + [d for d in sorted(os.listdir(pages_dir))
                      if os.path.isdir(os.path.join(pages_dir, d)) and d not in TABBAR]

    all_apis = set()

    for idx, name in enumerate(order):
        f = os.path.join(pages_dir, name, "index.vue")
        if not os.path.exists(f):
            continue

        with open(f, encoding="utf-8") as fp:
            src = fp.read()

        # 切三段
        tpl_m = re.search(r"<template>(.*?)</template>", src, re.S)
        scr_m = re.search(r"<script[^>]*>(.*?)</script>", src, re.S)
        sty_m = re.search(r"<style[^>]*>(.*?)</style>", src, re.S)

        tpl = tpl_m.group(1) if tpl_m else ""
        scr = scr_m.group(1) if scr_m else ""
        sty = sty_m.group(1) if sty_m else ""

        nodes = parse_template(tpl)
        state, computed_list, methods, lifecycles, api_list = parse_script(scr)
        all_apis.update(api_list)

        pages.append({
            "id": rid("p"),
            "name": name,
            "title": PAGE_TITLES.get(name, name),
            "type": "page",
            "dir": False,
            "children": [],
            "raw": False,
            "pure": False,
            "hidden": False,
            "layout": name in TABBAR,
            "cache": name in TABBAR,
            "meta": {
                "path": f"/pages/{name}/index",
                "isTabBar": name in TABBAR,
                "tabIndex": TABBAR.index(name) if name in TABBAR else -1,
                "order": idx,
                "sourceFile": f"src/pages/{name}/index.vue",
                "lineCount": len(src.splitlines())
            },
            "dsl": {
                "id": rid("b"),
                "name": name,
                "version": "1.0.0",
                "platform": "web",
                "state": state,
                "computed": computed_list,
                "watch": [],
                "methods": methods,
                "lifeCycles": lifecycles,
                "apis": api_list,
                "nodes": nodes,
                "css": sty.strip()[:20000],
                "props": [],
                "emits": [],
                "expose": [],
                "slots": [],
                "inject": []
            }
        })

    project = {
        "id": rid("proj"),
        "name": "AI节律守护",
        "title": "AI节律守护 · 智能无感",
        "description": "老年人健康守护小程序 —— 节律打卡 / 居家守护 / 健康监测 / AI 助手",
        "platform": "web",
        "version": "1.0.0",
        "pages": pages,
        "blocks": [],
        "dependencies": [
            {"name": "vue", "version": "^3.4.21"},
            {"name": "element-plus", "version": "^2.4.0"},
            {"name": "echarts", "version": "^5.4.0"}
        ],
        "apis": [
            {"id": rid("api"), "name": a, "category": a.split(".")[0]}
            for a in sorted(all_apis)
        ],
        "meta": {
            "framework": "uni-app (Vue3)",
            "uiLibrary": "Element Plus",
            "convertFrom": "Taro + React",
            "designTokens": {
                "primary": "#8FA68E",
                "background": "#F4EFE6",
                "surface": "#FFF9EF",
                "textPrimary": "#2F2A24",
                "textSecondary": "#7D746B",
                "warning": "#D4A574",
                "danger": "#E55A5A",
                "radius": "24rpx",
                "shadow": "0 2px 12px rgba(0,0,0,0.08)"
            },
            "tabBar": [
                {"page": f"pages/{t}/index", "text": PAGE_TITLES[t], "index": i}
                for i, t in enumerate(TABBAR)
            ]
        },
        "config": {
            "navigationBarBackgroundColor": "#F4EFE6",
            "navigationBarTextStyle": "black",
            "navigationBarTitleText": "AI节律守护",
            "backgroundColor": "#F4EFE6"
        },
        "globals": {},
        "i18n": {},
        "env": []
    }

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(project, f, ensure_ascii=False, indent=2)

    size = os.path.getsize(OUT)
    print(f"pages      : {len(pages)}")
    print(f"apis       : {len(project['apis'])}")
    print(f"output     : {OUT}")
    print(f"size       : {size/1024:.0f} KB")


if __name__ == "__main__":
    main()
