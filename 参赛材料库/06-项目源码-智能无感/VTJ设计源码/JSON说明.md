# 智能无感 · VTJ 原型 JSON 说明

## 文件

| 文件 | 说明 |
|---|---|
| **智能无感-原型.json** | 完整项目（36 页面 / 3230 节点 / 57 接口） |
| 智能无感-原型-精简.json | 只含结构与样式，不含脚本细节（体积更小） |

---

## 怎么用

### 方式一：导入 VTJ 设计器

1. 打开 <https://app.vtj.pro/dev/web/>
2. 新建项目（或打开已有项目）
3. 找到「导入 / Import」入口
4. 选择 `智能无感-原型.json`

### 方式二：交给 AI 助手

如果导入失败，可以把 JSON 直接丢给 VTJ 的 AI 助手：

> 这是我的项目 DSL，请帮我导入并创建对应页面

### 方式三：自己看结构

JSON 是标准 UTF-8，可以用任何编辑器打开。结构：

```jsonc
{
  "id": "...",
  "name": "AI节律守护",
  "platform": "web",
  "pages": [
    {
      "id": "...",
      "name": "home",
      "title": "节律守护",
      "type": "page",
      "meta": { "path": "/pages/home/index", "isTabBar": true },
      "dsl": {
        "state": [...],      // 响应式状态
        "computed": [...],   // 计算属性
        "methods": [...],    // 方法
        "lifeCycles": [...], // 生命周期
        "apis": [...],       // 调用的接口
        "nodes": [...],      // 节点树
        "css": "..."         // 样式
      }
    }
  ],
  "apis": [...],
  "meta": { "designTokens": {...}, "tabBar": [...] }
}
```

---

## 数据来源

**从这 36 个 uni-app 页面自动转换而来**：

```
D:\msr150174\项目\智能无感\转换-uniapp前端\guardian-uniapp\src\pages\
```

**转换内容**：

| 原文件部分 | 转成 | 结果 |
|---|---|---|
| `<template>` | `dsl.nodes` | **3230 个节点** |
| 静态文字 | `text` 节点的 `content` | **723 处** |
| `{{ 表达式 }}` | `expression: true` 绑定 | **387 处** |
| `<script setup>` | `state` / `computed` / `methods` / `lifeCycles` | — |
| `xxxApi.yyy()` | `dsl.apis` + 项目级 `apis` | **57 个接口** |
| `<style scoped>` | `dsl.css` | 每个页面都有 |

**标签映射**（uni-app → Web）：

| uni-app | Web |
|---|---|
| `<view>` | `<div>` |
| `<text>` | `<span>` |
| `<image>` | `<img>` |
| `<scroll-view>` | `<div>` |

> 原始 uni-app 标签名**保留在每个节点的 `__uniTag` 属性里**，方便对照。

---

## 5 个 tabBar 页面

| 顺序 | 页面 | 标题 | 节点数 |
|---|---|---|---|
| 1 | home | 节律守护 | 68 |
| 2 | guardian | 居家守护 | 178 |
| 3 | monitor | 健康监测 | 145 |
| 4 | ai-chat | 智能助手 | 82 |
| 5 | profile | 个人中心 | 69 |

---

## 设计令牌

```json
{
  "primary":       "#8FA68E",
  "background":    "#F4EFE6",
  "surface":       "#FFF9EF",
  "textPrimary":   "#2F2A24",
  "textSecondary": "#7D746B",
  "warning":       "#D4A574",
  "danger":        "#E55A5A",
  "radius":        "24rpx",
  "shadow":        "0 2px 12px rgba(0,0,0,0.08)"
}
```

---

## ⚠️ 已知限制

写清楚，免得你导入后觉得「怎么不对」：

| # | 限制 | 说明 |
|---|---|---|
| 1 | **组件是 HTML 标签，不是 Element Plus** | 转出来的是 `div` / `span`，不是 `<ElButton>`。因为源项目是 uni-app，用了自研组件而非 EP |
| 2 | **脚本只有签名，没有函数体** | `methods` 里是 `{name, params}`，不含实现。要跑逻辑需自己补 |
| 3 | **样式是 rpx，不是 px** | 小程序单位。Web 端需按 1px = 2rpx 换算 |
| 4 | **接口是声明，不是真实请求** | `apis` 只是清单，没绑真实 HTTP |
| 5 | **VTJ 私有字段名可能有出入** | DSL 规范我按官方文档写的，但 `props` 编码、`from` 枚举这些细节文档没公开 |

**第 5 点最重要** —— 如果导入报错，把**报错信息**发我，我按真实格式改。

---

## 转换脚本

生成脚本在：

```
D:\msr150174\项目\智能无感\_gen-json.py
```

改动后重新跑：

```powershell
python _gen-json.py
```

**自动做**：
1. 扫描 36 个 `.vue`
2. 解析 template / script / style
3. 组装成 `ProjectSchema`
4. 输出 JSON

**新增页面**：在 `src/pages/` 下建目录，重跑脚本即可。
